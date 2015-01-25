package org.x3f.autobot;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.x3f.lib.RestClient;

import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import android.app.Application;
import android.content.res.Resources.NotFoundException;
import android.widget.Toast;

public class AutobotApplication extends Application {
	public String ip;
	public String port;
	
	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getPort() {
		return port;
	}

	public void setPort(String port) {
		this.port = port;
	}

	@Override  
    public void onCreate() {
        super.onCreate();  
        setIp("192.168.1.104");
        setPort("8000");
    }
	
	public void call(String command, RequestParams params) {
		RestClient.get("http://" + getIp() + ":" + getPort() + "/" + command, params, new JsonHttpResponseHandler() {
			@Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject data) {
                try {
                	if (data.getInt("code") != 0) {
						Toast.makeText(getApplicationContext(), data.getString("msg"), Toast.LENGTH_SHORT).show();
					}
				} catch (NotFoundException e) {
					Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
				} catch (JSONException e) {
					Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
				}
            }
            
            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable e, JSONObject error) {
            	Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable e, JSONArray error) {
            	Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onFailure(int statusCode, Header[] headers, String error, Throwable e) {
            	Toast.makeText(getApplicationContext(), error, Toast.LENGTH_SHORT).show();
            }
		});
	}
}
