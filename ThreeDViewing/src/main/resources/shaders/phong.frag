#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec3 Normal;
in vec2 TexCoords;
in vec4 FragPosLightSpace;

uniform vec3 lightPos;
uniform vec3 viewPos;
uniform vec3 lightColor;
uniform vec3 objectColor;
uniform float specularStrength;   // kekuatan kilap tiap material (0.0 - 1.0)
uniform sampler2D textureSampler;
uniform sampler2D shadowMap;
uniform bool useTexture;
uniform bool useBlinnPhong;
uniform bool useShadow;
uniform bool usePCF;
uniform int biasMode;   // 0 = tanpa bias, 1 = 0.005, 2 = dinamis, 3 = 0.05

// Shadow Mapping: bandingkan kedalaman fragment dengan shadow map
float ShadowCalculation(vec4 fragPosLS, vec3 norm, vec3 lightDir) {
    vec3 proj = fragPosLS.xyz / fragPosLS.w;   // perspective divide
    proj = proj * 0.5 + 0.5;                   // NDC [-1,1] -> [0,1]
    if (proj.z > 1.0) return 1.0;              // di luar far plane cahaya

    float bias;
    if (biasMode == 0)      bias = 0.0;
    else if (biasMode == 1) bias = 0.005;
    else if (biasMode == 3) bias = 0.05;
    else                    bias = max(0.05 * (1.0 - dot(norm, lightDir)), 0.005);

    if (usePCF) {
        // PCF 3x3: rata-rata 9 sampel agar tepi bayangan lebih halus
        vec2 texel = 1.0 / vec2(textureSize(shadowMap, 0));
        float sum = 0.0;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                float d = texture(shadowMap, proj.xy + vec2(x, y) * texel).r;
                sum += (proj.z > d + bias) ? 0.2 : 1.0;
            }
        }
        return sum / 9.0;
    }
    float closest = texture(shadowMap, proj.xy).r;
    return (proj.z > closest + bias) ? 0.2 : 1.0;   // 0.2 = bayangan, 1.0 = terang
}

void main() {
    vec3 norm = normalize(Normal);
    vec3 lightDir = normalize(lightPos - FragPos);
    vec3 viewDir = normalize(viewPos - FragPos);

    // 1. Ambient
    vec3 ambient = 0.2 * lightColor;

    // 2. Diffuse (Lambertian)
    float diff = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = diff * lightColor;

    // 3. Specular (Phong vs Blinn-Phong)
    float spec = 0.0;
    if (useBlinnPhong) {
        vec3 halfwayDir = normalize(lightDir + viewDir);
        spec = pow(max(dot(norm, halfwayDir), 0.0), 32.0);    // Blinn-Phong
    } else {
        vec3 reflectDir = reflect(-lightDir, norm);
        spec = pow(max(dot(viewDir, reflectDir), 0.0), 16.0); // Phong
    }
    vec3 specular = specularStrength * spec * lightColor;

    // 4. Bayangan
    float shadow = useShadow ? ShadowCalculation(FragPosLightSpace, norm, lightDir) : 1.0;

    // 5. Gabungkan dengan tekstur / warna objek
    vec3 baseColor = useTexture ? texture(textureSampler, TexCoords).rgb : objectColor;
    vec3 result = (ambient + shadow * (diffuse + specular)) * baseColor;
    FragColor = vec4(result, 1.0);
}
