#pragma once
#include <cstdint>
#include <string>
#include <vector>
namespace pocket {
struct Sample {
    std::string path;
    uint32_t rate = 48000, channels = 2, bits = 16, format = 1;
    uint64_t dataOffset = 0, frames = 0;
    std::vector<float> head; // Immutable, resident attack frames, interleaved stereo.
    static Sample open(const std::string& path, size_t headFrames = 8192);
    void decode(uint64_t start, size_t count, float* output) const; // worker only
};
}
