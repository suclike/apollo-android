package com.apollographql.apollo;

import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.integration.normalizer.HeroAndFriendsNamesQuery;
import com.apollographql.apollo.integration.normalizer.type.Episode;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class SendOperationIdentifiersTest {

  @Rule public final MockWebServer server = new MockWebServer();

  @Test public void sendOperationIdsTrue() throws InterruptedException, ApolloException, IOException {
    enqueueResponse("/HeroAndFriendsNameResponse.json");
    final HeroAndFriendsNamesQuery heroAndFriendsNamesQuery = new HeroAndFriendsNamesQuery(Episode.EMPIRE);
    final String expectedId = heroAndFriendsNamesQuery.operationId();
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .addInterceptor(new Interceptor() {
          @Override public okhttp3.Response intercept(Chain chain) throws IOException {
            if (chain.request().body().toString().contains("id: " + expectedId)) {
              countDownLatch.countDown();
            }
            return chain.proceed(chain.request());
          }
        })
        .build();

    ApolloClient apolloClient = ApolloClient.builder()
        .serverUrl(server.url("/"))
        .sendOperationIdentifiers(true)
        .okHttpClient(okHttpClient)
        .build();

    apolloClient.query(heroAndFriendsNamesQuery).execute();
    countDownLatch.await(3, TimeUnit.SECONDS);
  }

  @Test public void doesNotSendOperationIdsWhenFalse() throws InterruptedException, ApolloException, IOException {
    enqueueResponse("/HeroAndFriendsNameResponse.json");
    final HeroAndFriendsNamesQuery heroAndFriendsNamesQuery = new HeroAndFriendsNamesQuery(Episode.EMPIRE);
    final String expectedQueryDocument = heroAndFriendsNamesQuery.queryDocument();
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .addInterceptor(new Interceptor() {
          @Override public okhttp3.Response intercept(Chain chain) throws IOException {
            if (chain.request().body().toString().contains("query: " + expectedQueryDocument)) {
              countDownLatch.countDown();
            }
            return chain.proceed(chain.request());
          }
        })
        .build();

    ApolloClient apolloClient = ApolloClient.builder()
        .serverUrl(server.url("/"))
        .sendOperationIdentifiers(false)
        .okHttpClient(okHttpClient)
        .build();

    apolloClient.query(heroAndFriendsNamesQuery).execute();
    countDownLatch.await(3, TimeUnit.SECONDS);
  }

  private void enqueueResponse(String fileName) throws IOException {
    server.enqueue(mockResponse(fileName));
  }

  private MockResponse mockResponse(String fileName) throws IOException {
    return new MockResponse().setChunkedBody(Utils.readFileToString(getClass(), fileName), 32);
  }
}
