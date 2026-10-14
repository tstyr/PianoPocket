#include "SamplerEngine.h"
#include "ImpulseLoader.h"
#include "OfflineRenderer.h"
#include "CompressedSampleDecoder.h"
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
    require(argc==2 || (argc==4 && std::string(argv[2])=="--offline-export") || (argc==6 && (std::string(argv[2])=="--reverb-stress" || std::string(argv[2])=="--poly-stress")),"usage: owned_preset_probe <production-parser.mapping> [--offline-export <private-output.wav>]");std::ifstream file(argv[1]);require(bool(file),"mapping file missing");
    auto bank=std::make_unique<Instrument>();std::unordered_map<std::string,unsigned> paths;std::vector<EffectSpec> fx;
    auto playable=[&](const std::string& path){auto extension=std::filesystem::path(path).extension().string();if(extension!=".flac" && extension!=".ogg")return path;auto folder=std::filesystem::path(argv[1]).parent_path()/"decoded-samples";std::filesystem::create_directories(folder);auto target=(folder/(std::to_string(std::hash<std::string>{}(path))+".wav")).string();if(!std::filesystem::exists(target))decodeCompressedSample(path,target);return target;};
    std::vector<Event> setup;std::string line;std::set<std::string> impulses;
    while(std::getline(file,line)){
        auto t=split(line,'\t');if(t.empty())continue;
        if(t[0]=="R"){
            auto ints=split(t.at(2),',');auto floats=split(t.at(3),',');require(((ints.size()==19 || ints.size()==20) && floats.size()==9) || ((ints.size()==24 || ints.size()==31 || ints.size()==32) && (floats.size()==13 || floats.size()==141)),"mapping schema");
            int a[32]={};float f[141]={};for(unsigned i=0;i<ints.size();++i)a[i]=std::stoi(ints[i]);for(unsigned i=0;i<floats.size();++i)f[i]=std::stof(floats[i]);
            auto path=t.at(1);auto found=paths.find(path);unsigned sample;
            if(found==paths.end()){sample=bank->samples.size();paths[path]=sample;bank->samples.push_back(Sample::open(playable(path)));}else sample=found->second;
            Region r;r.sample=sample;r.group=a[0];r.low=a[1];r.high=a[2];r.root=a[3];r.lowVel=a[4];r.highVel=a[5];r.trigger=Trigger(a[6]);r.start=a[7];r.end=a[8];r.loopStart=a[9];r.loopEnd=a[10];r.cc=a[11];r.ccLow=a[12];r.ccHigh=a[13];r.enabled=a[18];r.gain=f[0];r.pan=f[1];r.tune=f[2];r.attack=f[3];r.decay=f[4];r.sustain=f[5];r.release=f[6];r.velTrack=f[7];r.keyTrack=f[8];
            r.oneShot=a[19]&1;r.loopDuringRelease=a[19]&2;r.sfzVelocity=a[19]&4;r.fadeInLow=a[14];r.fadeInHigh=a[15];r.fadeOutLow=a[16];r.fadeOutHigh=a[17];
            if(ints.size()>=24){r.sequenceLength=a[20];r.sequencePosition=a[21];r.filterType=a[22];r.customVelocity=a[23];r.randomLow=f[9];r.randomHigh=f[10];r.cutoff=f[11];r.resonance=f[12];if(r.customVelocity)for(int i=0;i<128;++i)r.velocityCurve[i]=f[13+i];}
            require(r.group<256 && r.low>=0 && r.high<=127 && r.low<=r.high,"zone bounds");require(r.start>=0 && uint64_t(r.start)<bank->samples[sample].frames,"sample start");require(r.end<0 || uint64_t(r.end)<=bank->samples[sample].frames,"sample end");if(ints.size()>=31){r.switchLow=a[24];r.switchHigh=a[25];r.switchLast=a[26];r.switchDefault=a[27];r.offGroup=a[28];r.offBy=a[29];r.offMode=a[30];}if(ints.size()==32)r.bank=a[31];bank->regions.push_back(r);
        }else if(t[0]=="V"){auto g=std::stoi(t[1]);auto values=split(t[2],',');require(values.size()==128 && g>=0 && g<256,"velocity curve size");for(int i=0;i<128;++i)bank->velocityGains[g][i]=std::stof(values[i]);}
        else if(t[0]=="G")setup.push_back({6,0,std::stoi(t[1]),0,std::stof(t[2])});
        else if(t[0]=="M")setup.push_back({9,0,0,0,std::stof(t[1])});
        else if(t[0]=="L")setup.push_back({8,0,std::stoi(t[1]),0,0});
        else if(t[0]=="F"){EffectSpec spec;spec.type=EffectType(std::stoi(t[1]));auto p=split(t[2],',');require(p.size()==5,"effect parameters");for(int i=0;i<5;++i)spec.p[i]=std::stof(p[i]);if(spec.type==EffectType::Convolution){spec.ir=loadImpulse(t.at(3),48000);impulses.insert(t.at(3));}fx.push_back(std::move(spec));}
    }
    require(!bank->regions.empty(),"no regions");auto regions=bank->regions.size(),samples=bank->samples.size();int noteTests=0;float data[1024];

    if(argc==6 && std::string(argv[2])=="--poly-stress"){
        int notes=std::stoi(argv[3]),limit=std::stoi(argv[4]),memory=std::stoi(argv[5]);require(notes>0 && notes<=80 && limit<=1024 && memory>=32 && memory<=512,"stress range");
        bank->memoryBudget=size_t(memory)*1024*1024;bank->prepare();SamplerEngine engine;engine.install(std::move(bank),fx,48000);setup.push_back({8,0,limit});engine.initialControls(setup);
        for(int note=21;note<21+notes;++note)engine.send({0,0,note,90});
        auto deadline=std::chrono::steady_clock::now();double maxMs=0,peak=0;int zeroNotes=0;
        for(int frame=0;frame<48000*4;frame+=256){auto begin=std::chrono::steady_clock::now();engine.render(data,256);maxMs=std::max(maxMs,std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now()-begin).count());for(int i=0;i<512;++i){require(std::isfinite(data[i]),"finite stream audio");peak=std::max(peak,double(std::abs(data[i])));}if(frame==256)for(int note=21;note<21+notes;++note)if(engine.noteVoiceCount(note,0)==0)++zeroNotes;deadline+=std::chrono::nanoseconds(256000000000LL/48000);std::this_thread::sleep_until(deadline);}
        std::cout<<"{\"notes\":"<<notes<<",\"limit\":"<<limit<<",\"memoryMiB\":"<<memory<<",\"missingStartedNotes\":"<<zeroNotes<<",\"steals\":"<<engine.stolenVoices.load()<<",\"cacheMisses\":"<<engine.misses()<<",\"readErrors\":"<<engine.errors()<<",\"requestDrops\":"<<engine.cacheDrops()<<",\"pages\":"<<engine.cachePages()<<",\"peak\":"<<peak<<",\"maxHostCallbackMs\":"<<maxMs<<"}"<<std::endl;require(zeroNotes==0 && engine.errors()==0 && engine.cacheDrops()==0,"polyphonic stream stress");return 0;
    }
    if(argc==6){
        auto mix=std::stof(argv[4]);require(mix>=0 && mix<=1,"mix range");
        for(auto& effect:fx)if(effect.type==EffectType::Convolution){effect.ir=loadImpulse(argv[3],48000);effect.p[0]=mix;}
        bank->prepare(true);SamplerEngine engine;engine.install(std::move(bank),fx,48000);setup.push_back({8,0,128});engine.initialControls(setup);
        std::vector<int> notes=std::string(argv[5])=="solo"?std::vector<int>{71}:std::string(argv[5])=="chord"?std::vector<int>{71,75,78,83}:std::vector<int>{36,43,48,52,55,60,64,67,71,72,76,79};
        for(auto n:notes)engine.send({0,0,n,127});
        uint64_t hot=0,count=0;double energy=0,peak=0,prePeak=0,reduction=0;
        for(int frame=0;frame<48000*6;frame+=96){
            if(frame==24000)engine.send({2,0,64,127});
            if(frame==72000)for(auto n:notes)engine.send({1,0,n,0});
            if(frame==96000)engine.send({2,0,64,0});
            engine.render(data,96);prePeak=std::max(prePeak,double(engine.inputPeak.load()));reduction=std::max(reduction,double(engine.gainReductionDb.load()));for(int i=0;i<192;++i){auto value=data[i];require(std::isfinite(value) && std::abs(value)<=1,"bounded stress audio");peak=std::max(peak,double(std::abs(value)));energy+=value*value;++count;if(std::abs(value)>.98f)++hot;}
        }
        std::cout<<"{\"ir\":\""<<std::filesystem::path(argv[3]).filename().string()<<"\",\"mix\":"<<mix<<",\"notes\":"<<notes.size()<<",\"velocity\":127,\"outputPeak\":"<<peak<<",\"rms\":"<<std::sqrt(energy/count)<<",\"nearFullScaleSamples\":"<<hot<<",\"samples\":"<<count<<",\"cacheMisses\":"<<engine.misses()<<",\"preLimiterPeak\":"<<prePeak<<",\"maxReductionDb\":"<<reduction<<",\"readErrors\":"<<engine.errors()<<"}"<<std::endl;return 0;
    }
    if(argc==4){
        // Deliberately turn up the IR mix for this export test; the factory preset's mix is zero.
        for(auto& effect:fx)if(effect.type==EffectType::Convolution)effect.p[0]=.35f;
        bank->prepare(true);SamplerEngine offline;offline.install(std::move(bank),fx,48000);offline.initialControls(setup);
        offline.setTimeline({{0,{0,17,60,33}},{12000,{1,17,60,0}},{12000,{0,17,64,77}},{24000,{2,17,64,127}},{24000,{1,17,64,0}},{48000,{0,17,67,110}},{60000,{1,17,67,0}},{72000,{0,17,72,127}},{96000,{1,17,72,0}},{120000,{2,17,64,0}}},144000);
        std::atomic<bool> cancel{false};std::atomic<float> progress{0};renderWav(offline,argv[3],48000,24,480000,cancel,progress);
        auto output=Sample::open(argv[3],64);double total=0,tailEnergy=0;float block[1024];for(uint64_t frame=0;frame<output.frames;frame+=512){output.decode(frame,512,block);for(float value:block){require(std::isfinite(value) && std::abs(value)<=1,"offline export finite bounded output");total+=value*value;if(frame>=144000)tailEnergy+=value*value;}}
        require(total>0 && tailEnergy>0 && offline.misses()==0 && offline.errors()==0,"offline owned instrument export, tail and deterministic cache");
        std::cout<<"{\"status\":\"pass\",\"exportFrames\":"<<output.frames<<",\"bits\":24,\"rate\":48000,\"channels\":2,\"irMixTestOverride\":0.35,\"energy\":"<<total<<",\"tailEnergy\":"<<tailEnergy<<",\"cacheMisses\":"<<offline.misses()<<",\"readErrors\":"<<offline.errors()<<",\"androidDeviceVerified\":false}"<<std::endl;return 0;
    }
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
