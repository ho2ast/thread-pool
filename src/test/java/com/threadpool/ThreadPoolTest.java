package com.threadpool;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

class ThreadPoolTest {

  @Test
  void submittedTaskAreExecuted() {
    final ThreadPool executor = new ThreadPool(100);
    final int numTasks = 100;
    final CountDownLatch latch = new CountDownLatch(numTasks);

    try {
      for (int i = 0; i < 100; i++) {
        final int finalI = i;
        executor.execute(() -> {
          System.err.println("Thread '" + Thread.currentThread().getName() + "' executes a task " + finalI);
          try {
            Thread.sleep(1);
          } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
          }
          latch.countDown();
        });
      }

//      latch.await();
    } finally {
      executor.shutdown();
    }
  }
}
