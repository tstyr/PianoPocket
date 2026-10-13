#pragma once
#include "SampleDecoder.h"
#include "SpscQueue.h"
#include <array>
#include <thread>
#include <memory>
namespace pocket {
class SampleCache {
public:
    static constexpr size_t PageFrames=4096,MinPages=32,PageCount=256,MaxPages=8192;
    static constexpr size_t Buckets=8192,Ways=4;
    struct Page {std::atomic<int> readers{0};uint64_t key=UINT64_MAX;std::atomic<uint64_t> used{0};std::array<float,(PageFrames+1)*2> data{};};
    static constexpr size_t DirectoryBytes=Buckets*Ways*sizeof(std::atomic<uint32_t>)+Buckets*sizeof(std::atomic<uint64_t>);
    struct View {
        Page* page=nullptr;const float* data=nullptr;uint64_t start=0;size_t frames=0;
        View()=default;View(const View&)=delete;View& operator=(const View&)=delete;~View(){reset();}
        void reset() noexcept {if(page)page->readers.fetch_sub(1,std::memory_order_release);page=nullptr;data=nullptr;}
    };
    explicit SampleCache(const std::vector<Sample>& samples,bool offline=false,size_t count=PageCount);
    ~SampleCache();
    void view(unsigned sample,uint64_t frame,View& out) noexcept;
    void prefetch(unsigned sample,uint64_t frame) noexcept;
    size_t capacity() const {return count;}
    size_t bytes() const {return count*sizeof(Page)+DirectoryBytes;}
    std::atomic<uint64_t> misses{0},readErrors{0},requestDrops{0};
private:
    const std::vector<Sample>& samples;size_t count;
    std::unique_ptr<Page[]> pages;
    std::array<std::atomic<uint32_t>,Buckets*Ways> directory;
    std::array<std::atomic<uint64_t>,Buckets> pending;
    SpscQueue<uint64_t,4096> requests;
    SpscQueue<uint64_t,2048> urgent;
    std::atomic<bool> running{true};std::atomic<uint64_t> clock{0};std::thread worker;bool offline=false;
    static size_t bucket(uint64_t key) noexcept;
    bool find(uint64_t key,View& out,bool touch) noexcept;
    void request(uint64_t key,bool priority) noexcept;
    bool load(uint64_t key);
    void run();
};
}
