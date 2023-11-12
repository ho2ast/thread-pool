package com.threadpool;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadPool implements Executor {

  private static final Runnable SHUTDOWN_TASK = () -> {
  };
  private static final Thread[] EMPTY_THREADS_ARRAY = new Thread[0];
  private final int maxNumThread;
  private final BlockingQueue<Runnable> queue = new LinkedTransferQueue<>();
  private final AtomicBoolean shutdown = new AtomicBoolean();
  private final Set<Thread> threads = new HashSet<>();
  private final Lock threadsLock = new ReentrantLock();
  //  volatile 무조건 메모리에서 가져와라
//  volatile 아니면 여러 코어 중 한 코어에서 호출하여 shutdown 상태를 변경한 경우 다른 코어에서는 캐시된 상태를 가져오므로 동시성 문제가 발생 할 수 있다.
  private final AtomicInteger numThreads = new AtomicInteger();
  private final AtomicInteger numActiveThreads = new AtomicInteger();

  /**
   * @param maxNumThread 필요한 만큼 스레드를 만들기 위한 스레드 개수
   */
  public ThreadPool(int maxNumThread) {
    this.maxNumThread = maxNumThread;
  }


  @Override
  public void execute(Runnable command) {
    if (shutdown.get()) {
      throw new RejectedExecutionException();
    }

    queue.add(command);
    aadThreadIfNecessary();

    if (shutdown.get()) {
      queue.remove(command);
      throw new RejectedExecutionException();
    }
  }

  private void aadThreadIfNecessary() {
    // 동시성 이슈 - 남는 스레드가 없어 1개의 스레드만 추가하면 되는데 모든 스레드에서 스레드 추가를 호출하는 문제

    if (needsMoreThreads()) {
      threadsLock.lock();
      Thread newThread = null;
      try {
        if (needsMoreThreads()) {
          newThread = newThread();
        }
      } finally {
        threadsLock.unlock();
      }
      if (newThread != null) {
        newThread.start();
      }
    }
  }

  private boolean needsMoreThreads() {
    final int numActiveThreads = this.numActiveThreads.get();
    return numActiveThreads < maxNumThread && numActiveThreads >= numThreads.get();
  }

  private Thread newThread() {
    numThreads.incrementAndGet();
    numActiveThreads.incrementAndGet();

    final Thread thread = new Thread(() -> {
      boolean isActive = true;
      try {
        for (; ; ) {
          try {
            Runnable task = queue.poll();
            if (task == null) {
              if (isActive) {
                isActive = false;
                numActiveThreads.decrementAndGet();
              }
              task = queue.take();
              isActive = true;
              numActiveThreads.incrementAndGet();
            } else {
              if (!isActive) {
                isActive = true;
                numActiveThreads.incrementAndGet();
              }
            }

            if (task == SHUTDOWN_TASK) {
              break;
            } else {
              task.run();
            }
          } catch (Throwable t) {
            if (!(t instanceof InterruptedException)) {
              System.err.println("Unexpected exception: ");
              t.printStackTrace();

            }
          }
        }
      } finally {
        threadsLock.lock();
        try {
          threads.remove(Thread.currentThread());
        } finally {
          threadsLock.unlock();
        }
        numThreads.decrementAndGet();
      }
      System.err.println("Shutting thread '" + Thread.currentThread().getName() + '\'');
    });

    threads.add(thread);
    return thread;
  }

  /**
   * Thread를 정지하는 기능
   */
  public void shutdown() {
    if (shutdown.compareAndSet(false, true)) {
      for (int i = 0; i < maxNumThread; i++) {
        queue.add(SHUTDOWN_TASK);
      }
    }

    for (; ; ) {
      final Thread[] threads;
      threadsLock.lock();
      try {
        threads = this.threads.toArray(EMPTY_THREADS_ARRAY);
      } finally {
        threadsLock.unlock();
      }

      if (threads.length == 0) {
        break;
      }

      for (Thread thread : threads) {
        do {
          try {
            thread.join();
          } catch (InterruptedException e) {
            // Do not propagate to prevent incomplete shutdown.
          }
        } while (thread.isAlive());
      }
    }
  }
}
