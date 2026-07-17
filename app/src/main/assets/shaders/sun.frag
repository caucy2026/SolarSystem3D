precision mediump float;
varying vec2 vUv;
uniform sampler2D uEmission;
uniform sampler2D uPlasma;
uniform sampler2D uHelio;
uniform float uTime;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    vec2 s = vec2(1.0);
    for (int i = 0; i < 4; i++) {
        v += a * noise(p * s);
        a *= 0.5;
        s *= 2.1;
    }
    return v;
}

void main() {
    vec3 base = texture2D(uEmission, vUv).rgb;
    vec2 pUV = vUv + vec2(sin(vUv.y * 12.0 + uTime * 0.06) * 0.003 + uTime * 0.002,
                           cos(vUv.x * 15.0 + uTime * 0.08) * 0.003);
    vec3 plasma = texture2D(uPlasma, pUV).rgb;
    vec2 hUV = vUv + vec2(cos(vUv.y * 8.0 + uTime * 0.04) * 0.002,
                           sin(vUv.x * 10.0 + uTime * 0.05) * 0.002);
    vec3 helio = texture2D(uHelio, hUV).rgb;
    vec3 col = base * 1.1 + plasma * 0.35 + helio * 0.25;

    float wind = sin(uTime * 1.3) * 0.04 + sin(uTime * 2.1 + vUv.y * 5.0) * 0.03;
    vec2 fUV = vUv * vec2(1.0, 0.7) + vec2(wind, uTime * 0.015);
    float flameNoise = fbm(fUV * 3.0);
    float flameStreak = abs(fract(vUv.y * 8.0 + flameNoise * 0.6) - 0.5) * 2.0;
    float flameShape = 1.0 - smoothstep(0.0, 0.5, flameStreak) *
                               (1.0 - smoothstep(0.3, 0.7, flameNoise));
    float flicker = 0.7 + 0.3 * sin(uTime * 7.3 + vUv.x * 13.0) *
                             cos(uTime * 5.1 + vUv.y * 17.0);
    float flameMask = smoothstep(0.3, 0.65, flameNoise) *
                      smoothstep(0.0, 0.25, flameShape) * flicker;
    float flameH = flameNoise * flameShape;
    vec3 flameCol = mix(vec3(0.8, 0.15, 0.01),
                        mix(vec3(1.0, 0.55, 0.05), vec3(1.0, 0.9, 0.3), flameH), flameH);
    col = mix(col, col + flameCol * 2.5, flameMask * 0.55);
    gl_FragColor = vec4(col * 1.5, 1.0);
}
