#pragma once
#include <array>
#include <algorithm>
#include <cmath>
#include <cstdint>
namespace pocket {
/** Linked stereo sample-peak limiter. Fixed buffers; no callback allocation or I/O. */
class OutputLimiter {
    static constexpr size_t Capacity=513,PeakCapacity=1024;
    struct Peak {uint64_t time=0;float value=0;};
    std::array<std::array<float,2>,Capacity> delay{};
    std::array<Peak,PeakCapacity> peaks{};
    size_t ahead=48,write=0,front=0,back=0;
    uint64_t frame=0;
    float gain=1,attack=1,release=1,heldPeak=0,meterDecay=1;
    float heldTarget=1;
    int gainHold=0,gainHoldLength=1440;
    int hold=0,holdLength=24000;
public:
    static constexpr float Ceiling=.95f;
    void prepare(float rate) noexcept {
        ahead=size_t(std::clamp(std::ceil(double(rate)/1000),1.0,512.0));
        attack=1-std::exp(-1.f/(rate*.0001f));release=1-std::exp(-1.f/(rate*.080f));
        meterDecay=std::exp(-1.f/(rate*.2f));holdLength=int(rate*.5f);gainHoldLength=int(rate*.030f);
        delay={};front=back=write=0;frame=0;gain=1;heldPeak=0;hold=0;heldTarget=1;gainHold=0;
    }
    int delayFrames() const noexcept {return int(ahead);}
    float peak() const noexcept {return heldPeak;}
    float reductionDb() const noexcept {return -20*std::log10(std::max(gain,1e-20f));}
    void process(float& l,float& r) noexcept {
        if(!std::isfinite(l))l=0;
        if(!std::isfinite(r))r=0;
        const float peak=std::max(std::abs(l),std::abs(r));
        if(peak>=heldPeak){heldPeak=peak;hold=holdLength;}else if(--hold<=0){heldPeak*=meterDecay;hold=0;}
        // Monotonic deque keeps the maximum over the delayed/current sample window.
        while(front!=back && peaks[front].time+ahead<frame)front=(front+1)%PeakCapacity;
        while(front!=back){auto last=(back+PeakCapacity-1)%PeakCapacity;if(peaks[last].value>peak)break;back=last;}
        peaks[back]={frame,peak};back=(back+1)%PeakCapacity;
        delay[write]={l,r};write=(write+1)%(ahead+1);l=delay[write][0];r=delay[write][1];++frame;
        const float maximum=peaks[front].value;
        const float target=maximum>Ceiling?Ceiling/maximum:1;
        // Hold reduction through bass-wave zero crossings; otherwise even a slow
        // release modulates each low note cycle and generates audible harmonics.
        if(target<heldTarget){heldTarget=target;gainHold=gainHoldLength;}
        else if(target<=heldTarget*1.0001f)gainHold=gainHoldLength;
        else if(--gainHold<=0){heldTarget=target;gainHold=0;}
        gain+=(heldTarget-gain)*(heldTarget<gain?attack:release);
        // Covers extreme first transients and rounding; steady tones use envelope gain.
        const float delayedPeak=std::max(std::abs(l),std::abs(r));
        if(delayedPeak>Ceiling)gain=std::min(gain,Ceiling/delayedPeak);
        l=std::clamp(l*gain,-Ceiling,Ceiling);r=std::clamp(r*gain,-Ceiling,Ceiling);
    }
};
}
