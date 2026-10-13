#include "AudioEngine.h"
#include "ImpulseLoader.h"
#include "OfflineRenderer.h"
#include "CompressedEncoder.h"
#include <jni.h>
#include <unordered_map>
#include <string>
using namespace pocket;
static AudioEngine engine;
static std::unique_ptr<Instrument> staged;
static std::vector<EffectSpec> effects;
static std::unordered_map<std::string,unsigned> paths;
static size_t budget=256*1024*1024;
static int exportRate=0;
static std::atomic<bool> exportCancel{false};
static std::atomic<float> exportProgress{0};
static std::vector<Event> exportControls;
static std::string string(JNIEnv* e,jstring s){auto p=e->GetStringUTFChars(s,nullptr);std::string out(p);e->ReleaseStringUTFChars(s,p);return out;}
static void fail(JNIEnv* e,const std::exception& x){e->ThrowNew(e->FindClass("java/lang/IllegalArgumentException"),x.what());}
extern "C" JNIEXPORT jboolean JNICALL Java_dev_pianopocket_audio_NativeAudio_start(JNIEnv*,jobject,jint device,jint bursts){return engine.start(device,bursts);}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_stop(JNIEnv*,jobject){engine.stop();}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_suspendPerformance(JNIEnv* e,jobject){try{engine.stop();engine.sampler.install(nullptr,{},engine.sampler.currentRate());}catch(const std::exception& x){fail(e,x);}}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_stability(JNIEnv*,jobject,jboolean enabled){engine.stability(enabled);}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_begin(JNIEnv*,jobject,jint memoryMiB){staged=std::make_unique<Instrument>();effects.clear();paths.clear();exportRate=0;budget=size_t(std::clamp(memoryMiB,32,512))*1024*1024;staged->memoryBudget=budget;}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_abort(JNIEnv*,jobject){staged.reset();effects.clear();paths.clear();}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_region(JNIEnv* e,jobject,jstring file,jintArray ints,jfloatArray floats){
    try{
        if(!staged)throw std::runtime_error("No import transaction");
        if(e->GetArrayLength(ints)!=19 || e->GetArrayLength(floats)!=9)throw std::runtime_error("Invalid mapping");
        jint a[19];jfloat f[9];e->GetIntArrayRegion(ints,0,19,a);e->GetFloatArrayRegion(floats,0,9,f);
        auto path=string(e,file);auto found=paths.find(path);unsigned sample;
        if(found==paths.end()){
            if(staged->samples.size()>=4096 || staged->residentBytes+8193*8+SampleCache::PageCount*sizeof(SampleCache::Page)+SampleCache::DirectoryBytes>budget)throw std::runtime_error("Sample-head memory budget exceeded; increase budget or use fewer samples");
            auto wav=Sample::open(path);staged->residentBytes+=wav.head.size()*sizeof(float);sample=staged->samples.size();paths[path]=sample;staged->samples.push_back(std::move(wav));
        }else sample=found->second;
        Region r;r.sample=sample;r.group=a[0];r.low=a[1];r.high=a[2];r.root=a[3];r.lowVel=a[4];r.highVel=a[5];r.trigger=Trigger(a[6]);r.start=a[7];r.end=a[8];r.loopStart=a[9];r.loopEnd=a[10];r.cc=a[11];r.ccLow=a[12];r.ccHigh=a[13];r.fadeInLow=a[14];r.fadeInHigh=a[15];r.fadeOutLow=a[16];r.fadeOutHigh=a[17];r.enabled=a[18]!=0;
        r.gain=f[0];r.pan=f[1];r.tune=f[2];r.attack=f[3];r.decay=f[4];r.sustain=f[5];r.release=f[6];r.velTrack=f[7];r.keyTrack=f[8];
        auto frames=staged->samples[sample].frames;
        if(r.group>=256 || r.low<0 || r.high>127 || r.low>r.high || r.root<0 || r.root>127 || a[6]<0 || a[6]>3 || r.start<0 || uint64_t(r.start)>=frames || (r.end>=0 && (uint64_t(r.end)>frames || r.end<r.start)) || (r.loopStart>=0 && (r.loopEnd<=r.loopStart || uint64_t(r.loopEnd)>=frames)))throw std::runtime_error("Region boundaries outside WAV or invalid mapping");
        staged->regions.push_back(r);
    }catch(const std::exception& x){fail(e,x);}
}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_effect(JNIEnv* e,jobject,jint type,jfloatArray params,jstring ir){
    try{
        if(type<0 || type>11 || e->GetArrayLength(params)!=5 || effects.size()>=8)throw std::runtime_error("Unsupported effect chain");
        EffectSpec s;s.type=EffectType(type);e->GetFloatArrayRegion(params,0,5,s.p.data());
        if(type==7)s.ir=loadImpulse(string(e,ir),exportRate?exportRate:engine.sampleRate());
        effects.push_back(std::move(s));
    }catch(const std::exception& x){fail(e,x);}
}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_velocityCurve(JNIEnv* e,jobject,jint group,jfloatArray curve){
    if(!staged || group<0 || group>=256 || e->GetArrayLength(curve)!=128){e->ThrowNew(e->FindClass("java/lang/IllegalArgumentException"),"Invalid velocity curve");return;}
    e->GetFloatArrayRegion(curve,0,128,staged->velocityGains[group].data());
}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_replaceImpulse(JNIEnv* e,jobject,jint index,jstring ir,jfloatArray params){
    try{if(e->GetArrayLength(params)!=5)throw std::runtime_error("Invalid effect parameters");EffectSpec s;s.type=EffectType::Convolution;e->GetFloatArrayRegion(params,0,5,s.p.data());s.ir=loadImpulse(string(e,ir),engine.sampleRate());auto replacement=std::make_unique<Effect>(s,engine.sampleRate());engine.replaceEffect(index,std::move(replacement));}catch(const std::exception& x){fail(e,x);}
}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_commit(JNIEnv* e,jobject){try{if(!staged || staged->regions.empty())throw std::runtime_error("No playable regions");staged->prepare();engine.install(std::move(staged),effects);effects.clear();paths.clear();}catch(const std::exception& x){fail(e,x);}}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_event(JNIEnv*,jobject,jint type,jint channel,jint a,jint b,jfloat v){engine.sampler.send({type,channel,a,b,v});}
extern "C" JNIEXPORT jdoubleArray JNICALL Java_dev_pianopocket_audio_NativeAudio_stats(JNIEnv* e,jobject){auto values=engine.stats();auto a=e->NewDoubleArray(values.size());e->SetDoubleArrayRegion(a,0,values.size(),values.data());return a;}
static std::vector<TimedEvent> songEvents(JNIEnv* e,jlongArray times,jintArray data,float sr){
    int n=e->GetArrayLength(times);if(n>200000 || e->GetArrayLength(data)!=n*3)throw std::runtime_error("Invalid MIDI timeline");
    std::vector<jlong> t(n);std::vector<jint> d(n*3);if(n){e->GetLongArrayRegion(times,0,n,t.data());e->GetIntArrayRegion(data,0,n*3,d.data());}
    std::vector<TimedEvent> out;out.reserve(n);
    for(int i=0;i<n;++i){if(t[i]<0 || t[i]>7200000000LL)throw std::runtime_error("MIDI time limit exceeded");int status=d[i*3],a=d[i*3+1],b=d[i*3+2];if(status<0x80 || status>0xef || a<0 || a>127 || b<0 || b>127)throw std::runtime_error("Invalid MIDI message");Event event;event.channel=(status&15)+17;event.a=a;event.b=b;
        switch(status&0xf0){case 0x90:event.type=b?0:1;break;case 0x80:event.type=1;break;case 0xb0:event.type=2;break;case 0xe0:event.type=3;event.value=((b*128+a)-8192)/8192.f*2;break;default:continue;}
        out.push_back({int64_t(std::llround(t[i]*double(sr)/1000000)),event});
    }return out;
}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_timeline(JNIEnv* e,jobject,jlongArray times,jintArray data,jlong duration){try{if(duration<0 || duration>7200000000LL)throw std::runtime_error("MIDI duration limit exceeded");float rate=engine.sampleRate();engine.timeline(songEvents(e,times,data,rate),int64_t(std::llround(duration*double(rate)/1000000)));}catch(const std::exception& x){fail(e,x);}}
extern "C" JNIEXPORT jdoubleArray JNICALL Java_dev_pianopocket_audio_NativeAudio_transport(JNIEnv* e,jobject){auto values=engine.sampler.transport();auto a=e->NewDoubleArray(values.size());e->SetDoubleArrayRegion(a,0,values.size(),values.data());return a;}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_seekTimeline(JNIEnv* e,jobject,jlong micros){try{if(micros<0 || micros>7200000000LL)throw std::runtime_error("Invalid seek position");engine.seek(int64_t(std::llround(micros*double(engine.sampleRate())/1000000)));}catch(const std::exception& x){fail(e,x);}}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_practiceLoop(JNIEnv* e,jobject,jlong begin,jlong end,jboolean enabled){try{if(begin<0 || end<0 || end>7200000000LL)throw std::runtime_error("Invalid loop range");auto rate=engine.sampleRate();engine.loop(int64_t(std::llround(begin*double(rate)/1000000)),int64_t(std::llround(end*double(rate)/1000000)),enabled);}catch(const std::exception& x){fail(e,x);}}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_exportBegin(JNIEnv* e,jobject,jint memory,jint rate){if(rate!=44100 && rate!=48000){e->ThrowNew(e->FindClass("java/lang/IllegalArgumentException"),"Invalid export sample rate");return;}Java_dev_pianopocket_audio_NativeAudio_begin(e,nullptr,memory);exportRate=rate;exportControls.clear();exportCancel.store(false);exportProgress.store(0);}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_exportControl(JNIEnv*,jobject,jint type,jint a,jint b,jfloat v){exportControls.push_back({type,0,a,b,v});}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_exportWav(JNIEnv* e,jobject,jstring file,jlongArray times,jintArray data,jlong duration,jint bits,jint tail){try{
    if(!staged || !exportRate || duration<0 || duration>7200000000LL || tail<0 || tail>30)throw std::runtime_error("Invalid export transaction");
    auto events=songEvents(e,times,data,exportRate);SamplerEngine offline;staged->prepare(true);offline.install(std::move(staged),effects,exportRate);offline.setTimeline(std::move(events),int64_t(std::llround(duration*double(exportRate)/1000000)));
    offline.initialControls(exportControls);effects.clear();paths.clear();exportControls.clear();
    renderWav(offline,string(e,file),exportRate,bits,int64_t(std::llround(duration*double(exportRate)/1000000))+int64_t(tail)*exportRate,exportCancel,exportProgress);exportRate=0;
}catch(const std::exception& x){staged.reset();effects.clear();paths.clear();exportControls.clear();exportRate=0;fail(e,x);}}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_cancelExport(JNIEnv*,jobject){exportCancel.store(true);}
extern "C" JNIEXPORT jfloat JNICALL Java_dev_pianopocket_audio_NativeAudio_exportProgress(JNIEnv*,jobject){return exportProgress.load();}
extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_resetExportProgress(JNIEnv*,jobject){exportProgress.store(0);}

extern "C" JNIEXPORT void JNICALL Java_dev_pianopocket_audio_NativeAudio_encodeCompressed(JNIEnv* e,jobject,jstring wav,jstring output,jint format,jint bitrate) {
    try { encodeCompressed(string(e,wav),string(e,output),format,bitrate,exportCancel,exportProgress); }
    catch(const std::exception& x){fail(e,x);}
}
