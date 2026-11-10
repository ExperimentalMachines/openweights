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

// Fourth review: a try scheduled by a reply's last step must not open the next reply at the planned
// count inside the pause, even after a thermal re-plan down and back up. The engine calls abandon()
// when a reply starts.
static void a_try_left_by_the_last_reply_is_dropped_at_the_next() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);                             // falls back
    run(g, StallGuard::MIN_FALLBACK_SAMPLES - 1, 95, t);
    t += StallGuard::PROBE_EVERY_MS;
    EXPECT_EQ(g.step(95, t), 4);                    // the reply's last step schedules a try
    g.abandon(t);                                   // the next reply starts
    EXPECT_EQ(g.plan(2), 2);                        // a hot re-plan
    EXPECT_EQ(g.plan(4), 2);                        // and back: still the fallback
    EXPECT_EQ(g.fallen(), true);
}

// Fourth review: a first step that is slow but under the absolute bar must not become the
// baseline that a stall then hides under.
static void a_slow_first_step_does_not_hide_a_stall() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 1, 1000, t);                             // under 1.5 s, so not slow on its own
    EXPECT_EQ(run(g, 1, 4100, t), 4);
    EXPECT_EQ(run(g, 1, 4100, t), 2);               // the stall is still caught
}

// Fourth review: steps several times the normal one but under the stall factor must not be
// averaged into the baseline, or a stall that grows in steps is never caught.
static void a_moderate_slowdown_does_not_raise_the_bar() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    EXPECT_EQ(run(g, 200, 300, t), 4);              // 7 times normal: not a stall, not the new normal
    EXPECT_EQ(g.falls(), 0);
    EXPECT_EQ(run(g, 2, 2000, t), 2);               // 46 times the normal: caught
}

// Fourth review: steps at a plan under the fallback count are not fallback samples.
static void steps_at_one_thread_are_not_fallback_samples() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);                             // falls back
    g.plan(1);
    run(g, 20, 1000, t);                            // one thread, slow steps
    g.plan(4);
    run(g, StallGuard::MIN_FALLBACK_SAMPLES - 1, 95, t);
    t += StallGuard::PROBE_EVERY_MS;
    EXPECT_EQ(g.step(95, t), 4);                    // a try
    t += 900;
    EXPECT_EQ(g.step(900, t), 2);                   // 9 times the 2-thread steps: fails
}

// Fifth review: a stall inside the first steps is judged against the steps before it, not only
// against the absolute bar.
static void a_stall_while_learning_the_normal_step_is_caught() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 2, 43, t);
    EXPECT_EQ(run(g, 1, 1000, t), 4);
    EXPECT_EQ(run(g, 1, 1000, t), 2);               // 23 times the steps before: falls back
}

// Fifth review: one unusually fast first step must not become the bar later steps are held to.
static void a_fast_first_step_does_not_set_the_bar() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 1, 10, t);
    run(g, 3, 150, t);
    EXPECT_EQ(run(g, 100, 300, t), 4);              // steady 300 ms steps are the normal ones
    EXPECT_EQ(g.falls(), 0);
}

// Sixth review: one fast first step must not make the next two ordinary steps a stall either.
static void a_fast_first_step_does_not_make_the_next_two_a_stall() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 1, 10, t);
    EXPECT_EQ(run(g, 3, 300, t), 4);
    EXPECT_EQ(g.falls(), 0);
}

// Seventh review: a pause adds seconds to each step (4 to 9 s in r27, r34 and r38). A model whose
// normal step is already a second shows the pause as 4 to 6 times slower, under the factor; the
// added seconds give it away.
static void a_pause_on_already_slow_steps_is_caught() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 8, 1000, t);
    EXPECT_EQ(run(g, 1, 4100, t), 4);
    EXPECT_EQ(run(g, 1, 4100, t), 2);
    EXPECT_EQ(g.fallen(), true);
}

static void slow_steps_that_wander_are_not_a_pause() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 8, 1000, t);
    EXPECT_EQ(run(g, 20, 1800, t), 4);
    EXPECT_EQ(g.falls(), 0);
}

// Seventh review: on such a model a stalled try is only about three times the 2-thread step, so the
// try is also held to the added seconds.
static void a_try_in_a_pause_on_slow_steps_fails() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 8, 1000, t);
    run(g, 2, 5500, t);                             // falls back
    run(g, StallGuard::MIN_FALLBACK_SAMPLES - 1, 2000, t);
    t += StallGuard::PROBE_EVERY_MS;
    EXPECT_EQ(g.step(2000, t), 4);                  // a try
    t += 5500;
    EXPECT_EQ(g.step(5500, t), 2);                  // under 3 times 2,000 but 3.5 s over: still paused
    EXPECT_EQ(g.fallen(), true);
}

