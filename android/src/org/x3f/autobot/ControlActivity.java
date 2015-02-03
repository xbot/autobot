package org.x3f.autobot;

import java.io.IOException;
import java.net.URI;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.x3f.lib.ToastUtil;
import com.loopj.android.http.RequestParams;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.ToggleButton;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;

public class ControlActivity extends Activity implements OnClickListener, OnTouchListener {

    private static final String TAG = "AUTOBOT";

    private MjpegView videoView = null;
    
    private static boolean suspending = false;
    
	final Handler handler = new Handler();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_controlpanel);
		
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
		btnToggleVideo.setOnClickListener(this);
		
		videoView = (MjpegView) findViewById(R.id.mv);  
        if(videoView != null){
        	videoView.setResolution(160, 120);
        }
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
		case R.id.btnStop:
			params.add("hold", "1");
			app.call("stop", params);
			break;
		case R.id.btnGearUp:
			params.add("speed", "+20");
			app.call("vary", params);
			break;
		case R.id.btnGearDown:
			params.add("speed", "-20");
			app.call("vary", params);
			break;
		case R.id.btnToggleVideo:
			ToggleButton btn = (ToggleButton)v;
			if (btn.isChecked()) {
				// enable video
				videoView.setBackgroundColor(Color.TRANSPARENT);
				app.call("videoOn", params);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
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
		default:
			break;
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		AutobotApplication app = (AutobotApplication)getApplication();
		RequestParams params = new RequestParams();
		switch (v.getId()) {
		case R.id.btnLeft:
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				app.call("left", params);
			}
			if (event.getAction() == MotionEvent.ACTION_UP) {
				params.add("hold", "1");
				app.call("stop", params);
			}
			break;
		case R.id.btnRight:
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				app.call("right", params);
			}
			if (event.getAction() == MotionEvent.ACTION_UP) {
				params.add("hold", "1");
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
        if(videoView!=null){
        	if(suspending){
        		AutobotApplication app = (AutobotApplication)getApplication();
        		new DoRead().execute(app.getVideoURL());
        		suspending = false;
        	}
        }
    }

    public void onStart() {
        super.onStart();
    }
    
    public void onPause() {
        super.onPause();
        if(videoView!=null){
        	if(videoView.isStreaming()){
        		videoView.stopPlayback();
		        suspending = true;
        	}
        }
    }
    
    public void onStop() {
        super.onStop();
    }

    public void onDestroy() {
    	if(videoView!=null){
    		videoView.freeCameraMemory();
    	}
    	
        super.onDestroy();
    }
    
    public void setImageError(){
    	handler.post(new Runnable() {
    		@Override
    		public void run() {
    			ToastUtil.showToast(getApplicationContext(), getString(R.string.title_imageerror));
    			return;
    		}
    	});
    }
    
    public class DoRead extends AsyncTask<String, Void, MjpegInputStream> {
    	
        protected MjpegInputStream doInBackground(String... url) {
            //TODO: if camera has authentication deal with it and don't just not work
            HttpResponse res = null;         
            DefaultHttpClient httpclient = new DefaultHttpClient(); 
            HttpParams httpParams = httpclient.getParams();
            HttpConnectionParams.setConnectionTimeout(httpParams, 5*1000);
            HttpConnectionParams.setSoTimeout(httpParams, 5*1000);
            try {
                res = httpclient.execute(new HttpGet(URI.create(url[0])));
                if(res.getStatusLine().getStatusCode()==401){
                    //You must turn off camera User Access Control before this will work
                    return null;
                }
                return new MjpegInputStream(res.getEntity().getContent());  
            } catch (ClientProtocolException e) {
	            Log.d(TAG, "Request failed-ClientProtocolException", e);
                //Error connecting to camera
            } catch (IOException e) {
	            Log.d(TAG, "Request failed-IOException", e);
                //Error connecting to camera
            }
            return null;
        }

        protected void onPostExecute(MjpegInputStream result) {
        	videoView.setSource(result);
            if(result!=null){
            	result.setSkip(1);
            	ToastUtil.showToast(getApplicationContext(), getString(R.string.msg_connected));
            }else{
            	ToastUtil.showToast(getApplicationContext(), getString(R.string.msg_connectionfailed));
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
        	startActivity((new Intent(ControlActivity.this, ControlActivity.class)));
        }
    }

}
