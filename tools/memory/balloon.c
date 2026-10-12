/*
 * Memory pressure for MemoryPressureBenchmark: holds a given number of megabytes and keeps
 * using them, as an app in active use would.
 *
 *   balloon <megabytes> [touch-interval-seconds]
 *
 * The pages are filled with random bytes, because zram compresses what it swaps and a page
 * of zeros costs it almost nothing: a balloon of zeros would make no pressure at all. Every
 * interval it writes to one byte of every page, so its pages stay hot and the kernel has to
 * find the room somewhere else, which is the model. It reports how much of itself is
 * resident each round, so a run can see when the kernel started taking from it instead.
 */
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <time.h>
#include <unistd.h>

static uint64_t state = 0x9E3779B97F4A7C15ull;

static uint64_t next(void) {
    state ^= state << 13;
    state ^= state >> 7;
    state ^= state << 17;
    return state;
}

static long resident_kb(void) {
    FILE * f = fopen("/proc/self/status", "r");
    char line[256];
    long kb = -1;
    while (f && fgets(line, sizeof line, f)) {
        if (sscanf(line, "VmRSS: %ld kB", &kb) == 1) break;
    }
    if (f) fclose(f);
    return kb;
}

int main(int argc, char ** argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: balloon <megabytes> [touch-interval-seconds]\n");
        return 2;
    }
    const size_t megabytes = strtoul(argv[1], NULL, 10);
    const unsigned interval = argc > 2 ? (unsigned) strtoul(argv[2], NULL, 10) : 2;
    const size_t bytes = megabytes << 20;
    const long page = sysconf(_SC_PAGESIZE);
    uint8_t * memory = mmap(NULL, bytes, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (memory == MAP_FAILED) {
        perror("mmap");
        return 1;
    }
    // Progress every gigabyte, so a fill the kernel slows down is visible in the log.
    for (size_t offset = 0; offset < bytes; offset += 8) {
        *(uint64_t *) (memory + offset) = next();
        if ((offset & ((1u << 30) - 1)) == 0 && offset) {
            printf("filled %zu MB rss %ld MB\n", offset >> 20, resident_kb() >> 10);
            fflush(stdout);
        }
    }
    printf("ready %zu MB rss %ld MB\n", megabytes, resident_kb() >> 10);
    fflush(stdout);
    for (unsigned round = 0;; round++) {
        struct timespec start, end;
        clock_gettime(CLOCK_MONOTONIC, &start);
        for (size_t offset = 0; offset < bytes; offset += (size_t) page) memory[offset] ^= (uint8_t) next();
        clock_gettime(CLOCK_MONOTONIC, &end);
        const double ms = (end.tv_sec - start.tv_sec) * 1e3 + (end.tv_nsec - start.tv_nsec) / 1e6;
        printf("round %u touch %.0f ms rss %ld MB\n", round, ms, resident_kb() >> 10);
        fflush(stdout);
        sleep(interval);
    }
}
