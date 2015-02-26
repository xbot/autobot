package org.x3f.autobot;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import org.x3f.lib.ToastUtil;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Toast;

public class BluetoothActivity extends FragmentActivity implements
		OnClickListener {

	private long exitTime;
	private EditText editBTAddr;
	private BluetoothAdapter btAdapter;
	private BluetoothDevice btDevice;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_bluetooth);

		View btnBTConnect = this.findViewById(R.id.btnBTConnect);
		btnBTConnect.setOnClickListener(this);

		AutobotApplication app = (AutobotApplication) getApplication();
		editBTAddr = (EditText) this.findViewById(R.id.editBTAddr);
		editBTAddr.setText(app.getBtAddr());

		// Enable BT service
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		btAdapter.enable();
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
			if (editBTAddr.getText().length() <= 0) {
				Toast.makeText(getApplicationContext(),
						this.getString(R.string.msg_emptybtaddr),
						Toast.LENGTH_SHORT).show();
				break;
			}
			AutobotApplication app = (AutobotApplication) getApplication();
			app.setBtAddr(editBTAddr.getText().toString());

			Set<BluetoothDevice> devices = btAdapter.getBondedDevices();
			Iterator<BluetoothDevice> it = devices.iterator();
			while (it.hasNext()) {
				BluetoothDevice dv = (BluetoothDevice) it.next();
				if (dv.getAddress().equals(app.getBtAddr())) {
					btDevice = dv;
					break;
				}
			}
			
			if (btDevice instanceof BluetoothDevice) {
				final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";     
			    UUID uuid = UUID.fromString(SPP_UUID);     
				try {
					BluetoothSocket btSocket = btDevice.createRfcommSocketToServiceRecord(uuid);
					btSocket.connect();
					app.setBtSocket(btSocket);
					app.setProtocol(AutobotApplication.PROTOCOL_BT);
					Intent itCtrl = new Intent(getApplicationContext(), ControlActivity.class);
            		startActivity(itCtrl);
				} catch (IOException e) {
					ToastUtil.showToast(getApplicationContext(), getString(R.string.msg_btconnectionfailed));
				}
			} else {
				ToastUtil.showToast(getApplicationContext(), getString(R.string.msg_btdevicenotbonded));
			}
		}
	}
}