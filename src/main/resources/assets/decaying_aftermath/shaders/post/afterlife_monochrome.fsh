#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D InSampler;
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec3 color=texture(InSampler,texCoord).rgb;
    float gray=dot(color,vec3(0.2126,0.7152,0.0722));
    float edge=clamp(1.0-length(texCoord-vec2(0.5))*0.60,0.45,1.0);
    fragColor=vec4(vec3(gray*0.82*edge),1.0);
}
