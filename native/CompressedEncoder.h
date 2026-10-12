#pragma once
#include <string>
#include <atomic>
namespace pocket {
// Worker-only PCM WAV -> FLAC24 or MP3. Never called from the audio callback.
void encodeCompressed(const std::string& wav,const std::string& output,int format,int bitrate,
                      std::atomic<bool>& cancel,std::atomic<float>& progress);
}
