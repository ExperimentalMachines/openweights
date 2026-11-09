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

// Host test for StallGuard, which has no llama.cpp in it. CI runs it (ci.yml, "Native host tests");
// from the repo root, as one line (no backslash: GCC reads one at the end of a // comment as joining
// the next line into the comment, and -Werror fails on its warning):
//   c++ -std=c++17 -Wall -Wextra -Werror -I core/engine/src/main/cpp core/engine/src/hostTest/cpp/stall_guard_test.cpp -o /tmp/stall_guard_test && /tmp/stall_guard_test
// Step times are modelled on r27 (docs/research/snapdragon-pd-matrix.md): about 43 ms a step at
// 4 threads, about 4,100 ms at 4 threads in a pause, about 95 ms at 2 threads in one.

#include "stall_guard.h"

#include <cstdio>
#include <cstdlib>

static int failures = 0;

#define EXPECT_EQ(a, b)                                                                          \
    do {                                                                                         \
        const auto va = (a);                                                                     \
        const auto vb = (b);                                                                     \
        if (va != vb) {                                                                          \
            std::fprintf(stderr, "%s:%d: %s is %lld, expected %lld\n", __FILE__, __LINE__, #a,     \
                         static_cast<long long>(va), static_cast<long long>(vb));                \
            ++failures;                                                                          \
        }                                                                                        \
    } while (0)

// Feeds `n` steps of `ms` each from `t`, advancing the clock by the step; returns the last answer.
static int32_t run(StallGuard & g, int n, double ms, int64_t & t) {
    int32_t want = g.active();
    for (int i = 0; i < n; ++i) {
        t += static_cast<int64_t>(ms);
        want = g.step(ms, t);
    }
    return want;
}

static void normal_steps_keep_the_plan() {
    StallGuard g;
    int64_t t = 0;
    EXPECT_EQ(g.plan(4), 4);
    EXPECT_EQ(run(g, 200, 43, t), 4);
    EXPECT_EQ(g.fallen(), false);
}

static void one_hiccup_is_not_a_pause() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    EXPECT_EQ(run(g, 1, 900, t), 4);  // one slow step
    EXPECT_EQ(run(g, 50, 43, t), 4);
    EXPECT_EQ(g.falls(), 0);
}

static void a_pause_mid_reply_falls_back_after_two_slow_steps() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    EXPECT_EQ(run(g, 1, 4100, t), 4);  // the first slow step only counts
    EXPECT_EQ(run(g, 1, 4100, t), 2);  // the second falls back
    EXPECT_EQ(g.fallen(), true);
    EXPECT_EQ(run(g, 100, 95, t), 2);  // and stays there before the probe is due
}

static void a_pause_from_the_first_step_is_caught_without_history() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    EXPECT_EQ(run(g, 1, 4100, t), 4);
    EXPECT_EQ(run(g, 1, 4100, t), 2);
}

static void a_failed_probe_waits_another_interval() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);
    run(g, StallGuard::MIN_FALLBACK_SAMPLES - 1, 95, t);
    t += StallGuard::PROBE_EVERY_MS;      // time passes at 2 threads
    EXPECT_EQ(g.step(95, t), 4);           // enough steps and the interval: try 4
    EXPECT_EQ(g.probing(), true);
    t += 4100;
    EXPECT_EQ(g.step(4100, t), 2);         // still paused: back to 2
    EXPECT_EQ(g.fallen(), true);
    EXPECT_EQ(run(g, 10, 95, t), 2);       // and no new probe for a while
}

static void a_good_probe_returns_to_the_plan() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);
    run(g, StallGuard::MIN_FALLBACK_SAMPLES - 1, 95, t);
    t += StallGuard::PROBE_EVERY_MS;
    EXPECT_EQ(g.step(95, t), 4);           // probe
    t += 45;
    EXPECT_EQ(g.step(45, t), 4);           // the cores are back
    EXPECT_EQ(g.fallen(), false);
    EXPECT_EQ(run(g, 50, 43, t), 4);
}

static void a_plan_of_two_or_fewer_is_left_alone() {
    StallGuard g;
    int64_t t = 0;
    EXPECT_EQ(g.plan(2), 2);
    EXPECT_EQ(run(g, 5, 4100, t), 2);
    EXPECT_EQ(g.falls(), 0);
}

static void disabled_never_changes_the_count() {
    StallGuard g(false);
    int64_t t = 0;
    g.plan(4);
    EXPECT_EQ(run(g, 10, 4100, t), 4);
    EXPECT_EQ(g.falls(), 0);
}