// The cores come back: the fallback's own steps get much faster (r48: about 267 ms a step in the
// pause, about 77 ms after it), so the planned count is tried at once, not up to 30 s later.
static void steps_that_speed_up_try_the_plan_at_once() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);                             // falls back
    run(g, 8, 95, t);                               // 2 threads in the pause, well inside 30 s
    EXPECT_EQ(run(g, 1, 42, t), 2);                 // one fast step is not enough
    EXPECT_EQ(run(g, 1, 42, t), 4);                 // a second in a row: try the plan now
    EXPECT_EQ(g.probing(), true);
    t += 43;
    EXPECT_EQ(g.step(43, t), 4);                    // and it runs: back
    EXPECT_EQ(g.fallen(), false);
}

static void steps_that_wander_under_the_factor_do_not_try_early() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);
    for (int i = 0; i < 120; ++i) {                 // about 10 s of steps 1.36 times apart, under 1.6
        t += i % 2 ? 95 : 70;
        EXPECT_EQ(g.step(i % 2 ? 95 : 70, t), 2);
    }
    EXPECT_EQ(g.probing(), false);
}

static void one_fast_step_in_a_pause_is_not_a_return() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);
    run(g, 8, 95, t);
    EXPECT_EQ(run(g, 1, 42, t), 2);
    EXPECT_EQ(run(g, 1, 95, t), 2);                 // the run of fast steps is broken
    EXPECT_EQ(run(g, 1, 42, t), 2);                 // so one more fast step starts it again
    EXPECT_EQ(g.probing(), false);
}

// A speed-up with the pause still on (the cores left got faster, the parked ones did not come back)
// costs one failed try; the steps after it are the new fallback level, so it must not fire again.
static void a_failed_early_try_does_not_fire_again() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);
    run(g, 8, 95, t);
    EXPECT_EQ(run(g, 2, 55, t), 4);                 // 1.7 times faster: an early try
    t += 4100;
    EXPECT_EQ(g.step(4100, t), 2);                  // still paused: it fails
    for (int i = 0; i < 200; ++i) {                 // 11 s more at the new level: no try
        t += 55;
        EXPECT_EQ(g.step(55, t), 2);
    }
}

// Review: the early try is judged against the fast steps that started it, not against the pause's
// slower ones, or a stalled step at the planned count could pass and become the new normal.
static void an_early_try_is_judged_against_the_fast_steps() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);
    run(g, 8, 2000, t);                             // a slow fallback
    EXPECT_EQ(run(g, 2, 1250, t), 4);               // 1.6 times faster: an early try
    t += 3900;
    EXPECT_EQ(g.step(3900, t), 2);                  // over 3 times the fast steps: still stalled
    EXPECT_EQ(g.fallen(), true);
}

// Review: in a pause whose 2-thread steps swing past the factor again and again, only the first
// false alarm costs a try; the scheduled ones carry on.
static void a_swinging_pause_costs_one_false_early_try() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);
    run(g, 8, 2000, t);
    int early = 0, tries = 0;
    for (int i = 0; i < 400; ++i) {                 // about 9 minutes of steps swinging 2.7 times
        const double ms = (i / 2) % 2 ? 750 : 2000;
        t += static_cast<int64_t>(ms);
        if (g.step(ms, t) == 4) {
            ++tries;
            early += g.early();
            t += 10000;
            EXPECT_EQ(g.step(10000, t), 2);         // every try stalls: the pause holds
        }
    }
    EXPECT_EQ(early, 1);
    EXPECT_EQ(tries > 10, true);                    // and the scheduled tries kept coming
}

// Review: the timer rule is the rule before the early try, with nothing else changed: after a failed
// try it tries again once the interval has passed, on the fallback steps it already had.
static void the_timer_rule_tries_again_on_the_steps_it_has() {
    StallGuard g(true, false);
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);
    EXPECT_EQ(run(g, 4, 11000, t), 4);              // four steps and 44 s at 2 threads: a try
    t += 14000;
    EXPECT_EQ(g.step(14000, t), 2);                 // 3 s over the fallback's steps: fails
    EXPECT_EQ(run(g, 2, 11000, t), 2);              // 22 s
    EXPECT_EQ(run(g, 1, 11000, t), 4);              // 33 s: the next try, on the four steps kept
}

// Review: a try dropped before it was judged (the reply ended, or its decode failed) delays the next
// scheduled try, but the steps that started an early one can start another: nothing was learned.
static void an_abandoned_early_try_can_come_again() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);
    run(g, 8, 95, t);
    EXPECT_EQ(run(g, 2, 42, t), 4);                 // an early try
    g.abandon(t);                                   // the reply ends before it is judged
    EXPECT_EQ(g.active(), 2);
    EXPECT_EQ(run(g, 1, 42, t), 2);
    EXPECT_EQ(run(g, 1, 42, t), 4);                 // the next reply's fast steps: tried again
}

