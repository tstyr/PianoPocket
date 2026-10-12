#include "AudioEngine.h"
#include <stdexcept>
namespace pocket {
bool AudioEngine::start(int id,int buffer){
    stop();device=id;bursts=std::clamp(buffer,1,8);
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)->setPerformanceMode(oboe::PerformanceMode::LowLatency)->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)->setChannelCount(2)->setUsage(oboe::Usage::Media)->setContentType(oboe::ContentType::Music)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)->setDataCallback(this)->setErrorCallback(this);
    if(id)builder.setDeviceId(id);
    auto result=builder.openStream(stream);
    if(result!=oboe::Result::OK){builder.setSharingMode(oboe::SharingMode::Shared);result=builder.openStream(stream);}
    if(result!=oboe::Result::OK){stream.reset();return false;}
    stream->setBufferSizeInFrames(stream->getFramesPerBurst()*bursts);
    disconnected.store(false);result=stream->requestStart();if(result!=oboe::Result::OK){stop();return false;}return true;
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
oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream*,void* output,int32_t frames){
    auto begin=std::chrono::steady_clock::now();sampler.render(static_cast<float*>(output),frames);
    float used=std::chrono::duration<float>(std::chrono::steady_clock::now()-begin).count();
    load.store(used*sampleRate()/frames,std::memory_order_relaxed);return oboe::DataCallbackResult::Continue;
}
void AudioEngine::replaceEffect(int index,std::unique_ptr<Effect> effect){
    stop();sampler.replaceEffect(index,std::move(effect));
    if(!start(device,bursts))throw std::runtime_error("Audio restart failed after IR change");
}
std::array<double,12> AudioEngine::stats(){
    double latency=-1;int xruns=-1,buffer=0,burst=0,state=0,sharing=0;
    if(stream){auto l=stream->calculateLatencyMillis();if(l)latency=l.value();auto x=stream->getXRunCount();if(x)xruns=x.value();buffer=stream->getBufferSizeInFrames();burst=stream->getFramesPerBurst();state=int(stream->getState());sharing=int(stream->getSharingMode());}
    return {sampleRate(),double(buffer),double(burst),double(xruns),latency,load.load()*100,double(sampler.activeVoices.load()),double(sampler.memory()),double(sampler.misses()),double(sampler.droppedEvents.load()),double(disconnected.load() || !stream),double(sampler.errors())};
}
}
