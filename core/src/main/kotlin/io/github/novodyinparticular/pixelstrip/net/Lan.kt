package io.github.novodyinparticular.pixelstrip.net

import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.JsonWriter
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Head-to-head over the local network, with no server: one phone hosts (listens on [PORT] and
 * announces itself by UDP broadcast on [DISCOVERY_PORT]), the other finds it and connects.
 * Messages are one JSON object per line over the TCP connection.
 */
object Lan {
    const val PORT = 47014
    const val DISCOVERY_PORT = 47013
    const val MAGIC = "PIXELSTRIP"
    /** Bumped when the messages change: both phones must speak the same one. */
    const val PROTOCOL = 1
    /** No line from the other side for this long: the connection is treated as dropped. */
    const val TIMEOUT_S = 10f

    /** This device's IPv4 addresses on the local network (what the other player can type in). */
    fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && VIRTUAL.none { v -> it.name.startsWith(v) } }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .map { it.hostAddress }
            // home Wi-Fi is nearly always 192.168.x or 10.x; 172.16-31 is mostly containers and VPNs
            .sortedBy { if (it.startsWith("192.168.")) 0 else if (it.startsWith("10.")) 1 else 2 }
    }.getOrDefault(emptyList())

    /** Interfaces that are never the Wi-Fi the other phone is on (containers, VMs, VPNs). */
    private val VIRTUAL = listOf("docker", "br-", "veth", "virbr", "vmnet", "vboxnet", "tailscale", "tun", "wg", "zt", "lxc", "cni", "flannel")

    private fun broadcastAddresses(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { it.broadcast }
    }.getOrDefault(emptyList())

    fun obj(vararg pairs: Pair<String, Any?>): JsonValue = JsonValue(JsonValue.ValueType.`object`).also { o ->
        for ((k, v) in pairs) when (v) {
            null -> {}
            is JsonValue -> o.addChild(k, v)
            is String -> o.addChild(k, JsonValue(v))
            is Boolean -> o.addChild(k, JsonValue(v))
            is Int -> o.addChild(k, JsonValue(v.toLong()))
            is Long -> o.addChild(k, JsonValue(v))
            is Float -> o.addChild(k, JsonValue(v.toDouble()))
            is Double -> o.addChild(k, JsonValue(v))
            else -> o.addChild(k, JsonValue(v.toString()))
        }
    }

    /** Announces a hosted game once a second until [close]d. */
    class Beacon(private val port: Int, private val name: String) {
        @Volatile private var running = true
        private val thread = Thread({ run() }, "lan-beacon").apply { isDaemon = true; start() }

        private fun run() {
            runCatching {
                DatagramSocket().use { s ->
                    s.broadcast = true
                    while (running) {
                        val msg = "$MAGIC $PROTOCOL $port ${name.replace(' ', '_')}".toByteArray(Charsets.UTF_8)
                        val targets = (listOf(InetAddress.getByName("255.255.255.255"), InetAddress.getLoopbackAddress()) + broadcastAddresses()).distinct()
                        for (a in targets) runCatching { s.send(DatagramPacket(msg, msg.size, a, DISCOVERY_PORT)) }
                        Thread.sleep(1000)
                    }
                }
            }
        }

        fun close() {
            running = false
        }
    }

    /** A game someone is hosting, as heard from their beacon. */
    class Found(val address: String, val port: Int, val name: String, val protocol: Int, @Volatile var seen: Long)

    /** Listens for beacons. */
    class Finder {
        private val hosts = ConcurrentHashMap<String, Found>()
        @Volatile private var running = true
        @Volatile var error: String? = null
            private set
        private var socket: DatagramSocket? = null
        private val thread = Thread({ run() }, "lan-finder").apply { isDaemon = true; start() }

        private fun run() {
            try {
                val s = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 500
                    bind(InetSocketAddress(DISCOVERY_PORT))
                }
                socket = s
                val buf = ByteArray(256)
                while (running) {
                    val p = DatagramPacket(buf, buf.size)
                    try {
                        s.receive(p)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val parts = String(p.data, 0, p.length, Charsets.UTF_8).trim().split(' ')
                    if (parts.size < 4 || parts[0] != MAGIC) continue
                    val protocol = parts[1].toIntOrNull() ?: continue
                    val port = parts[2].toIntOrNull() ?: continue
                    // the same host heard over loopback and Wi-Fi: keep the real address when there is one
                    val addr = p.address.hostAddress
                    if (p.address.isLoopbackAddress && hosts.values.any { !InetAddress.getByName(it.address).isLoopbackAddress && it.port == port && it.name == parts[3] }) continue
                    hosts[addr] = Found(addr, port, parts[3].replace('_', ' '), protocol, System.currentTimeMillis())
                }
                s.close()
            } catch (e: Exception) {
                if (running) error = "CAN'T LISTEN FOR GAMES: " + (e.message ?: "network error")
            }
        }

        /** Games heard from in the last few seconds, nearest first. */
        fun list(): List<Found> {
            val cutoff = System.currentTimeMillis() - 4000
            return hosts.values.filter { it.seen >= cutoff }.sortedBy { it.address }
        }

        fun close() {
            running = false
            runCatching { socket?.close() }
        }
    }

    /** Waits for one racer to connect. */
    class Host(name: String) {
        private val server: ServerSocket = try {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(PORT)) }
        } catch (_: Exception) {
            ServerSocket(0)            // the usual port is taken (another copy running): any port, the beacon says which
        }
        val port: Int = server.localPort
        private val beacon = Beacon(port, name)
        @Volatile var link: Link? = null
            private set
        @Volatile var error: String? = null
            private set

        init {
            Thread({
                try {
                    val s = server.accept()
                    link = Link(s, host = true)
                } catch (e: Exception) {
                    if (!server.isClosed) error = e.message ?: "network error"
                } finally {
                    beacon.close()
                    runCatching { server.close() }
                }
            }, "lan-host").apply { isDaemon = true; start() }
        }

        fun close() {
            beacon.close()
            runCatching { server.close() }
        }
    }

    /** Connects to a host in the background; [link] or [error] fills in. */
    class Connect(address: String, port: Int) {
        @Volatile var link: Link? = null
            private set
        @Volatile var error: String? = null
            private set

        init {
            Thread({
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress(address, port), 5000)
                    link = Link(s, host = false)
                } catch (e: Exception) {
                    error = e.message ?: "can't connect"
                }
            }, "lan-connect").apply { isDaemon = true; start() }
        }
    }

    /** The connection: lines in on a reader thread, lines out on a writer thread, a ping every second. */
    class Link(private val socket: Socket, val host: Boolean) {
        private val inbox = ConcurrentLinkedQueue<JsonValue>()
        private val outbox = LinkedBlockingQueue<String>()
        @Volatile var closed = false
            private set
        @Volatile private var lastHeard = System.nanoTime()
        val peer: String = socket.inetAddress.hostAddress

        init {
            socket.tcpNoDelay = true
            Thread({ read() }, "lan-read").apply { isDaemon = true; start() }
            Thread({ write() }, "lan-write").apply { isDaemon = true; start() }
        }

        private fun read() {
            try {
                val r = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val parser = JsonReader()
                while (!closed) {
                    val line = r.readLine() ?: break
                    lastHeard = System.nanoTime()
                    if (line.isBlank()) continue
                    val j = runCatching { parser.parse(line) }.getOrNull() ?: continue
                    if (j.getString("k", "") == "ping") continue
                    inbox.add(j)
                }
            } catch (_: Exception) {
            }
            close()
        }

        private fun write() {
            try {
                val w = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                while (!closed) {
                    val line = outbox.poll(1, TimeUnit.SECONDS) ?: "{\"k\":\"ping\"}"
                    w.write(line)
                    w.write("\n")
                    if (outbox.isEmpty()) w.flush()
                }
            } catch (_: Exception) {
            }
            close()
        }

        fun send(j: JsonValue) {
            if (!closed) outbox.add(j.toJson(JsonWriter.OutputType.json).replace('\n', ' '))
        }

        fun poll(): JsonValue? = inbox.poll()

        /** Seconds since anything came in. */
        val quiet get() = (System.nanoTime() - lastHeard) / 1e9f

        val dead get() = closed || quiet > TIMEOUT_S

        fun close() {
            if (closed) return
            closed = true
            runCatching { socket.close() }
        }
    }
}
