#include "ImpulseLoader.h"
#include "SampleDecoder.h"
#include <stdexcept>
#include <algorithm>
namespace pocket {
std::vector<float> loadImpulse(const std::string& path,float rate){
    auto wave=Sample::open(path,0);auto count=size_t(wave.frames*double(rate)/wave.rate);
    if(!count || count>1048576)throw std::runtime_error("IR exceeds 1048576 output frames; not truncated");
    std::vector<float> source(wave.frames*2),output(count*2);wave.decode(0,wave.frames,source.data());
    for(size_t i=0;i<count;++i){double position=i*double(wave.rate)/rate;auto k=std::min<uint64_t>(position,wave.frames-1),next=std::min<uint64_t>(k+1,wave.frames-1);auto fraction=float(position-k);for(int c=0;c<2;++c)output[i*2+c]=source[k*2+c]*(1-fraction)+source[next*2+c]*fraction;}
    return output;
}
}
