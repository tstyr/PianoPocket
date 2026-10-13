#pragma once
#include <array>
#include <cmath>
#include <algorithm>
namespace pocket {
struct VoiceFilter {
    std::array<double,2> z1{},z2{};
    static std::array<double,5> coefficients(int type,float cutoff,float resonance,float rate) noexcept {
        double omega=6.283185307179586*std::clamp(cutoff,10.f,rate*.49f)/rate,c=std::cos(omega),s=std::sin(omega);
        if(type==1 || type==3){double k=std::tan(omega/2),d=1/(1+k);return type==1?std::array<double,5>{k*d,k*d,0,(k-1)*d,0}:std::array<double,5>{d,-d,0,(k-1)*d,0};}
        double q=std::pow(10.,std::clamp(resonance,0.f,40.f)/20)/std::sqrt(2.),alpha=s/(2*q),d=1/(1+alpha);
        if(type==2)return {(1-c)*.5*d,(1-c)*d,(1-c)*.5*d,-2*c*d,(1-alpha)*d};
        if(type==4)return {(1+c)*.5*d,-(1+c)*d,(1+c)*.5*d,-2*c*d,(1-alpha)*d};
        if(type==5)return {alpha*d,0,-alpha*d,-2*c*d,(1-alpha)*d};
        if(type==6)return {d,-2*c*d,d,-2*c*d,(1-alpha)*d};
        return {1,0,0,0,0};
    }
    void process(float& left,float& right,const std::array<double,5>& c) noexcept {
        float* channels[2]={&left,&right};
        for(int i=0;i<2;++i){double x=*channels[i],y=c[0]*x+z1[i];z1[i]=c[1]*x-c[3]*y+z2[i];z2[i]=c[2]*x-c[4]*y;if(std::abs(z1[i])<1e-24)z1[i]=0;if(std::abs(z2[i])<1e-24)z2[i]=0;*channels[i]=std::isfinite(y)?float(y):0;}
    }
};
}
