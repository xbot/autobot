package org.x3f.lib;

import com.loopj.android.http.*;

public class RestClient {

  private static AsyncHttpClient client = new AsyncHttpClient();

  public static void get(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
	  client.setMaxRetriesAndTimeout(0, 3000);
      client.get(url, params, responseHandler);
  }

  public static void post(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
	  client.setMaxRetriesAndTimeout(0, 3000);
      client.post(url, params, responseHandler);
  }
}