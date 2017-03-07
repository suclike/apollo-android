package com.apollographql.android.impl.util;


import com.apollographql.android.ApolloCall;
import com.apollographql.android.api.graphql.Operation;

import javax.annotation.Nonnull;

public class ExceptionRunnable<T extends Operation.Data> implements Runnable {

  @Nonnull
  private final Exception exception;
  @Nonnull
  private final ApolloCall.Callback<T> callback;

  public ExceptionRunnable(@Nonnull ApolloCall.Callback<T> callback, @Nonnull Exception exception) {
    this.callback = callback;
    this.exception = exception;
  }

  @Override public void run() {
    callback.onFailure(exception);
  }
}