static void a_thermal_round_trip_keeps_the_fallback() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);
    EXPECT_EQ(g.plan(4), 2);   // the next reply's plan: still paused, so 2
    EXPECT_EQ(g.plan(2), 2);   // a lower plan is obeyed
    EXPECT_EQ(g.fallen(), true);  // without ending the fallback
    EXPECT_EQ(g.plan(4), 2);   // so planning 4 again does not hand back the stalled count
    EXPECT_EQ(g.plan(6), 2);
}

// Review: two legitimately slow steps with no history, from a big model on a slow phone, fall back;
// the guard must come back, since the planned count is no slower than the fallback there.
static void cold_start_outliers_do_not_lock_in() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    EXPECT_EQ(run(g, 2, 1600, t), 2);              // falls back on the absolute rule
    run(g, 15, 2000, t);                            // 2 threads, slower here, for 30 s
    EXPECT_EQ(g.probing(), true);                   // a try is in flight
    t += 1600;
    EXPECT_EQ(g.step(1600, t), 4);                  // 4 threads are not slower: back
    EXPECT_EQ(g.fallen(), false);
    EXPECT_EQ(run(g, 50, 1600, t), 4);              // and the new baseline holds
    EXPECT_EQ(g.falls(), 1);
}

// Review: a lasting change in the work, 40 ms steps becoming 400 ms, is not a pause.
static void a_workload_jump_costs_one_interval_at_most() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 40, t);                              // t = 2000
    EXPECT_EQ(run(g, 2, 400, t), 2);                // looks like a stall: falls back at t = 2800
    run(g, 50, 600, t);                             // 30 s at 2 threads, a try now due
    EXPECT_EQ(g.probing(), true);
    t += 400;
    EXPECT_EQ(g.step(400, t), 4);                   // the try at 400 ms is not slower: back
    EXPECT_EQ(g.fallen(), false);
    EXPECT_EQ(run(g, 50, 400, t), 4);               // and 400 ms is the new normal
    EXPECT_EQ(g.falls(), 1);
}

// Second review: one slow step just after falling back must not let a still-stalled try pass.
static void one_slow_fallback_step_does_not_pass_a_stalled_try() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);                             // falls back
    run(g, 1, 2000, t);                             // a transient slow step at 2 threads
    t += StallGuard::PROBE_EVERY_MS;                // an idle stretch
    run(g, 3, 95, t);                               // healthy fallback steps; the try is due
    EXPECT_EQ(g.probing(), true);
    t += 4100;
    EXPECT_EQ(g.step(4100, t), 2);                  // still paused: the try fails
    EXPECT_EQ(g.fallen(), true);
}

static void a_try_waits_for_enough_fallback_steps() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);                             // falls back
    t += StallGuard::PROBE_EVERY_MS;                // long past the interval
    EXPECT_EQ(run(g, StallGuard::MIN_FALLBACK_SAMPLES - 1, 95, t), 2);  // too few steps for a try
    EXPECT_EQ(g.probing(), false);
    EXPECT_EQ(run(g, 1, 95, t), 4);                 // the fourth makes it due
}

static void an_abandoned_try_waits_another_interval() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);
    run(g, StallGuard::MIN_FALLBACK_SAMPLES - 1, 95, t);
    t += StallGuard::PROBE_EVERY_MS;
    EXPECT_EQ(g.step(95, t), 4);                    // a try is due
    g.abandon(t);                                   // and its decode failed
    EXPECT_EQ(g.probing(), false);
    EXPECT_EQ(g.active(), 2);
    EXPECT_EQ(run(g, 10, 95, t), 2);                // no new try at once
}

int main() {
    normal_steps_keep_the_plan();
    one_hiccup_is_not_a_pause();
    a_pause_mid_reply_falls_back_after_two_slow_steps();
    a_pause_from_the_first_step_is_caught_without_history();
    a_failed_probe_waits_another_interval();
    a_good_probe_returns_to_the_plan();
    a_plan_of_two_or_fewer_is_left_alone();
    disabled_never_changes_the_count();
    a_thermal_round_trip_keeps_the_fallback();
    cold_start_outliers_do_not_lock_in();
    a_workload_jump_costs_one_interval_at_most();
    one_slow_fallback_step_does_not_pass_a_stalled_try();
    a_try_waits_for_enough_fallback_steps();
    an_abandoned_try_waits_another_interval();
    if (failures) {
        std::fprintf(stderr, "%d failed\n", failures);
        return EXIT_FAILURE;
    }
    std::printf("stall_guard_test: all passed\n");
    return EXIT_SUCCESS;
}
