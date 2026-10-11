#pragma once
#include <algorithm>
namespace pocket {
struct Envelope {
    enum Stage {Attack,Decay,Sustain,Release,Done} stage=Done;
    float value=0, attack=.003f,decay=.1f,sustain=1,release=.25f,releaseStep=0;
    void start(float a,float d,float s,float r){attack=std::max(a,.002f);decay=std::max(d,.001f);sustain=std::clamp(s,0.f,1.f);release=std::max(r,.005f);value=0;stage=Attack;}
    void off(float rate){if(stage!=Done && stage!=Release){releaseStep=value/(release*rate);stage=Release;}}
    float next(float rate) noexcept {
        switch(stage){
            case Attack: value+=1/(attack*rate);if(value>=1){value=1;stage=Decay;}break;
            case Decay: value-=(1-sustain)/(decay*rate);if(value<=sustain){value=sustain;stage=Sustain;}break;
            case Release: value-=releaseStep;if(value<=0){value=0;stage=Done;}break;
            default:break;
        }return value;
    }
};
}
