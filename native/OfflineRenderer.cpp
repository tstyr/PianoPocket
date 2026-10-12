#include "OfflineRenderer.h"
#include <fstream>
#include <cstdio>
#include <stdexcept>
#include <cstring>
namespace pocket {
void renderWav(SamplerEngine& sampler,const std::string& path,int rate,int bits,int64_t frames,std::atomic<bool>& cancel,std::atomic<float>& progress){
    if((bits!=16 && bits!=24 && bits!=32) || (rate!=44100 && rate!=48000) || frames<0 || frames>int64_t(rate)*7230)throw std::runtime_error("Invalid WAV export settings");
    uint64_t bytes=uint64_t(frames)*2*(bits/8);
    auto extra=bits==32?14u:0u; // Float WAVEFORMATEX and fact sample count.
    if(bytes>UINT32_MAX-36-extra)throw std::runtime_error("WAV exceeds RIFF size limit");
    try {
        std::ofstream out(path,std::ios::binary|std::ios::trunc);if(!out)throw std::runtime_error("Cannot create export file");
        auto put=[&](uint32_t value,int n){for(int i=0;i<n;++i)out.put(char(value>>(i*8)));};
        out.write("RIFF",4);put(uint32_t(bytes+36+extra),4);out.write("WAVEfmt ",8);put(bits==32?18:16,4);put(bits==32?3:1,2);put(2,2);put(rate,4);put(rate*2*(bits/8),4);put(2*(bits/8),2);put(bits,2);
        if(bits==32){put(0,2);out.write("fact",4);put(4,4);put(uint32_t(frames),4);}
        out.write("data",4);put(uint32_t(bytes),4);
        std::array<float,512> block{};sampler.send({10,0,1});
        const int latency=sampler.outputDelayFrames();int64_t processed=0;
        for(int64_t done=0;done<frames;){
            if(cancel.load())throw std::runtime_error("書き出しをキャンセルしました");
            int n=int(std::min<int64_t>(256,frames+latency-processed));sampler.render(block.data(),n);
            if(sampler.transport()[2]>0)throw std::runtime_error("MIDI event density exceeds render limit");
            if(sampler.errors() || sampler.misses())throw std::runtime_error("Sample read failed during offline export");
            int skip=int(std::clamp<int64_t>(latency-processed,0,n));
            for(int i=skip*2;i<n*2;++i){
                float value=std::clamp(block[i],-1.f,1.f);
                if(bits==32){uint32_t raw;std::memcpy(&raw,&value,4);put(raw,4);}
                else {int32_t pcm=int32_t(std::llround(double(value)*((int64_t(1)<<(bits-1))-1)));put(uint32_t(pcm),bits/8);}
            }
            processed+=n;done+=n-skip;progress.store(float(double(done)/frames));if(!out)throw std::runtime_error("Export storage is full or unavailable");
        }
        out.flush();if(!out)throw std::runtime_error("Cannot finish WAV export");
    } catch(...) {std::remove(path.c_str());throw;}
}
}
