package io.github.novodyinparticular.pixelstrip.net

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Net
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.JsonWriter
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.save.Ledger

/**
 * Firebase over plain HTTPS (so Android and desktop share it): anonymous sign-in through the
 * Identity Toolkit REST API, then the `sync` callable function, which replays the pending ledger
 * on the server and returns the authoritative garage.
 *
 * Config: assets/data/firebase.json. An empty projectId means offline: the game plays exactly as
 * before and the ledger just queues. Desktop can point at the local emulator suite with
 * -Dpixelstrip.emulator=127.0.0.1 (project demo-pixel-strip).
 */
class Cloud(private val game: PixelStrip, enabled: Boolean) {
    enum class Status { OFFLINE, SIGNING_IN, SYNCING, SYNCED, ERROR }

    private val emulator: String? = System.getProperty("pixelstrip.emulator")
    private val cfg = runCatching { JsonReader().parse(Gdx.files.internal("data/firebase.json")) }.getOrNull()
    private val projectId = if (emulator != null) System.getProperty("pixelstrip.project", "demo-pixel-strip") else cfg?.getString("projectId", "") ?: ""
    private val apiKey = if (emulator != null) "emulator" else cfg?.getString("apiKey", "") ?: ""
    private val region = cfg?.getString("region", "us-central1") ?: "us-central1"
    val configured = enabled && projectId.isNotEmpty() && apiKey.isNotEmpty()

    @Volatile var status = if (configured) Status.SIGNING_IN else Status.OFFLINE
        private set
    /** Entries the server refused, since this session started. */
    var rejected = 0
        private set
    var lastError: String? = null
        private set
    /** Short form of the account id, for the settings screen. */
    val playerId get() = uid?.take(8)

    private val accountFile = game.platform.saveFile(if (emulator != null) "account-emulator.json" else "account.json")
    private var uid: String? = null
    private var refreshToken: String? = null
    private var idToken: String? = null
    private var idTokenExpires = 0L
    private var busy = false
    private var again = false
    private var retryAt = 0L

    init {
        runCatching { JsonReader().parse(accountFile) }.getOrNull()?.let {
            uid = it.getString("uid", null)
            refreshToken = it.getString("refreshToken", null)
        }
    }

    private val authBase = if (emulator != null) "http://$emulator:9099/identitytoolkit.googleapis.com" else "https://identitytoolkit.googleapis.com"
    private val tokenBase = if (emulator != null) "http://$emulator:9099/securetoken.googleapis.com" else "https://securetoken.googleapis.com"
    private val fnUrl = if (emulator != null) "http://$emulator:5001/$projectId/$region/sync" else "https://$region-$projectId.cloudfunctions.net/sync"

    /** Push pending entries (and pull the server's state) soon. Safe to call often. */
    fun requestSync() {
        if (!configured) return
        if (busy) {
            again = true
            return
        }
        busy = true
        again = false
        withToken { token -> sync(token) }
    }

    /** Called every frame: retries a failed sync after a pause. */
    fun update() {
        if (configured && !busy && retryAt != 0L && System.currentTimeMillis() > retryAt) {
            retryAt = 0L
            requestSync()
        }
    }

    private fun done(failed: Boolean = false) {
        busy = false
        if (failed) retryAt = System.currentTimeMillis() + 30_000L
        else if (again) requestSync()
    }

    private fun fail(why: String) {
        lastError = why
        status = Status.ERROR
        Gdx.app.log("cloud", why)
        done(failed = true)
    }

    // --- auth ---------------------------------------------------------------------------------------

    /** Runs [then] with a fresh ID token; on failure it reports and never calls [then]. */
    private fun withToken(then: (String) -> Unit) {
        val token = idToken
        if (token != null && System.currentTimeMillis() < idTokenExpires - 60_000L) return then(token)
        val refresh = refreshToken
        if (refresh == null) {
            status = Status.SIGNING_IN
            post("$authBase/v1/accounts:signUp?key=$apiKey", """{"returnSecureToken":true}""", null) { code, body ->
                if (code != 200 || body == null) return@post fail("sign-in failed ($code)")
                uid = body.getString("localId")
                storeTokens(body.getString("idToken"), body.getString("refreshToken"), body.getLong("expiresIn", 3600))
                then(id())
            }
        } else {
            post("$tokenBase/v1/token?key=$apiKey", "grant_type=refresh_token&refresh_token=$refresh", null, form = true) { code, body ->
                if (code != 200 || body == null) return@post fail("token refresh failed ($code)")
                uid = body.getString("user_id", uid)
                storeTokens(body.getString("id_token"), body.getString("refresh_token"), body.getLong("expires_in", 3600))
                then(id())
            }
        }
    }

