// 9-tap separable gaussian (linear-sampled offsets), direction in texels.
#ifdef GL_ES
precision mediump float;
#endif
varying vec2 v_uv;
uniform sampler2D u_texture;
uniform vec2 u_dir;

void main() {
    vec4 c = texture2D(u_texture, v_uv) * 0.2270270270;
    c += texture2D(u_texture, v_uv + u_dir * 1.3846153846) * 0.3162162162;
    c += texture2D(u_texture, v_uv - u_dir * 1.3846153846) * 0.3162162162;
    c += texture2D(u_texture, v_uv + u_dir * 3.2307692308) * 0.0702702703;
    c += texture2D(u_texture, v_uv - u_dir * 3.2307692308) * 0.0702702703;
    gl_FragColor = c;
}
