#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;


layout(std140) uniform BoneTransforms {
    mat4 BonePoses[64];
    mat4 BoneNormals[64];
};

void main() {
    vec3 posedPosition = (BonePoses[UV1.x] * vec4(Position, 1.0)).xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(posedPosition, 1.0);

    vertexDistance = fog_distance(ModelViewMat, IViewRotMat * posedPosition, FogShape);
    vertexColor = Color;
    texCoord0 = UV0;
}