// Review: a scheduled try is judged against the fallback's steps as they stand when it is judged, as
// the rule before did, even if a re-plan to 2 while it waited added slower ones.
static void a_scheduled_try_is_judged_on_the_steps_at_judgement() {
    StallGuard g(true, false);
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);
    run(g, StallGuard::MIN_FALLBACK_SAMPLES - 1, 95, t);
    t += StallGuard::PROBE_EVERY_MS;
    EXPECT_EQ(g.step(95, t), 4);                    // a scheduled try is pending
    g.plan(2);                                      // a hot re-plan before it runs
    run(g, 8, 2000, t);                             // slower steps at 2 join the fallback's
    g.plan(4);
    t += 3900;
    EXPECT_EQ(g.step(3900, t), 4);                  // under 3 times 2,000 and 2 s over it: passes
    EXPECT_EQ(g.fallen(), false);
}

// Review: the two fast steps that start an early try are two in a row at the fallback count; steps
// taken at a lower plan between them break the run.
static void fast_steps_split_by_a_lower_plan_do_not_try_early() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);
    run(g, 8, 95, t);
    EXPECT_EQ(run(g, 1, 42, t), 2);                 // one fast step
    g.plan(2);
    run(g, 2, 95, t);                               // steps at a plan of 2
    g.plan(4);
    EXPECT_EQ(run(g, 1, 42, t), 2);                 // one more fast step is not two in a row
    EXPECT_EQ(run(g, 1, 42, t), 4);                 // the next one is
}

// Review: when the second fast step lands as a scheduled try falls due, the try is an early one,
// judged against the fast steps and logged as one.
static void a_fast_run_at_the_deadline_is_an_early_try() {
    StallGuard g;
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);                             // falls back at t = 10,350
    run(g, 8, 2000, t);                             // t = 26,350; the scheduled try is due at 40,350
    t += 12000;                                     // t = 38,350
    EXPECT_EQ(g.step(1250, t), 2);                  // one fast step
    t += 2000;                                      // t = 40,350: the second, as the try falls due
    EXPECT_EQ(g.step(1250, t), 4);
    EXPECT_EQ(g.early(), true);
    t += 3900;
    EXPECT_EQ(g.step(3900, t), 2);                  // judged against 1,250, not 2,000: still stalled
}

// The rule before this change, for comparing the two on a phone: tries on the 30 s clock only.
static void the_timer_rule_waits_for_the_interval() {
    StallGuard g(true, false);
    int64_t t = 0;
    g.plan(4);
    run(g, 50, 43, t);
    run(g, 2, 4100, t);
    run(g, 8, 95, t);
    EXPECT_EQ(run(g, 20, 42, t), 2);                // much faster steps, but no try before 30 s
    t += StallGuard::PROBE_EVERY_MS;
    EXPECT_EQ(g.step(42, t), 4);                    // the scheduled try
}

int main() {
    steps_that_speed_up_try_the_plan_at_once();
    steps_that_wander_under_the_factor_do_not_try_early();
    one_fast_step_in_a_pause_is_not_a_return();
    a_failed_early_try_does_not_fire_again();
    the_timer_rule_waits_for_the_interval();
    an_early_try_is_judged_against_the_fast_steps();
    a_swinging_pause_costs_one_false_early_try();
    the_timer_rule_tries_again_on_the_steps_it_has();
    an_abandoned_early_try_can_come_again();
    a_scheduled_try_is_judged_on_the_steps_at_judgement();
    fast_steps_split_by_a_lower_plan_do_not_try_early();
    a_fast_run_at_the_deadline_is_an_early_try();
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
    a_try_left_by_the_last_reply_is_dropped_at_the_next();
    a_slow_first_step_does_not_hide_a_stall();
    a_moderate_slowdown_does_not_raise_the_bar();
    steps_at_one_thread_are_not_fallback_samples();
    a_stall_while_learning_the_normal_step_is_caught();
    a_fast_first_step_does_not_set_the_bar();
    a_fast_first_step_does_not_make_the_next_two_a_stall();
    a_pause_on_already_slow_steps_is_caught();
    slow_steps_that_wander_are_not_a_pause();
    a_try_in_a_pause_on_slow_steps_fails();
    if (failures) {
        std::fprintf(stderr, "%d failed\n", failures);
        return EXIT_FAILURE;
    }
    std::printf("stall_guard_test: all passed\n");
    return EXIT_SUCCESS;
}
