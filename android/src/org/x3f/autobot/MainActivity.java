package org.x3f.autobot;

import org.x3f.lib.RestClient;

import com.loopj.android.http.*;

import org.json.*;
import org.apache.http.Header;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends FragmentActivity implements OnClickListener {

	private long exitTime;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		View btnConn = this.findViewById(R.id.btnConnect);
		btnConn.setOnClickListener(this);
		View btnAbout = this.findViewById(R.id.btnAbout);
		btnAbout.setOnClickListener(this);
		
		AutobotApplication app = (AutobotApplication)getApplication();
		EditText editIP = (EditText) this.findViewById(R.id.editIP);
		editIP.setText(app.getIp());
		EditText editPort = (EditText) this.findViewById(R.id.editPort);
		editPort.setText(app.getPort());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
			if (System.currentTimeMillis()-exitTime>2000) {
				Toast.makeText(this.getApplicationContext(), this.getString(R.string.msg_quit), Toast.LENGTH_SHORT).show();
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
		Bundle b = new Bundle();
		switch(v.getId()) {
		case R.id.btnConnect:
			// Check ip address and port
			EditText editIP = (EditText) this.findViewById(R.id.editIP);
			if (editIP.getText().length()<=0) {
				Toast.makeText(getApplicationContext(), this.getString(R.string.msg_emptyip), Toast.LENGTH_SHORT).show();
				break;
			}
			AutobotApplication app = (AutobotApplication)getApplication();
			app.setIp(editIP.getText().toString());
			EditText editPort = (EditText) this.findViewById(R.id.editPort);
			if (editPort.getText().length()<=0) {
				Toast.makeText(getApplicationContext(), this.getString(R.string.msg_emptyport), Toast.LENGTH_SHORT).show();
				break;
			}
			app.setPort(editPort.getText().toString());
			
			// Test connection.
			RestClient.get("http://" + editIP.getText() + ":" + editPort.getText() + "/connect", null, new JsonHttpResponseHandler() {
				@Override
	            public void onSuccess(int statusCode, Header[] headers, JSONObject data) {
	                try {
	                	if (data.getInt("code") == 0) {
	                		Intent itCtrl = new Intent(getApplicationContext(), ControlActivity.class);
	                		startActivity(itCtrl);
						} else {
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
			break;
		case R.id.btnAbout:
			Intent iAbout = new Intent(this, AboutActivity.class);
			startActivity(iAbout);
			break;
		}
	}

}