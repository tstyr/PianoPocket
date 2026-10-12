#pragma once
#include "SampleCache.h"
#include <array>
#include <memory>
namespace pocket {
enum class Trigger:int {Attack=0,Release=1,PedalDown=2,PedalUp=3};
struct Region {
    unsigned sample=0,group=0;
    int low=0,high=127,root=60,lowVel=1,highVel=127;
    Trigger trigger=Trigger::Attack;
    float gain=1,pan=0,tune=0,attack=.003f,decay=.1f,sustain=1,release=.25f,velTrack=1,keyTrack=1;
    int cc=-1,ccLow=0,ccHigh=127;
    int fadeInLow=-1,fadeInHigh=-1,fadeOutLow=-1,fadeOutHigh=-1;
    int64_t start=0,end=-1,loopStart=-1,loopEnd=-1;
    bool enabled=true;
};
struct Instrument {
    std::array<std::array<float,128>,256> velocityGains;
    std::vector<Sample> samples;
    std::vector<Region> regions;
    std::array<std::array<std::vector<unsigned>,128>,4> lookup;
    std::unique_ptr<SampleCache> cache;
    size_t residentBytes=0;
    Instrument(){for(auto& curve:velocityGains)curve.fill(1);}
    void prepare(bool offline=false){
        for(unsigned i=0;i<regions.size();++i){auto& r=regions[i];
            if(r.trigger==Trigger::PedalDown || r.trigger==Trigger::PedalUp)lookup[int(r.trigger)][60].push_back(i);
            else for(int k=r.low;k<=r.high;++k)lookup[int(r.trigger)][k].push_back(i);
        }
        cache=std::make_unique<SampleCache>(samples,offline);
        residentBytes=SampleCache::PageCount*(SampleCache::PageFrames+1)*2*sizeof(float);
        for(auto& s:samples)residentBytes+=s.head.size()*sizeof(float);
    }
};
}
