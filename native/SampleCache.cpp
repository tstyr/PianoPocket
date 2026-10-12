#include "SampleCache.h"
#include <chrono>
#include <algorithm>
namespace pocket {
SampleCache::SampleCache(const std::vector<Sample>& s):samples(s),pages(new Page[PageCount]),worker([this]{run();}){}
SampleCache::~SampleCache(){running.store(false);worker.join();}
void SampleCache::prefetch(unsigned s,uint64_t f) noexcept {
    if(s>=samples.size() || f>=samples[s].frames) return;
    if(f<samples[s].head.size()/2-1)return;
    requests.push((uint64_t(s)<<32)|(f/PageFrames));
}
void SampleCache::view(unsigned s,uint64_t f,View& out) noexcept {
    out.reset(); if(s>=samples.size())return;
    const auto& sample=samples[s];
    if(f+1<sample.head.size()/2) {out.data=sample.head.data();out.start=0;out.frames=sample.head.size()/2-1;return;}
    uint64_t key=(uint64_t(s)<<32)|(f/PageFrames);
    for(size_t i=0;i<PageCount;++i){
        auto& p=pages[i]; int expected=p.readers.load(std::memory_order_relaxed);
        if(expected<0 || !p.readers.compare_exchange_strong(expected,expected+1,std::memory_order_acquire))continue;
        if(p.key==key){out.page=&p;out.data=p.data.data();out.start=(f/PageFrames)*PageFrames;out.frames=PageFrames;p.used.store(++clock,std::memory_order_relaxed);return;}
        p.readers.fetch_sub(1,std::memory_order_release);
    }
    misses.fetch_add(1,std::memory_order_relaxed);prefetch(s,f);
}
void SampleCache::run(){
    uint64_t key;
    while(running.load()){
        if(!requests.pop(key)){std::this_thread::sleep_for(std::chrono::milliseconds(1));continue;}
        bool found=false; Page* victim=nullptr; uint64_t oldest=UINT64_MAX;
        for(size_t i=0;i<PageCount;++i){
            auto& p=pages[i]; // Key writes happen only on this worker; reader claims prevent overwrite.
            if(p.key==key){found=true;break;}
            auto age=p.used.load(std::memory_order_relaxed);
            if(p.readers.load(std::memory_order_relaxed)==0 && (!victim || age<oldest)){victim=&p;oldest=age;}
        }
        if(found || !victim)continue;
        int expected=0;if(!victim->readers.compare_exchange_strong(expected,-1,std::memory_order_acquire))continue;
        auto s=unsigned(key>>32); auto f=(key&0xffffffff)*PageFrames;
        victim->key=UINT64_MAX;
        try{samples.at(s).decode(f,PageFrames+1,victim->data.data());victim->key=key;victim->used.store(++clock,std::memory_order_relaxed);}
        catch(...){readErrors.fetch_add(1);}
        victim->readers.store(0,std::memory_order_release);
    }
}
}
