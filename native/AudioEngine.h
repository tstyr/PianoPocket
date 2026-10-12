#pragma once
#include "SamplerEngine.h"
#include <oboe/Oboe.h>
#include <chrono>
#include <stdexcept>
namespace pocket {
class AudioEngine:public oboe::AudioStreamDataCallback,public oboe::AudioStreamErrorCallback {
    std::shared_ptr<oboe::AudioStream> stream;
    int device=0,bursts=2;
    std::atomic<float> load{0};
    std::atomic<bool> disconnected{false};
public:
    SamplerEngine sampler;
    bool start(int deviceId=0,int bufferBursts=2);
    void stop();
    float sampleRate() const {return stream?stream->getSampleRate():48000;}
    void install(std::unique_ptr<Instrument> bank,const std::vector<EffectSpec>& fx);
    void replaceEffect(int index,std::unique_ptr<Effect> effect);
    void timeline(std::vector<TimedEvent> events,int64_t duration){stop();sampler.setTimeline(std::move(events),duration);if(!start(device,bursts))throw std::runtime_error("Audio restart failed");}
    void seek(int64_t frame){stop();sampler.seek(frame);if(!start(device,bursts))throw std::runtime_error("Audio restart failed");}
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*,void*,int32_t) override;
    void onErrorAfterClose(oboe::AudioStream*,oboe::Result) override {disconnected.store(true);}
    std::array<double,12> stats();
};
}
