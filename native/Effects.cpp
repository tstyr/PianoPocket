#include "Effects.h"
#include <algorithm>
#include <stdexcept>
namespace pocket {
constexpr float Pi=3.14159265358979323846f;
template<size_t Block> void PartitionedConvolver<Block>::fft(std::array<Complex,N>& x,bool inv) noexcept {
    for(size_t i=1,j=0;i<N;++i){size_t bit=N>>1;for(;j&bit;bit>>=1)j^=bit;j^=bit;if(i<j)std::swap(x[i],x[j]);}
    for(size_t len=2;len<=N;len<<=1){float angle=(inv?2:-2)*Pi/len;Complex step(cosf(angle),sinf(angle));for(size_t i=0;i<N;i+=len){Complex w(1,0);for(size_t j=0;j<len/2;++j){auto u=x[i+j],v=x[i+j+len/2]*w;x[i+j]=u+v;x[i+j+len/2]=u-v;w*=step;}}}
    if(inv)for(auto& v:x)v/=float(N);
}
template<size_t Block> PartitionedConvolver<Block>::PartitionedConvolver(const std::vector<float>& ir){
    partitions=(ir.size()/2+Block-1)/Block;
    for(int c=0;c<2;++c){kernels[c].resize(partitions);history[c].resize(partitions);active[c].resize(partitions);for(size_t p=0;p<partitions;++p){auto& k=kernels[c][p];for(size_t j=0;j<Block && p*Block+j<ir.size()/2;++j)k[j]=ir[(p*Block+j)*2+c];fft(k,false);}}
}
template<size_t Block> void PartitionedConvolver<Block>::process(float& l,float& r) noexcept {
    input[0][position]=l;input[1][position]=r;l=output[0][position];r=output[1][position];
    if(++position<Block)return;
    position=0;
    for(int c=0;c<2;++c){scratch.fill({});bool nonzero=false;for(size_t i=0;i<Block;++i){scratch[i]=input[c][i];nonzero=nonzero || std::abs(input[c][i])>1e-12f;}
        active[c][ring]=nonzero;if(nonzero)fft(scratch,false);history[c][ring]=scratch;sum.fill({});bool contributions=false;
        for(size_t p=0;p<partitions;++p){auto slot=(ring+partitions-p)%partitions;if(!active[c][slot])continue;contributions=true;auto& h=history[c][slot];auto& k=kernels[c][p];for(size_t i=0;i<N;++i)sum[i]+=h[i]*k[i];}
        if(contributions)fft(sum,true);
        for(size_t i=0;i<Block;++i){output[c][i]=sum[i].real()+overlap[c][i];overlap[c][i]=sum[i+Block].real();}
    }ring=(ring+1)%partitions;
}
Convolver::Convolver(const std::vector<float>& ir){
    if(ir.empty() || ir.size()/2>1048576)throw std::runtime_error("IR must contain 1..1048576 stereo frames");
    constexpr size_t split=2048*2;auto earlyEnd=ir.begin()+std::min(split,ir.size());
    early=std::make_unique<PartitionedConvolver<256>>(std::vector<float>(ir.begin(),earlyEnd));
    if(ir.size()>split)late=std::make_unique<PartitionedConvolver<2048>>(std::vector<float>(earlyEnd,ir.end()));
}
void Convolver::process(float& l,float& r) noexcept {
    float lateL=l,lateR=r;early->process(l,r);
    if(late){late->process(lateL,lateR);l+=lateDelay[0][position];r+=lateDelay[1][position];lateDelay[0][position]=lateL;lateDelay[1][position]=lateR;position=(position+1)%256;}
}
Effect::Effect(const EffectSpec& s,float sr):type(s.type),rate(sr),smoothing(1-std::exp(-1.f/(sr*.015f))),current(s.p),target(s.p){
    if(type==EffectType::Delay)delay.resize(size_t(rate*25+4)*2);
    if(type==EffectType::Chorus)delay.resize(size_t(rate*.1+4)*2);
    if(type==EffectType::Reverb){int lengths[8]={1116,1188,1277,1356,1139,1211,1300,1379};for(int i=0;i<8;++i)comb[i].resize(size_t(lengths[i]*rate/44100));int ap[4]={225,556,248,579};for(int i=0;i<4;++i)allpass[i].resize(size_t(ap[i]*rate/44100));}
    if(type==EffectType::Convolution){
        // Reverb IR files have different recorded gains. One stereo-linked energy
        // factor avoids raw IR amplification; no quiet-IR boost or channel imbalance.
        double energy[2]={0,0};for(size_t i=0;i<s.ir.size();++i){if(!std::isfinite(s.ir[i]))throw std::runtime_error("IR contains non-finite audio");energy[i%2]+=double(s.ir[i])*s.ir[i];}
        auto norm=std::sqrt(std::max(energy[0],energy[1]));
        if(norm>.125)irGain=float(.125/norm);
        convolution=std::make_unique<Convolver>(s.ir);
    }
    coefficients();
}
void Effect::parameter(int slot,float value) noexcept {
    if(slot<0 || slot>=5 || !std::isfinite(value))return;
    if(slot==4){target[slot]=value>=.5f?1:0;return;}
    if(type==EffectType::Gain){target[slot]=std::clamp(value,0.f,8.f);return;}
    if(type>=EffectType::Lowpass && type!=EffectType::Convolution){
        target[slot]= slot==1?std::clamp(value,20.f,22000.f):slot==2?std::clamp(value,.001f,5.f):slot==3?std::clamp(value,.001f,16.f):std::clamp(value,0.f,1.f);return;
    }
    if(type==EffectType::Delay){target[slot]=slot==1?std::clamp(value,0.f,20.f):slot==3?std::clamp(value,-10.f,10.f):std::clamp(value,0.f,slot==2?.995f:1.f);return;}
    target[slot]=std::clamp(value,0.f,type==EffectType::Chorus && slot==2?10.f:1.f);
}
void Effect::coefficients() noexcept {
    if(type==EffectType::Gain || type==EffectType::Reverb || type==EffectType::Delay || type==EffectType::Chorus || type==EffectType::Convolution)return;
    double w=2*Pi*std::clamp(current[1],20.f,rate*.49f)/rate,cs=cos(w),sn=sin(w),alpha=sn/(2*std::max(current[2],.001f)),a0=1+alpha;
    b0=(1-cs)/2;b1=1-cs;b2=b0;a1=-2*cs;a2=1-alpha;
    if(type==EffectType::Highpass){b0=(1+cs)/2;b1=-(1+cs);b2=b0;}
    if(type==EffectType::Bandpass){b0=alpha;b1=0;b2=-alpha;}
    if(type==EffectType::Notch){b0=1;b1=-2*cs;b2=1;}
    if(type==EffectType::Peak){double A=sqrt(std::max(current[3],.001f));b0=1+alpha*A;b1=-2*cs;b2=1-alpha*A;a0=1+alpha/A;a2=1-alpha/A;}
    if(type==EffectType::LowShelf || type==EffectType::HighShelf){
        double A=sqrt(std::max(current[3],.001f)),t=2*sqrt(A)*alpha;
        if(type==EffectType::LowShelf){b0=A*((A+1)-(A-1)*cs+t);b1=2*A*((A-1)-(A+1)*cs);b2=A*((A+1)-(A-1)*cs-t);a0=(A+1)+(A-1)*cs+t;a1=-2*((A-1)+(A+1)*cs);a2=(A+1)+(A-1)*cs-t;}
        else{b0=A*((A+1)+(A-1)*cs+t);b1=-2*A*((A-1)+(A+1)*cs);b2=A*((A+1)+(A-1)*cs-t);a0=(A+1)-(A-1)*cs+t;a1=2*((A-1)-(A+1)*cs);a2=(A+1)-(A-1)*cs-t;}
    }
    b0/=a0;b1/=a0;b2/=a0;a1/=a0;a2/=a0;
}
void Effect::process(float& l,float& r) noexcept {
    for(int i=0;i<5;++i)current[i]+=(target[i]-current[i])*smoothing;
    float dryL=l,dryR=r,wetL=l,wetR=r;
    if(type==EffectType::Gain){wetL*=current[1];wetR*=current[1];}
    else if(type==EffectType::Convolution){if(current[0]<1e-6f && target[0]==0)wetL=wetR=0;convolution->process(wetL,wetR);wetL=dryL*(1-current[0])+wetL*irGain*current[0];wetR=dryR*(1-current[0])+wetR*irGain*current[0];}
    else if(type==EffectType::Reverb){
        wetL=wetR=0;float feed=(dryL+dryR)*.015f,feedback=.7f+current[1]*.27f,damp=current[2]*.9f;
        for(int i=0;i<8;++i){auto& buf=comb[i];auto& pos=combPos[i];auto x=buf[pos];damped[i]=x*(1-damp)+damped[i]*damp;buf[pos]=feed+damped[i]*feedback;pos=(pos+1)%buf.size();(i<4?wetL:wetR)+=x;}
        for(int i=0;i<4;++i){auto& buf=allpass[i];auto& pos=allPos[i];auto& x=(i<2?wetL:wetR);auto delayed=buf[pos];buf[pos]=x+delayed*.5f;x=delayed-x;pos=(pos+1)%buf.size();}
        wetL=dryL+wetL*current[0];wetR=dryR+wetR*current[0];
    }else if(type==EffectType::Delay || type==EffectType::Chorus){
        size_t capacity=delay.size()/2;
        for(int c=0;c<2;++c){float seconds=type==EffectType::Delay?current[1]+(c?1:-1)*current[3]*.5f:.015f+.010f*current[1]*sinf(phase+c*Pi*.5f);
            double read=double(write)-std::clamp(seconds*rate,1.f,float(capacity-2));if(read<0)read+=capacity;
            auto i=size_t(read);float t=float(read-i);float value=delay[i*2+c]*(1-t)+delay[((i+1)%capacity)*2+c]*t;
            auto dry=c?dryR:dryL;delay[write*2+c]=dry+(type==EffectType::Delay?value*std::min(current[2],.995f):0);
            (c?wetR:wetL)=type==EffectType::Delay?dry+value*current[0]:dry*(1-current[0])+value*current[0];
        }write=(write+1)%capacity;phase+=2*Pi*current[2]/rate;if(phase>2*Pi)phase-=2*Pi;
    }else{coefficients();for(int c=0;c<2;++c){double x=c?dryR:dryL,y=b0*x+z1[c];z1[c]=b1*x-a1*y+z2[c];z2[c]=b2*x-a2*y;(c?wetR:wetL)=float(y);}}
    l=dryL+(wetL-dryL)*current[4];r=dryR+(wetR-dryR)*current[4];
}
void Effects::prepare(const std::vector<EffectSpec>& specs,float rate){chain.clear();for(auto& s:specs)chain.push_back(std::make_unique<Effect>(s,rate));}
void Effects::parameter(int effect,int slot,float value) noexcept {if(effect>=0 && size_t(effect)<chain.size())chain[effect]->parameter(slot,value);}
void Effects::replace(int index,std::unique_ptr<Effect> effect){if(index<0 || size_t(index)>=chain.size())throw std::runtime_error("Invalid effect index");chain[index]=std::move(effect);}
}
