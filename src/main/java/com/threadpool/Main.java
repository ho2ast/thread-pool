package com.threadpool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Main {
  public static void main(String[] args) throws IOException {
    Runnable runnable = () -> {
      for (int i = 0; i < 10; i++) {
        System.out.println(i);
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    };


    Thread thread = new Thread(runnable);
    thread.start();

    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    String s = br.readLine();
    System.out.println(s + "++++++");
  }
}