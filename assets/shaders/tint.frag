// Regular sprites. u_lit > 0 adds the scene's point lights (flat normal: chrome and glass catch
// glints); u_ghost > 0 renders a hologram instead.
#ifdef GL_ES
precision mediump float;
#endif
varying vec4 v_color;
varying vec2 v_uv;
uniform sampler2D u_texture;
uniform float u_ghost;
uniform float u_time;
uniform float u_lit;
uniform vec3 u_lightPos[8];
uniform vec4 u_lightColor[8];
uniform int u_lightCount;

void main() {
    vec4 src = texture2D(u_texture, v_uv) * v_color;
    if (u_ghost > 0.0) {
        if (src.a < 0.5) discard;
        float lum = dot(src.rgb, vec3(0.3, 0.55, 0.15));
        float scan = mod(floor(gl_FragCoord.y), 2.0) < 1.0 ? 1.0 : 0.55;
        vec3 holo = mix(vec3(0.1, 0.55, 0.75), vec3(0.75, 1.0, 0.95), lum) * scan;
        gl_FragColor = vec4(holo, u_ghost * v_color.a);
        return;
    }
    if (u_lit > 0.0) {
        vec3 add = vec3(0.0);
        for (int i = 0; i < 8; i++) {
            if (i >= u_lightCount) break;
            float dist = length(u_lightPos[i].xy - gl_FragCoord.xy);
            float atten = 1.0 - smoothstep(0.0, u_lightColor[i].a, dist);
            add += u_lightColor[i].rgb * atten * atten;
        }
        float bright = max(src.r, max(src.g, src.b));
        src.rgb += add * u_lit * (0.25 + bright * 0.75) * src.a;
    }
    gl_FragColor = src;
}
