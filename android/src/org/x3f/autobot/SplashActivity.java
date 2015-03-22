package org.x3f.autobot;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.WindowManager;

public class SplashActivity extends Activity {

	public static final int SPLASH_DISPLAY_TIME = 1000;
	private SharedPreferences sharedPref;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.activity_splash);

		sharedPref = getSharedPreferences(AutobotApplication.PREF_FILE_KEY,
				Context.MODE_PRIVATE);

		new Handler().postDelayed(new Runnable() {

			@Override
			public void run() {
				Intent mainIntent;
				if (sharedPref.getInt("last_protocol",
						AutobotApplication.PROTOCOL_BT) == AutobotApplication.PROTOCOL_BT) {
					mainIntent = new Intent(SplashActivity.this,
							BluetoothActivity.class);
				} else {
					mainIntent = new Intent(SplashActivity.this,
							MainActivity.class);
				}
				startActivity(mainIntent);
				finish();
			}

		}, SPLASH_DISPLAY_TIME);
	}

}
