#include "SamplerEngine.h"
#include "AdaptiveBuffer.h"
#include "Effects.h"
#include "OfflineRenderer.h"
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <cstdlib>
#include <chrono>
#include <thread>
#include <atomic>
using namespace pocket;
static thread_local bool audioThread=false;
static std::atomic<int> audioAllocations{0};
void* operator new(std::size_t n){if(audioThread)++audioAllocations;if(auto p=std::malloc(n))return p;throw std::bad_alloc();}
void* operator new[](std::size_t n){return ::operator new(n);}
void* operator new(std::size_t n,const std::nothrow_t&) noexcept {try{return ::operator new(n);}catch(...){return nullptr;}}
void* operator new[](std::size_t n,const std::nothrow_t&) noexcept {return ::operator new(n,std::nothrow);}
void operator delete(void* p,const std::nothrow_t&) noexcept {std::free(p);}
void operator delete[](void* p,const std::nothrow_t&) noexcept {std::free(p);}

void operator delete(void* p) noexcept {std::free(p);}
void operator delete[](void* p) noexcept {std::free(p);}
void operator delete(void* p,std::size_t) noexcept {std::free(p);}
void operator delete[](void* p,std::size_t) noexcept {std::free(p);}
static void check(bool v,const char* text){if(!v)throw std::runtime_error(text);}
static void render(SamplerEngine& e,float* b,int n){audioThread=true;e.render(b,n);audioThread=false;}
static void put16(std::ofstream& f,uint16_t x){char b[2]={char(x),char(x>>8)};f.write(b,2);}
static void put32(std::ofstream& f,uint32_t x){put16(f,x);put16(f,x>>16);}
static std::string wav(){
    std::string path="/tmp/pianopocket-test.wav";std::ofstream f(path,std::ios::binary);unsigned frames=50000;
    f.write("RIFF",4);put32(f,36+frames*4);f.write("WAVEfmt ",8);put32(f,16);put16(f,1);put16(f,2);put32(f,44100);put32(f,44100*4);put16(f,4);put16(f,16);f.write("data",4);put32(f,frames*4);
    for(unsigned i=0;i<frames;++i){put16(f,int16_t(std::sin(i*.04)*16000));put16(f,int16_t(std::sin(i*.04)*8000));}return path;
}
static std::unique_ptr<Instrument> bank(bool release=false){
    auto b=std::make_unique<Instrument>();Sample s;s.frames=100000;s.rate=48000;s.head.resize((s.frames+1)*2);for(size_t i=0;i<s.frames;++i){s.head[i*2]=.15f*std::sin(i*.04);s.head[i*2+1]=.075f*std::sin(i*.04);}b->samples.push_back(std::move(s));Region r;r.release=.01f;b->regions.push_back(r);if(release){r.trigger=Trigger::Release;r.gain=.1f;b->regions.push_back(r);}b->prepare();return b;
}
static float energy(const float* b,int n){float sum=0;for(int i=0;i<n*2;++i)sum+=b[i]*b[i];return sum;}
int main(){try{
    {AdaptiveBuffer p;p.reset(4,0,0);check(p.observe(true,1,8,500)==4,"adaptive buffer cooldown");check(p.observe(true,1,8,2000)==5,"pending underrun increases by one burst");check(p.observe(true,1,8,9000)==5,"stable buffers do not shrink");check(p.observe(true,0,8,10000)==5,"counter restart is not an underrun");check(p.observe(false,20,8,15000)==5,"low latency leaves buffer manual");p.reset(4,-1,0);check(p.observe(true,10,8,3000)==4,"unknown xrun does not invent historical failures");check(p.observe(true,11,4,6000)==4,"hardware capacity is respected");p.reset(8,0,0);check(p.observe(true,100,16,9000)==8,"eight burst upper bound");}
    auto path=wav();auto sample=Sample::open(path,8192);check(sample.rate==44100 && sample.frames==50000,"WAV metadata");float data[20];sample.decode(100,10,data);check(std::abs(data[0])>std::abs(data[1]),"WAV stereo");
    {std::vector<Sample> samples;samples.push_back(std::move(sample));SampleCache cache(samples);cache.prefetch(0,20000);cache.prefetch(0,24000);cache.prefetch(0,28000);std::this_thread::sleep_for(std::chrono::milliseconds(40));SampleCache::View v;cache.view(0,20000,v);check(v.data!=nullptr,"stream cache worker / prefetch / LRU");check(std::abs(v.data[(20000-v.start)*2])>0,"stream decoded content");}
    float out[512];SamplerEngine e;e.install(bank(),{},48000);e.send({0,0,60,127,0});render(e,out,256);float loud=energy(out,256);check(loud>0,"note playback");check(e.activeVoices==1,"voice count");
    SamplerEngine soft;soft.install(bank(),{},48000);soft.send({0,0,60,32,0});render(soft,out,256);check(energy(out,256)<loud*.1,"velocity gain");
    e.send({2,0,64,127,0});e.send({1,0,60,0,0});for(int i=0;i<8;++i)render(e,out,256);check(e.activeVoices==1,"sustain holds released key");e.send({2,0,64,0,0});for(int i=0;i<8;++i)render(e,out,256);check(e.activeVoices==0,"sustain releases");
    e.install(bank(true),{},48000);e.send({0,0,60,100,0});render(e,out,256);e.send({1,0,60,0,0});render(e,out,256);check(e.activeVoices==2,"release sample starts");
    {auto shortBank=bank(true);shortBank->samples[0].frames=256;e.install(std::move(shortBank),{},48000);e.send({0,0,60,100,0});render(e,out,256);render(e,out,256);check(e.activeVoices==0,"sample ended while key held");e.send({1,0,60,0,0});render(e,out,256);check(energy(out,256)>0,"release trigger after main sample naturally ends");}
    e.install(bank(),{},48000);e.send({0,0,60,127,0});e.send({0,1,60,127,0});render(e,out,256);e.send({1,0,60,0,0});for(int i=0;i<8;++i)render(e,out,256);check(e.activeVoices==1,"channel isolation");
    e.send({5});for(int i=0;i<8;++i)render(e,out,256);check(e.activeVoices==0,"panic releases");
    {e.install(bank(),{},48000);e.send({0,0,60,127,0});e.send({0,0,60,127,0});render(e,out,256);e.send({1,0,60,0,0});for(int i=0;i<8;++i)render(e,out,256);check(e.activeVoices==1,"overlapping same-note FIFO note-off");}
    for(int limit:{32,80,128,512,1024}){e.install(bank(),{},48000);e.send({8,0,limit});for(int n=0;n<limit+16;++n)e.send({0,n/128,n%128,127,0});render(e,out,256);check(e.activeVoices==limit,"voice stealing limit");for(float v:out)check(std::isfinite(v) && std::abs(v)<=1,"limiter finite output");auto start=std::chrono::steady_clock::now();for(int i=0;i<100;++i)render(e,out,256);std::cout<<"voices="<<limit<<" average_block_us="<<std::chrono::duration<double,std::micro>(std::chrono::steady_clock::now()-start).count()/100<<'\n';}
    e.install(bank(),{},48000);for(int i=0;i<3000;++i)e.send({0,0,60,127,0});render(e,out,256);check(e.droppedEvents>0,"queue overflow measured");for(int i=0;i<8;++i)render(e,out,256);check(e.activeVoices==0,"overflow fails safe");
    {std::vector<float> ir(2,1);Convolver c(ir);for(int i=0;i<700;++i){float l=i==0?1:0,r=l;audioThread=true;c.process(l,r);audioThread=false;check(std::abs(l-(i==256?1.f:0.f))<.0001,"FFT convolution impulse / block latency");}}
    {std::vector<float> ir(10000,0);ir[0]=1;ir[1]=.5f;ir[2047*2]=.25f;ir[2048*2]=.75f;ir[4097*2+1]=.4f;Convolver c(ir);for(int i=0;i<6000;++i){float l=i==0?1:0,r=l;audioThread=true;c.process(l,r);audioThread=false;float el=i==256?1.f:i==2303?.25f:i==2304?.75f:0;float er=i==256?.5f:i==4353?.4f:0;check(std::abs(l-el)<.0002f && std::abs(r-er)<.0002f,"hybrid early/late IR partitions align at 256 frames");}}
    {auto b=bank();for(int v=0;v<128;++v)b->velocityGains[0][v]=0;e.install(std::move(b),{},48000);e.send({0,0,60,127,0});render(e,out,256);check(energy(out,256)==0 && e.activeVoices==0,"per-voice velocity table suppresses zero layer");}
    {auto b=bank();b->regions[0].trigger=Trigger::PedalDown;b->regions[0].root=1;b->regions[0].low=b->regions[0].high=1;b->lookup={};b->prepare();e.install(std::move(b),{},48000);e.send({2,0,64,127,0});render(e,out,256);check(e.activeVoices==1,"CC trigger independent of mapped note 1");e.send({2,0,64,100,0});render(e,out,256);check(e.activeVoices==1,"continuous pedal messages are debounced");}
    {EffectSpec fx;fx.p={1,.8f,.3f,0,1};Effect reverb(fx,48000);float tail=0;for(int i=0;i<10000;++i){float l=i==0?1:0,r=l;audioThread=true;reverb.process(l,r);audioThread=false;if(i>2000)tail+=std::abs(l)+std::abs(r);}check(tail>0,"reverb tail survives silence");}
    // lowpass_4pl is parsed as this existing 2-pole effect, per the official
    // legacy-name definition. Verify actual audio, not only successful parsing.
    for(int rate:{44100,48000,96000}) {
        for(int frequency:{250,8000}) {
            EffectSpec spec;spec.type=EffectType::Lowpass;spec.p={0,1000,.7f,1,1};Effect filter(spec,rate);
            double inputEnergy=0,outputEnergy=0;
            for(int i=0;i<rate/2;++i) {
                float input=.1f*std::sin(2*3.141592653589793*frequency*i/rate),left=input,right=input*.5f;
                audioThread=true;filter.process(left,right);audioThread=false;
                check(std::isfinite(left) && std::abs(right-left*.5f)<1e-7f,"lowpass stereo balance and finite audio");
                if(i>rate/10){inputEnergy+=double(input)*input;outputEnergy+=double(left)*left;}
            }
            double ratio=outputEnergy/inputEnergy;
            check(frequency==250?ratio>.9 && ratio<1.01:ratio<.001,"2-pole lowpass preserves bass and attenuates high frequencies");
        }
        EffectSpec spec;spec.type=EffectType::Lowpass;spec.p={0,1200,.7f,1,0};Effect filter(spec,rate);
        for(int i=0;i<1000;++i){float left=.1f*std::sin(i*.2f),right=0,expected=left;audioThread=true;filter.process(left,right);audioThread=false;check(left==expected && right==0,"disabled lowpass preserves dry audio and independent channels");}
        for(int i=0;i<rate;++i) {
            if(i%4000==0){filter.parameter(1,i%8000?40.f:22000.f);filter.parameter(2,i%8000?5.f:.001f);filter.parameter(4,1);}
            float left=.1f*std::sin(i*.2f),right=0;
            audioThread=true;filter.process(left,right);audioThread=false;
            check(std::isfinite(left) && std::abs(left)<10 && right==0,"lowpass frequency and resonance automation stays finite without stereo leakage");
        }
        filter.parameter(4,0);
        for(int i=0;i<rate;++i){float left=.1f*std::sin(i*.2f),right=0,expected=left;audioThread=true;filter.process(left,right);audioThread=false;if(i>rate/2)check(std::abs(left-expected)<1e-6f,"lowpass bypass smoothing returns to dry signal");}
    }
    {
        e.install(bank(),{},48000);e.setTimeline({{100,{0,17,60,127}},{400,{1,17,60,0}}},600);e.send({10,0,1});
        render(e,out,256);check(energy(out,50)==0,"timeline waits until sample-accurate onset");check(energy(out+200,156)>0,"timeline dispatches within audio block");
        check(e.transport()[0]==256/48000.,"timeline sample clock");e.send({10,0,0});render(e,out,256);check(e.transport()[0]==256/48000. && e.transport()[1]==0,"transport pause freezes clock");
        e.seek(400);e.send({10,0,1});render(e,out,256);check(e.transport()[1]==0 && e.transport()[0]==600/48000.,"timeline ends and releases notes");
    }
    { e.install(bank(),{},48000);e.setTimeline({{0,{0,17,60,127}}},10000);e.send({0,0,60,127});e.send({10,0,1});render(e,out,256);check(e.activeVoices==2,"file and live MIDI use independent channels");e.send({10,0,0});render(e,out,256);check(e.activeVoices==1,"pausing file preserves live MIDI voice"); }
    {e.install(bank(),{},48000);std::vector<TimedEvent> dense(3000,TimedEvent{0,{2,17,7,127}});e.setTimeline(std::move(dense),48000);e.send({10,0,1});render(e,out,256);check(e.transport()[2]==1 && e.transport()[1]==0 && e.activeVoices==0,"dense timeline fails safely within callback event budget");}
    for(int rate:{44100,48000})for(int bits:{16,24,32}){
        auto instrument=std::make_unique<Instrument>();instrument->samples.push_back(Sample::open(path));Region r;r.root=60;r.release=.01f;instrument->regions.push_back(r);instrument->prepare(true);
        SamplerEngine offline;offline.install(std::move(instrument),{},rate);offline.setTimeline({{0,{0,17,60,127}},{rate/2,{1,17,60,0}}},rate/2);
        std::string target="/tmp/pianopocket-export-"+std::to_string(bits)+".wav";std::atomic<bool> cancel{false};std::atomic<float> progress{0};renderWav(offline,target,rate,bits,rate,cancel,progress);
        auto decoded=Sample::open(target);check(decoded.rate==unsigned(rate) && decoded.frames==unsigned(rate) && decoded.bits==unsigned(bits),"WAV export format/header");
        decoded.decode(12000,10,data);check(energy(data,10)>0,"offline export streams beyond resident sample head");decoded.decode(rate-1000,10,data);check(energy(data,10)<.000001,"export includes note release and silence tail");
        check(offline.misses()==0 && offline.errors()==0 && progress.load()==1,"offline cache is deterministic");cancel.store(true);bool failed=false;try{renderWav(offline,target,rate,bits,rate,cancel,progress);}catch(...){failed=true;}check(failed && !std::ifstream(target).good(),"cancelled WAV is removed");
    }
    {
        OutputLimiter limiter;limiter.prepare(48000);check(limiter.delayFrames()==48,"one millisecond limiter lookahead");
        for(int i=0;i<2000;++i){float l=.4f*std::sin(i*.03),r=l*.5f;audioThread=true;limiter.process(l,r);audioThread=false;float expected=i>=48?.4f*std::sin((i-48)*.03):0;check(std::abs(l-expected)<1e-7 && std::abs(r-expected*.5f)<1e-7,"limiter preserves sub-ceiling stereo waveform");}
        check(limiter.reductionDb()==0,"no unnecessary volume reduction below ceiling");
        for(int frequency:{55,1000}){
            limiter.prepare(48000);double fundamental=0,third=0,oldFundamental=0,oldThird=0;
            for(int i=0;i<144000;++i){float raw=4*std::sin(2*3.141592653589793*frequency*i/48000.);float l=raw,r=raw*.5f;audioThread=true;limiter.process(l,r);audioThread=false;check(std::isfinite(l) && std::abs(l)<=OutputLimiter::Ceiling+1e-6 && std::abs(r-l*.5f)<1e-6,"hot transient ceiling and linked stereo balance");
                if(i>=96000){double phase=2*3.141592653589793*frequency*(i-48)/48000.;fundamental+=l*std::sin(phase);third+=l*std::sin(phase*3);double old=std::copysign(.9+.1*std::tanh((std::abs(raw)-.9)*10),raw);if(std::abs(raw)<=.9)old=raw;oldFundamental+=old*std::sin(phase);oldThird+=old*std::sin(phase*3);}
            }
            double distortion=std::abs(third/fundamental);check(distortion<.001,"limiter does not flatten sustained piano-frequency sine waves");if(frequency==1000)check(std::abs(oldThird/oldFundamental)>.1,"regression reproduces old saturator harmonic distortion");
        }
        limiter.prepare(48000);for(int i=0;i<100;++i){float l=1000,r=-500;limiter.process(l,r);check(std::abs(l)<=OutputLimiter::Ceiling+1e-6,"first extreme transient is bounded");}
        for(int i=0;i<48000;++i){float l=0,r=0;limiter.process(l,r);}check(limiter.reductionDb()<.001,"limiter gain recovers after overload");
    }
    {
        for(float scale:{.0625f,1.f,8.f}){
            EffectSpec spec;spec.type=EffectType::Convolution;spec.p={1,0,0,0,1};spec.ir={scale,scale*.5f};Effect effect(spec,48000);
            for(int i=0;i<400;++i){float l=i==0?1:0,r=l;audioThread=true;effect.process(l,r);audioThread=false;float expected=i==256?std::min(scale,.125f):0;check(std::abs(l-expected)<.00001 && std::abs(r-expected*.5f)<.00001,"IR energy gain preserves stereo ratio and never boosts quiet IR");}
        }
        EffectSpec spec;spec.type=EffectType::Convolution;spec.p={0,0,0,0,1};spec.ir={8,4};Effect dry(spec,48000);for(int i=0;i<500;++i){float l=.3,r=-.2;dry.process(l,r);check(l==.3f && r==-.2f,"zero reverb mix leaves dry audio unchanged");}
    }

    {
        e.install(bank(),{},48000);e.send({12,0,2});double sum=0;float peak=0;for(int b=0;b<40;++b){render(e,out,256);sum+=energy(out,256);for(float value:out)peak=std::max(peak,std::abs(value));}
        check(sum>1 && peak<.09f,"diagnostic tone is audible, bounded, allocation-free and independent of notes");e.send({12,0,0});for(int b=0;b<80;++b)render(e,out,256);check(energy(out,256)<1e-10,"diagnostic tone smoothly ends on normal mode");
    }
    {
        SamplerEngine slow,fast;slow.install(bank(),{},48000);fast.install(bank(),{},48000);std::vector<TimedEvent> notes={ {0,{0,17,60,100}}, {9600,{1,17,60,0}} };slow.setTimeline(notes,12000);fast.setTimeline(notes,12000);slow.send({11,0,0,0,.5f});fast.send({11,0,0,0,2.f});slow.send({10,0,1});fast.send({10,0,1});float reference[512];render(slow,reference,256);render(fast,out,256);for(int i=0;i<512;++i)check(reference[i]==out[i],"speed changes MIDI timing but keeps sample pitch and onset waveform");
        for(float speed:{.25f,.5f,1.f,1.5f,2.f}){e.install(bank(),{},48000);e.setTimeline({{0,{0,17,60,100}},{4800,{1,17,60,0}}},9600);e.send({11,0,0,0,speed});e.send({10,0,1});render(e,out,256);check(std::abs(e.transport()[0]-256*speed/48000.)<.000001,"practice speed follows source clock");}
        e.install(bank(),{},48000);e.setTimeline({{0,{2,17,64,127}},{4800,{0,17,60,100}},{7200,{1,17,60,0}},{8500,{2,17,64,0}},{9600,{0,17,99,100}},{12000,{2,17,64,0}}},24000);e.configureLoop(4800,9600,true);e.send({0,0,48,100});e.send({10,0,1});for(int i=0;i<150;++i){render(e,out,256);const auto position=e.transport()[0];if(position>.165 && position<.175)check(e.noteVoiceCount(60,17)>0,"loop restores pedal down from before A on every pass");check(e.noteVoiceCount(99,17)==0,"loop end B is exclusive");}check(e.transport()[1]==1 && e.transport()[0]>=.1 && e.transport()[0]<=.2,"loop stays inside A-B without ending transport");check(e.noteVoiceCount(48,0)==1,"loop rewind leaves live MIDI voices alone");check(e.misses()==0,"loop has no sample misses");bool rejected=false;try{e.configureLoop(0,1,true);}catch(...){rejected=true;}check(rejected,"too short loop rejected off audio callback");e.configureLoop(0,0,false);for(int i=0;i<100;++i)render(e,out,256);check(e.transport()[1]==0,"disabling loop reaches normal end");
    }
    {
        e.install(bank(),{},44100);e.setTimeline({{0,{0,17,60,100}}},44100);e.send({10,0,1});render(e,out,256);const auto before=e.transport()[0];e.install(nullptr,{},e.currentRate());check(e.memory()==0 && e.currentRate()==44100 && e.transport()[0]==before && e.transport()[1]==0,"export releases performance memory and pauses while preserving source rate/time");render(e,out,256);check(energy(out,256)==0,"unloaded performance has no stale effect or voice output");
    }
    {
        auto layers=bank();layers->regions.resize(6,layers->regions.front());layers->prepare();
        e.install(std::move(layers),{},48000);e.send({8,0,32});
        for(int key=60;key<65;++key)e.send({0,0,key,100});render(e,out,256);
        e.send({0,0,72,100});render(e,out,256);
        check(e.noteVoiceCount(72,0)==6,"new layered note must not steal its own just-started layers");
        check(e.stolenVoices.load()==4,"only older layers are replaced at budget boundary");
        auto many=bank();many->regions.resize(6,many->regions.front());many->prepare();e.install(std::move(many),{},48000);e.send({8,0,512});
        for(int note=21;note<101;++note)e.send({0,0,note,100});render(e,out,256);
        check(e.activeVoices==480 && e.stolenVoices.load()==0,"80 simultaneous six-layer notes fit 512 budget");
        for(int note=21;note<101;++note)check(e.noteVoiceCount(note,0)==6,"every key retains all its microphone layers");
    }
    {
        auto source=Sample::open(path,32);std::vector<Sample> many(350,source);SampleCache cache(many,false,1024);
        for(unsigned i=0;i<many.size();++i)cache.prefetch(i,20000);
        bool complete=false;
        for(int retry=0;retry<100 && !complete;++retry){std::this_thread::sleep_for(std::chrono::milliseconds(10));complete=true;for(unsigned i=0;i<many.size();++i){SampleCache::View v;cache.view(i,20000,v);if(!v.data)complete=false;}}
        check(complete && cache.readErrors==0,"hashed budget cache holds more than legacy 256 sample pages");
        for(int i=0;i<1000;++i)cache.prefetch(0,30000);std::this_thread::sleep_for(std::chrono::milliseconds(20));
        check(cache.requestDrops==0,"duplicate prefetch requests are coalesced");
        auto instrument=bank();instrument->memoryBudget=64*1024*1024;instrument->prepare();check(instrument->residentBytes<2*1024*1024 && instrument->cache->capacity()==SampleCache::MinPages,"resident samples do not allocate the whole cache budget");
        auto large=std::make_unique<Instrument>();Sample stream;stream.frames=10000000;large->samples.push_back(std::move(stream));large->memoryBudget=32*1024*1024;large->prepare();check(large->residentBytes<=large->memoryBudget && large->cache->capacity()>256,"large streaming banks retain a bounded budget-sized cache");
        auto small=std::make_unique<Instrument>();small->samples.push_back(Sample::open(path,8192));small->memoryBudget=256*1024*1024;small->prepare();check(small->cache->capacity()==SampleCache::MinPages && small->residentBytes<2*1024*1024,"small streamed WAV uses only needed pages");
    }

    {
        const std::string missing="/tmp/pocket-missing-stream.wav";std::ofstream f(missing,std::ios::binary);unsigned frames=48000;
        f.write("RIFF",4);put32(f,36+frames*4);f.write("WAVEfmt ",8);put32(f,16);put16(f,1);put16(f,2);put32(f,48000);put32(f,48000*4);put16(f,4);put16(f,16);f.write("data",4);put32(f,frames*4);
        for(unsigned i=0;i<frames;++i){put16(f,16000);put16(f,8000);}f.close();
        auto b=std::make_unique<Instrument>();b->samples.push_back(Sample::open(missing));std::remove(missing.c_str());Region r;r.root=60;b->regions.push_back(r);b->prepare();e.install(std::move(b),{},48000);e.send({0,0,60,127});
        float previous=0,maxJump=0;for(int frame=0;frame<16000;frame+=256){render(e,out,256);for(int i=0;i<256;++i){maxJump=std::max(maxJump,std::abs(out[i*2]-previous));previous=out[i*2];}}
        check(e.misses()>0 && maxJump<.02f,"stream starvation fades the last source instead of a hard zero click");
    }
    {
        SamplerEngine normal,oneShot;auto n=bank(),o=bank();o->regions[0].oneShot=true;
        normal.install(std::move(n),{},48000);oneShot.install(std::move(o),{},48000);
        normal.send({0,0,60,127});oneShot.send({0,0,60,127});render(normal,out,256);render(oneShot,out,256);
        normal.send({1,0,60,0});oneShot.send({1,0,60,0});
        for(int i=0;i<12;++i){render(normal,out,256);render(oneShot,out,256);}
        check(normal.noteVoiceCount(60,0)==0 && oneShot.noteVoiceCount(60,0)==1,"SFZ one-shot survives note-off");
        oneShot.send({5});for(int i=0;i<12;++i)render(oneShot,out,256);check(oneShot.noteVoiceCount(60,0)==0,"panic stops SFZ one-shots");
        float loud=0,soft=0;
        for(int velocity:{127,64}){SamplerEngine s;auto b=bank();b->regions[0].sfzVelocity=true;s.install(std::move(b),{},48000);s.send({0,0,60,velocity});for(int i=0;i<4;++i)render(s,out,256);if(velocity==127)loud=energy(out,256);else soft=energy(out,256);}
        check(std::abs(soft/loud-std::pow(64.f/127,4))<.002f,"SFZ default velocity is squared amplitude");
        for(bool continuous:{false,true}){
            auto b=bank();b->regions[0].loopStart=100;b->regions[0].loopEnd=300;b->regions[0].loopDuringRelease=continuous;b->regions[0].release=.2f;
            for(size_t i=301*2;i<b->samples[0].head.size();++i)b->samples[0].head[i]=0;
            SamplerEngine loop;loop.install(std::move(b),{},48000);loop.send({0,0,60,127});render(loop,out,256);loop.send({1,0,60,0});for(int i=0;i<4;++i)render(loop,out,256);
            check(continuous?energy(out,256)>.01f:energy(out,256)<.00001f,"SFZ continuous loops continue on release; sustain loops leave the loop");
        }
        SamplerEngine tuned,reference;auto t=bank(),r=bank();t->regions[0].keyTrack=0;t->regions[0].tune=1200;
        tuned.install(std::move(t),{},48000);reference.install(std::move(r),{},48000);tuned.send({0,0,60,127});reference.send({0,0,72,127});float compare[512];render(tuned,out,256);render(reference,compare,256);for(int i=0;i<512;++i)check(std::abs(out[i]-compare[i])<.00001f,"tuning remains independent of pitch key tracking");
    }
    {
        auto b=bank();b->regions[0].sequenceLength=2;b->regions[0].sequencePosition=1;b->regions[0].gain=.3f;
        Region other=b->regions[0];other.sequencePosition=2;other.gain=.6f;b->regions.push_back(other);b->prepare();SamplerEngine s;s.install(std::move(b),{},48000);float previous=0;
        for(int k=0;k<6;++k){s.send({2,0,120,0});s.send({0,0,60,127});for(int i=0;i<4;++i)render(s,out,256);check(s.noteVoiceCount(60,0)==1,"one round-robin variant per note");float current=energy(out,256);if(k>0)check(std::abs(current/previous-(k%2?4.f:.25f))<.02f,"round robin alternates actual audio gains");previous=current;}
        b=bank();b->regions[0].randomHigh=.5f;b->regions[0].gain=.3f;other=b->regions[0];other.randomLow=.5f;other.randomHigh=1;other.gain=.6f;b->regions.push_back(other);b->prepare();s.install(std::move(b),{},48000);float minimum=1e9f,maximum=0;
        for(int k=0;k<64;++k){s.send({2,0,120,0});s.send({0,0,60,127});for(int i=0;i<4;++i)render(s,out,256);check(s.noteVoiceCount(60,0)==1,"random partitions never stack or miss a note");float current=energy(out,256);minimum=std::min(minimum,current);maximum=std::max(maximum,current);}check(maximum/minimum>3.9f,"both random samples play");
        float unfiltered=0,filtered=0;
        for(bool filter:{false,true}){b=bank();b->regions[0].filterType=filter?2:0;b->regions[0].cutoff=80;s.install(std::move(b),{},48000);s.send({0,0,60,127});for(int i=0;i<24;++i)render(s,out,256);if(filter)filtered=energy(out,256);else unfiltered=energy(out,256);}
        check(filtered/unfiltered<.02f,"per-voice SFZ lowpass attenuates above cutoff");
        float loud=0,soft=0;
        for(int velocity:{127,64}){b=bank();b->regions[0].sfzVelocity=true;b->regions[0].customVelocity=true;b->regions[0].velocityCurve.fill(1);b->regions[0].velocityCurve[64]=.2f;s.install(std::move(b),{},48000);s.send({0,0,60,velocity});for(int i=0;i<4;++i)render(s,out,256);if(velocity==127)loud=energy(out,256);else soft=energy(out,256);}check(std::abs(soft/loud-.04f)<.002f,"custom velocity points are normalized amplitude");
    }
    {
        auto engine=std::make_unique<SamplerEngine>();auto b=bank();b->regions[0].switchLow=24;b->regions[0].switchHigh=25;b->regions[0].switchLast=24;b->regions[0].switchDefault=24;b->regions[0].gain=.2f;
        auto other=b->regions[0];other.switchLast=25;other.gain=.4f;b->regions.push_back(other);b->prepare();engine->install(std::move(b),{},48000);
        engine->send({0,0,60,127});for(int n=0;n<4;++n)render(*engine,out,256);float normal=energy(out,256);check(engine->noteVoiceCount(60,0)==1,"default keyswitch selects one articulation");
        engine->send({2,0,120,0});engine->send({0,0,25,127});engine->send({1,0,25,0});engine->send({0,0,60,127});for(int n=0;n<4;++n)render(*engine,out,256);
        check(engine->noteVoiceCount(25,0)==0 && engine->noteVoiceCount(60,0)==1 && energy(out,256)>normal*3.9f,"sticky switch changes real audio without sounding the control key");
        engine->send({0,1,60,127});render(*engine,out,256);check(engine->noteVoiceCount(60,1)==1,"keyswitch states are independent per MIDI source/channel");
        engine->setTimeline({{0,{0,17,25,100}},{1000,{1,17,25,0}},{4000,{0,17,60,100}}},48000);engine->seek(3000);engine->send({10,0,1});for(int n=0;n<12;++n)render(*engine,out,256);
        check(engine->noteVoiceCount(60,17)==1,"seek restores earlier keyswitch notes before playback");
    }
    {
        auto engine=std::make_unique<SamplerEngine>();auto b=bank();b->regions[0].offBy=2;b->regions[0].release=1;
        auto closer=b->regions[0];closer.low=closer.high=61;closer.offBy=0;closer.offGroup=2;b->regions[0].low=b->regions[0].high=60;b->regions.push_back(closer);b->prepare();engine->install(std::move(b),{},48000);
        engine->send({0,0,60,100});engine->send({0,1,60,100});for(int n=0;n<4;++n)render(*engine,out,256);engine->send({0,0,61,100});for(int n=0;n<4;++n)render(*engine,out,256);
        check(engine->noteVoiceCount(60,0)==0 && engine->noteVoiceCount(61,0)==1 && engine->noteVoiceCount(60,1)==1,"off_by chokes the right group without stopping another channel");
        float two=0,four=0;
        for(int type:{2,7}){b=bank();b->regions[0].filterType=type;b->regions[0].cutoff=80;engine->install(std::move(b),{},48000);engine->send({0,0,60,127});for(int n=0;n<24;++n)render(*engine,out,256);if(type==2)two=energy(out,256);else four=energy(out,256);}
        check(four<two*.1f,"four-pole filter has stronger above-cutoff attenuation");
    }
    {
        SamplerEngine p;p.install(bank(),{},48000);p.setTimeline({},48000);
        p.configurePractice({{100,{uint64_t(1)<<60,1}},{600,{uint64_t(1)<<60,0}}},{},true,false,120,.3f,true);p.send({10,0,1});render(p,out,256);
        check(p.practiceStatus()[0]==1 && std::abs(p.transport()[0]*48000-100)<.01,"guided practice freezes at the note onset");
        p.send({0,0,63,90});render(p,out,256);check(p.practiceStatus()[0]==1 && std::abs(p.transport()[0]*48000-100)<.01,"wrong keys do not advance");
        p.send({0,0,60,90});render(p,out,256);check(p.practiceStatus()[1]==0 && p.practiceStatus()[2]==1,"remaining chord notes reported separately");
        p.send({0,17,64,90});render(p,out,256);check(p.practiceStatus()[0]==1,"file playback cannot satisfy live practice");
        p.send({0,16,64,90});render(p,out,256);check(p.practiceStatus()[0]==0 && p.transport()[0]*48000>100,"screen keyboard completes a chord after USB input");
        render(p,out,256);check(p.practiceStatus()[0]==1 && std::abs(p.transport()[0]*48000-600)<.01,"a held note does not satisfy the next repeated note");
        p.send({1,0,60});p.send({0,0,60,90});render(p,out,256);check(p.practiceStatus()[0]==0,"fresh repeated note resumes practice");
        p.seek(100);p.send({10,0,1});render(p,out,256);check(p.practiceStatus()[0]==1,"seek clears previously consumed practice input");
        SamplerEngine metro;metro.install(nullptr,{},48000);metro.configurePractice({}, {},false,true,120,1,false);std::vector<float> ticks(48000*2);
        for(int frame=0;frame<48000;frame+=256){int n=std::min(256,48000-frame);render(metro,ticks.data()+frame*2,n);}
        check(energy(ticks.data(),1000)>.001f && energy(ticks.data()+24000*2,1000)>.001f && energy(ticks.data()+5000*2,1000)<.000001f,"free metronome ticks at sample-clock quarter-note intervals");
        metro.setTimeline({},48000);metro.configurePractice({},{{0,true},{12000,false}},false,true,120,1,true);metro.send({10,0,1});render(metro,out,256);metro.send({10,0,0});for(int i=0;i<60;++i)render(metro,out,256);check(energy(out,256)<.000001f,"song metronome pauses with transport");
    }
    check(audioAllocations==0,"no heap allocation in render/effects");std::cout<<"PASS: sampler, streaming, FX, SFZ, guided practice and metronome; audio allocations=0\n";
    {
        auto routingOwner=std::make_unique<MidiOutputQueue>();auto& routing=*routingOwner;MidiPacket packets[16];routing.configure(true,false,15);
        routing.message(0,0x90,60,100,1);routing.message(16,0x90,61,90,2);routing.message(17,0x80,62,0,3);
        int n=routing.drain(packets,16);check(n==2 && packets[0].status==0x9f && packets[1].status==0x80,"MIDI output excludes input thru by default and preserves file channels");
        routing.message(16,0x90,63,100,4);routing.configure(true,true,2);routing.message(3,0xb0,64,127,5);
        n=routing.drain(packets,16);check(n==1 && packets[0].status==0xb3,"output destination change discards old queued notes and enables explicit thru");
        for(int i=0;i<5000;++i)routing.message(16,0xb0,1,64,0);
        check(routing.drain(packets,16)==1 && packets[0].status==MidiOutputQueue::Overflow,"MIDI queue overflow requests a terminal external panic");
        routing.configure(true,false,0);routing.message(16,0x90,60,100,6);check(routing.drain(packets,16)==1 && packets[0].status==0x90,"MIDI queue recovers on explicit reconnection");
    }
    {
        auto midiOwner=std::make_unique<SamplerEngine>();auto& midi=*midiOwner;midi.install(bank(),{},48000);midi.setTimeline({{240,{0,17,60,100}},{480,{1,17,60,0}}},48000);midi.midiOutput(true,false,15);midi.send({10,0,1});
        MidiPacket packets[128];audioThread=true;midi.render(out,256,1000000000LL);audioThread=false;
        int n=midi.drainMidi(packets,128);bool found=false;for(int i=0;i<n;++i)if(packets[i].status==0x90){found=true;check(packets[i].a==60 && packets[i].b==100 && packets[i].nanos==1005000000LL,"MIDI note onset uses within-buffer sample offset");}check(found,"file note reaches external output");
        audioThread=true;midi.render(out,256,1005333333LL);audioThread=false;n=midi.drainMidi(packets,128);found=false;for(int i=0;i<n;++i)if(packets[i].status==0x80){found=true;check(std::abs(packets[i].nanos-1010000000LL)<=2,"MIDI note off timestamp follows source timing");}check(found,"external note off is emitted without sustain extension");
        midi.send({10,0,0});render(midi,out,256);n=midi.drainMidi(packets,128);check(n==1 && packets[0].status==MidiOutputQueue::Reset && packets[0].a==1,"pause only resets file MIDI channels");
        midi.midiOutput(true,true,15);midi.send({256,0,60,100});render(midi,out,256);check(midi.drainMidi(packets,128)==0 && midi.noteVoiceCount(60,0)>0,"device echo suppression leaves native input playback working");
        midi.send({5});render(midi,out,256);n=midi.drainMidi(packets,128);check(n==1 && packets[0].status==MidiOutputQueue::Reset && packets[0].a==0xffff,"panic releases every external MIDI channel");
    }
    {
        auto midiOwner=std::make_unique<SamplerEngine>();auto& midi=*midiOwner;midi.install(bank(),{},48000);std::vector<TimedEvent> messages;
        for(int i=0;i<20000;++i)messages.push_back({i,{2,17,1,i%128}});
        messages.push_back({20000,{2,17,0,2}});messages.push_back({20001,{2,17,32,3}});messages.push_back({20002,{14,17,79,0,192}});messages.push_back({20003,{2,17,121,0}});
        midi.setTimeline(std::move(messages),48000);midi.midiOutput(true,false,15);midi.seek(21000);
        MidiPacket packets[128];int n=midi.drainMidi(packets,128),bankIndex=-1,programIndex=-1;
        check(n<=10 && n>0 && packets[0].status==MidiOutputQueue::Reset,"seek chases a bounded final controller snapshot instead of flooding historical messages");
        for(int i=0;i<n;++i){if(packets[i].status==0xb0 && packets[i].a==32){bankIndex=i;check(packets[i].b==3,"seek restores bank selection");}if(packets[i].status==0xc0){programIndex=i;check(packets[i].a==79,"seek restores program change");}}
        check(bankIndex>=0 && programIndex>bankIndex,"bank select is sent before program change on seek");
    }
    {
        auto midiOwner=std::make_unique<SamplerEngine>();auto& midi=*midiOwner;midi.install(bank(),{},48000);midi.setTimeline({{0,{2,17,64,127}},{1,{14,17,5,0,192}},{12000,{0,17,60,100}},{18000,{1,17,60,0}}},48000);midi.configureLoop(12000,24000,true);midi.midiOutput(true,false,15);midi.send({10,0,1});
        MidiPacket packets[128];bool reset=false,program=false,pedal=false;int notes=0;
        for(int block=0;block<100;++block){audioThread=true;midi.render(out,256,1000000000LL+int64_t(block*256000000000.0/48000));audioThread=false;int n=midi.drainMidi(packets,128);for(int i=0;i<n;++i){if(packets[i].status==MidiOutputQueue::Reset && packets[i].b==1)reset=true;if(reset && packets[i].status==0xc0 && packets[i].a==5)program=true;if(reset && packets[i].status==0xb0 && packets[i].a==64 && packets[i].b==127)pedal=true;if(packets[i].status==0x90)++notes;}}
        check(reset && program && pedal && notes>=2,"MIDI A-B loop restores program and pedal before repeated notes");
    }
    {
        auto midiOwner=std::make_unique<SamplerEngine>();auto& midi=*midiOwner;midi.install(bank(),{},48000);midi.setTimeline({{0,{0,17,60,100}}},48000);midi.midiOutput(true,false,15);midi.send({10,0,1});
        for(int block=0;block<12;++block){for(int i=0;i<500;++i)midi.send({2,16,1,64});render(midi,out,64);}
        MidiPacket packets[8];check(midi.drainMidi(packets,8)==1 && packets[0].status==MidiOutputQueue::Overflow,"blocked MIDI consumer is measured independently");
        check(midi.transport()[1]==1 && midi.activeVoices>0 && midi.droppedEvents==0,"external MIDI overload does not stop native playback");
    }
    check(audioAllocations==0,"no heap allocation in render or MIDI event mirroring");std::cout<<"PASS: timestamped MIDI output, routing, controller chase, panic, loop and overload isolation; audio allocations=0\n";
    std::remove(path.c_str());return 0;
}catch(const std::exception& x){std::cerr<<"FAIL: "<<x.what()<<'\n';return 1;}}
