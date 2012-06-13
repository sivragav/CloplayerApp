package com.cloplayer.http;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.StringTokenizer;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import com.cloplayer.CloplayerService;
import com.cloplayer.MaryConnector;
import com.cloplayer.sqlite.MySQLiteHelper;
import com.cloplayer.sqlite.Story;
import com.cloplayer.utils.ServerConstants;

public class DownloadTask extends AsyncTask<String, String, String> {

	Story story;
	
	public DownloadTask(Story story) {
		this.story = story;
	}

	@Override
	protected String doInBackground(String... data) {

		Log.e("DownloadTask", "Story State : " + story.getState());

		if (story.getState() < Story.STATE_BOOTSTRAPPED) {
			startBootstrap();
		} else if (story.getState() < Story.STATE_DOWNLOADED) {
			startAudioDownload();
		}		

		return "";

	}

	public void startBootstrap() {

		Log.e("DownloadTask", "Starting Bootstrap");

		SyncHTTPClient client = new SyncHTTPClient("http://api.cloplayer.com/api/parse?url=" + story.getUrl()) {

			public void onSuccessResponse(String response) {
				try {

					JSONObject content = new JSONObject(response);

					ContentValues values = new ContentValues();
					values.put(MySQLiteHelper.COLUMN_HEADLINE, content.getString("title"));
					values.put(MySQLiteHelper.COLUMN_DETAIL, content.getString("cleanedArticleText"));
					values.put(MySQLiteHelper.COLUMN_DOMAIN, content.getString("domain"));
					values.put(MySQLiteHelper.COLUMN_STATE, Story.STATE_BOOTSTRAPPED);
					CloplayerService.getInstance().datasource.updateStory(story, values);

					// CloplayerService.getInstance().showNotification("Downloading : "
					// + story.getHeadline(), "Downloading : " +
					// story.getHeadline());

				} catch (JSONException e) {
					e.printStackTrace();
				}
			}

			public void onErrorResponse(Exception e) {
				Log.e("LoginActivity", "Error", e);
			}
		};
		
		client.execute();
		
		startAudioDownload();
	}

	public void startAudioDownload() {

		Log.e("DownloadTask", "Starting download");

		String textToRead = story.getHeadline() + ". " + story.getDetail();

		StringTokenizer st = new StringTokenizer(textToRead.replace("\"", ""), ".");

		ContentValues values = new ContentValues();
		values.put(MySQLiteHelper.COLUMN_ITEM_COUNT, st.countTokens());
		values.put(MySQLiteHelper.COLUMN_DOWNLOAD_PROGRESS, 0);
		values.put(MySQLiteHelper.COLUMN_PLAY_PROGRESS, 0);
		CloplayerService.getInstance().datasource.updateStory(story, values);

		int currentLine = 0;

		SharedPreferences globalSettings = CloplayerService.getInstance().getSharedPreferences(ServerConstants.CLOPLAYER_GLOBAL_PREFS, 0);
		SharedPreferences.Editor editor = globalSettings.edit();

		while (st.hasMoreTokens()) {
			String text = st.nextToken().trim();
			Log.e("DownloadTask", "Downloading voice for : " + text);
			byte[] byteArray = MaryConnector.getAudio(text);

			Log.e("DownloadTask", "Downloaded voice for : " + byteArray.length);

			synchronized (story) {
				editor.putString(story.getId() + "." + currentLine + ".text", text);
				editor.putInt(story.getId() + "." + currentLine + ".audio", byteArray.length);
				editor.commit();

				// CloplayerService.getInstance().cache.put(story.getId() + "."
				// + currentLine + ".audio", byteArray);

				String sdcardPath = Environment.getExternalStorageDirectory().getAbsolutePath();

				File dstDir = new File(sdcardPath + "/cloplayer");
				dstDir.mkdirs();

				File dstFile = new File(sdcardPath + "/cloplayer/" + story.getId() + "." + currentLine + ".audio.wav");
				DataOutputStream outFile;
				try {
					outFile = new DataOutputStream(new FileOutputStream(dstFile));
					outFile.write(byteArray, 0, byteArray.length);
					outFile.close();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				story.notifyAll();
			}

			currentLine++;

			Log.e("DownloadTask", "DownloadProgress : " + currentLine);

			values = new ContentValues();
			values.put(MySQLiteHelper.COLUMN_DOWNLOAD_PROGRESS, currentLine);
			CloplayerService.getInstance().datasource.updateStory(story, values);
		}

		values = new ContentValues();
		values.put(MySQLiteHelper.COLUMN_STATE, Story.STATE_DOWNLOADED);
		CloplayerService.getInstance().datasource.updateStory(story, values);

		// CloplayerService.getInstance().showNotification("Downloading Complete : "
		// + story.getHeadline(), "Downloading Complete : " +
		// story.getHeadline());
	}

	@Override
	protected void onPostExecute(String result) {

	}

}
