#pragma once
#include "SpscQueue.h"
#include <algorithm>
#include <cstdint>
namespace pocket {
struct MidiPacket {int status=0,a=0,b=0;int64_t nanos=0;uint64_t route=0;};
// One producer (render, or the stopped stream) and one MIDI I/O consumer.
// Android MIDI sends and memory ownership never enter the audio callback.
class MidiOutputQueue {
    SpscQueue<MidiPacket,4096> queue;
    std::atomic<uint64_t> configuration{0},failedRoute{0};
    void push(int status,int a,int b,int64_t nanos,uint64_t route) noexcept {
        if(!(route&1) || failedRoute.load(std::memory_order_relaxed)==route)return;
        if(!queue.push({status,a,b,nanos,route}))failedRoute.store(route,std::memory_order_release);
    }
public:
    static constexpr int Reset=0x100,Overflow=0x101;
    void configure(bool enabled,bool thru,int screenChannel) noexcept { // I/O consumer only
        auto previous=configuration.load(std::memory_order_relaxed);
        uint64_t flags=enabled?1ULL|(thru?2ULL:0ULL)|(uint64_t(std::clamp(screenChannel,0,15))<<8):0;
        auto epoch=((previous>>32)+1)<<32;
        configuration.store(epoch,std::memory_order_release);
        MidiPacket discard;for(int i=0;i<4096 && queue.pop(discard);++i){}
        configuration.store(epoch|flags,std::memory_order_release);
    }
    void message(int origin,int kind,int a,int b,int64_t nanos) noexcept {
        auto flags=configuration.load(std::memory_order_acquire);
        if(!(flags&1) || (origin<16 && !(flags&2)))return;
        int channel=origin>=17?origin-17:origin==16?int((flags>>8)&15):origin;
        if(channel<0 || channel>15 || kind<0x80 || kind>0xe0)return;
        push(kind|channel,a,b,nanos,flags);
    }
    void reset(int channels,bool controllers,int64_t nanos) noexcept {if(channels)push(Reset,channels,controllers?1:0,nanos,configuration.load(std::memory_order_acquire));}
    int drain(MidiPacket* packets,int capacity) noexcept { // never called by render
        auto route=configuration.load(std::memory_order_acquire);
        bool overflow=(route&1) && failedRoute.load(std::memory_order_acquire)==route;
        int n=0,scanned=0;MidiPacket packet;
        while(scanned++<4096 && n<capacity && queue.pop(packet)) {
            if(!overflow && (route&1) && packet.route==route)packets[n++]=packet;
        }
        if(overflow && capacity>0){packets[0]={Overflow,0xffff,0,0,route};return 1;}
        return n;
    }
};
}
