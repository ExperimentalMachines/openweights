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
    /** A step this much longer than the normal one is slow as well. A pause adds seconds to each
     *  step rather than multiplying it: 4 to 9 s where measured (r27, r34, r38, normal steps of 43 to
     *  129 ms), so on a model whose normal step is already a second the pause is 4 to 6 times, under
     *  the factor, and the added seconds give it away (seventh review). No normal step measured was
     *  over 0.3 s. A try at the planned count is held to this too: there a stalled step is about
     *  three times the fallback's, which PROBE_FACTOR alone would let through. */
    static constexpr double STALL_EXCESS_MS = 2000.0;
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
    /** The normal step is the median of the last NORMAL_SAMPLES steps at the planned count that were
     *  not slow. A median, so that one odd step either way, a slow first step or an unusually fast
     *  one, does not set the bar a stall must clear; an average let a slow first step hide a stall,
     *  and the fastest early step let a fast one cause false fallbacks (fourth and fifth reviews). */
    static constexpr int32_t NORMAL_SAMPLES = 8;
    /** Until MIN_HISTORY such steps are in, a step past STALL_ABS_MS is slow as well: the median of
     *  one or two steps may itself be a stalled step's (fourth review). And against fewer than two
     *  the relative rule is not applied at all: one fast first step would make the next two ordinary
     *  steps a stall (sixth review). */
    static constexpr int32_t MIN_HISTORY = 4;
    /** After that, a step joins the normal ones only within this many times their median. A step
     *  between this and STALL_FACTOR is neither a stall nor the new normal: let in, a slowdown that
     *  grows in such steps would raise the bar until a real stall passed under it (fourth review).
     *  Context growth moves the normal step a little at a time and stays well inside it; a lasting
     *  jump past it costs one fallback and a successful try, which starts the normal ones afresh. */
    static constexpr double NORMAL_FACTOR = 3.0;
    /** While fallen back, a step at the fallback count this many times faster than the fallback's
     *  median, RETURN_CONFIRM times in a row, means the cores are back, and the planned count is
     *  tried at once instead of at the next scheduled try, up to PROBE_EVERY_MS later. When the
     *  pause ended, 2-thread steps got 2.2 to 3.5 times faster (r49 with a pause set by hand, r34
     *  and r38 on phone D, r48 in one heat brought on); 1.6 stays under the smallest. Such a try is
     *  judged against those fast steps, not the slower median: judged against the median, a step
     *  still stalled at the planned count could pass and become the normal (review). And one that
     *  fails turns early tries off until the fallback ends, so a pause whose 2-thread steps swing
     *  past the factor again and again (0.7 to 2.1 s on an SM8550, r50) costs one stalled step,
     *  not one every few steps; the scheduled tries carry on. */
    static constexpr double RETURN_FACTOR = 1.6;
    static constexpr int32_t RETURN_CONFIRM = 2;

    /** `early_try` false keeps only the scheduled tries, the rule before the early one, for
     *  comparing the two on a phone (OPENWEIGHTS_STALL_GUARD=timer). */
    explicit StallGuard(bool enabled = true, bool early_try = true) : enabled_(enabled), early_try_(early_try) {}

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
        if (!enabled_) return planned_;
        if (planned_ <= FALLBACK_THREADS) {
            // Steps at a plan under the fallback count are slower than the fallback's, and a try is
            // judged against the fallback's own steps; steps at any such plan are not the planned
            // count's normal either (fourth review).
            if (fallen_ && planned_ == FALLBACK_THREADS) record_fallback(step_ms);
            fast_run_ = 0;  // the fast steps that start an early try are two in a row at the planned count's fallback
            fast_max_ = 0.0;
            return planned_;
        }
        if (probing_) {
            probing_ = false;
            // An early try against the fast steps that started it; a scheduled one against the
            // fallback's steps as they stand now, as the rule before did, re-plans and all (review).
            const double fallback = judge_fast_ ? try_base_ : fallback_median();
            if (step_ms <= PROBE_FACTOR * fallback && step_ms <= fallback + STALL_EXCESS_MS) {
                fallen_ = false;
                // A fresh normal at the planned count, from now: the try showed this count runs.
                normal_n_ = 0;
                normal_at_ = 0;
                record(normal_, normal_at_, normal_n_, step_ms);
                seeded_ = true;
                fallback_n_ = 0;
                fallback_at_ = 0;
                return planned_;
            }
            if (early_) early_spent_ = true;  // a false alarm: no more early tries this fallback
            probe_at_ = now_ms + PROBE_EVERY_MS;
            return FALLBACK_THREADS;
        }
        if (fallen_) {
            // Judged against the median before this step joins it.
            const bool fast = fallback_n_ >= MIN_FALLBACK_SAMPLES && step_ms * RETURN_FACTOR <= fallback_median();
            if (fast) {
                ++fast_run_;
                fast_max_ = std::max(fast_max_, step_ms);
            } else {
                fast_run_ = 0;
                fast_max_ = 0.0;
            }
            record_fallback(step_ms);
            const bool back = early_try_ && !early_spent_ && fast_run_ >= RETURN_CONFIRM;
            const bool due = now_ms >= probe_at_ && fallback_n_ >= MIN_FALLBACK_SAMPLES;
            if (back || due) {
                // Fast steps make it an early try even when the schedule falls due at the same step:
                // it is judged against them, and logged as what it is (review).
                early_ = back;
                judge_fast_ = back;
                try_base_ = fast_max_;
                fast_run_ = 0;
                fast_max_ = 0.0;
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
                fast_run_ = 0;
                fast_max_ = 0.0;
                early_spent_ = false;
                probe_at_ = now_ms + PROBE_EVERY_MS;
                ++falls_;
                return FALLBACK_THREADS;
            }
            return planned_;  // a slow step is not counted as normal: it would raise the bar for the next
        }
        slow_run_ = 0;
        if (!seeded_ || step_ms <= NORMAL_FACTOR * normal_ms()) {
            record(normal_, normal_at_, normal_n_, step_ms);
            if (normal_n_ >= MIN_HISTORY) seeded_ = true;
        }
        return planned_;
    }

    /** A decode that failed or was stopped mid-step, or a new reply starting: a try in flight is
     *  abandoned, not judged, and the next scheduled one waits another interval. An early one can
     *  come again as soon as the steps are fast again: the dropped try showed nothing. */
    void abandon(int64_t now_ms) {
        if (probing_) {
            probing_ = false;
            probe_at_ = now_ms + PROBE_EVERY_MS;
        }
    }

    bool fallen() const { return fallen_; }
    bool probing() const { return probing_; }
    /** Whether the try in flight came from the steps speeding up rather than the schedule. */
    bool early() const { return probing_ && early_; }
    int32_t falls() const { return falls_; }
    double normal_ms() const { return median(normal_, normal_n_); }

  private:
    bool slow(double ms) const {
        const double normal = normal_ms();
        const bool against_normal =
            ms > STALL_MIN_MS && (ms > STALL_FACTOR * normal || ms > normal + STALL_EXCESS_MS);
        if (seeded_) return against_normal;  // a passed try leaves one normal step, and it is trusted
        if (normal_n_ < 2) return ms > STALL_ABS_MS;
        return against_normal || ms > STALL_ABS_MS;
    }

    void record_fallback(double ms) { record(fallback_, fallback_at_, fallback_n_, ms); }

    double fallback_median() const { return median(fallback_, fallback_n_); }

    /** Into a ring of the last N: `at` is where the next goes, `n` how many there are, up to N. */
    template <size_t N>
    static void record(std::array<double, N> & ring, int32_t & at, int32_t & n, double ms) {
        ring[static_cast<size_t>(at)] = ms;
        at = (at + 1) % static_cast<int32_t>(N);
        n = std::min(n + 1, static_cast<int32_t>(N));
    }

    /** The upper median of the first n of a ring, 0 with none. */
    template <size_t N>
    static double median(const std::array<double, N> & ring, int32_t n) {
        if (n == 0) return 0.0;
        std::array<double, N> v = ring;
        std::nth_element(v.begin(), v.begin() + n / 2, v.begin() + n);
        return v[static_cast<size_t>(n / 2)];
    }

    bool enabled_;
    bool early_try_;
    int32_t planned_ = 0;
    bool fallen_ = false;
    bool probing_ = false;
    int32_t slow_run_ = 0;
    int64_t probe_at_ = 0;
    int32_t falls_ = 0;
    std::array<double, NORMAL_SAMPLES> normal_{};  // the last normal steps at the planned count
    int32_t normal_at_ = 0;
    int32_t normal_n_ = 0;
    bool seeded_ = false;  // MIN_HISTORY normal steps in, or a try passed, since the session began
    std::array<double, FALLBACK_SAMPLES> fallback_{};  // the last steps at the fallback count
    int32_t fallback_at_ = 0;  // where the next one goes
    int32_t fallback_n_ = 0;   // how many there are, up to FALLBACK_SAMPLES
    int32_t fast_run_ = 0;      // fallback steps in a row past RETURN_FACTOR
    double fast_max_ = 0.0;     // the slowest of them
    bool early_ = false;        // the try in flight was started by fast_run_
    bool early_spent_ = false;  // an early try failed in this fallback
    bool judge_fast_ = false;   // the try in flight is judged against try_base_, not the median
    double try_base_ = 0.0;     // the slower of the fast steps that started an early try
};
