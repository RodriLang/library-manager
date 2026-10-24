package com.rodrilang.librarymanager.importer.price.configuration.parser;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;

/** Lightweight per-worker instrumentation; no sampling thread or retained row data. */
final class PriceParserRuntimeMetrics {
    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private final long threadId = Thread.currentThread().getId();
    private long previousWall = System.nanoTime();
    private long previousCpu = cpuNanos();
    private long previousGcMs = gcMillis();

    private long cpuNanos() {
        if (!threads.isThreadCpuTimeSupported()) return -1;
        try {
            if (!threads.isThreadCpuTimeEnabled()) return -1;
            return threads.getThreadCpuTime(threadId);
        } catch (UnsupportedOperationException | SecurityException ex) {
            return -1;
        }
    }

    private static long gcMillis() {
        long sum = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long value = gc.getCollectionTime();
            if (value >= 0) sum += value;
        }
        return sum;
    }

    Sample sample() {
        long wall = System.nanoTime();
        long cpu = cpuNanos();
        long gc = gcMillis();
        long wallMs = Math.max(0, (wall - previousWall) / 1_000_000);
        long cpuMs = cpu < 0 || previousCpu < 0 ? -1 : Math.max(0, (cpu - previousCpu) / 1_000_000);
        long gcMs = Math.max(0, gc - previousGcMs);
        previousWall = wall;
        previousCpu = cpu;
        previousGcMs = gc;
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        long used = memory.getHeapMemoryUsage().getUsed();
        long max = memory.getHeapMemoryUsage().getMax();
        return new Sample(wallMs, cpuMs, gcMs, used / 1_048_576, max < 0 ? -1 : max / 1_048_576);
    }

    record Sample(long wallMs, long threadCpuMs, long jvmGcMs, long heapUsedMiB, long heapMaxMiB) { }
}
