package org.x3f.autobot;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

import org.x3f.lib.RestClient;
import org.x3f.lib.ToastUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import android.app.Application;
import android.bluetooth.BluetoothSocket;
import android.content.SharedPreferences;
import android.util.Log;

public class AutobotApplication extends Application {

	private static final String TAG = "AutobotApplication";

	private String ip;
	private String port;
	private String videoPort;
	private String videoResolution;
	private String videoFps;
	private int behavior;
	private BluetoothSocket btSocket;
	private int protocol;

	private SharedPreferences sharedPref;
	public static int BEHAVIOR_NONE = 0;
	public static int BEHAVIOR_ANTICOLLISION = 1;
	public static int BEHAVIOR_AUTOMATION = 2;
	public static int PROTOCOL_HTTP = 1;
	public static int PROTOCOL_BT = 2;
	public static String PREF_FILE_KEY = "org.x3f.autobot.sharedpref";

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
		
		sharedPref = getSharedPreferences(PREF_FILE_KEY, MODE_PRIVATE);
		
		setIp(sharedPref.getString("last_bot_ip", "10.0.0.1"));
		setPort(sharedPref.getString("last_bot_port", "8000"));
		setVideoPort(sharedPref.getString("last_video_port", "8080"));
		setVideoResolution(sharedPref.getString("last_video_resolution", "320x240"));
		setVideoFps(sharedPref.getString("last_video_fps", "30"));
		setBehavior(BEHAVIOR_NONE);
		setProtocol(sharedPref.getInt("last_protocol", PROTOCOL_BT));
	}

	public void call(String command, HashMap<String, String> params) {
		try {
			OutputStream outStream = btSocket.getOutputStream();
			HashMap<String, Object> request = new HashMap<String, Object>();
			request.put("command", command);
			request.put("params", params);
			ObjectMapper om = new ObjectMapper();
			String json = om.writeValueAsString(request);
			outStream.write(json.getBytes());
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
			e.printStackTrace();
			ToastUtil.showToast(getApplicationContext(), e.getMessage());
		}
	}

	public void call(String command, HashMap<String, String> params,
			JsonHttpResponseHandler callback) {
		RequestParams rp = new RequestParams(params);
		RestClient.get("http://" + getIp() + ":" + getPort() + "/" + command,
				rp, callback);
	}

	public String getVideoURL() {
		return "http://" + this.getIp() + ":" + this.getVideoPort()
				+ "/?action=stream";
	}
	
	public boolean isBTConnected() {
		return getBtSocket() != null && getBtSocket().isConnected();
	}
	
	public String getMotionLabel(String motion) {
		if (motion.equals("forward")) {
			return getString(R.string.btn_forward);
		} else if (motion.equals("backward")) {
			return getString(R.string.btn_backward);
		} else if (motion.equals("left")) {
			return getString(R.string.btn_left);
		} else if (motion.equals("right")) {
			return getString(R.string.btn_right);
		} else if (motion.equals("adjleft")) {
			return getString(R.string.btn_adjleft);
		} else if (motion.equals("adjright")) {
			return getString(R.string.btn_adjright);
		} else if (motion.equals("stop")) {
			return getString(R.string.btn_stop);
		}
		return "";
	}
}
