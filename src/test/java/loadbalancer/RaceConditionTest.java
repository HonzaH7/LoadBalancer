package loadbalancer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RaceConditionTest {

    private static final int THREADS = 100;
    private static final int PER_THREAD = 1000;
    private static final int EXPECTED = THREADS * PER_THREAD;   // 100_000

    static class NaiveCounter {
        int value = 0;
        void increment() { value++; }
    }

    @Test
    void atomicCounterKeepsAllIncrements() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);

        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> {
                for (int j = 0; j < PER_THREAD; j++) {
                    counter.incrementAndGet();
                }
            });
            t.start();
            workers.add(t);
        }
        for (Thread t : workers) {
            t.join();
        }

        assertEquals(EXPECTED, counter.get());
    }

    @Disabled
    @Test
    void naiveCounterLosesIncrements() throws InterruptedException {
        NaiveCounter counter = new NaiveCounter();

        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> {
                for (int j = 0; j < PER_THREAD; j++) {
                    counter.increment();          // racy int++
                }
            });
            t.start();
            workers.add(t);
        }
        for (Thread t : workers) {
            t.join();
        }
        System.out.println("Ztraceno: " + (EXPECTED - counter.value) + " z " + EXPECTED);
        assertNotEquals(EXPECTED, counter.value);
    }
}