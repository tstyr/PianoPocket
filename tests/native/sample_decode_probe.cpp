#include "CompressedSampleDecoder.h"
#include "SampleDecoder.h"
#include <iostream>
#include <cmath>
int main(int argc,char** argv){try{
    if(argc!=3)throw std::runtime_error("sample_decode_probe <FLAC/OGG> <WAV>");
    pocket::decodeCompressedSample(argv[1],argv[2]);auto s=pocket::Sample::open(argv[2]);double energy=0;for(float x:s.head){if(!std::isfinite(x))throw std::runtime_error("Non-finite PCM");energy+=x*x;}
    std::cout<<"{\"rate\":"<<s.rate<<",\"channels\":"<<s.channels<<",\"bits\":"<<s.bits<<",\"frames\":"<<s.frames<<",\"energy\":"<<energy<<"}\n";return 0;
}catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}}
