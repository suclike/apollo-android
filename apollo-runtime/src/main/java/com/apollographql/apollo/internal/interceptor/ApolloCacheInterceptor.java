package com.apollographql.apollo.internal.interceptor;

import com.apollographql.apollo.CustomTypeAdapter;
import com.apollographql.apollo.api.Operation;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.api.ResponseFieldMapper;
import com.apollographql.apollo.api.ScalarType;
import com.apollographql.apollo.api.internal.Optional;
import com.apollographql.apollo.cache.CacheHeaders;
import com.apollographql.apollo.cache.normalized.ApolloStore;
import com.apollographql.apollo.cache.normalized.CacheControl;
import com.apollographql.apollo.cache.normalized.Record;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.interceptor.ApolloInterceptor;
import com.apollographql.apollo.interceptor.ApolloInterceptorChain;
import com.apollographql.apollo.internal.cache.normalized.ResponseNormalizer;
import com.apollographql.apollo.internal.cache.normalized.Transaction;
import com.apollographql.apollo.internal.cache.normalized.WriteableStore;
import com.apollographql.apollo.internal.util.ApolloLogger;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.apollographql.apollo.api.internal.Utils.checkNotNull;

/**
 * ApolloCacheInterceptor is a concrete {@link ApolloInterceptor} responsible for serving requests from the normalized
 * cache. It takes the following actions based on the {@link CacheControl} set:
 *
 * <ol> <li> <b>CACHE_ONLY</b>: First tries to get the data from the normalized cache. If the data doesn't exist or
 * there was an error inflating the models, it returns the
 * {@link com.apollographql.apollo.interceptor.ApolloInterceptor.InterceptorResponse}
 * with the GraphQL {@link Operation} object wrapped inside. </li>
 *
 * <li><b>CACHE_FIRST</b>: First tries to get the data from the normalized cache. If the data doesn't exist or there was
 * an error inflating the models, it then makes a network request.</li>
 *
 * <li><b>NETWORK_FIRST</b>: First tries to get the data from the network. If there was an error getting data from the
 * network, it tries to get it from the normalized cache. If it is not present in the cache, then it rethrows the
 * network exception.</li>
 *
 * <li><b>NETWORK_ONLY</b>: First tries to get the data from the network. If the network request fails, it throws an
 * exception.</li>
 *
 * </ol>
 */
public final class ApolloCacheInterceptor implements ApolloInterceptor {
  private final ApolloStore apolloStore;
  private final CacheControl cacheControl;
  private final CacheHeaders cacheHeaders;
  private final ResponseFieldMapper responseFieldMapper;
  private final Map<ScalarType, CustomTypeAdapter> customTypeAdapters;
  private final ExecutorService dispatcher;
  private final ApolloLogger logger;

  public ApolloCacheInterceptor(@Nonnull ApolloStore apolloStore, @Nonnull CacheControl cacheControl,
      @Nonnull CacheHeaders cacheHeaders,
      @Nonnull ResponseFieldMapper responseFieldMapper,
      @Nonnull Map<ScalarType, CustomTypeAdapter> customTypeAdapters,
      @Nonnull ExecutorService dispatcher, @Nonnull ApolloLogger logger) {
    this.apolloStore = checkNotNull(apolloStore, "cache == null");
    this.cacheControl = checkNotNull(cacheControl, "cacheControl == null");
    this.cacheHeaders = checkNotNull(cacheHeaders, "cacheHeaders == null");
    this.responseFieldMapper = checkNotNull(responseFieldMapper, "responseFieldMapper == null");
    this.customTypeAdapters = checkNotNull(customTypeAdapters, "customTypeAdapters == null");
    this.dispatcher = checkNotNull(dispatcher, "dispatcher == null");
    this.logger = checkNotNull(logger, "logger == null");
  }

  @Nonnull @Override public InterceptorResponse intercept(Operation operation, ApolloInterceptorChain chain)
      throws ApolloException {
    InterceptorResponse cachedResponse = resolveCacheFirstResponse(operation);
    if (cachedResponse != null) {
      return cachedResponse;
    }

    InterceptorResponse networkResponse;
    try {
      networkResponse = chain.proceed();
    } catch (Exception e) {
      InterceptorResponse networkFirstCacheResponse = resolveNetworkFirstCacheResponse(operation);
      if (networkFirstCacheResponse != null) {
        logger.d(e, "Failed to fetch network response for operation %s, return cached one", operation);
        return networkFirstCacheResponse;
      }
      throw e;
    }
    return handleNetworkResponse(operation, networkResponse);
  }

