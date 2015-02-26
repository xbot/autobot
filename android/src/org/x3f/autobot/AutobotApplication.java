package org.x3f.autobot;

import java.util.HashMap;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.x3f.lib.RestClient;
import org.x3f.lib.ToastUtil;

import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import android.app.Application;
import android.bluetooth.BluetoothSocket;
import android.content.res.Resources.NotFoundException;

public class AutobotApplication extends Application {
	private String ip;
	private String port;
	private String videoPort;
	private String videoResolution;
	private String videoFps;
	private String btAddr;
	private int behavior;
	private BluetoothSocket btSocket;
	private int protocol;
	public static int BEHAVIOR_NONE = 0;
	public static int BEHAVIOR_ANTICOLLISION = 1;
	public static int BEHAVIOR_AUTOMATION = 2;
	public static int PROTOCOL_HTTP = 1;
	public static int PROTOCOL_BT = 2;
	
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
	
	public String getVideoPort() {
		return videoPort;
	}

	public void setVideoPort(String port) {
		this.videoPort = port;
	}

	public String getVideoResolution() {
		return videoResolution;
	}

	public void setVideoResolution(String videoResolution) {
		this.videoResolution = videoResolution;
	}

	public String getVideoFps() {
		return videoFps;
	}

	public void setVideoFps(String videoFps) {
		this.videoFps = videoFps;
	}

	public int getBehavior() {
		return behavior;
	}

	public void setBehavior(int behavior) {
		this.behavior = behavior;
	}

	public String getBtAddr() {
		return btAddr;
	}

	public void setBtAddr(String btAddr) {
		this.btAddr = btAddr;
	}

	public BluetoothSocket getBtSocket() {
		return btSocket;
	}

	public void setBtSocket(BluetoothSocket btSocket) {
		this.btSocket = btSocket;
	}

	public int getProtocol() {
		return protocol;
	}

	public void setProtocol(int protocol) {
		this.protocol = protocol;
	}

	@Override  
    public void onCreate() {
        super.onCreate();  
        setIp("10.0.0.1");
        setPort("8000");
        setVideoPort("8080");
        setVideoResolution("160x120");
        setVideoFps("30");
        setBehavior(BEHAVIOR_NONE);
        setBtAddr("00:15:83:0C:BF:EB");
        setProtocol(PROTOCOL_BT);
    }
	
	public void call(String command, HashMap<String, String> params) {
		if (getProtocol() == PROTOCOL_BT) {
			// TODO 用蓝牙
		} else {
			RequestParams rp = new RequestParams(params);
			RestClient.get("http://" + getIp() + ":" + getPort() + "/" + command, rp, new JsonHttpResponseHandler() {
				@Override
	            public void onSuccess(int statusCode, Header[] headers, JSONObject data) {
	                try {
	                	if (data.getInt("code") != 0) {
	                		ToastUtil.showToast(getApplicationContext(), data.getString("msg"));
						} else {
							ToastUtil.showToast(getApplicationContext(), getString(R.string.msg_currentspeed) + data.getString("data") + "%");
						}
					} catch (NotFoundException e) {
						ToastUtil.showToast(getApplicationContext(), e.getMessage());
					} catch (JSONException e) {
						ToastUtil.showToast(getApplicationContext(), e.getMessage());
					}
	            }
	            
	            @Override
	            public void onFailure(int statusCode, Header[] headers, Throwable e, JSONObject error) {
	            	ToastUtil.showToast(getApplicationContext(), e.getMessage());
	            }
	            @Override
	            public void onFailure(int statusCode, Header[] headers, Throwable e, JSONArray error) {
	            	ToastUtil.showToast(getApplicationContext(), e.getMessage());
	            }
	            @Override
	            public void onFailure(int statusCode, Header[] headers, String error, Throwable e) {
	            	ToastUtil.showToast(getApplicationContext(), error);
	            }
			});
		}
	}
	
	public void call(String command, HashMap<String, String> params, JsonHttpResponseHandler callback) {
		RequestParams rp = new RequestParams(params);
		RestClient.get("http://" + getIp() + ":" + getPort() + "/" + command, rp, callback);
	}
	
	public String getVideoURL() {
		return "http://"+this.getIp()+":"+this.getVideoPort()+"/?action=stream";
	}
}
