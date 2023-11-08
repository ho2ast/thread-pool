package com.threadpool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThreadPool implements Executor {

  private final BlockingQueue<Runnable> queue = new LinkedTransferQueue<>();
  private final Thread[] threads;
  private final AtomicBoolean started = new AtomicBoolean();
//  volatile 무조건 메모리에서 가져와라
//  volatile 아니면 여러 코어 중 한 코어에서 호출하여 shutdown 상태를 변경한 경우 다른 코어에서는 캐시된 상태를 가져오므로 동시성 문제가 발생 할 수 있다.
  private volatile boolean shutdown;

  /**
   * @param numThreads 필요한 만큼 스레드를 만들기 위한 스레드 개수
   */
  public ThreadPool(int numThreads) {
    threads = new Thread[numThreads];
    for (int i = 0; i < numThreads; i++) {
      threads[i] = new Thread(() -> {
        // 큐가 빌때까지 기다린다
          while (!shutdown || !queue.isEmpty()) {
            Runnable task = null;

            try {
              task = queue.take();
            } catch (InterruptedException e) {
            }

            if (task != null) {
              try {
                task.run();
              }  catch (Throwable t) {
                // java 스펙상 throw 가 될 수 없지만 가능하다..
                if (!(t instanceof InterruptedException)) {
                  System.err.println("Unexpected exception: ");
                  t.printStackTrace();
                }
              }
            }
          }

          System.err.println("Shutting thread '" + Thread.currentThread().getName() + '\'');
      });
    }
  }


  @Override
  public void execute(Runnable command) {
    // 동시에 스레드가 호출 될 경우를 대비해서 Atomic Boolean을 사용한다.
    if (started.compareAndSet(false, true)) {
      for (Thread thread : threads) {
        thread.start();
      }
    }

    if (shutdown) {
      throw new RejectedExecutionException();
    }

    queue.add(command);
  }

  /**
   * Thread를 정지하는 기능
   */
  public void shutdown() {
    // InterruptedException 일 때 정상적으로 중지하는 것으로 개발할 수 도 있지 그렇지 않은 경우가 많으므로 플래그를 두고 처리
    shutdown = true;

    for (Thread thread : threads) {
      // interrupt 를 걸면 예외 발생
      thread.interrupt();
    }

    for (Thread thread : threads) {
      for (;;) {
        try {
          thread.join();
        } catch (InterruptedException e) {
          // Do not propagate to prevent incomplete shutdown.
        }
        if (!thread.isAlive()) {
          break;
        }
        thread.interrupt();
      }
    }
  }
}
