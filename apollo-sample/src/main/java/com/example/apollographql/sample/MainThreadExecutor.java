package com.example.apollographql.sample;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import java.util.concurrent.Executor;


public class MainThreadExecutor implements Executor {

  @NonNull
  private final Handler handler;

  public MainThreadExecutor() {
    handler = new Handler(Looper.getMainLooper());
  }

  @Override public void execute(Runnable command) {
    handler.post(command);
  }
}
