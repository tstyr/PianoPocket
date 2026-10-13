#pragma once
#include <algorithm>
#include <cstdint>
namespace pocket {
/** Polled off the audio thread. No automatic shrinking while performing. */
class AdaptiveBuffer {
    int current=2,lastXrun=-1;
    int64_t lastGrowth=0;
    bool pending=false;
public:
    void reset(int bursts,int xrun,int64_t now) noexcept {current=std::clamp(bursts,1,8);lastXrun=xrun;lastGrowth=now;pending=false;}
    int observe(bool stable,int xrun,int capacity,int64_t now) noexcept {
        current=std::min(current,std::clamp(capacity,1,8));
        if(!stable){lastXrun=xrun;pending=false;return current;}
        if(xrun<0){lastXrun=-1;pending=false;return current;}
        if(lastXrun>=0 && xrun>lastXrun)pending=true;
        if(lastXrun>=0 && xrun<lastXrun){pending=false;lastGrowth=now;}
        lastXrun=xrun;
        if(pending && now-lastGrowth>=2000 && current<std::min(8,capacity)){++current;lastGrowth=now;pending=false;}
        return current;
    }
};
}
