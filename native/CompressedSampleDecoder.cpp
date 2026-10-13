#include "CompressedSampleDecoder.h"
#include <FLAC/stream_decoder.h>
#define STB_VORBIS_HEADER_ONLY
#define STB_VORBIS_MAX_CHANNELS 2
#include "../third_party/stb/stb_vorbis.c"
#include <array>
#include <vector>
#include <fstream>
#include <memory>
#include <stdexcept>
#include <cstring>
#include <algorithm>
#include <cmath>
namespace pocket {
namespace {
constexpr uint64_t MaxBytes=512ULL*1024*1024;
class WaveSink {
    std::ofstream out;
    std::array<char,65536> buffer{};
    size_t pending=0;
    uint64_t bytes=0;
    void flush(){out.write(buffer.data(),pending);pending=0;if(!out)throw std::runtime_error("Compressed sample: storage write failed");}
public:
    unsigned channels=0,rate=0,bits=0;
    explicit WaveSink(const std::string& path):out(path,std::ios::binary){if(!out)throw std::runtime_error("Cannot create decoded sample");}
    void begin(unsigned c,unsigned r,unsigned b,bool floating=false){
        if(channels || c<1 || c>2 || r<8000 || r>192000 || (b!=16 && b!=24 && b!=32))throw std::runtime_error("Unsupported compressed sample format");
        channels=c;rate=r;bits=b;
        auto put=[this](uint32_t v,int n){for(int i=0;i<n;++i)out.put(char(v>>(i*8)));};
        out.write("RIFF",4);put(0,4);out.write("WAVEfmt ",8);put(16,4);put(floating?3:1,2);put(c,2);put(r,4);put(r*c*(b/8),4);put(c*(b/8),2);put(b,2);out.write("data",4);put(0,4);
    }
    void sample(uint32_t value){
        unsigned n=bits/8;if(bytes+n>MaxBytes)throw std::runtime_error("Decoded sample exceeds 512 MiB");
        if(pending+n>buffer.size())flush();for(unsigned i=0;i<n;++i)buffer[pending++]=char(value>>(8*i));bytes+=n;
    }
    void finish(){
        if(!channels || !bytes)throw std::runtime_error("Compressed sample contains no audio");flush();if(bytes&1)out.put(0);out.seekp(4);for(int i=0;i<4;++i)out.put(char((bytes+36+(bytes&1))>>(8*i)));out.seekp(40);for(int i=0;i<4;++i)out.put(char(bytes>>(8*i)));out.flush();if(!out)throw std::runtime_error("Decoded WAV finalization failed");
    }
};
struct FlacState {WaveSink sink;bool failed=false;explicit FlacState(const std::string& p):sink(p){};};
void metadata(const FLAC__StreamDecoder*,const FLAC__StreamMetadata* m,void* ptr){
    if(m->type!=FLAC__METADATA_TYPE_STREAMINFO)return;auto& s=*static_cast<FlacState*>(ptr);auto& i=m->data.stream_info;
    try{if(i.bits_per_sample!=16 && i.bits_per_sample!=24 && i.bits_per_sample!=32)throw std::runtime_error("Unsupported FLAC bit depth");if(i.total_samples>MaxBytes/(std::max(1u,i.channels)*(i.bits_per_sample/8)))throw std::runtime_error("FLAC exceeds decode size limit");s.sink.begin(i.channels,i.sample_rate,i.bits_per_sample);}catch(...){s.failed=true;}
}
FLAC__StreamDecoderWriteStatus writeFlac(const FLAC__StreamDecoder*,const FLAC__Frame* frame,const FLAC__int32* const data[],void* ptr){
    auto& s=*static_cast<FlacState*>(ptr);if(s.failed)return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
    try{for(unsigned i=0;i<frame->header.blocksize;++i)for(unsigned c=0;c<s.sink.channels;++c)s.sink.sample(uint32_t(data[c][i]));}catch(...){s.failed=true;return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;}return FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE;
}
void flacError(const FLAC__StreamDecoder*,FLAC__StreamDecoderErrorStatus,void* p){static_cast<FlacState*>(p)->failed=true;}
}
void decodeCompressedSample(const std::string& source,const std::string& target){
    std::ifstream file(source,std::ios::binary|std::ios::ate);if(!file || uint64_t(file.tellg())>MaxBytes)throw std::runtime_error("Compressed sample missing or too large");char magic[4]={};file.seekg(0);file.read(magic,4);file.close();
    if(!std::memcmp(magic,"fLaC",4)){
        FlacState state(target);std::unique_ptr<FLAC__StreamDecoder,decltype(&FLAC__stream_decoder_delete)> decoder(FLAC__stream_decoder_new(),FLAC__stream_decoder_delete);if(!decoder)throw std::bad_alloc();
        FLAC__stream_decoder_set_md5_checking(decoder.get(),true);
        auto init=FLAC__stream_decoder_init_file(decoder.get(),source.c_str(),writeFlac,metadata,flacError,&state);
        if(init!=FLAC__STREAM_DECODER_INIT_STATUS_OK)throw std::runtime_error("Cannot open FLAC sample");
        bool ok=FLAC__stream_decoder_process_until_end_of_stream(decoder.get());bool checked=FLAC__stream_decoder_finish(decoder.get());if(!ok || !checked || state.failed)throw std::runtime_error("Corrupt or unsupported FLAC sample");state.sink.finish();
    }else if(!std::memcmp(magic,"OggS",4)){
        std::vector<char> workspace(16*1024*1024);stb_vorbis_alloc alloc{workspace.data(),int(workspace.size())};int error=0;
        std::unique_ptr<stb_vorbis,decltype(&stb_vorbis_close)> decoder(stb_vorbis_open_filename(source.c_str(),&error,&alloc),stb_vorbis_close);
        if(!decoder)throw std::runtime_error("Cannot decode OGG Vorbis (Opus is unsupported)");auto info=stb_vorbis_get_info(decoder.get());WaveSink sink(target);sink.begin(info.channels,info.sample_rate,32,true);
        std::array<float,8192> values{};int n;
        while((n=stb_vorbis_get_samples_float_interleaved(decoder.get(),info.channels,values.data(),int(values.size())))>0){for(int i=0;i<n*info.channels;++i){float value=std::isfinite(values[i])?std::clamp(values[i],-1.f,1.f):0.f;uint32_t word;std::memcpy(&word,&value,4);sink.sample(word);}}
        if(stb_vorbis_get_error(decoder.get())!=VORBIS__no_error)throw std::runtime_error("Corrupt OGG Vorbis sample");sink.finish();
    }else throw std::runtime_error("Only FLAC and OGG Vorbis compression are supported");
}
}
