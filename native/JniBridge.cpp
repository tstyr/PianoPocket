#include "AudioEngine.h"
#include <jni.h>
#include <unordered_map>
#include <string>
using namespace pocket;
static AudioEngine engine;
static std::unique_ptr<Instrument> staged;
static std::vector<EffectSpec> effects;
static std::unordered_map<std::string,unsigned> paths;
static size_t budget=256*1024*1024;
static std::string string(JNIEnv* e,jstring s){auto p=e->GetStringUTFChars(s,nullptr);std::string out(p);e->ReleaseStringUTFChars(s,p);return out;}
static void fail(JNIEnv* e,const std::exception& x){e->ThrowNew(e->FindClass("java/lang/IllegalArgumentException"),x.what());}
extern "C" JNIEXPORT jboolean JNICALL Java_dev_pianopocket_audio_NativeAudio_start(JNIEnv*,jobject,jint device,jint bursts){return engine.start(device,bursts);}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_stop(JNIEnv*,jobject){engine.stop();}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_begin(JNIEnv*,jobject,jint memoryMiB){staged=std::make_unique<Instrument>();effects.clear();paths.clear();budget=size_t(std::clamp(memoryMiB,32,512))*1024*1024;}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_abort(JNIEnv*,jobject){staged.reset();effects.clear();paths.clear();}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_region(JNIEnv* e,jobject,jstring file,jintArray ints,jfloatArray floats){
    try{
        if(!staged)throw std::runtime_error("No import transaction");
        if(e->GetArrayLength(ints)!=19 || e->GetArrayLength(floats)!=9)throw std::runtime_error("Invalid mapping");
        jint a[19];jfloat f[9];e->GetIntArrayRegion(ints,0,19,a);e->GetFloatArrayRegion(floats,0,9,f);
        auto path=string(e,file);auto found=paths.find(path);unsigned sample;
        if(found==paths.end()){
            if(staged->samples.size()>=4096 || staged->residentBytes+8193*8+SampleCache::PageCount*(SampleCache::PageFrames+1)*8>budget)throw std::runtime_error("Sample-head memory budget exceeded; increase budget or use fewer samples");
            auto wav=Sample::open(path);staged->residentBytes+=wav.head.size()*sizeof(float);sample=staged->samples.size();paths[path]=sample;staged->samples.push_back(std::move(wav));
        }else sample=found->second;
        Region r;r.sample=sample;r.group=a[0];r.low=a[1];r.high=a[2];r.root=a[3];r.lowVel=a[4];r.highVel=a[5];r.trigger=Trigger(a[6]);r.start=a[7];r.end=a[8];r.loopStart=a[9];r.loopEnd=a[10];r.cc=a[11];r.ccLow=a[12];r.ccHigh=a[13];r.fadeInLow=a[14];r.fadeInHigh=a[15];r.fadeOutLow=a[16];r.fadeOutHigh=a[17];r.enabled=a[18]!=0;
        r.gain=f[0];r.pan=f[1];r.tune=f[2];r.attack=f[3];r.decay=f[4];r.sustain=f[5];r.release=f[6];r.velTrack=f[7];r.keyTrack=f[8];
        auto frames=staged->samples[sample].frames;
        if(r.group>=256 || r.low<0 || r.high>127 || r.low>r.high || r.root<0 || r.root>127 || a[6]<0 || a[6]>3 || r.start<0 || uint64_t(r.start)>=frames || (r.end>=0 && (uint64_t(r.end)>=frames || r.end<r.start)) || (r.loopStart>=0 && (r.loopEnd<=r.loopStart || uint64_t(r.loopEnd)>=frames)))throw std::runtime_error("Region boundaries outside WAV or invalid mapping");
        staged->regions.push_back(r);
    }catch(const std::exception& x){fail(e,x);}
}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_effect(JNIEnv* e,jobject,jint type,jfloatArray params,jstring ir){
    try{
        if(type<0 || type>11 || e->GetArrayLength(params)!=5 || effects.size()>=8)throw std::runtime_error("Unsupported effect chain");
        EffectSpec s;s.type=EffectType(type);e->GetFloatArrayRegion(params,0,5,s.p.data());
        if(type==7){auto wave=Sample::open(string(e,ir),0);auto outRate=engine.sampleRate();auto count=size_t(wave.frames*outRate/wave.rate);if(count>262144)throw std::runtime_error("IR is too long (max 262144 output frames); not truncated");std::vector<float> source(wave.frames*2);wave.decode(0,wave.frames,source.data());s.ir.resize(count*2);
            for(size_t i=0;i<count;++i){double p=i*double(wave.rate)/outRate;auto k=std::min<uint64_t>(p,wave.frames-1),next=std::min<uint64_t>(k+1,wave.frames-1);auto t=float(p-k);for(int c=0;c<2;++c)s.ir[i*2+c]=source[k*2+c]*(1-t)+source[next*2+c]*t;}}
        effects.push_back(std::move(s));
    }catch(const std::exception& x){fail(e,x);}
}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_commit(JNIEnv* e,jobject){try{if(!staged || staged->regions.empty())throw std::runtime_error("No playable regions");staged->prepare();engine.install(std::move(staged),effects);effects.clear();paths.clear();}catch(const std::exception& x){fail(e,x);}}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_event(JNIEnv*,jobject,jint type,jint channel,jint a,jint b,jfloat v){engine.sampler.send({type,channel,a,b,v});}
extern "C" JNIEXPORT jdoubleArray JNICALL Java_dev_pianopocket_audio_NativeAudio_stats(JNIEnv* e,jobject){auto values=engine.stats();auto a=e->NewDoubleArray(values.size());e->SetDoubleArrayRegion(a,0,values.size(),values.data());return a;}
