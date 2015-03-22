package org.x3f.autobot;

import org.x3f.lib.RestClient;

import com.loopj.android.http.*;

import org.json.*;
import org.apache.http.Header;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources.NotFoundException;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class MainActivity extends FragmentActivity implements OnClickListener {

	private long exitTime;
	private EditText editIP;
	private EditText editPort;
	private EditText editVideoPort;
	private EditText editFps;
	private Spinner spinRslv;
	private ArrayAdapter<?> spinRslvAdp;
	private SharedPreferences sharedPref;
	private Editor prefEditor;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		sharedPref = getSharedPreferences(AutobotApplication.PREF_FILE_KEY,
				MODE_PRIVATE);
		prefEditor = sharedPref.edit();
		prefEditor.putInt("last_protocol", AutobotApplication.PROTOCOL_HTTP);
		prefEditor.commit();

		View btnConn = this.findViewById(R.id.btnConnect);
		btnConn.setOnClickListener(this);

		AutobotApplication app = (AutobotApplication) getApplication();
		editIP = (EditText) this.findViewById(R.id.editIP);
		editIP.setText(app.getIp());
		editPort = (EditText) this.findViewById(R.id.editPort);
		editPort.setText(app.getPort());
		editVideoPort = (EditText) this.findViewById(R.id.editVideoPort);
		editVideoPort.setText(app.getVideoPort());
		spinRslv = (Spinner) this.findViewById(R.id.spinRslv);
		spinRslvAdp = ArrayAdapter.createFromResource(this,
				R.array.resolutions, android.R.layout.simple_spinner_item);
		spinRslvAdp
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinRslv.setAdapter(spinRslvAdp);
		String[] resolutions = getResources().getStringArray(
				R.array.resolutions);
		for (int i = 0; i < resolutions.length; i++) {
			if (resolutions[i].equals(sharedPref.getString(
					"last_video_resolution", ""))) {
				spinRslv.setSelection(i);
				break;
			}
		}
		editFps = (EditText) this.findViewById(R.id.editFps);
		editFps.setText(app.getVideoFps());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.about:
			Intent settings_intent = new Intent(this, AboutActivity.class);
			startActivity(settings_intent);
			return true;
		case R.id.bluetooth:
			Intent bluetooth_intent = new Intent(this, BluetoothActivity.class);
			bluetooth_intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(bluetooth_intent);
			finish();
			return true;
		}
		return false;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK
				&& event.getAction() == KeyEvent.ACTION_DOWN) {
			if (System.currentTimeMillis() - exitTime > 2000) {
				Toast.makeText(this.getApplicationContext(),
						this.getString(R.string.msg_quit), Toast.LENGTH_SHORT)
						.show();
				exitTime = System.currentTimeMillis();
			} else {
				finish();
				System.exit(0);
			}
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btnConnect:
			// Fetch ip
			if (editIP.getText().length() <= 0) {
				Toast.makeText(getApplicationContext(),
						this.getString(R.string.msg_emptyip),
						Toast.LENGTH_SHORT).show();
				break;
			}
			AutobotApplication app = (AutobotApplication) getApplication();
			app.setIp(editIP.getText().toString());
			// Fetch port
			if (editPort.getText().length() <= 0) {
				Toast.makeText(getApplicationContext(),
						this.getString(R.string.msg_emptyport),
						Toast.LENGTH_SHORT).show();
				break;
			}
			app.setPort(editPort.getText().toString());
			// Fetch video port
			if (editVideoPort.getText().length() <= 0) {
				Toast.makeText(getApplicationContext(),
						this.getString(R.string.msg_emptyvideoport),
						Toast.LENGTH_SHORT).show();
				break;
			}
			app.setVideoPort(editVideoPort.getText().toString());
			// Fetch resolution
			int pos = spinRslv.getSelectedItemPosition();
			String[] resolutions = getResources().getStringArray(
					R.array.resolutions);
			app.setVideoResolution(resolutions[pos]);
			// Fetch video fps
			if (editFps.getText().length() <= 0) {
				Toast.makeText(getApplicationContext(),
						this.getString(R.string.msg_emptyvideofps),
						Toast.LENGTH_SHORT).show();
				break;
			}
			app.setVideoFps(editFps.getText().toString());

			// Test connection.
			RestClient.get(
					"http://" + editIP.getText() + ":" + editPort.getText()
							+ "/connect", null, new JsonHttpResponseHandler() {
						@Override
						public void onSuccess(int statusCode, Header[] headers,
								JSONObject data) {
							try {
								if (data.getInt("code") == 0) {
									AutobotApplication app = (AutobotApplication) getApplication();
									app.setProtocol(AutobotApplication.PROTOCOL_HTTP);
									Intent itCtrl = new Intent(
											getApplicationContext(),
											ControlActivity.class);
									startActivity(itCtrl);

									// save preferences for future use
									prefEditor.putString("last_bot_ip",
											app.getIp());
									prefEditor.putString("last_bot_port",
											app.getPort());
									prefEditor.putString("last_video_port",
											app.getVideoPort());
									prefEditor.putString(
											"last_video_resolution",
											app.getVideoResolution());
									prefEditor.putString("last_video_fps",
											app.getVideoFps());
									prefEditor.commit();
								} else {
									Toast.makeText(getApplicationContext(),
											data.getString("msg"),
											Toast.LENGTH_SHORT).show();
								}
							} catch (NotFoundException e) {
								Toast.makeText(getApplicationContext(),
										e.getMessage(), Toast.LENGTH_SHORT)
										.show();
							} catch (JSONException e) {
								Toast.makeText(getApplicationContext(),
										e.getMessage(), Toast.LENGTH_SHORT)
										.show();
							}
						}

						@Override
						public void onFailure(int statusCode, Header[] headers,
								Throwable e, JSONObject error) {
							Toast.makeText(getApplicationContext(),
									e.getMessage(), Toast.LENGTH_SHORT).show();
						}

						@Override
						public void onFailure(int statusCode, Header[] headers,
								Throwable e, JSONArray error) {
							Toast.makeText(getApplicationContext(),
									e.getMessage(), Toast.LENGTH_SHORT).show();
						}

						@Override
						public void onFailure(int statusCode, Header[] headers,
								String error, Throwable e) {
							Toast.makeText(getApplicationContext(), error,
									Toast.LENGTH_SHORT).show();
						}
					});
			break;
		}
	}

}