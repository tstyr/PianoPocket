#include "CompressedEncoder.h"
#include "SampleDecoder.h"
#include <FLAC/stream_encoder.h>
#include <lame.h>
#include <array>
#include <cstdio>
#include <fstream>
#include <memory>
#include <stdexcept>
namespace pocket {
struct FileCloser { void operator()(FILE* f) const { fclose(f); } };
void encodeCompressed(const std::string& wav,const std::string& output,int format,int bitrate,std::atomic<bool>& cancel,std::atomic<float>& progress) {
    if(wav==output || (format!=1 && format!=2))throw std::runtime_error("Invalid compressed export settings");
    if(format==2 && bitrate!=128 && bitrate!=192 && bitrate!=256 && bitrate!=320)throw std::runtime_error("Invalid MP3 bitrate");
    const auto sample=Sample::open(wav,0);
    if(sample.channels!=2 || sample.format!=1 || (sample.bits!=16 && sample.bits!=24) || (sample.rate!=44100 && sample.rate!=48000))throw std::runtime_error("Compressed export requires stereo PCM WAV");
    try {
        std::ifstream input(wav,std::ios::binary);input.seekg(sample.dataOffset);
        std::unique_ptr<FLAC__StreamEncoder,decltype(&FLAC__stream_encoder_delete)> flac(nullptr,FLAC__stream_encoder_delete);
        std::unique_ptr<lame_global_flags,decltype(&lame_close)> mp3(nullptr,lame_close);
        std::unique_ptr<FILE,FileCloser> file;
        if(format==1) {
            flac.reset(FLAC__stream_encoder_new());if(!flac)throw std::runtime_error("Cannot allocate FLAC encoder");
            if(!FLAC__stream_encoder_set_channels(flac.get(),2) || !FLAC__stream_encoder_set_bits_per_sample(flac.get(),sample.bits) || !FLAC__stream_encoder_set_sample_rate(flac.get(),sample.rate) || !FLAC__stream_encoder_set_compression_level(flac.get(),5) || !FLAC__stream_encoder_set_verify(flac.get(),true) || !FLAC__stream_encoder_set_total_samples_estimate(flac.get(),sample.frames) || FLAC__stream_encoder_init_file(flac.get(),output.c_str(),nullptr,nullptr)!=FLAC__STREAM_ENCODER_INIT_STATUS_OK)throw std::runtime_error("Cannot initialize FLAC encoder");
        } else {
            mp3.reset(lame_init());file.reset(fopen(output.c_str(),"w+b"));if(!mp3 || !file)throw std::runtime_error("Cannot create MP3 export");
            if(lame_set_num_channels(mp3.get(),2)<0 || lame_set_in_samplerate(mp3.get(),sample.rate)<0 || lame_set_out_samplerate(mp3.get(),sample.rate)<0 || lame_set_brate(mp3.get(),bitrate)<0 || lame_set_quality(mp3.get(),2)<0 || lame_set_num_samples(mp3.get(),sample.frames)<0 || lame_set_bWriteVbrTag(mp3.get(),1)<0 || lame_init_params(mp3.get())<0)throw std::runtime_error("Cannot initialize MP3 encoder");
        }
        constexpr size_t frames=4096;std::array<unsigned char,frames*2*3> raw{};std::array<FLAC__int32,frames*2> pcm{};std::array<float,frames*2> floats{};std::array<unsigned char,16384> encoded{};
        progress.store(0);
        auto write=[&](int bytes){if(bytes<0)throw std::runtime_error("MP3 encoding failed");if(bytes && fwrite(encoded.data(),1,bytes,file.get())!=size_t(bytes))throw std::runtime_error("Export storage is full or unavailable");};
        for(uint64_t done=0;done<sample.frames;) {
            if(cancel.load())throw std::runtime_error("書き出しをキャンセルしました");
            const auto count=std::min<uint64_t>(frames,sample.frames-done);const auto width=sample.bits/8;
            input.read(reinterpret_cast<char*>(raw.data()),count*2*width);if(!input)throw std::runtime_error("Export PCM read failed");
            for(size_t i=0;i<count*2;++i){auto p=raw.data()+i*width;int32_t value=p[0]|(int32_t(p[1])<<8);if(width==3){value|=int32_t(p[2])<<16;if(value&0x800000)value-=0x1000000;}else if(value&0x8000)value-=0x10000;pcm[i]=value;floats[i]=float(value)/float(uint32_t(1)<<(sample.bits-1));}
            if(format==1){if(!FLAC__stream_encoder_process_interleaved(flac.get(),pcm.data(),count))throw std::runtime_error("FLAC encoding or verification failed");}
            else write(lame_encode_buffer_interleaved_ieee_float(mp3.get(),floats.data(),count,encoded.data(),encoded.size()));
            done+=count;progress.store(float(double(done)/sample.frames));
        }
        if(cancel.load())throw std::runtime_error("書き出しをキャンセルしました");
        if(format==1){if(!FLAC__stream_encoder_finish(flac.get()))throw std::runtime_error("Cannot finish FLAC export");}
        else {write(lame_encode_flush(mp3.get(),encoded.data(),encoded.size()));lame_mp3_tags_fid(mp3.get(),file.get());if(fflush(file.get()) || ferror(file.get()))throw std::runtime_error("Cannot finish MP3 export");}
        progress.store(1);
    } catch(...) {std::remove(output.c_str());throw;}
}
}
