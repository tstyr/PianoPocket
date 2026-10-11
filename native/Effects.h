#pragma once
#include <vector>
#include <array>
#include <complex>
#include <memory>
#include <cmath>
namespace pocket {
enum class EffectType:int {Reverb,Delay,Chorus,Lowpass,Highpass,Bandpass,Gain,Convolution,Peak,Notch,LowShelf,HighShelf};
// Public parameter slots: wet/mix, room/time/depth/frequency/gain, damping/feedback/rate/Q, stereoOffset/EQ gain, enabled.
struct EffectSpec {EffectType type=EffectType::Reverb;std::array<float,5> p{0,.7f,.3f,0,1};std::vector<float> ir;};
class Convolver {
    static constexpr size_t Block=256,N=512;
    using Complex=std::complex<float>;
    std::vector<std::array<Complex,N>> kernels[2],history[2];
    std::array<std::array<float,Block>,2> input{},output{},overlap{};
    std::array<Complex,N> scratch{},sum{};
    size_t partitions=0,position=0,ring=0;
    static void fft(std::array<Complex,N>& x,bool inverse) noexcept;
public:
    explicit Convolver(const std::vector<float>& stereoIr);
    void process(float& left,float& right) noexcept;
};
class Effect {
    EffectType type;
    float rate;
    float smoothing;
    std::array<float,5> current,target;
    std::vector<float> delay;
    size_t write=0;
    float phase=0;
    std::array<std::vector<float>,8> comb;
    std::array<size_t,8> combPos{};
    std::array<float,8> damped{};
    std::array<std::vector<float>,4> allpass;
    std::array<size_t,4> allPos{};
    std::array<double,2> z1{},z2{};
    double b0=1,b1=0,b2=0,a1=0,a2=0;
    std::unique_ptr<Convolver> convolution;
    void coefficients() noexcept;
public:
    Effect(const EffectSpec& spec,float sampleRate);
    void parameter(int slot,float value) noexcept;
    void process(float& l,float& r) noexcept;
};
class Effects {
    std::vector<std::unique_ptr<Effect>> chain;
public:
    void prepare(const std::vector<EffectSpec>& specs,float rate);
    void parameter(int effect,int slot,float value) noexcept;
    void process(float& l,float& r) noexcept {for(auto& fx:chain)fx->process(l,r);}
};
}
