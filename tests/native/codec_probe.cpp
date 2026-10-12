#include "CompressedEncoder.h"
#include <atomic>
#include <fstream>
#include <iostream>
#include <stdexcept>
int main(int argc,char** argv){try {
    if(argc!=3)throw std::runtime_error("usage: codec_probe input.wav output-prefix");
    std::atomic<bool> cancel{false};std::atomic<float> progress{0};const std::string prefix=argv[2];
    pocket::encodeCompressed(argv[1],prefix+".flac",1,256,cancel,progress);
    if(progress.load()!=1)throw std::runtime_error("incomplete FLAC export");
    for(int bitrate:{128,192,256,320})pocket::encodeCompressed(argv[1],prefix+"-"+std::to_string(bitrate)+".mp3",2,bitrate,cancel,progress);
    cancel.store(true);bool failed=false;try{pocket::encodeCompressed(argv[1],prefix+"-cancel.mp3",2,256,cancel,progress);}catch(...){failed=true;}
    if(!failed || std::ifstream(prefix+"-cancel.mp3").good())throw std::runtime_error("cancelled MP3 was not removed");
    failed=false;try{pocket::encodeCompressed(argv[1],prefix+"-cancel.flac",1,256,cancel,progress);}catch(...){failed=true;}
    if(!failed || std::ifstream(prefix+"-cancel.flac").good())throw std::runtime_error("cancelled FLAC was not removed");
    std::cout<<"PASS FLAC / MP3 128,192,256,320 / cancellation\n";return 0;
}catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}}
