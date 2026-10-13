#pragma once
#include "InstrumentMapping.h"
#include "Envelope.h"
#include "Effects.h"
#include "OutputLimiter.h"
#include "SpscQueue.h"
#include <mutex>
namespace pocket {
struct Event {int type=0,channel=0,a=0,b=0;float value=0;};
struct TimedEvent {int64_t frame=0;Event event;};
struct Voice {
    bool active=false,held=false,pending=false;
    int channel=0,note=0,velocity=0;
    uint64_t id=0;
    unsigned region=0;
    double position=0,baseStep=1;
    float gain=1,lastL=0,lastR=0,sourceL=0,sourceR=0,stealL=0,stealR=0,streamGain=1,mixGain=0;
    int stealRemaining=0;
    Envelope envelope;
};
class SamplerEngine {
    struct Key { uint64_t id=0; int velocity=0; bool down=false,pending=false; };
    std::unique_ptr<Instrument> instrument;
    Effects effects;
    OutputLimiter outputLimiter;
    static constexpr int MaxVoices=1024;
    std::vector<Voice> voices=std::vector<Voice>(MaxVoices); // allocated before audio starts
    std::array<std::array<std::array<Key,4>,128>,33> keys{};
    std::array<std::array<int,128>,33> controllers{};
    std::array<float,33> bends{};
    std::array<float,256> groupGains{};
    SpscQueue<Event,2048> events;
    std::mutex producer; // producers ONLY; never taken by render
    std::atomic<bool> panic{false};
    float rate=48000,volume=.7f,targetVolume=.7f;
    float instrumentVolume=1,targetInstrumentVolume=1;
    int diagnosticMode=0;
    float diagnosticFx=1,diagnosticTone=0;
    double tonePhaseL=0,tonePhaseR=0;
    int limit=512;
    uint64_t nextId=0;
    std::vector<TimedEvent> timeline;
    size_t cursor=0;
    double playFrame=0;
    int64_t totalFrames=0,loopStartFrame=0,loopEndFrame=0;
    float playbackSpeed=1;
    bool looping=false;
    size_t loopCursor=0;
    std::array<std::array<int,128>,16> loopControllers{};
    std::array<float,16> loopBends{};
    bool playing=false;
    std::atomic<int64_t> reportedFrame{0};
    std::atomic<bool> reportedPlaying{false};
    std::atomic<bool> transportOverload{false};
    std::atomic<float> reportedRate{48000};
    void renderBlock(float*,int) noexcept;
    void seekTimeline(int64_t frame) noexcept;
    void rewindLoop() noexcept;
    void dispatch(const Event&) noexcept;
    void trigger(Trigger,int channel,int note,int velocity,uint64_t id) noexcept;
    void releaseId(int channel,int note,uint64_t id,int velocity) noexcept;
public:
    std::atomic<int> activeVoices{0};
    std::atomic<uint64_t> droppedEvents{0},stolenVoices{0};
    std::atomic<float> inputPeak{0},gainReductionDb{0};
    int outputDelayFrames() const noexcept {return outputLimiter.delayFrames();}
    void install(std::unique_ptr<Instrument> bank,const std::vector<EffectSpec>& fx,float sr); // stream stopped
    bool send(Event event); // outside callback; multiple producers serialized
    void render(float* stereo,int frames) noexcept;
    void initialControls(const std::vector<Event>& values){for(const auto& e:values)dispatch(e);volume=targetVolume;instrumentVolume=targetInstrumentVolume;}
    void setTimeline(std::vector<TimedEvent> events,int64_t duration); // callback stopped
    void configureLoop(int64_t begin,int64_t end,bool enabled); // callback stopped; snapshots prepared here
    void seek(int64_t frame){playing=false;transportOverload.store(false);reportedPlaying.store(false);seekTimeline(frame);}
    std::array<double,3> transport() const {return {double(reportedFrame.load())/reportedRate.load(),reportedPlaying.load()?1.:0.,transportOverload.load()?1.:0.};}
    void replaceEffect(int index,std::unique_ptr<Effect> effect){effects.replace(index,std::move(effect));}
    uint64_t misses() const {return instrument?instrument->cache->misses.load():0;}
    uint64_t errors() const {return instrument?instrument->cache->readErrors.load():0;}
    int noteVoiceCount(int note,int channel) const {int n=0;for(const auto& v:voices)if(v.active && v.note==note && v.channel==channel)++n;return n;} // stopped/offline diagnostics only
    uint64_t cacheDrops() const {return instrument?instrument->cache->requestDrops.load():0;}
    size_t cachePages() const {return instrument?instrument->cache->capacity():0;}
    size_t memory() const {return instrument?instrument->residentBytes:0;}
};
}
