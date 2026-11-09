// Copyright 2026 The OpenWeights Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include <algorithm>
#include <array>
#include <cstdint>

/**
 * Picks the decode thread count step by step, so that a reply caught by a CPU pause falls back to
 * the threads that can still run and climbs back once the cores return.
 *
 * Why: on a Snapdragon 8 Elite Gen 5 the thermal engine's hard throttle parks six of the eight
 * cores. With more decode threads than the two left, llama.cpp's barrier spins on cores the
 * working threads need, and decode nearly stops: the app's engine at 4 threads ran at a hundredth
 * of its rate, at 2 threads at 0.46 of it (docs/research/snapdragon-pd-matrix.md, r27; the spin was
 * confirmed by profile in r28). An app cannot rely on reading the pause itself, so the guard reads
 * the one thing it always has, how long each step took.
 *
 * Pure logic with no llama.cpp in it: the session feeds it each step's time and applies the count
 * it returns, and the host test drives it with made-up times.
 */
class StallGuard {
  public:
    /** The count that still runs during the pause: two cores are left. */
    static constexpr int32_t FALLBACK_THREADS = 2;
    /** A step this many times the recent normal one, and at least STALL_MIN_MS, is slow. In a
     *  pause steps took 50 to 300 times longer (r27), so a factor of 8 leaves a wide margin. */
    static constexpr double STALL_FACTOR = 8.0;
    static constexpr double STALL_MIN_MS = 250.0;
    /** With no history yet, a step this long is slow on its own. */
    static constexpr double STALL_ABS_MS = 1500.0;
    /** Two slow steps in a row before falling back: a pause slows every step, one hiccup does not. */
    static constexpr int32_t STALL_CONFIRM = 2;
    /** While fallen back, one step at the planned count is tried this often. A failed try costs one
     *  slow step (about 4 s at 4 threads); natural pauses lasted about two minutes (r20). */
    static constexpr int64_t PROBE_EVERY_MS = 30000;
    /** A try succeeds when the planned count's step is at most this many times the fallback's own
     *  recent steps. In a pause the planned count was about 40 times slower than 2 threads (r27);
     *  with the cores all there, more threads are about as fast or faster. Judging against the
     *  fallback's steps, not the old baseline, is what lets a fallback that was never a pause end:
     *  a longer context or a bigger model makes every step slower, and the old baseline would then
     *  fail every try for good (review). */
    static constexpr double PROBE_FACTOR = 3.0;
    /** The fallback's steps are judged by the median of the last FALLBACK_SAMPLES, and a try waits
     *  for at least MIN_FALLBACK_SAMPLES: one slow step right after falling back would otherwise
     *  inflate an average enough to pass a still-stalled try, which then becomes the baseline and
     *  hides every later stall (second review). */
    static constexpr int32_t FALLBACK_SAMPLES = 8;
    static constexpr int32_t MIN_FALLBACK_SAMPLES = 4;
    static constexpr double EMA_WEIGHT = 0.1;

    explicit StallGuard(bool enabled = true) : enabled_(enabled) {}

    /** The count the app planned for what follows. Returns the count to run with now. A plan at
     *  or under the fallback is obeyed without ending a fallback: a thermal re-plan down and back
     *  up again within one pause must not hand the stalled count back (review). */
    int32_t plan(int32_t planned) {
        planned_ = planned;
        return active();
    }

    /** The count in force: the planned one, unless fallen back and not trying the planned one. */
    int32_t active() const {
        return fallen_ && !probing_ ? std::min(planned_, FALLBACK_THREADS) : planned_;
    }

    /** After a decode step that took step_ms, at now_ms. Returns the count for the next step. */
    int32_t step(double step_ms, int64_t now_ms) {
        if (!enabled_) {
            update(ema_, step_ms);
            return planned_;
        }
        if (planned_ <= FALLBACK_THREADS) {
            if (fallen_) {
                record_fallback(step_ms);
            } else {
                update(ema_, step_ms);
            }
            return planned_;
        }
        if (probing_) {
            probing_ = false;
            if (step_ms <= PROBE_FACTOR * fallback_median()) {
                fallen_ = false;
                ema_ = step_ms;  // a fresh baseline at the planned count, from now
                fallback_n_ = 0;
                fallback_at_ = 0;
                return planned_;
            }
            probe_at_ = now_ms + PROBE_EVERY_MS;
            return FALLBACK_THREADS;
        }
        if (fallen_) {
            record_fallback(step_ms);
            if (now_ms >= probe_at_ && fallback_n_ >= MIN_FALLBACK_SAMPLES) {
                probing_ = true;
                return planned_;
            }
            return FALLBACK_THREADS;
        }
        if (slow(step_ms)) {
            if (++slow_run_ >= STALL_CONFIRM) {
                slow_run_ = 0;
                fallen_ = true;
                fallback_n_ = 0;
                fallback_at_ = 0;
                probe_at_ = now_ms + PROBE_EVERY_MS;
                ++falls_;
                return FALLBACK_THREADS;
            }
            return planned_;  // a slow step is not averaged in: it would raise the bar for the next
        }
        slow_run_ = 0;
        update(ema_, step_ms);
        return planned_;
    }

    /** A decode that failed or was stopped mid-step: a try in flight is abandoned, not judged. */
    void abandon(int64_t now_ms) {
        if (probing_) {
            probing_ = false;
            probe_at_ = now_ms + PROBE_EVERY_MS;
        }
    }

    bool fallen() const { return fallen_; }
    bool probing() const { return probing_; }
    int32_t falls() const { return falls_; }
    double normal_ms() const { return ema_; }

  private:
    bool slow(double ms) const {
        return ema_ > 0 ? (ms > STALL_MIN_MS && ms > STALL_FACTOR * ema_) : ms > STALL_ABS_MS;
    }

    void record_fallback(double ms) {
        fallback_[static_cast<size_t>(fallback_at_)] = ms;
        fallback_at_ = (fallback_at_ + 1) % FALLBACK_SAMPLES;
        fallback_n_ = std::min(fallback_n_ + 1, FALLBACK_SAMPLES);
    }

    double fallback_median() const {
        if (fallback_n_ == 0) return 0.0;
        std::array<double, FALLBACK_SAMPLES> v = fallback_;
        std::nth_element(v.begin(), v.begin() + fallback_n_ / 2, v.begin() + fallback_n_);
        return v[static_cast<size_t>(fallback_n_ / 2)];
    }

    static void update(double & ema, double ms) {
        ema = ema > 0 ? (1.0 - EMA_WEIGHT) * ema + EMA_WEIGHT * ms : ms;
    }

    bool enabled_;
    int32_t planned_ = 0;
    bool fallen_ = false;
    bool probing_ = false;
    int32_t slow_run_ = 0;
    int64_t probe_at_ = 0;
    int32_t falls_ = 0;
    double ema_ = 0.0;           // steps at the planned count, outside a fallback
    std::array<double, FALLBACK_SAMPLES> fallback_{};  // the last steps at the fallback count
    int32_t fallback_at_ = 0;  // where the next one goes
    int32_t fallback_n_ = 0;   // how many there are, up to FALLBACK_SAMPLES
};
