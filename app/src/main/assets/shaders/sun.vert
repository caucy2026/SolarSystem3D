uniform mat4 uMVPMatrix;
attribute vec4 aPosition;
attribute vec2 aTexCoord;
varying vec2 vUv;
void main() {
    vUv = aTexCoord;
    gl_Position = uMVPMatrix * aPosition;
}
