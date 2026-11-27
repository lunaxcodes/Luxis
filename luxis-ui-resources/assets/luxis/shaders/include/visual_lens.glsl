uniform sampler2D InSampler;
layout(std140) uniform LuxisLens {
    vec4 Grade;           // blend, brightness, saturation, contrast
    vec4 Balance;         // exposure, gamma, warmth, tint
    vec4 Look;            // hue, fade, vignette strength, vignette radius
    vec4 Gains;           // red, green, blue, sharpening
    vec4 TextureControls; // grain strength, grain size, palette levels, palette blend
    vec4 Optics;          // fringe, pixel size, bloom strength, bloom threshold
    vec4 Kernel;          // bloom radius
};

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
const vec3 NEUTRAL_AXIS = vec3(0.57735027);

vec3 scene(vec2 uv) {
    return texture(InSampler, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
}

vec3 highlights(vec2 uv) {
    vec3 c = scene(uv);
    return c * smoothstep(Optics.w, Optics.w + 0.18, dot(c, LUMA));
}

void main() {
    vec2 size = vec2(textureSize(InSampler, 0));
    vec2 texel = 1.0 / size;
    vec2 offset = texCoord * 2.0 - 1.0;
    vec2 uv = texCoord;
    if (Optics.y > 1.0) {
        uv = (floor(uv * size / Optics.y) + 0.5) * Optics.y * texel;
    }
    vec3 rgb = scene(uv);
    if (Optics.x > 0.0) {
        vec2 shift = offset * texel * Optics.x;
        rgb.r = scene(uv + shift).r;
        rgb.b = scene(uv - shift).b;
    }
    if (Gains.w > 0.0) {
        vec2 px = vec2(texel.x, 0.0), py = vec2(0.0, texel.y);
        vec3 soft = 0.25 * (scene(uv + px) + scene(uv - px) + scene(uv + py) + scene(uv - py));
        rgb += (scene(uv) - soft) * Gains.w;
    }
    if (Grade.x > 0.0) {
        vec3 graded = max(rgb * exp2(Balance.x), vec3(0.0));
        graded = pow(graded, vec3(1.0 / Balance.y));
        graded = (graded - 0.5) * Grade.w + 0.5 + Grade.y;
        vec3 colorBalance = vec3(1.0 + 0.25 * Balance.z + 0.12 * Balance.w,
                                 1.0 - 0.20 * Balance.w,
                                 1.0 - 0.25 * Balance.z + 0.12 * Balance.w);
        graded *= colorBalance * Gains.rgb;
        if (Look.x != 0.0) {
            float c = cos(Look.x), s = sin(Look.x);
            graded = graded * c + cross(NEUTRAL_AXIS, graded) * s
                    + NEUTRAL_AXIS * dot(NEUTRAL_AXIS, graded) * (1.0 - c);
        }
        graded = mix(vec3(dot(graded, LUMA)), graded, Grade.z);
        graded = mix(graded, vec3(0.28), clamp(Look.y, 0.0, 0.65));
        rgb = mix(rgb, graded, Grade.x);
    }
    if (Optics.z > 0.0) {
        vec2 px = vec2(texel.x * Kernel.x, 0.0), py = vec2(0.0, texel.y * Kernel.x);
        vec3 glow = (highlights(uv + px) + highlights(uv - px)
                   + highlights(uv + py) + highlights(uv - py)) * 0.25;
        rgb += glow * Optics.z;
    }
    if (TextureControls.w > 0.0) {
        float steps = TextureControls.z - 1.0;
        rgb = mix(rgb, floor(clamp(rgb, 0.0, 1.0) * steps + 0.5) / steps, TextureControls.w);
    }
    if (Look.z > 0.0) {
        rgb *= 1.0 - Look.z * smoothstep(Look.w, 1.36, length(offset));
    }
    if (TextureControls.x > 0.0) {
        vec2 pixel = floor(texCoord * size / TextureControls.y);
        float grain = fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715)))) - 0.5;
        rgb += grain * TextureControls.x * 0.5;
    }
    fragColor = vec4(clamp(rgb, 0.0, 1.0), 1.0);
}
