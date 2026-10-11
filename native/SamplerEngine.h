#pragma once
#include "InstrumentMapping.h"
#include "Envelope.h"
#include "Effects.h"
#include "SpscQueue.h"
#include <mutex>
namespace pocket {
struct Event {int type=0,channel=0,a=0,b=0;float value=0;};
struct Voice {
    bool active=false,held=false,pending=false;
    int channel=0,note=0,velocity=0;
    uint64_t id=0;
    unsigned region=0;
    double position=0,baseStep=1;
    float gain=1,lastL=0,lastR=0,stealL=0,stealR=0,streamGain=1,mixGain=0;
    int stealRemaining=0;
    Envelope envelope;
};
class SamplerEngine {
    struct Key { uint64_t id=0; int velocity=0; bool down=false,pending=false; };
    std::unique_ptr<Instrument> instrument;
    Effects effects;
    std::array<Voice,128> voices{};
    std::array<std::array<std::array<Key,4>,128>,17> keys{};
    std::array<std::array<int,128>,17> controllers{};
    std::array<float,17> bends{};
    std::array<float,256> groupGains{};
    SpscQueue<Event,2048> events;
    std::mutex producer; // producers ONLY; never taken by render
    std::atomic<bool> panic{false};
    float rate=48000,volume=.7f,targetVolume=.7f;
    int limit=80;
    uint64_t nextId=0;
    void dispatch(const Event&) noexcept;
    void trigger(Trigger,int channel,int note,int velocity,uint64_t id) noexcept;
    void releaseId(int channel,int note,uint64_t id,int velocity) noexcept;
public:
    std::atomic<int> activeVoices{0};
    std::atomic<uint64_t> droppedEvents{0};
    void install(std::unique_ptr<Instrument> bank,const std::vector<EffectSpec>& fx,float sr); // stream stopped
    bool send(Event event); // outside callback; multiple producers serialized
    void render(float* stereo,int frames) noexcept;
    uint64_t misses() const {return instrument?instrument->cache->misses.load():0;}
    uint64_t errors() const {return instrument?instrument->cache->readErrors.load():0;}
    size_t memory() const {return instrument?instrument->residentBytes:0;}
};
}
