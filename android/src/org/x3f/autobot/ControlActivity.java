package org.x3f.autobot;

import com.loopj.android.http.RequestParams;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.app.Activity;

public class ControlActivity extends Activity implements OnClickListener {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_controlpanel);
		
		View btnForward = this.findViewById(R.id.btnForward);
		btnForward.setOnClickListener(this);
		View btnBackward = this.findViewById(R.id.btnBackward);
		btnBackward.setOnClickListener(this);
		View btnLeft = this.findViewById(R.id.btnLeft);
		btnLeft.setOnClickListener(this);
		View btnRight = this.findViewById(R.id.btnRight);
		btnRight.setOnClickListener(this);
		View btnStop = this.findViewById(R.id.btnStop);
		btnStop.setOnClickListener(this);
		View btnGearUp = this.findViewById(R.id.btnGearUp);
		btnGearUp.setOnClickListener(this);
		View btnGearDown = this.findViewById(R.id.btnGearDown);
		btnGearDown.setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {
		AutobotApplication app = (AutobotApplication)getApplication();
		RequestParams params = new RequestParams();
		
		switch (v.getId()) {
		case R.id.btnForward:
			app.call("forward", null);
			break;
		case R.id.btnBackward:
			app.call("backward", null);
			break;
		case R.id.btnLeft:
			app.call("left", null);
			break;
		case R.id.btnRight:
			app.call("right", null);
			break;
		case R.id.btnStop:
			app.call("stop", null);
			break;
		case R.id.btnGearUp:
			params.add("speed", "+10");
			app.call("vary", params);
			break;
		case R.id.btnGearDown:
			params.add("speed", "-10");
			app.call("vary", params);
			break;
		default:
			break;
		}
	}

}