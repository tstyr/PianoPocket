#pragma once
#include <array>
#include <atomic>
#include <cstddef>
namespace pocket {
template<class T, size_t N> class SpscQueue {
    std::array<T, N> data{};
    alignas(64) std::atomic<size_t> write{0};
    alignas(64) std::atomic<size_t> read{0};
public:
    bool push(const T& value) noexcept {
        auto w = write.load(std::memory_order_relaxed);
        auto next = (w + 1) % N;
        if (next == read.load(std::memory_order_acquire)) return false;
        data[w] = value;
        write.store(next, std::memory_order_release); return true;
    }
    bool pop(T& value) noexcept {
        auto r = read.load(std::memory_order_relaxed);
        if (r == write.load(std::memory_order_acquire)) return false;
        value = data[r]; read.store((r + 1) % N, std::memory_order_release); return true;
    }
};
}
