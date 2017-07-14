package com.apollographql.apollo.test.espresso;


import android.support.test.espresso.IdlingResource;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.OperationName;
import com.apollographql.apollo.api.Query;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.api.ResponseFieldMapper;
import com.apollographql.apollo.api.ResponseReader;
import com.apollographql.apollo.exception.ApolloException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static com.google.common.truth.Truth.assertThat;

public class ApolloIdlingResourceTest {

  private ApolloIdlingResource idlingResource;
  private ApolloClient apolloClient;
  private MockWebServer server;
  private OkHttpClient okHttpClient;

  private static final long TIME_OUT_SECONDS = 3;
  private static final String IDLING_RESOURCE_NAME = "apolloIdlingResource";

  private static final Query EMPTY_QUERY = new Query() {
    @Override public String queryDocument() {
      return "";
    }

    @Override public Variables variables() {
      return EMPTY_VARIABLES;
    }

    @Override public ResponseFieldMapper<Data> responseFieldMapper() {
      return new ResponseFieldMapper<Data>() {
        @Override public Data map(ResponseReader responseReader) {
          return null;
        }
      };
    }

    @Override public Object wrapData(Data data) {
      return data;
    }

    @Nonnull @Override public OperationName name() {
      return null;
    }

    @Nonnull @Override public String operationId() {
      return "";
    }
  };

  @Before
  public void setup() {
    server = new MockWebServer();
    okHttpClient = new OkHttpClient.Builder()
        .build();
  }

  @After
  public void tearDown() {
    idlingResource = null;
    try {
      server.shutdown();
    } catch (IOException ignored) {

    }
  }

  @Test
  public void onNullNamePassed_NullPointerExceptionIsThrown() {

    apolloClient = ApolloClient.builder()
        .okHttpClient(okHttpClient)
        .serverUrl(server.url("/"))
        .build();

    try {
      idlingResource = ApolloIdlingResource.create(null, apolloClient);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(NullPointerException.class);
      assertThat(e.getMessage()).isEqualTo("name == null");
    }
  }

  @Test
  public void onNullApolloClientPassed_NullPointerExceptionIsThrown() {
    try {
      idlingResource = ApolloIdlingResource.create(IDLING_RESOURCE_NAME, null);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(NullPointerException.class);
      assertThat(e.getMessage()).isEqualTo("apolloClient == null");
    }
  }

  @Test
  public void checkValidIdlingResourceNameIsRegistered() {

    apolloClient = ApolloClient.builder()
        .okHttpClient(okHttpClient)
        .serverUrl(server.url("/"))
        .build();

    idlingResource = ApolloIdlingResource.create(IDLING_RESOURCE_NAME, apolloClient);

    assertThat(idlingResource.getName()).isEqualTo(IDLING_RESOURCE_NAME);
  }

  @Test
  public void checkIsIdleNow_whenCallIsQueued() throws IOException, TimeoutException, InterruptedException {
    server.enqueue(mockResponse());

    final CountDownLatch latch = new CountDownLatch(1);

    ExecutorService executorService = Executors.newFixedThreadPool(1);

    apolloClient = ApolloClient.builder()
        .okHttpClient(okHttpClient)
        .dispatcher(executorService)
        .serverUrl(server.url("/"))
        .build();

    idlingResource = ApolloIdlingResource.create(IDLING_RESOURCE_NAME, apolloClient);
    assertThat(idlingResource.isIdleNow()).isTrue();

    apolloClient.query(EMPTY_QUERY).enqueue(new ApolloCall.Callback<Object>() {
      @Override public void onResponse(@Nonnull Response<Object> response) {
        latch.countDown();
      }

      @Override public void onFailure(@Nonnull ApolloException e) {
        throw new AssertionError("This callback can't be called.");
      }
    });
    assertThat(idlingResource.isIdleNow()).isFalse();

    latch.await(TIME_OUT_SECONDS, TimeUnit.SECONDS);

    executorService.shutdown();
    executorService.awaitTermination(TIME_OUT_SECONDS, TimeUnit.SECONDS);
    Thread.sleep(100);
    assertThat(idlingResource.isIdleNow()).isTrue();
  }

  @Test
  public void checkIdlingResourceTransition_whenCallIsQueued() throws IOException, ApolloException {
    server.enqueue(mockResponse());

    apolloClient = ApolloClient.builder()
        .okHttpClient(okHttpClient)
        .dispatcher(immediateExecutorService())
        .serverUrl(server.url("/"))
        .build();

    final AtomicInteger counter = new AtomicInteger(1);
    idlingResource = ApolloIdlingResource.create(IDLING_RESOURCE_NAME, apolloClient);

    idlingResource.registerIdleTransitionCallback(new IdlingResource.ResourceCallback() {
      @Override public void onTransitionToIdle() {
        counter.decrementAndGet();
      }
    });

    assertThat(counter.get()).isEqualTo(1);
    apolloClient.query(EMPTY_QUERY).execute();
    assertThat(counter.get()).isEqualTo(0);
  }

  private MockResponse mockResponse() throws IOException {
    return new MockResponse().setResponseCode(200).setBody(new StringBuilder()
        .append("{")
        .append("  \"errors\": [")
        .append("    {")
        .append("      \"message\": \"Cannot query field \\\"names\\\" on type \\\"Species\\\".\",")
        .append("      \"locations\": [")
        .append("        {")
        .append("          \"line\": 3,")
        .append("          \"column\": 5")
        .append("        }")
        .append("      ]")
        .append("    }")
        .append("  ]")
        .append("}")
        .toString());
  }

  private ExecutorService immediateExecutorService() {
    return new AbstractExecutorService() {
      @Override public void shutdown() {

      }

      @Override public List<Runnable> shutdownNow() {
        return null;
      }

      @Override public boolean isShutdown() {
        return false;
      }

      @Override public boolean isTerminated() {
        return false;
      }

      @Override public boolean awaitTermination(long l, TimeUnit timeUnit) throws InterruptedException {
        return false;
      }

      @Override public void execute(Runnable runnable) {
        runnable.run();
      }
    };
  }
}