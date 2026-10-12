#pragma once
#include <vector>
#include <string>
namespace pocket {
// Worker only. Complete IR decode/resample, no truncation and no runtime dependency.
std::vector<float> loadImpulse(const std::string& path,float outputRate);
}
