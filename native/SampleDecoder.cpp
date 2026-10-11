#include "SampleDecoder.h"
#include <fstream>
#include <cstring>
#include <cmath>
#include <algorithm>
#include <stdexcept>
namespace pocket {
static uint16_t u16(const uint8_t* p) { return p[0] | (uint16_t(p[1]) << 8); }
static uint32_t u32(const uint8_t* p) { return u16(p) | (uint32_t(u16(p+2)) << 16); }
Sample Sample::open(const std::string& path, size_t headFrames) {
    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file) throw std::runtime_error("WAV file missing: " + path);
    auto size = uint64_t(file.tellg()); file.seekg(0);
    uint8_t hdr[12]; file.read(reinterpret_cast<char*>(hdr),12);
    if (!file || memcmp(hdr,"RIFF",4) || memcmp(hdr+8,"WAVE",4)) throw std::runtime_error("Only RIFF WAV is supported: " + path);
    if (uint64_t(u32(hdr+4))+8 > size) throw std::runtime_error("Truncated RIFF");
    Sample s; s.path = path; bool fmt = false, data = false; uint32_t bytes = 0, align = 0;
    uint64_t limit=uint64_t(u32(hdr+4))+8;
    for (uint64_t pos = 12; pos + 8 <= limit;) {
        file.seekg(pos); uint8_t chunk[8]; file.read(reinterpret_cast<char*>(chunk),8);
        auto len = u32(chunk+4); if (pos + 8 + uint64_t(len) > limit) throw std::runtime_error("Truncated WAV chunk");
        if (!memcmp(chunk,"fmt ",4)) {
            if (len < 16 || len > 4096) throw std::runtime_error("Invalid WAV format chunk");
            std::vector<uint8_t> b(len); file.read(reinterpret_cast<char*>(b.data()),len);
            s.format=u16(b.data()); s.channels=u16(b.data()+2); s.rate=u32(b.data()+4); align=u16(b.data()+12); s.bits=u16(b.data()+14);
            if (s.format==0xfffe) {
                const uint8_t guidTail[14]={0,0,0,0,0x10,0,0x80,0,0,0xaa,0,0x38,0x9b,0x71};
                if(len<40 || u16(b.data()+16)<22 || u16(b.data()+18)!=s.bits || memcmp(b.data()+26,guidTail,14)) throw std::runtime_error("Unsupported extensible WAV subtype/valid bits");
                s.format=u16(b.data()+24);
            }
            fmt=true;
        } else if (!memcmp(chunk,"data",4) && !data) { s.dataOffset=pos+8; bytes=len; data=true; }
        pos += 8 + uint64_t(len) + (len&1);
    }
    if (!fmt || !data || s.channels<1 || s.channels>2 || s.rate<8000 || s.rate>192000 ||
        !((s.format==1 && (s.bits==16 || s.bits==24 || s.bits==32)) || (s.format==3 && s.bits==32)) ||
        align != s.channels*(s.bits/8) || bytes%align || !bytes) throw std::runtime_error("Unsupported or invalid WAV encoding: " + path);
    s.frames=bytes/align; auto count=std::min<uint64_t>(s.frames,headFrames);
    s.head.resize((count+1)*2); s.decode(0,count+1,s.head.data()); return s;
}
void Sample::decode(uint64_t start, size_t count, float* out) const {
    std::fill(out,out+count*2,0.f); if(start>=frames) return;
    auto n=std::min<uint64_t>(count,frames-start); auto stride=channels*(bits/8);
    std::vector<uint8_t> bytes(n*stride);
    std::ifstream file(path,std::ios::binary); file.seekg(dataOffset+start*stride);
    file.read(reinterpret_cast<char*>(bytes.data()),bytes.size()); if(!file) throw std::runtime_error("WAV read failed");
    for(size_t i=0;i<n;++i) for(unsigned c=0;c<2;++c) {
        auto p=bytes.data()+i*stride+std::min(c,channels-1)*(bits/8); float v=0;
        if(format==3) { memcpy(&v,p,4); if(!std::isfinite(v)) v=0; }
        else if(bits==16) v=int16_t(u16(p))/32768.f;
        else if(bits==24) { int32_t x=p[0]|(p[1]<<8)|(p[2]<<16); if(x&0x800000)x-=0x1000000; v=x/8388608.f; }
        else v=int32_t(u32(p))/2147483648.f;
        out[i*2+c]=std::clamp(v,-1.f,1.f);
    }
}
}
