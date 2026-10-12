#include "SamplerEngine.h"
#include "ImpulseLoader.h"
#include <filesystem>
#include <fstream>
#include <sstream>
#include <unordered_map>
#include <iostream>
#include <chrono>
#include <set>
#include <thread>
using namespace pocket;
static std::vector<std::string> split(const std::string& line,char delimiter){std::vector<std::string> parts;std::stringstream s(line);std::string p;while(std::getline(s,p,delimiter))parts.push_back(p);return parts;}
static void require(bool pass,const char* message){if(!pass)throw std::runtime_error(message);}
int main(int argc,char** argv){try{
    require(argc==2,"usage: owned_preset_probe <production-parser.mapping>");std::ifstream file(argv[1]);require(bool(file),"mapping file missing");
    auto bank=std::make_unique<Instrument>();std::unordered_map<std::string,unsigned> paths;std::vector<EffectSpec> fx;
    std::vector<Event> setup;std::string line;std::set<std::string> impulses;
    while(std::getline(file,line)){
        auto t=split(line,'\t');if(t.empty())continue;
        if(t[0]=="R"){
            auto ints=split(t.at(2),',');auto floats=split(t.at(3),',');require(ints.size()==19 && floats.size()==9,"mapping schema");
            int a[19];float f[9];for(int i=0;i<19;++i)a[i]=std::stoi(ints[i]);for(int i=0;i<9;++i)f[i]=std::stof(floats[i]);
            auto path=t.at(1);auto found=paths.find(path);unsigned sample;
            if(found==paths.end()){sample=bank->samples.size();paths[path]=sample;bank->samples.push_back(Sample::open(path));}else sample=found->second;
            Region r;r.sample=sample;r.group=a[0];r.low=a[1];r.high=a[2];r.root=a[3];r.lowVel=a[4];r.highVel=a[5];r.trigger=Trigger(a[6]);r.start=a[7];r.end=a[8];r.loopStart=a[9];r.loopEnd=a[10];r.cc=a[11];r.ccLow=a[12];r.ccHigh=a[13];r.enabled=a[18];r.gain=f[0];r.pan=f[1];r.tune=f[2];r.attack=f[3];r.decay=f[4];r.sustain=f[5];r.release=f[6];r.velTrack=f[7];r.keyTrack=f[8];
            require(r.group<256 && r.low>=0 && r.high<=127 && r.low<=r.high,"zone bounds");require(r.start>=0 && uint64_t(r.start)<bank->samples[sample].frames,"sample start");require(r.end<0 || uint64_t(r.end)<=bank->samples[sample].frames,"sample end");bank->regions.push_back(r);
        }else if(t[0]=="V"){auto g=std::stoi(t[1]);auto values=split(t[2],',');require(values.size()==128 && g>=0 && g<256,"velocity curve size");for(int i=0;i<128;++i)bank->velocityGains[g][i]=std::stof(values[i]);}
        else if(t[0]=="G")setup.push_back({6,0,std::stoi(t[1]),0,std::stof(t[2])});
        else if(t[0]=="M")setup.push_back({9,0,0,0,std::stof(t[1])});
        else if(t[0]=="L")setup.push_back({8,0,std::stoi(t[1]),0,0});
        else if(t[0]=="F"){EffectSpec spec;spec.type=EffectType(std::stoi(t[1]));auto p=split(t[2],',');require(p.size()==5,"effect parameters");for(int i=0;i<5;++i)spec.p[i]=std::stof(p[i]);if(spec.type==EffectType::Convolution){spec.ir=loadImpulse(t.at(3),48000);impulses.insert(t.at(3));}fx.push_back(std::move(spec));}
    }
    require(!bank->regions.empty(),"no regions");auto regions=bank->regions.size(),samples=bank->samples.size();int noteTests=0;float data[1024];
    // Verify all genuine samples including the five menu IRs using the production decoder.
    auto sampleRoot=std::filesystem::path(bank->samples[0].path).parent_path();size_t decodedFiles=0;std::set<std::string> rates;
    for(auto& entry:std::filesystem::recursive_directory_iterator(sampleRoot))if(entry.is_regular_file() && entry.path().extension()==".wav"){
        auto s=Sample::open(entry.path().string(),64);++decodedFiles;rates.insert(std::to_string(s.rate));
        if(entry.path().parent_path().filename()=="IR"){auto ir=loadImpulse(entry.path().string(),48000);Convolver validate(ir);
            for(size_t i=0;i<ir.size()/2+512;++i){float l=i==0?1.f:0.f,r=l;validate.process(l,r);float expectedL=i>=256 && i-256<ir.size()/2?ir[(i-256)*2]:0,expectedR=i>=256 && i-256<ir.size()/2?ir[(i-256)*2+1]:0;require(std::isfinite(l) && std::isfinite(r) && std::abs(l-expectedL)<.0002f && std::abs(r-expectedR)<.0002f,"complete real IR delta response at 256-frame delay");}}
    }
    bank->prepare();SamplerEngine engine;engine.install(std::move(bank),fx,48000);for(auto& event:setup)engine.send(event);
    double energy=0;int maxVoices=0;
    for(int note=21;note<=108;++note)for(int velocity:{1,25,33,34,43,64,69,73,78,87,95,102,111,120,127}){
        engine.send({2,0,120,0});engine.render(data,512);engine.send({0,0,note,velocity});double blockEnergy=0;
        for(int block=0;block<8;++block){engine.render(data,512);for(auto value:data){require(std::isfinite(value) && std::abs(value)<=1,"finite bounded audio");blockEnergy+=value*value;}}
        require(blockEnergy>1e-14,"silent key/velocity zone");energy+=blockEnergy;maxVoices=std::max(maxVoices,engine.activeVoices.load());++noteTests;
    }
    engine.send({2,0,120,0});engine.render(data,512);std::this_thread::sleep_for(std::chrono::milliseconds(100));auto before=engine.misses();auto next=std::chrono::steady_clock::now();
    engine.send({0,0,60,80});engine.send({2,0,64,127});engine.send({1,0,60,0});
    for(int i=0;i<300;++i){engine.render(data,480);next+=std::chrono::milliseconds(10);std::this_thread::sleep_until(next);}
    auto realtimeMisses=engine.misses()-before;require(engine.activeVoices>0,"pedal holds note");
    engine.send({2,0,64,0});for(int i=0;i<70;++i)engine.render(data,512);
    engine.send({5});for(int i=0;i<110;++i)engine.render(data,512);require(engine.activeVoices==0,"all notes off ends voices");
    std::cout<<"{\"status\":\"pass\",\"regions\":"<<regions<<",\"mappedSamples\":"<<samples<<",\"decodedWavs\":"<<decodedFiles<<",\"noteVelocityCases\":"<<noteTests<<",\"maxObservedVoices\":"<<maxVoices<<",\"residentBytes\":"<<engine.memory()<<",\"energy\":"<<energy<<",\"readErrors\":"<<engine.errors()<<",\"cacheMissesOfflineTotal\":"<<engine.misses()<<",\"pacedStreamingSeconds\":3,\"pacedStreamingCacheMisses\":"<<realtimeMisses<<",\"androidDeviceVerified\":false}"<<std::endl;
    return 0;
}catch(const std::exception& e){std::cerr<<"FAIL: "<<e.what()<<'\n';return 1;}}
