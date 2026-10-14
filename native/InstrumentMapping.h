#pragma once
#include "SampleCache.h"
#include <array>
#include <memory>
#include <stdexcept>
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
    bool oneShot=false,loopDuringRelease=false,sfzVelocity=false;
    int sequenceLength=1,sequencePosition=1,filterType=0;
    float randomLow=0,randomHigh=1,cutoff=20000,resonance=0;
    int switchLow=-1,switchHigh=-1,switchLast=-1,switchDefault=-1,offGroup=0,offBy=0,offMode=0;
    bool customVelocity=false;
    std::array<float,128> velocityCurve{};
    std::array<double,5> filterCoefficients{1,0,0,0,0};
};
struct Instrument {
    std::array<std::array<float,128>,256> velocityGains;
    std::vector<Sample> samples;
    std::vector<Region> regions;
    std::array<std::array<std::vector<unsigned>,128>,4> lookup;
    std::unique_ptr<SampleCache> cache;
    std::array<std::vector<unsigned>,128> switchLookup;
    int switchDefault=-1;
    size_t residentBytes=0,memoryBudget=0;
    Instrument(){for(auto& curve:velocityGains)curve.fill(1);}
    void prepare(bool offline=false){
        for(auto& kind:lookup)for(auto& notes:kind)notes.clear();
        for(unsigned i=0;i<regions.size();++i){auto& r=regions[i];
            if(r.trigger==Trigger::PedalDown || r.trigger==Trigger::PedalUp)lookup[int(r.trigger)][60].push_back(i);
            else for(int k=r.low;k<=r.high;++k)lookup[int(r.trigger)][k].push_back(i);
        }
        switchDefault=-1;for(auto& notes:switchLookup)notes.clear();
        for(unsigned i=0;i<regions.size();++i){auto& r=regions[i];if(r.switchDefault>=0)switchDefault=r.switchDefault;for(int k=r.switchLow;k>=0 && k<=r.switchHigh;++k)switchLookup[k].push_back(i);}
        size_t heads=0,streamPages=0;
        for(auto& s:samples){
            heads+=s.head.size()*sizeof(float);
            const uint64_t residentFrames=s.head.empty()?0:s.head.size()/2-1;
            if(residentFrames<s.frames){
                const auto total=(s.frames+SampleCache::PageFrames-1)/SampleCache::PageFrames;
                const auto first=residentFrames/SampleCache::PageFrames;
                streamPages+=size_t(std::min<uint64_t>(total-first,SampleCache::MaxPages));
                streamPages=std::min(streamPages,SampleCache::MaxPages);
            }
        }
        size_t count=SampleCache::PageCount;
        if(memoryBudget){
            const size_t minimum=SampleCache::MinPages*sizeof(SampleCache::Page)+SampleCache::DirectoryBytes;
            if(heads+minimum>memoryBudget)throw std::runtime_error("Sample heads and cache exceed memory budget");
            count=std::min(SampleCache::MaxPages,(memoryBudget-heads-SampleCache::DirectoryBytes)/sizeof(SampleCache::Page));
            if(offline)count=std::min<size_t>(count,512);
        }
        count=std::min(count,std::max(SampleCache::MinPages,streamPages));
        cache=std::make_unique<SampleCache>(samples,offline,count);residentBytes=heads+cache->bytes();
    }
};
}
