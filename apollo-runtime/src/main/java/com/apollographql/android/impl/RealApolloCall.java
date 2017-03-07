package com.apollographql.android.impl;

import com.apollographql.android.ApolloCall;
import com.apollographql.android.api.graphql.Operation;
import com.apollographql.android.api.graphql.Response;
import com.apollographql.android.cache.http.HttpCache;
import com.apollographql.android.cache.normalized.Cache;
import com.apollographql.android.cache.normalized.ResponseNormalizer;
import com.apollographql.android.impl.util.ExceptionRunnable;
import com.apollographql.android.impl.util.HttpException;
import com.apollographql.android.impl.util.ResponseRunnable;
import com.squareup.moshi.Moshi;

import java.io.IOException;
import java.util.concurrent.Executor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.internal.Util;

import static com.apollographql.android.cache.http.HttpCache.CacheControl;

final class RealApolloCall<T extends Operation.Data> extends BaseApolloCall implements ApolloCall<T> {
  volatile Call httpCall;
  private final Cache cache;
  private final HttpCache httpCache;
  private final ResponseBodyConverter responseBodyConverter;
  private boolean executed;
  private CacheControl cacheControl = CacheControl.DEFAULT;
  @Nullable
  private final Executor executor;

  RealApolloCall(Operation operation, HttpUrl serverUrl, Call.Factory httpCallFactory, HttpCache httpCache, Moshi moshi,
      ResponseBodyConverter responseBodyConverter, Cache cache, Executor executor) {
    super(operation, serverUrl, httpCallFactory, moshi);
    this.cache = cache;
    this.httpCache = httpCache;
    this.responseBodyConverter = responseBodyConverter;
    this.executor = executor;
  }

  private RealApolloCall(Operation operation, HttpUrl serverUrl, Call.Factory httpCallFactory, HttpCache httpCache,
      Moshi moshi, ResponseBodyConverter responseBodyConverter, Cache cache, CacheControl cacheControl, Executor executor) {
    super(operation, serverUrl, httpCallFactory, moshi);
    this.httpCache = httpCache;
    this.responseBodyConverter = responseBodyConverter;
    this.cache = cache;
    this.cacheControl = cacheControl;
    this.executor = executor;
  }

  @Nonnull @Override public Response<T> execute() throws IOException {
    synchronized (this) {
      if (executed) throw new IllegalStateException("Already Executed");
      executed = true;
    }
    httpCall = prepareHttpCall(cacheControl, false);
    return handleResponse(httpCall.execute());
  }

  @Nonnull @Override public ApolloCall<T> enqueue(@Nullable final Callback<T> callback) {
    synchronized (this) {
      if (executed) throw new IllegalStateException("Already Executed");
      executed = true;
    }

    try {
      httpCall = prepareHttpCall(cacheControl, false);
    } catch (Exception e) {
      if (callback != null) {
        callback.onFailure(e);
      }
      return this;
    }

    httpCall.enqueue(new okhttp3.Callback() {
      @Override public void onFailure(Call call, IOException e) {
        handleCallbackException(e, callback);
      }

      @Override public void onResponse(Call call, okhttp3.Response httpResponse) throws IOException {
        try {
          Response<T> response = handleResponse(httpResponse);
          handleCallbackResponse(response, callback);
        } catch (Exception e) {
          handleCallbackException(e, callback);
        }
      }
    });
    return this;
  }

  void handleCallbackResponse(@Nonnull Response<T> response, @Nullable final Callback<T> callback) {
    if (callback == null) {
      return;
    } else if (executor == null) {
      callback.onResponse(response);
    } else {
      executor.execute(new ResponseRunnable<>(response, callback));
    }
  }

  void handleCallbackException(@Nonnull final Exception e, @Nullable final Callback<T> callback) {
    if (callback == null) {
      return;
    } else if (executor == null) {
      callback.onFailure(e);
    } else {
      executor.execute(new ExceptionRunnable<T>(callback, e));
    }
  }

  @Nonnull @Override public ApolloCall<T> network() {
    synchronized (this) {
      if (executed) throw new IllegalStateException("Already Executed");
    }
    cacheControl = CacheControl.NETWORK_ONLY;
    return this;
  }

  @Nonnull @Override public ApolloCall<T> cache() {
    synchronized (this) {
      if (executed) throw new IllegalStateException("Already Executed");
    }
    cacheControl = CacheControl.CACHE_ONLY;
    return this;
  }

  @Nonnull @Override public ApolloCall<T> networkBeforeStale() {
    synchronized (this) {
      if (executed) throw new IllegalStateException("Already Executed");
    }
    cacheControl = CacheControl.NETWORK_BEFORE_STALE;
    return this;
  }

  @Nonnull @Override public ApolloCall<T> expireAfterRead() {
    synchronized (this) {
      if (executed) throw new IllegalStateException("Already Executed");
    }
    cacheControl = CacheControl.EXPIRE_AFTER_READ;
    return this;
  }

  @Override public void cancel() {
    Call call = httpCall;
    if (call != null) {
      call.cancel();
    }
  }

  @Override @Nonnull public ApolloCall<T> clone() {
    return new RealApolloCall<>(operation, serverUrl, httpCallFactory, httpCache, moshi, responseBodyConverter, cache,
        cacheControl, executor);
  }

  private <T extends Operation.Data> Response<T> handleResponse(okhttp3.Response response) throws IOException {
    String cacheKey = response.request().header(HttpCache.CACHE_KEY_HEADER);
    if (response.isSuccessful()) {
      try {
        ResponseNormalizer normalizer = cache.responseNormalizer();
        Response<T> convertedResponse = responseBodyConverter.convert(response.body(), normalizer);
        cache.cacheStore().merge(normalizer.records());
        return convertedResponse;
      } catch (Exception rethrown) {
        Util.closeQuietly(response);
        if (httpCache != null) {
          httpCache.removeQuietly(cacheKey);
        }
        throw rethrown;
      }
    } else {
      Util.closeQuietly(response);
      throw new HttpException(response);
    }
  }
}
