package com.practice3.loadgen;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed-size reservoir sampling for latency values.
 * Stores up to N samples; after that keeps a random subset.
 */
public class Reservoir {

    private final long[] buf;
    private final int size;
    private final AtomicLong seen = new AtomicLong(0);

    public Reservoir(int size) {
        this.size = size;
        this.buf = new long[size];
    }

    public void record(long v) {
        long i = seen.getAndIncrement();
        if (i < size) {
            buf[(int) i] = v;
            return;
        }
        long j = ThreadLocalRandom.current().nextLong(i + 1);
        if (j < size) {
            buf[(int) j] = v;
        }
    }

    public long[] snapshot() {
        int n = (int) Math.min(seen.get(), size);
        long[] out = Arrays.copyOf(buf, n);
        Arrays.sort(out);
        return out;
    }
}