    private fun id() = idToken!!

    private fun storeTokens(id: String, refresh: String, expiresIn: Long) {
        idToken = id
        idTokenExpires = System.currentTimeMillis() + expiresIn * 1000L
        refreshToken = refresh
        val o = JsonValue(JsonValue.ValueType.`object`)
        uid?.let { o.addChild("uid", JsonValue(it)) }
        o.addChild("refreshToken", JsonValue(refresh))
        val tmp = accountFile.sibling(accountFile.name() + ".tmp")
        tmp.writeString(o.toJson(JsonWriter.OutputType.json), false)
        tmp.moveTo(accountFile)
    }

    // --- sync ---------------------------------------------------------------------------------------

    private fun sync(token: String) {
        status = Status.SYNCING
        val save = game.save
        val sent = save.pending.size
        val data = JsonValue(JsonValue.ValueType.`object`)
        data.addChild("base", JsonValue(save.serverSeq))
        data.addChild("entries", JsonValue(JsonValue.ValueType.array).also { a -> save.pending.forEach { a.addChild(it.toJson()) } })
        if (save.legacy) data.addChild("legacy", save.economicState())
        if (save.lastJson == null) save.write()
        save.lastJson?.let { data.addChild("profile", JsonValue(it.toJson(JsonWriter.OutputType.json))) }
        val body = JsonValue(JsonValue.ValueType.`object`).also { it.addChild("data", data) }
        post(fnUrl, body.toJson(JsonWriter.OutputType.json), token) { code, res ->
            val result = res?.get("result")
            if (code != 200 || result == null) return@post fail("sync failed ($code ${res?.get("error")?.getString("message", "") ?: ""})")
            adopt(result, sent)
            status = Status.SYNCED
            lastError = null
            done()
        }
    }

    /**
     * Server state wins. Entries queued while the request was in flight are replayed on top of it
     * (and dropped if the rules now refuse them).
     */
    private fun adopt(result: JsonValue, sent: Int) {
        val save = game.save
        rejected += result.get("rejected")?.size ?: 0
        val later = save.pending.drop(sent)
        val profile = result.getString("profile", null)?.let { runCatching { JsonReader().parse(it) }.getOrNull() }
        save.adopt(result.get("state"), profile)
        save.serverSeq = result.getLong("seq")
        save.legacy = false
        save.pending.clear()
        for (e in later) if (Ledger.apply(game.rules, save, e)) save.pending.add(e) else rejected++
        save.write()
    }

    // --- http ---------------------------------------------------------------------------------------

    private fun post(url: String, content: String, bearer: String?, form: Boolean = false, then: (Int, JsonValue?) -> Unit) {
        val req = Net.HttpRequest(Net.HttpMethods.POST)
        req.url = url
        req.content = content
        req.timeOut = 15_000
        req.setHeader("Content-Type", if (form) "application/x-www-form-urlencoded" else "application/json")
        bearer?.let { req.setHeader("Authorization", "Bearer $it") }
        Gdx.net.sendHttpRequest(req, object : Net.HttpResponseListener {
            override fun handleHttpResponse(r: Net.HttpResponse) {
                val code = r.status.statusCode
                val text = r.resultAsString
                val json = runCatching { JsonReader().parse(text) }.getOrNull()
                Gdx.app.postRunnable { then(code, json) }       // touch the save on the game thread only
            }

            override fun failed(t: Throwable) {
                Gdx.app.postRunnable { then(-1, null) }
            }

            override fun cancelled() {
                Gdx.app.postRunnable { then(-1, null) }
            }
        })
    }
}
