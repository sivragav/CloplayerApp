package com.cloplayer;

import com.cloplayer.http.URLHelper;
import com.cloplayer.utils.ServerConstants;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

public class PlayLaterActivity extends Activity {

	Messenger mService = null;
	boolean mIsBound;

	SharedPreferences globalSettings;

	TextView sourceUrlText;
	TextView headlineText;
	TextView detailText;

	String extra_text = URLHelper.home();

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		// Get the intent that started this activity
		Intent intent = getIntent();

		Bundle extras = intent.getExtras();

		if (extras != null)
			extra_text = extras.getString(Intent.EXTRA_TEXT);

		resumeService();
		
	}

	private void resumeService() {
		if (!CloplayerService.isRunning()) {
			startService(new Intent(PlayLaterActivity.this, CloplayerService.class));
		}

		doBindService();
	}

	public void playSource(String sourceUrl) {
		sendStringMessageToService(CloplayerService.MSG_STORE_SOURCE, sourceUrl);
	}

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mService = new Messenger(service);

			SharedPreferences globalSettings = CloplayerService.getInstance().getSharedPreferences(ServerConstants.CLOPLAYER_GLOBAL_PREFS, 0);
			String userId = globalSettings.getString("userId", null);

			Log.e("PlayLaterActivity", "UserId : " + userId);

			if (userId == null) {
				Log.e("PlayLaterActivity", "User not logged in");
				Intent intentToGo = new Intent();
				intentToGo.setClass(PlayLaterActivity.this, HomeActivity.class);
				startActivity(intentToGo);
				Toast.makeText(PlayLaterActivity.this, "Please login to cloplayer and try again", Toast.LENGTH_SHORT).show();
				finish();
			} else {
				Log.e("PlayLaterActivity", "User logged in as : " + userId);
				
				Toast.makeText(PlayLaterActivity.this, "Added to Cloplayer", Toast.LENGTH_SHORT).show();
				
				playSource(extra_text);

				/*
				 * Intent intentToGo = new Intent();
				 * intentToGo.setClass(AddToCloplayerActivity.this,
				 * PlayerActivity.class); startActivity(intentToGo);
				 */
				finish();
			}

		}

		public void onServiceDisconnected(ComponentName className) {
			mService = null;
		}
	};

	void sendIntMessageToService(int messageId, int value) {
		if (mIsBound && mService != null) {
			try {
				Message msg = Message.obtain(null, messageId, value, 0);
				mService.send(msg);
			} catch (RemoteException e) {
			}
		}
	}

	void sendStringMessageToService(int messageId, String value) {
		if (mIsBound && mService != null) {
			try {
				Message msg = Message.obtain(null, messageId, value);
				mService.send(msg);
			} catch (RemoteException e) {
			}
		}
	}

	void sendEmptyMessageToService(int messageId) {
		if (mIsBound && mService != null) {
			try {
				Message msg = Message.obtain(null, messageId);
				mService.send(msg);
			} catch (RemoteException e) {
			}
		}
	}

	void doBindService() {
		bindService(new Intent(this, CloplayerService.class), mConnection, Context.BIND_AUTO_CREATE);
		mIsBound = true;
	}

	void doUnbindService() {
		if (mIsBound) {
			unbindService(mConnection);
			mIsBound = false;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		try {
			doUnbindService();
		} catch (Throwable t) {
			Log.e("CloplayerActivity", "Failed to unbind from the service", t);
		}
	}

}