  @Override
  public void interceptAsync(@Nonnull final Operation operation, @Nonnull final ApolloInterceptorChain chain,
      @Nonnull final ExecutorService dispatcher, @Nonnull final CallBack callBack) {
    dispatcher.execute(new Runnable() {
      @Override public void run() {
        //Imperative strategy
        final InterceptorResponse cachedResponse = resolveCacheFirstResponse(operation);
        if (cachedResponse != null) {
          callBack.onResponse(cachedResponse);
          return;
        }

        chain.proceedAsync(dispatcher, new CallBack() {
          @Override public void onResponse(@Nonnull InterceptorResponse response) {
            callBack.onResponse(handleNetworkResponse(operation, response));
          }

          @Override public void onFailure(@Nonnull ApolloException e) {
            InterceptorResponse response = resolveNetworkFirstCacheResponse(operation);
            if (response != null) {
              logger.d(e, "Failed to fetch network response for operation %s, return cached one", operation);
              callBack.onResponse(response);
            } else {
              callBack.onFailure(e);
            }
          }
        });
      }
    });
  }

  @Override public void dispose() {
    //no op
  }

  private InterceptorResponse resolveCacheFirstResponse(Operation operation) {
    if (cacheControl == CacheControl.CACHE_ONLY || cacheControl == CacheControl.CACHE_FIRST) {
      ResponseNormalizer<Record> responseNormalizer = apolloStore.cacheResponseNormalizer();

      Response cachedResponse = apolloStore.read(operation, responseFieldMapper, responseNormalizer, cacheHeaders);
      if (cachedResponse.data() != null) {
        logger.d("Cache HIT for operation %s", operation);
      }

      if (cacheControl == CacheControl.CACHE_ONLY || cachedResponse.data() != null) {
        return new InterceptorResponse(null, cachedResponse, responseNormalizer.records());
      }
    }
    logger.d("Cache MISS for operation %s", operation);
    return null;
  }

  private InterceptorResponse handleNetworkResponse(Operation operation, InterceptorResponse networkResponse) {
    boolean networkFailed = (!networkResponse.httpResponse.isPresent()
        || !networkResponse.httpResponse.get().isSuccessful());
    if (networkFailed && cacheControl != CacheControl.NETWORK_ONLY) {
      ResponseNormalizer<Record> responseNormalizer = apolloStore.cacheResponseNormalizer();
      Response cachedResponse = apolloStore.read(operation, responseFieldMapper, responseNormalizer, cacheHeaders);
      if (cachedResponse.data() != null) {
        logger.d("Cache HIT for operation %s", operation);
        return new InterceptorResponse(networkResponse.httpResponse.get(), cachedResponse,
            responseNormalizer.records());
      } else {
        logger.d("Cache MISS for operation %s", operation);
      }
    }

    if (!networkFailed) {
      cacheResponse(networkResponse);
    }

    return networkResponse;
  }

  private void cacheResponse(final InterceptorResponse networkResponse) {
    final Optional<Collection<Record>> records = networkResponse.cacheRecords;
    if (!records.isPresent()) {
      return;
    }

    final Set<String> changedKeys;
    try {
      changedKeys = apolloStore.writeTransaction(new Transaction<WriteableStore, Set<String>>() {
        @Nullable @Override public Set<String> execute(WriteableStore cache) {
          return cache.merge(records.get(), cacheHeaders);
        }
      });
    } catch (Exception e) {
      logger.e("Failed to cache operation response", e);
      return;
    }

    dispatcher.execute(new Runnable() {
      @Override public void run() {
        try {
          apolloStore.publish(changedKeys);
        } catch (Exception e) {
          logger.e("Failed to publish cache changes", e);
        }
      }
    });
  }

  private InterceptorResponse resolveNetworkFirstCacheResponse(Operation operation) {
    if (cacheControl == CacheControl.NETWORK_FIRST) {
      ResponseNormalizer<Record> responseNormalizer = apolloStore.cacheResponseNormalizer();
      Response cachedResponse = apolloStore.read(operation, responseFieldMapper, responseNormalizer, cacheHeaders);
      if (cachedResponse.data() != null) {
        logger.d("Cache HIT for operation %s", operation);
        return new InterceptorResponse(null, cachedResponse, responseNormalizer.records());
      }
    }
    logger.d("Cache MISS for operation %s", operation);
    return null;
  }
}
