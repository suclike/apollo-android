package com.apollographql.android.impl.util;

import android.support.annotation.NonNull;

import com.apollographql.android.ApolloCall;
import com.apollographql.android.api.graphql.Operation;
import com.apollographql.android.api.graphql.Response;

public final class ResponseRunnable<T extends Operation.Data> implements Runnable {

  private final Response<T> response;
  private final ApolloCall.Callback<T> callback;

  public ResponseRunnable(@NonNull Response<T> response, @NonNull ApolloCall.Callback<T> callback) {
    this.response = response;
    this.callback = callback;
  }

  @Override public void run() {
    callback.onResponse(response);
  }
}
