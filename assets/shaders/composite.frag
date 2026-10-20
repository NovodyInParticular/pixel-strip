// Final pass, low-res buffers -> screen:
//   world: zoom around the focus, heat-haze distortion, sharp-bilinear pixel upscale,
//          chromatic aberration + radial blur (nitrous), bloom, per-track LUT grade,
//          photo-finish print look
//   then the UI layer (never zoomed or graded), screen flash, optional CRT.
#ifdef GL_ES
precision mediump float;
#endif
varying vec2 v_uv;
uniform sampler2D u_scene;
uniform sampler2D u_bloom;
uniform sampler2D u_lut;
uniform sampler2D u_ui;
uniform sampler2D u_distort;
uniform vec2 u_src;
uniform vec2 u_dst;
uniform float u_zoom;
uniform vec2 u_focus;
uniform float u_bloomGain;
uniform float u_lutMix;
uniform float u_ca;
uniform float u_radial;
uniform float u_distortAmp;   // source pixels at full strength
uniform vec4 u_haze;          // asphalt shimmer: y0, y1 (uv), amplitude (px), time
uniform float u_print;
uniform float u_crt;
uniform float u_flash;
uniform vec3 u_flashColor;

vec2 curve(vec2 uv) {
    uv = uv * 2.0 - 1.0;
    vec2 off = abs(uv.yx) / vec2(7.5, 6.0);
    uv = uv + uv * off * off;
    return uv * 0.5 + 0.5;
}

vec4 sharp(sampler2D tex, vec2 uv) {
    vec2 scale = max(u_dst / u_src * u_zoom, vec2(1.0));
    vec2 texel = uv * u_src;
    vec2 base = floor(texel);
    vec2 dist = fract(texel) - 0.5;
    vec2 region = 0.5 - 0.5 / scale;
    vec2 f = (dist - clamp(dist, -region, region)) * scale + 0.5;
    return texture2D(tex, (base + f) / u_src);
}

vec3 grade(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    float b = c.b * 15.0;
    float s0 = floor(b);
    float s1 = min(s0 + 1.0, 15.0);
    float y = (c.g * 15.0 + 0.5) / 16.0;
    float x = c.r * 15.0 + 0.5;
    vec3 g0 = texture2D(u_lut, vec2((s0 * 16.0 + x) / 256.0, y)).rgb;
    vec3 g1 = texture2D(u_lut, vec2((s1 * 16.0 + x) / 256.0, y)).rgb;
    return mix(c, mix(g0, g1, b - s0), u_lutMix);
}

void main() {
    vec2 uv = v_uv;
    if (u_crt > 0.5) {
        uv = curve(uv);
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
            gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
    }
    vec2 w = (uv - u_focus) / u_zoom + u_focus;

    vec4 d = texture2D(u_distort, w);
    vec2 off = (d.rg - 0.5) * 2.0 * d.a * u_distortAmp / u_src;
    if (u_haze.z > 0.0 && w.y > u_haze.x && w.y < u_haze.y) {
        float edge = smoothstep(u_haze.x, u_haze.x + 0.03, w.y) * smoothstep(u_haze.y, u_haze.y - 0.03, w.y);
        off.x += sin(w.y * u_src.y * 1.7 + u_haze.w * 7.0) * u_haze.z * edge / u_src.x;
    }
    w += off;

    vec2 toFocus = w - u_focus;
    vec3 col;
    if (u_ca > 0.01) {
        vec2 ca = (w - 0.5) * (u_ca / u_src.x) * 2.0;
        col = vec3(sharp(u_scene, w + ca).r, sharp(u_scene, w).g, sharp(u_scene, w - ca).b);
    } else {
        col = sharp(u_scene, w).rgb;
    }
    if (u_radial > 0.01) {
        vec3 acc = col;
        for (int i = 1; i < 6; i++) {
            float k = float(i) / 6.0 * u_radial * 0.06;
            acc += sharp(u_scene, w - toFocus * k).rgb;
        }
        col = acc / 6.0;
    }
    col += texture2D(u_bloom, w).rgb * u_bloomGain;
    col = grade(col);

    if (u_print > 0.0) {
        // Photo-finish print: sepia, halftone dots, paper white edges
        float lum = dot(col, vec3(0.3, 0.59, 0.11));
        vec3 sepia = vec3(lum) * vec3(1.08, 0.94, 0.76) + vec3(0.06, 0.04, 0.02);
        vec2 cell = fract(v_uv * u_dst / 7.0) - 0.5;
        float dotR = length(cell);
        float r = 0.55 * (1.0 - lum * 1.4);
        float ink = 1.0 - smoothstep(r - 0.06, r, dotR);
        vec3 halftone = sepia * (1.0 - ink * 0.45);
        col = mix(col, mix(sepia * 1.15, halftone, 0.5), u_print);
        float border = step(0.035, v_uv.x) * step(v_uv.x, 0.965) * step(0.05, v_uv.y) * step(v_uv.y, 0.95);
        col = mix(col, vec3(0.97, 0.95, 0.9), (1.0 - border) * u_print);
    }

    vec4 ui = sharp(u_ui, uv);
    col = col * (1.0 - ui.a) + ui.rgb;
    col = mix(col, u_flashColor, u_flash);

    vec2 toCentre = uv - 0.5;
    if (u_crt > 0.5) {
        float line = fract(uv.y * u_src.y);
        col *= 0.78 + 0.22 * smoothstep(0.0, 0.5, line) * smoothstep(1.0, 0.5, line) * 2.0;
        float mask = mod(floor(v_uv.x * u_dst.x), 3.0);
        col *= mask < 1.0 ? vec3(1.06, 0.97, 0.97) : (mask < 2.0 ? vec3(0.97, 1.06, 0.97) : vec3(0.97, 0.97, 1.06));
        col *= 1.0 - dot(toCentre, toCentre) * 1.1;
    } else {
        col *= 1.0 - dot(toCentre, toCentre) * 0.35;
    }
    gl_FragColor = vec4(col, 1.0);
}
