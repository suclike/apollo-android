package com.example.apollographql.sample;

import android.os.Bundle;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.apollographql.android.ApolloCall;
import com.apollographql.android.api.graphql.Response;
import com.example.FeedQuery;
import com.example.type.FeedType;

import javax.annotation.Nonnull;

public class MainActivity extends AppCompatActivity {
  private static final String TAG = "MainActivity";

  private SampleApplication application;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    final TextView txtResponse = (TextView) findViewById(R.id.txt_response);
    application = (SampleApplication) getApplication();


    application.executor().execute(new Runnable() {
      @Override public void run() {
        if (Looper.getMainLooper() == Looper.myLooper()) {
          Toast.makeText(MainActivity.this, "Main Thread", Toast.LENGTH_LONG).show();
        } else {
          Toast.makeText(MainActivity.this, "NOT Main Thread", Toast.LENGTH_LONG).show();
        }
      }
    });


    application.apolloClient().newCall(new FeedQuery(FeedQuery.Variables.builder()
        .limit(10)
        .type(FeedType.HOT)
        .build())).enqueue(new ApolloCall.Callback<FeedQuery.Data>() {

      @Override public void onResponse(@Nonnull Response<FeedQuery.Data> dataResponse) {

        final StringBuffer buffer = new StringBuffer();
        for (FeedQuery.Data.Feed feed : dataResponse.data().feed()) {
          buffer.append("name:" + feed.repository().fragments().repositoryFragment().name());
          buffer.append(" owner: " + feed.repository().fragments().repositoryFragment().owner().login());
          buffer.append(" postedBy: " + feed.postedBy().login());
          buffer.append("\n~~~~~~~~~~~");
          buffer.append("\n\n");
        }

        MainActivity.this.runOnUiThread(new Runnable() {
          @Override public void run() {
            txtResponse.setText(buffer.toString());
          }
        });

      }

      @Override public void onFailure(@Nonnull Exception e) {
        Log.e(TAG, e.getMessage(), e);
      }
    });
  }

}

