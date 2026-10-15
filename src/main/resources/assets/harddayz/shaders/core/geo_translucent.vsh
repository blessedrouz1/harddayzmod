#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform int FogShape;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out vec4 normal;

uniform ivec2 PackedLight;
uniform ivec2 OverlayUv;

layout(std140) uniform BoneTransforms {
    mat4 BonePoses[64];
    mat4 BoneNormals[64];
};

void main() {
    vec3 posedPosition = (BonePoses[UV1.x] * vec4(Position, 1.0)).xyz;
    vec3 posedNormal = mat3(BoneNormals[UV1.x]) * Normal;
    gl_Position = ProjMat * ModelViewMat * vec4(posedPosition, 1.0);

    vertexDistance = fog_distance(ModelViewMat, IViewRotMat * posedPosition, FogShape);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, posedNormal, Color);
    lightMapColor = texelFetch(Sampler2, PackedLight / 16, 0);
    overlayColor = texelFetch(Sampler1, OverlayUv, 0);
    texCoord0 = UV0;
    normal = ProjMat * ModelViewMat * vec4(posedNormal, 0.0);
}
