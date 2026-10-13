#pragma once
#include <string>
namespace pocket {
// Import worker only. Generates a bounded WAV for the existing asynchronous page decoder.
void decodeCompressedSample(const std::string& source,const std::string& target);
}
