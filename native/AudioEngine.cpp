#include "AudioEngine.h"
#include <stdexcept>
namespace pocket {
static int64_t millis(){return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();}
bool AudioEngine::start(int id,int buffer){
    stop();buffer=std::clamp(buffer,1,8);if(id!=device || buffer!=bursts)learnedBursts=buffer;device=id;bursts=buffer;
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)->setPerformanceMode(oboe::PerformanceMode::LowLatency)->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)->setChannelCount(2)->setUsage(oboe::Usage::Media)->setContentType(oboe::ContentType::Music)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)->setDataCallback(this)->setErrorCallback(this);
    if(id)builder.setDeviceId(id);
    auto result=builder.openStream(stream);
    if(result!=oboe::Result::OK){builder.setSharingMode(oboe::SharingMode::Shared);result=builder.openStream(stream);}
    if(result!=oboe::Result::OK){stream.reset();return false;}
    const int wanted=stable?std::max({4,bursts,learnedBursts}):bursts;
    const int burst=std::max(1,stream->getFramesPerBurst());
    stream->setBufferSizeInFrames(burst*wanted);
    learnedBursts=std::clamp((stream->getBufferSizeInFrames()+burst-1)/burst,1,8);
    auto x=stream->getXRunCount();adaptive.reset(learnedBursts,x?x.value():-1,millis());
    disconnected.store(false);result=stream->requestStart();if(result!=oboe::Result::OK){stop();return false;}return true;
}
void AudioEngine::stability(bool enabled){
    stable=enabled;learnedBursts=stable?std::max(4,bursts):bursts;
    if(stream){const int burst=std::max(1,stream->getFramesPerBurst());stream->setBufferSizeInFrames(burst*learnedBursts);learnedBursts=std::clamp((stream->getBufferSizeInFrames()+burst-1)/burst,1,8);auto x=stream->getXRunCount();adaptive.reset(learnedBursts,x?x.value():-1,millis());}
}
void AudioEngine::stop(){if(stream){stream->requestStop();stream->close();stream.reset();}}
void AudioEngine::install(std::unique_ptr<Instrument> bank,const std::vector<EffectSpec>& fx){
    // Stop and close joins callbacks before changing ownership. Deallocation is never on audio thread.
    stop();
    if(!start(device,bursts))throw std::runtime_error("Audio output could not be opened");
    auto sr=sampleRate();stop();
    try {sampler.install(std::move(bank),fx,sr);} catch(...) {start(device,bursts);throw;}
    if(!start(device,bursts))throw std::runtime_error("Audio output could not be restarted");
}
oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream* audio,void* output,int32_t frames){
    auto begin=std::chrono::steady_clock::now();
    auto nanos=std::chrono::duration_cast<std::chrono::nanoseconds>(begin.time_since_epoch()).count();
    auto lead=int64_t((audio->getBufferSizeInFrames()+sampler.outputDelayFrames())*1000000000.0/audio->getSampleRate());
    sampler.render(static_cast<float*>(output),frames,nanos+lead);
    float used=std::chrono::duration<float>(std::chrono::steady_clock::now()-begin).count();
    load.store(used*sampleRate()/frames,std::memory_order_relaxed);return oboe::DataCallbackResult::Continue;
}
void AudioEngine::replaceEffect(int index,std::unique_ptr<Effect> effect){
    stop();sampler.replaceEffect(index,std::move(effect));
    if(!start(device,bursts))throw std::runtime_error("Audio restart failed after IR change");
}
std::array<double,18> AudioEngine::stats(){
    double latency=-1;int xruns=-1,buffer=0,burst=0,state=0,sharing=0;
    if(stream){auto l=stream->calculateLatencyMillis();if(l)latency=l.value();auto x=stream->getXRunCount();if(x)xruns=x.value();buffer=stream->getBufferSizeInFrames();burst=stream->getFramesPerBurst();state=int(stream->getState());sharing=int(stream->getSharingMode());
        if(stable && burst>0 && state==int(oboe::StreamState::Started)){const int desired=adaptive.observe(stable,xruns,stream->getBufferCapacityInFrames()/burst,millis());if(desired>learnedBursts){stream->setBufferSizeInFrames(desired*burst);buffer=stream->getBufferSizeInFrames();learnedBursts=std::clamp((buffer+burst-1)/burst,1,8);}}
    }
    return {sampleRate(),double(buffer),double(burst),double(xruns),latency,load.load()*100,double(sampler.activeVoices.load()),double(sampler.memory()),double(sampler.misses()),double(sampler.droppedEvents.load()),double(disconnected.load() || !stream),double(sampler.errors()),sampler.inputPeak.load(),sampler.gainReductionDb.load(),sampler.outputDelayFrames()/double(sampleRate())*1000,double(sampler.stolenVoices.load()),double(sampler.cacheDrops()),double(sampler.cachePages())};
}
}
