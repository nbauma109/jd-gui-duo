/*
 * Copyright 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 */

package org.jd.gui.fixture;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class RemoteDebugLoop {
    private RemoteDebugLoop() {
    }

    /** Waits for the given time. */
    private static void pause(long millis) throws InterruptedException {
        new CountDownLatch(1).await(millis, TimeUnit.MILLISECONDS);
    }

    public static void main() throws InterruptedException {
        AtomicLong globalCounter = new AtomicLong();

        Runnable task = () -> {
            String threadName = Thread.currentThread().getName();
            int counter = 0;
            long lastTick = System.currentTimeMillis();
            String state = "starting";

            while (true) {
                counter++;
                long now = System.currentTimeMillis();
                long elapsedMillis = now - lastTick;
                state = (counter % 2 == 0) ? "even" : "odd";
                long total = globalCounter.incrementAndGet();
                String message = Instant.ofEpochMilli(now) + " [" + threadName + "] counter=" + counter + " total=" + total + " state=" + state + " elapsedMillis=" + elapsedMillis;

                System.out.println(message);
                lastTick = now;
                try {
                    pause(1000);
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        };

        Thread t1 = new Thread(task, "worker-1");
        Thread t2 = new Thread(task, "worker-2");
        Thread t3 = new Thread(task, "worker-3");

        t1.start();
        pause(333);
        t2.start();
        pause(333);
        t3.start();

        t1.join();
    }
}
