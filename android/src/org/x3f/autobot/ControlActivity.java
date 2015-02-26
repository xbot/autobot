package org.x3f.autobot;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.x3f.lib.ToastUtil;

import com.loopj.android.http.JsonHttpResponseHandler;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.ImageButton;
import android.widget.ToggleButton;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.graphics.Color;

public class ControlActivity extends Activity implements OnClickListener,
		OnTouchListener {

	private static final String TAG = "AUTOBOT";

	private MjpegView videoView = null;
	private ImageButton btnSwitchBehavior = null;

	private static boolean suspending = false;

	final Handler handler = new Handler();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_controlpanel);
		
		AutobotApplication app = (AutobotApplication) getApplication();

		View btnForward = this.findViewById(R.id.btnForward);
		btnForward.setOnClickListener(this);
		View btnBackward = this.findViewById(R.id.btnBackward);
		btnBackward.setOnClickListener(this);
		View btnLeft = this.findViewById(R.id.btnLeft);
		btnLeft.setOnTouchListener(this);
		View btnRight = this.findViewById(R.id.btnRight);
		btnRight.setOnTouchListener(this);
		View btnAdjLeft = this.findViewById(R.id.btnAdjLeft);
		btnAdjLeft.setOnTouchListener(this);
		View btnAdjRight = this.findViewById(R.id.btnAdjRight);
		btnAdjRight.setOnTouchListener(this);
		View btnStop = this.findViewById(R.id.btnStop);
		btnStop.setOnClickListener(this);
		View btnGearUp = this.findViewById(R.id.btnGearUp);
		btnGearUp.setOnClickListener(this);
		View btnGearDown = this.findViewById(R.id.btnGearDown);
		btnGearDown.setOnClickListener(this);
		View btnToggleVideo = this.findViewById(R.id.btnToggleVideo);
		if (app.getBtSocket() instanceof BluetoothSocket) {
			btnToggleVideo.setEnabled(false);
		} else {
			btnToggleVideo.setOnClickListener(this);
		}
		btnSwitchBehavior = (ImageButton) this
				.findViewById(R.id.btnSwitchBehavior);
		btnSwitchBehavior.setOnClickListener(this);

		videoView = (MjpegView) findViewById(R.id.mv);
		if (videoView != null) {
			String[] resolution = app.getVideoResolution().split("x");
			int width = Integer.parseInt(resolution[0]);
			int height = Integer.parseInt(resolution[1]);
			videoView.setResolution(width, height);
		}
	}

	@Override
	public void onClick(View v) {
		AutobotApplication app = (AutobotApplication) getApplication();
		HashMap<String, String> params = new HashMap<String, String>();

		switch (v.getId()) {
		case R.id.btnForward:
			app.call("forward", null);
			break;
		case R.id.btnBackward:
			app.call("backward", null);
			break;
		case R.id.btnStop:
			params.put("hold", "1");
			app.call("stop", params);
			break;
		case R.id.btnGearUp:
			params.put("speed", "+20");
			app.call("vary", params);
			break;
		case R.id.btnGearDown:
			params.put("speed", "-20");
			app.call("vary", params);
			break;
		case R.id.btnToggleVideo:
			ToggleButton btn = (ToggleButton) v;
			if (btn.isChecked()) {
				// enable video
				videoView.setBackgroundColor(Color.TRANSPARENT);
				params.put("resolution", app.getVideoResolution());
				params.put("fps", app.getVideoFps());
				app.call("videoOn", params);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				if (!videoView.isStreaming()) {
					new DoRead().execute(app.getVideoURL());
				}
			} else {
				// disable video
				app.call("videoOff", params);
				videoView.stopPlayback();
				videoView.setBackgroundColor(Color.BLACK);
			}
			break;
		case R.id.btnSwitchBehavior:
			if (app.getBehavior() == AutobotApplication.BEHAVIOR_NONE) {
				// switch to anti-collision
				params.put("v", String
						.valueOf(AutobotApplication.BEHAVIOR_ANTICOLLISION));
			} else if (app.getBehavior() == AutobotApplication.BEHAVIOR_ANTICOLLISION) {
				// switch to automation
				params.put("v",
						String.valueOf(AutobotApplication.BEHAVIOR_AUTOMATION));
			} else if (app.getBehavior() == AutobotApplication.BEHAVIOR_AUTOMATION) {
				// switch to manual
				params.put("v",
						String.valueOf(AutobotApplication.BEHAVIOR_NONE));
			}
			app.call("behavior", params, this.getBehaviorCallback());
			break;
		default:
			break;
		}
	}

	private JsonHttpResponseHandler getBehaviorCallback() {
		return new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(int statusCode, Header[] headers,
					JSONObject data) {
				AutobotApplication app = (AutobotApplication) getApplication();
				try {
					if (data.getInt("code") != 0) {
						ToastUtil.showToast(getApplicationContext(),
								data.getString("msg"));
					} else {
						try {
							int behavior = Integer.parseInt(data
									.getString("data"));
							if (behavior == AutobotApplication.BEHAVIOR_ANTICOLLISION) {
								app.setBehavior(AutobotApplication.BEHAVIOR_ANTICOLLISION);
								ToastUtil
										.showToast(
												getApplicationContext(),
												getString(R.string.msg_behavior_anticollision));
								btnSwitchBehavior
										.setImageDrawable(getResources()
												.getDrawable(
														R.drawable.avatar_gray));
							} else if (behavior == AutobotApplication.BEHAVIOR_AUTOMATION) {
								app.setBehavior(AutobotApplication.BEHAVIOR_AUTOMATION);
								ToastUtil
										.showToast(
												getApplicationContext(),
												getString(R.string.msg_behavior_automation));
								btnSwitchBehavior
										.setImageDrawable(getResources()
												.getDrawable(
														R.drawable.avatar_red));
							} else if (behavior == AutobotApplication.BEHAVIOR_NONE) {
								app.setBehavior(AutobotApplication.BEHAVIOR_NONE);
								ToastUtil.showToast(getApplicationContext(),
										getString(R.string.msg_behavior_none));
								btnSwitchBehavior
										.setImageDrawable(getResources()
												.getDrawable(R.drawable.avatar));
							}
						} catch (NumberFormatException e) {
							ToastUtil.showToast(getApplicationContext(),
									data.getString("msg"));
						}
					}
				} catch (NotFoundException e) {
					ToastUtil
							.showToast(getApplicationContext(), e.getMessage());
				} catch (JSONException e) {
					ToastUtil
							.showToast(getApplicationContext(), e.getMessage());
				}
			}

			@Override
			public void onFailure(int statusCode, Header[] headers,
					Throwable e, JSONObject error) {
				ToastUtil.showToast(getApplicationContext(), e.getMessage());
			}

			@Override
			public void onFailure(int statusCode, Header[] headers,
					Throwable e, JSONArray error) {
				ToastUtil.showToast(getApplicationContext(), e.getMessage());
			}

			@Override
			public void onFailure(int statusCode, Header[] headers,
					String error, Throwable e) {
				ToastUtil.showToast(getApplicationContext(), error);
			}
		};
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		AutobotApplication app = (AutobotApplication) getApplication();
		HashMap<String, String> params = new HashMap<String, String>();
		switch (v.getId()) {
		case R.id.btnLeft:
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				app.call("left", params);
			}
			if (event.getAction() == MotionEvent.ACTION_UP) {
				params.put("hold", "1");
				app.call("stop", params);
			}
			break;
		case R.id.btnRight:
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				app.call("right", params);
			}
			if (event.getAction() == MotionEvent.ACTION_UP) {
				params.put("hold", "1");
				app.call("stop", params);
			}
			break;
		case R.id.btnAdjLeft:
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				app.call("adjustLeft", params);
			}
			if (event.getAction() == MotionEvent.ACTION_UP) {
				app.call("resume", params);
			}
			break;
		case R.id.btnAdjRight:
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				app.call("adjustRight", params);
			}
			if (event.getAction() == MotionEvent.ACTION_UP) {
				app.call("resume", params);
			}
			break;
		default:
			break;
		}
		return false;
	}

	public void onResume() {
		super.onResume();

		AutobotApplication app = (AutobotApplication) getApplication();

		if (videoView != null) {
			if (suspending) {
				new DoRead().execute(app.getVideoURL());
				suspending = false;
			}
		}

		// Fetch autobot's states
		app.call("connect", new HashMap<String, String>(), this.getBehaviorCallback());
	}

	public void onStart() {
		super.onStart();
	}

	public void onPause() {
		super.onPause();
		if (videoView != null) {
			if (videoView.isStreaming()) {
				videoView.stopPlayback();
				suspending = true;
			}
		}
	}

	public void onStop() {
		super.onStop();
	}

	public void onDestroy() {
		if (videoView != null) {
			videoView.freeCameraMemory();
		}
		// Close BT socket if it is connected
		AutobotApplication app = (AutobotApplication) getApplication();
		if (app.getBtSocket() instanceof BluetoothSocket
				&& app.getBtSocket().isConnected()) {
			try {
				app.getBtSocket().close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		super.onDestroy();
	}

	public void setImageError() {
		handler.post(new Runnable() {
			@Override
			public void run() {
				ToastUtil.showToast(getApplicationContext(),
						getString(R.string.title_imageerror));
				return;
			}
		});
	}

	public class DoRead extends AsyncTask<String, Void, MjpegInputStream> {

		protected MjpegInputStream doInBackground(String... url) {
			// TODO: if camera has authentication deal with it and don't just
			// not work
			HttpResponse res = null;
			DefaultHttpClient httpclient = new DefaultHttpClient();
			HttpParams httpParams = httpclient.getParams();
			HttpConnectionParams.setConnectionTimeout(httpParams, 5 * 1000);
			HttpConnectionParams.setSoTimeout(httpParams, 5 * 1000);
			try {
				res = httpclient.execute(new HttpGet(URI.create(url[0])));
				if (res.getStatusLine().getStatusCode() == 401) {
					// You must turn off camera User Access Control before this
					// will work
					return null;
				}
				return new MjpegInputStream(res.getEntity().getContent());
			} catch (ClientProtocolException e) {
				Log.d(TAG, "Request failed-ClientProtocolException", e);
				// Error connecting to camera
			} catch (IOException e) {
				Log.d(TAG, "Request failed-IOException", e);
				// Error connecting to camera
			}
			return null;
		}

		protected void onPostExecute(MjpegInputStream result) {
			videoView.setSource(result);
			if (result != null) {
				result.setSkip(1);
				ToastUtil.showToast(getApplicationContext(),
						getString(R.string.msg_connected));
			} else {
				ToastUtil.showToast(getApplicationContext(),
						getString(R.string.msg_connectionfailed));
			}
			videoView.setDisplayMode(MjpegView.SIZE_BEST_FIT);
			videoView.showFps(true);
		}
	}

	public class RestartApp extends AsyncTask<Void, Void, Void> {
		protected Void doInBackground(Void... v) {
			ControlActivity.this.finish();
			return null;
		}

		protected void onPostExecute(Void v) {
			startActivity((new Intent(ControlActivity.this,
					ControlActivity.class)));
		}
	}

}
