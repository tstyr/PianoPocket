#pragma once
#include "SampleDecoder.h"
#include "SpscQueue.h"
#include <array>
#include <thread>
#include <memory>
namespace pocket {
class SampleCache {
public:
    static constexpr size_t PageFrames=4096, PageCount=256;
    struct Page {
        std::atomic<int> readers{0}; // -1: worker owns; >=0: callback readers
        uint64_t key=UINT64_MAX;
        std::atomic<uint64_t> used{0};
        std::array<float,(PageFrames+1)*2> data{};
    };
    struct View {
        Page* page=nullptr; const float* data=nullptr; uint64_t start=0; size_t frames=0;
        View()=default; View(const View&)=delete; View& operator=(const View&)=delete;
        ~View(){reset();}
        void reset() noexcept { if(page) page->readers.fetch_sub(1,std::memory_order_release); page=nullptr; data=nullptr; }
    };
    explicit SampleCache(const std::vector<Sample>& samples);
    ~SampleCache();
    void view(unsigned sample,uint64_t frame,View& out) noexcept;
    void prefetch(unsigned sample,uint64_t frame) noexcept;
    std::atomic<uint64_t> misses{0}, readErrors{0};
private:
    const std::vector<Sample>& samples;
    std::unique_ptr<Page[]> pages;
    SpscQueue<uint64_t,4096> requests;
    std::atomic<bool> running{true};
    std::atomic<uint64_t> clock{0};
    std::thread worker;
    void run();
};
}
