#include "SamplerEngine.h"
#include <algorithm>
#include <limits>
namespace pocket {
void SamplerEngine::install(std::unique_ptr<Instrument> bank,const std::vector<EffectSpec>& fx,float sr){
    Effects prepared;prepared.prepare(fx,sr);
    if(rate!=sr){for(auto& e:timeline)e.frame=int64_t(std::llround(e.frame*double(sr)/rate));playFrame=int64_t(std::llround(playFrame*double(sr)/rate));totalFrames=int64_t(std::llround(totalFrames*double(sr)/rate));}
    instrument=std::move(bank);rate=sr;reportedRate.store(sr);reportedFrame.store(playFrame);for(auto& v:voices)v=Voice{};
    keys={};
    instrumentVolume=targetInstrumentVolume=1;
    diagnosticMode=0;diagnosticFx=1;diagnosticTone=0;tonePhaseL=tonePhaseR=0;
    for(auto& c:controllers){c.fill(0);c[7]=c[11]=127;}bends.fill(0);groupGains.fill(1);
    effects=std::move(prepared);outputLimiter.prepare(sr);inputPeak.store(0);gainReductionDb.store(0);stolenVoices.store(0);Event discard;while(events.pop(discard)){}panic.store(false);
    playing=false;reportedPlaying.store(false);
}
bool SamplerEngine::send(Event e){std::lock_guard<std::mutex> lock(producer);if(events.push(e))return true;droppedEvents.fetch_add(1);panic.store(true);return false;}
void SamplerEngine::trigger(Trigger kind,int ch,int note,int vel,uint64_t id) noexcept {
    if(!instrument)return;
    for(auto index:instrument->lookup[int(kind)][note]){
        auto& r=instrument->regions[index];if(!r.enabled || vel<r.lowVel || vel>r.highVel || (r.cc>=0 && (controllers[ch][r.cc]<r.ccLow || controllers[ch][r.cc]>r.ccHigh)))continue;
        float fade=1;
        fade*=instrument->velocityGains[r.group][vel];
        if(r.fadeInLow>=0 && r.fadeInHigh>r.fadeInLow)fade*=std::clamp(float(vel-r.fadeInLow)/(r.fadeInHigh-r.fadeInLow),0.f,1.f);
        if(r.fadeOutLow>=0 && r.fadeOutHigh>r.fadeOutLow)fade*=std::clamp(float(r.fadeOutHigh-vel)/(r.fadeOutHigh-r.fadeOutLow),0.f,1.f);
        if(fade<=0)continue;
        Voice* voice=nullptr;for(int i=0;i<limit;++i)if(!voices[i].active){voice=&voices[i];break;}
        if(!voice){
            // Never steal a layer just allocated for this same MIDI note / trigger.
            for(int i=0;i<limit;++i){auto& candidate=voices[i];if(candidate.id==id)continue;
                if(!voice || (candidate.envelope.stage==Envelope::Release && voice->envelope.stage!=Envelope::Release) ||
                   ((candidate.envelope.stage==Envelope::Release)==(voice->envelope.stage==Envelope::Release) &&
                    (candidate.envelope.value<voice->envelope.value || (candidate.envelope.value==voice->envelope.value && candidate.id<voice->id))))voice=&candidate;
            }
            if(!voice)continue;
            stolenVoices.fetch_add(1,std::memory_order_relaxed);
        }
        float oldL=voice->active?voice->lastL:0,oldR=voice->active?voice->lastR:0;
        *voice=Voice{};voice->active=true;voice->held=kind==Trigger::Attack;voice->channel=ch;voice->note=note;voice->velocity=vel;voice->id=id;voice->region=index;voice->position=r.start;
        auto playedNote=(kind==Trigger::PedalDown || kind==Trigger::PedalUp)?r.root:note;
        voice->baseStep=instrument->samples[r.sample].rate/rate*std::pow(2.,(playedNote-r.root+r.tune/100.)*r.keyTrack/12.);
        voice->gain=r.gain*fade*((1-r.velTrack)+r.velTrack*vel/127.f);voice->envelope.start(r.attack,r.decay,r.sustain,r.release);
        voice->stealL=oldL;voice->stealR=oldR;voice->stealRemaining=oldL!=0 || oldR!=0?128:0;
        instrument->cache->prefetch(r.sample,r.start+4096);instrument->cache->prefetch(r.sample,r.start+8192);
    }
}
void SamplerEngine::releaseId(int ch,int note,uint64_t id,int velocity) noexcept {
    for(auto& key:keys[ch][note])if(key.id==id)key=Key{};
    for(auto& v:voices)if(v.active && v.id==id && v.channel==ch){v.pending=false;v.envelope.off(rate);}
    trigger(Trigger::Release,ch,note,velocity,++nextId);
}
void SamplerEngine::dispatch(const Event& e) noexcept {
    int ch=std::clamp(e.channel,0,32),a=std::clamp(e.a,0,127),b=std::clamp(e.b,0,127);
    if(e.type==0 && b>0){
        auto& stack=keys[ch][a];Key* key=&stack[0];
        for(auto& candidate:stack)if(!candidate.id){key=&candidate;break;}else if(candidate.id<key->id)key=&candidate;
        if(key->id)releaseId(ch,a,key->id,key->velocity);
        *key={++nextId,b,true,false};trigger(Trigger::Attack,ch,a,b,key->id);
    }
    else if(e.type==1 || (e.type==0 && b==0)){
        Key* key=nullptr;for(auto& candidate:keys[ch][a])if(candidate.id && candidate.down && (!key || candidate.id<key->id))key=&candidate;
        if(key){key->down=false;key->pending=controllers[ch][64]>=64;for(auto& v:voices)if(v.active && v.id==key->id){v.held=false;v.pending=key->pending;}
            if(!key->pending)releaseId(ch,a,key->id,key->velocity);}
    }else if(e.type==2){
        int previous=controllers[ch][a];controllers[ch][a]=b;
        if(a==64 && (previous>=64)!=(b>=64)){
            trigger(b>=64?Trigger::PedalDown:Trigger::PedalUp,ch,60,b>=64?127:64,++nextId);
            if(b<64){for(int note=0;note<128;++note)for(auto& key:keys[ch][note])if(key.id && key.pending){auto id=key.id;auto vel=key.velocity;releaseId(ch,note,id,vel);}}
        }
        if(a==120 || a==123){keys[ch]={};controllers[ch][64]=0;for(auto& v:voices)if(v.channel==ch){v.held=false;v.pending=false;if(a==120)v.active=false;else v.envelope.off(rate);}}
        if(a==121){controllers[ch].fill(0);controllers[ch][7]=controllers[ch][11]=127;bends[ch]=0;for(int note=0;note<128;++note)for(auto& key:keys[ch][note])if(key.id && key.pending)releaseId(ch,note,key.id,key.velocity);for(auto& v:voices)if(v.channel==ch && !v.held)v.envelope.off(rate);}
    }else if(e.type==3)bends[ch]=std::clamp(e.value,-2.f,2.f);
    else if(e.type==4)targetVolume=std::clamp(e.value,0.f,1.f);
    else if(e.type==5){keys={};for(auto& v:voices){v.held=false;v.pending=false;v.envelope.off(rate);}for(auto& c:controllers)c[64]=0;}
    else if(e.type==6)groupGains[std::clamp(e.a,0,255)]=std::clamp(e.value,0.f,4.f);
    else if(e.type==7)effects.parameter(e.a,e.b,e.value);
    else if(e.type==8){limit=std::clamp(e.a,32,MaxVoices);for(int i=limit;i<MaxVoices;++i)if(voices[i].active){voices[i].envelope.stage=Envelope::Release;voices[i].envelope.releaseStep=voices[i].envelope.value/(rate*.01f);voices[i].held=false;voices[i].pending=false;}}
    else if(e.type==9)targetInstrumentVolume=std::clamp(e.value,0.f,8.f);
    else if(e.type==10){playing=e.a!=0;reportedPlaying.store(playing);if(!playing)for(int channel=17;channel<33;++channel){dispatch({2,channel,120,0});dispatch({2,channel,64,0});}}
    else if(e.type==12){diagnosticMode=std::clamp(e.a,0,2);if(diagnosticMode==2){dispatch({10,0,0});dispatch({5});}}
}
void SamplerEngine::setTimeline(std::vector<TimedEvent> source,int64_t duration){
    timeline=std::move(source);std::stable_sort(timeline.begin(),timeline.end(),[](const auto& a,const auto& b){return a.frame<b.frame;});
    totalFrames=std::max<int64_t>(duration,timeline.empty()?0:timeline.back().frame);playing=false;transportOverload.store(false);seekTimeline(0);reportedPlaying.store(false);
}
void SamplerEngine::seekTimeline(int64_t frame) noexcept {
    // Seek restores controllers and pitch but intentionally starts only future notes.
    for(int ch=17;ch<33;++ch){dispatch({2,ch,120,0});dispatch({2,ch,121,0});}
    cursor=0;playFrame=frame;
    while(cursor<timeline.size() && timeline[cursor].frame<frame){const auto& e=timeline[cursor++].event;if(e.type==2 || e.type==3)dispatch(e);}
    for(auto& v:voices)if(v.channel>=17)v=Voice{};
    reportedFrame.store(playFrame);
}
void SamplerEngine::render(float* output,int count) noexcept {
    Event e;int processed=0;while(processed++<2048 && events.pop(e))dispatch(e);
    if(panic.exchange(false)){keys={};for(auto& v:voices)v.envelope.off(rate);for(auto& c:controllers)c[64]=0;}
    int done=0,timelineProcessed=0;
    while(done<count){
        if(playing){
            while(cursor<timeline.size() && timeline[cursor].frame<=playFrame && timelineProcessed<2048){dispatch(timeline[cursor++].event);++timelineProcessed;}
            if(cursor<timeline.size() && timeline[cursor].frame<=playFrame && timelineProcessed==2048){
                playing=false;reportedPlaying.store(false);transportOverload.store(true);droppedEvents.fetch_add(1);
                for(int ch=17;ch<33;++ch)dispatch({2,ch,120,0});
            }
            if(playFrame>=totalFrames && cursor==timeline.size()){playing=false;reportedPlaying.store(false);for(int ch=17;ch<33;++ch){dispatch({2,ch,123,0});dispatch({2,ch,64,0});}}
        }
        int n=count-done;
        if(playing){auto next=cursor<timeline.size()?timeline[cursor].frame:totalFrames;n=int(std::min<int64_t>(n,std::max<int64_t>(1,next-playFrame)));}
        renderBlock(output+done*2,n);done+=n;if(playing)playFrame+=n;
    }
    reportedFrame.store(playFrame,std::memory_order_relaxed);
    inputPeak.store(outputLimiter.peak(),std::memory_order_relaxed);gainReductionDb.store(outputLimiter.reductionDb(),std::memory_order_relaxed);
}
void SamplerEngine::renderBlock(float* output,int count) noexcept {
    std::fill(output,output+count*2,0.f);
    if(instrument)for(auto& v:voices){if(!v.active)continue;auto& r=instrument->regions[v.region];auto& s=instrument->samples[r.sample];
        SampleCache::View view;uint64_t attemptedPage=UINT64_MAX;int retry=0;double step=v.baseStep*std::pow(2.,bends[v.channel]*r.keyTrack/12.);float channelGain=controllers[v.channel][7]/127.f*controllers[v.channel][11]/127.f;
        uint64_t end=r.end>=0?std::min<uint64_t>(r.end+1,s.frames):s.frames;
        float gl=std::sqrt(1-std::max(0.f,r.pan)),gr=std::sqrt(1+std::min(0.f,r.pan));
        instrument->cache->prefetch(r.sample,uint64_t(v.position)+4096);instrument->cache->prefetch(r.sample,uint64_t(v.position)+8192);
        if(r.loopStart>=0)instrument->cache->prefetch(r.sample,r.loopStart);
        for(int i=0;i<count;++i){
            if(r.loopStart>=0 && r.loopEnd>r.loopStart && v.envelope.stage!=Envelope::Release && v.position>=r.loopEnd+1)v.position=r.loopStart+std::fmod(v.position-r.loopStart,double(r.loopEnd+1-r.loopStart));
            if(v.position>=end || v.envelope.stage==Envelope::Done){v.active=false;break;}
            auto pos=uint64_t(v.position);
            if((!view.data && (pos/SampleCache::PageFrames!=attemptedPage || retry++>=32)) || (view.data && (pos<view.start || pos>=view.start+view.frames))){retry=0;attemptedPage=pos/SampleCache::PageFrames;instrument->cache->view(r.sample,pos,view);}
            float l=v.sourceL,rr=v.sourceR,t=float(v.position-pos);
            if(view.data){auto off=(pos-view.start)*2;l=view.data[off]*(1-t)+view.data[off+2]*t;rr=view.data[off+1]*(1-t)+view.data[off+3]*t;v.sourceL=l;v.sourceR=rr;}
            v.streamGain+=((view.data?1.f:0.f)-v.streamGain)*.04f;
            v.mixGain+=(channelGain*groupGains[r.group]-v.mixGain)*.002f;
            float gain=v.gain*v.envelope.next(rate)*v.mixGain*v.streamGain;
            if(r.loopStart<0 || v.envelope.stage==Envelope::Release)gain*=std::clamp(float(end-v.position)/128.f,0.f,1.f);
            l*=gain*gl;rr*=gain*gr;
            if(v.stealRemaining>0){float fade=v.stealRemaining/128.f;l=l*(1-fade)+v.stealL*fade;rr=rr*(1-fade)+v.stealR*fade;--v.stealRemaining;}
            v.lastL=l;v.lastR=rr;output[i*2]+=l;output[i*2+1]+=rr;v.position+=step;
        }
    }
    int active=0;for(auto& v:voices)if(v.active)++active;activeVoices.store(active,std::memory_order_relaxed);
    for(int i=0;i<count;++i){float& l=output[i*2];float& r=output[i*2+1];const float dryL=l,dryR=r;effects.process(l,r);volume+=(targetVolume-volume)*.001f;instrumentVolume+=(targetInstrumentVolume-instrumentVolume)*.001f;
        diagnosticFx+=((diagnosticMode==1?0.f:1.f)-diagnosticFx)*.0015f;
        diagnosticTone+=((diagnosticMode==2?1.f:0.f)-diagnosticTone)*.0015f;
        l=(diagnosticFx==1?l:dryL+(l-dryL)*diagnosticFx)*instrumentVolume*(1-diagnosticTone);
        r=(diagnosticFx==1?r:dryR+(r-dryR)*diagnosticFx)*instrumentVolume*(1-diagnosticTone);
        if(diagnosticTone>1e-6f){l+=float(std::sin(tonePhaseL))*.08f*diagnosticTone;r+=float(std::sin(tonePhaseR))*.08f*diagnosticTone;tonePhaseL+=6.283185307179586*440/rate;tonePhaseR+=6.283185307179586*660/rate;if(tonePhaseL>6.283185307179586)tonePhaseL-=6.283185307179586;if(tonePhaseR>6.283185307179586)tonePhaseR-=6.283185307179586;}
        else tonePhaseL=tonePhaseR=0;
        l*=volume;r*=volume;
        outputLimiter.process(l,r);
    }
}
}
