#ifdef GL_ES
precision mediump float;
#endif
varying vec2 v_uv;
uniform sampler2D u_texture;
uniform float u_gain;

void main() {
    gl_FragColor = texture2D(u_texture, v_uv) * u_gain;
}
