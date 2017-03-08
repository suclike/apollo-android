package com.apollographql.android.impl.util;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import java.util.concurrent.Executor;


public class AndroidExecutor implements Executor {

  @NonNull
  private final Handler handler;

  public AndroidExecutor() {
    handler = new Handler(Looper.getMainLooper());
  }

  @Override public void execute(Runnable command) {
    handler.post(command);
  }

  public static Executor create() {
    return new AndroidExecutor();
  }
}
