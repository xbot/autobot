package org.x3f.autobot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;
import org.x3f.lib.ToastUtil;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

public class BluetoothActivity extends FragmentActivity implements
		OnClickListener {

	private static final String TAG = "BluetoothActivity";
	private static final int MSG_BONDED_DEVICES_FOUND = 1;
	private static final int MSG_BONDED_DEVICES_NOT_FOUND = 2;
	private static final int MSG_CONNECTED = 3;
	private static final int MSG_NOT_CONNECTED = 4;
	private static final int MSG_INVALID_JSON = 5;

	private long exitTime;
	private Spinner spinBondedDevices;
	private ArrayAdapter<String> spinDvcAdp;
	private BluetoothAdapter btAdapter;
	private BluetoothDevice btDevice;

	@SuppressLint("HandlerLeak")
	private Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_BONDED_DEVICES_FOUND:
				Set<BluetoothDevice> devices = btAdapter.getBondedDevices();
				for (BluetoothDevice device : devices) {
					spinDvcAdp.add(device.getName());
				}
				break;
			case MSG_BONDED_DEVICES_NOT_FOUND:
				ToastUtil.showToast(getApplicationContext(),
						getString(R.string.msg_btdevicenotbonded));
				break;
			case MSG_CONNECTED:
				AutobotApplication app = (AutobotApplication) getApplication();
				app.setBtSocket((BluetoothSocket) msg.obj);
				app.setProtocol(AutobotApplication.PROTOCOL_BT);
				Intent itCtrl = new Intent(getApplicationContext(),
						ControlActivity.class);
				startActivity(itCtrl);
				break;
			case MSG_NOT_CONNECTED:
				ToastUtil.showToast(getApplicationContext(),
						getString(R.string.msg_btconnectionfailed));
				break;
			case MSG_INVALID_JSON:
				ToastUtil.showToast(getApplicationContext(),
						getString(R.string.msg_invalidjson));
				break;
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_bluetooth);

		View btnBTConnect = this.findViewById(R.id.btnBTConnect);
		btnBTConnect.setOnClickListener(this);

		// Enable BT service
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		btAdapter.enable();

		// List bonded devices
		spinBondedDevices = (Spinner) this.findViewById(R.id.spinBondedDevices);
		spinDvcAdp = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item);
		spinDvcAdp
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinBondedDevices.setAdapter(spinDvcAdp);
		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				int counter = 0;
				while (counter < 30 && btAdapter.getBondedDevices().size() <= 0) {
					Log.e(TAG, "counter:" + String.valueOf(counter));
					counter++;
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				if (btAdapter.getBondedDevices().size() > 0) {
					mHandler.obtainMessage(MSG_BONDED_DEVICES_FOUND)
							.sendToTarget();
				} else {
					mHandler.obtainMessage(MSG_BONDED_DEVICES_NOT_FOUND)
							.sendToTarget();
				}
			}

		});
		t.start();
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
				// Disable BT service.
				btAdapter.disable();
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
		case R.id.btnBTConnect:
			// Fetch BT address
			int pos = spinBondedDevices.getSelectedItemPosition();

			Set<BluetoothDevice> devices = btAdapter.getBondedDevices();
			Iterator<BluetoothDevice> it = devices.iterator();
			while (it.hasNext()) {
				BluetoothDevice dv = (BluetoothDevice) it.next();
				if (dv.getName().equals(spinDvcAdp.getItem(pos))) {
					btDevice = dv;
					break;
				}
			}

			if (btDevice instanceof BluetoothDevice) {
				final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";
				UUID uuid = UUID.fromString(SPP_UUID);
				try {
					BluetoothSocket btSocket = btDevice
							.createRfcommSocketToServiceRecord(uuid);
					btSocket.connect();
					connect(btSocket);
				} catch (IOException e) {
					ToastUtil.showToast(getApplicationContext(),
							getString(R.string.msg_btconnectionfailed));
				}
			} else {
				ToastUtil.showToast(getApplicationContext(),
						getString(R.string.msg_btdevicenotbonded));
			}
		}
	}

	private void connect(final BluetoothSocket btSocket) throws IOException {
		if (btSocket.isConnected()) {
			final InputStream inStream = btSocket.getInputStream();
			OutputStream outStream = btSocket.getOutputStream();

			String command = "{\"command\":\"connect\"}";
			outStream.write(command.getBytes());

			Thread t = new Thread(new Runnable() {
				public void run() {
					try {
						byte[] buffer = new byte[4096];
						int bytes = inStream.read(buffer);
						byte[] newBuffer = new byte[bytes];
						System.arraycopy(buffer, 0, newBuffer, 0, bytes);
						JSONObject response = new JSONObject(new String(
								newBuffer));
						if (response.has("code")
								&& response.getInt("code") == 0) {
							mHandler.obtainMessage(MSG_CONNECTED, btSocket)
									.sendToTarget();
						} else {
							mHandler.obtainMessage(MSG_NOT_CONNECTED)
									.sendToTarget();
						}
					} catch (IOException e) {
						e.printStackTrace();
					} catch (JSONException e) {
						mHandler.obtainMessage(MSG_INVALID_JSON).sendToTarget();
					}
				}
			});

			synchronized (t) {
				t.start();
				try {
					t.wait(500);
					if (t.isAlive()) {
						Log.e(TAG, "Request over bluetooth timeout.");
						inStream.close();
						ToastUtil.showToast(getApplicationContext(),
								getString(R.string.msg_requesttimeout));
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		} else {
			ToastUtil.showToast(getApplicationContext(),
					getString(R.string.msg_socketnotconnected));
		}
	}
}