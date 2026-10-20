// Car body: R = paint ramp index (index * 32 + 16), G = shine mask. The colour comes from one
// row of the paint ramp texture, lit by up to 8 point lights through the body's normal map.
// u_ghost > 0 turns the car into a cyan hologram instead.
#ifdef GL_ES
precision mediump float;
#endif
varying vec4 v_color;
varying vec2 v_uv;
uniform sampler2D u_texture;
uniform sampler2D u_ramps;
uniform sampler2D u_normal;
uniform float u_row;
uniform float u_ghost;
uniform float u_time;
uniform float u_shine;        // 0..1 position of the paint-shop shine sweep, < 0 = off
uniform vec3 u_ambient;
uniform vec3 u_lightPos[8];   // framebuffer pixels; z = height toward the viewer
uniform vec4 u_lightColor[8]; // rgb, a = radius in pixels
uniform int u_lightCount;
uniform float u_specPower;
uniform float u_specStrength;

void main() {
    vec4 src = texture2D(u_texture, v_uv);
    if (src.a < 0.5) discard;
    float idx = floor(src.r * 255.0 / 32.0);
    vec3 paint = texture2D(u_ramps, vec2((idx + 0.5) / 8.0, u_row)).rgb;

    if (u_ghost > 0.0) {
        float lum = dot(paint, vec3(0.3, 0.55, 0.15));
        float scan = mod(floor(gl_FragCoord.y), 2.0) < 1.0 ? 1.0 : 0.55;
        float flicker = 0.85 + 0.15 * sin(u_time * 31.0 + gl_FragCoord.y * 0.7);
        vec3 holo = mix(vec3(0.1, 0.55, 0.75), vec3(0.75, 1.0, 0.95), lum) * scan * flicker;
        gl_FragColor = vec4(holo, u_ghost * v_color.a);
        return;
    }

    // Bolt-on parts (wings, kits) sit outside the body's normal map: treat them as facing up/out
    vec4 nTex = texture2D(u_normal, v_uv);
    vec3 n = nTex.a < 0.5 ? vec3(0.0, 0.5, 0.86) : normalize(nTex.xyz * 2.0 - 1.0);
    vec3 diffuse = vec3(0.0);
    vec3 spec = vec3(0.0);
    vec3 frag = vec3(gl_FragCoord.xy, 0.0);
    for (int i = 0; i < 8; i++) {
        if (i >= u_lightCount) break;
        vec3 d = u_lightPos[i] - frag;
        float dist = length(d.xy);
        float atten = 1.0 - smoothstep(0.0, u_lightColor[i].a, dist);
        if (atten <= 0.0) continue;
        vec3 l = normalize(d);
        float ndl = max(dot(n, l), 0.0);
        diffuse += u_lightColor[i].rgb * ndl * atten;
        vec3 h = normalize(l + vec3(0.0, 0.0, 1.0));
        spec += u_lightColor[i].rgb * pow(max(dot(n, h), 0.0), u_specPower) * atten;
    }
    vec3 col = paint * (u_ambient + diffuse) + spec * u_specStrength * (0.25 + src.g);
    if (u_shine >= 0.0) {
        float sd = abs(v_uv.x - u_shine);
        col += vec3(1.0, 0.95, 0.8) * src.g * smoothstep(0.06, 0.0, sd) * 0.55;
    }
    gl_FragColor = vec4(col, 1.0) * v_color;
}
