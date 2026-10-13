#include "SampleCache.h"
#include <chrono>
#include <algorithm>
namespace pocket {
static constexpr uint32_t NoPage=UINT32_MAX;
static constexpr uint64_t UrgentBit=uint64_t(1)<<63;
size_t SampleCache::bucket(uint64_t key) noexcept {key^=key>>33;key*=0xff51afd7ed558ccdULL;key^=key>>33;key*=0xc4ceb9fe1a85ec53ULL;key^=key>>33;return key&(Buckets-1);}
SampleCache::SampleCache(const std::vector<Sample>& s,bool sync,size_t n):samples(s),count(std::clamp(n,MinPages,MaxPages)),pages(new Page[count]),offline(sync){
    for(auto& x:directory)x.store(NoPage);for(auto& x:pending)x.store(0);
    if(!offline)worker=std::thread([this]{run();});
}
SampleCache::~SampleCache(){running.store(false);if(worker.joinable())worker.join();}
bool SampleCache::find(uint64_t key,View& out,bool touch) noexcept {
    auto base=bucket(key)*Ways;
    for(size_t i=0;i<Ways;++i){auto index=directory[base+i].load(std::memory_order_acquire);if(index>=count)continue;auto& p=pages[index];int readers=p.readers.load(std::memory_order_relaxed);
        if(readers<0 || !p.readers.compare_exchange_strong(readers,readers+1,std::memory_order_acquire))continue;
        if(p.key==key){out.page=&p;out.data=p.data.data();out.start=(key&0xffffffff)*PageFrames;out.frames=PageFrames;if(touch)p.used.store(++clock,std::memory_order_relaxed);return true;}
        p.readers.fetch_sub(1,std::memory_order_release);
    }return false;
}
void SampleCache::request(uint64_t key,bool priority) noexcept {
    auto token=(key+1)|(priority?UrgentBit:0);auto& mark=pending[bucket(key)];auto previous=mark.load(std::memory_order_relaxed);
    if(previous==token || (!priority && previous==((key+1)|UrgentBit)))return;
    mark.store(token,std::memory_order_relaxed);
    bool sent=priority?urgent.push(key):requests.push(key);
    if(!sent){mark.compare_exchange_strong(token,0);requestDrops.fetch_add(1);}
}
void SampleCache::prefetch(unsigned s,uint64_t f) noexcept {
    if(offline || s>=samples.size() || f>=samples[s].frames || f<samples[s].head.size()/2-1)return;
    auto key=(uint64_t(s)<<32)|(f/PageFrames);View loaded;if(find(key,loaded,false))return;request(key,false);
}
void SampleCache::view(unsigned s,uint64_t f,View& out) noexcept {
    out.reset();if(s>=samples.size())return;const auto& sample=samples[s];
    if(f+1<sample.head.size()/2){out.data=sample.head.data();out.start=0;out.frames=sample.head.size()/2-1;return;}
    auto key=(uint64_t(s)<<32)|(f/PageFrames);if(find(key,out,true))return;
    if(offline){try{if(load(key) && find(key,out,true))return;}catch(...){readErrors.fetch_add(1);}return;}
    misses.fetch_add(1,std::memory_order_relaxed);request(key,true);
}
bool SampleCache::load(uint64_t key){
    View existing;if(find(key,existing,false))return true;
    size_t victim=count;uint64_t oldest=UINT64_MAX;
    for(size_t i=0;i<count;++i){auto& p=pages[i];if(p.readers.load(std::memory_order_relaxed)!=0)continue;if(p.key==UINT64_MAX){victim=i;break;}auto age=p.used.load(std::memory_order_relaxed);if(age<oldest){victim=i;oldest=age;}}
    if(victim==count)return false;auto& p=pages[victim];int expected=0;if(!p.readers.compare_exchange_strong(expected,-1,std::memory_order_acquire))return false;
    auto oldKey=p.key;if(oldKey!=UINT64_MAX){auto base=bucket(oldKey)*Ways;for(size_t i=0;i<Ways;++i){uint32_t old=uint32_t(victim);directory[base+i].compare_exchange_strong(old,NoPage,std::memory_order_acq_rel);}}
    p.key=UINT64_MAX;
    try{samples.at(unsigned(key>>32)).decode((key&0xffffffff)*PageFrames,PageFrames+1,p.data.data());p.key=key;p.used.store(++clock,std::memory_order_relaxed);}
    catch(...){p.readers.store(0,std::memory_order_release);throw;}
    p.readers.store(0,std::memory_order_release);
    auto base=bucket(key)*Ways;size_t slot=base;oldest=UINT64_MAX;
    for(size_t i=0;i<Ways;++i){auto index=directory[base+i].load();if(index==NoPage){slot=base+i;break;}auto age=pages[index].used.load();if(age<oldest){oldest=age;slot=base+i;}}
    directory[slot].store(uint32_t(victim),std::memory_order_release);return true;
}
void SampleCache::run(){
    uint64_t key;
    while(running.load()){
        bool priority=urgent.pop(key);if(!priority && !requests.pop(key)){std::this_thread::sleep_for(std::chrono::milliseconds(1));continue;}
        try{load(key);}catch(...){readErrors.fetch_add(1);}
        auto token=(key+1)|(priority?UrgentBit:0);pending[bucket(key)].compare_exchange_strong(token,0);
    }
}
}
