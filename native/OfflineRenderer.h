#pragma once
#include "SamplerEngine.h"
#include <string>
namespace pocket {
void renderWav(SamplerEngine& sampler,const std::string& path,int rate,int bits,int64_t frames,std::atomic<bool>& cancel,std::atomic<float>& progress);
}
