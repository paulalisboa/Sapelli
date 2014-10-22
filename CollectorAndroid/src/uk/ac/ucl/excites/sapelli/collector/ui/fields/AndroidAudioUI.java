/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2014 University College London - ExCiteS group
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package uk.ac.ucl.excites.sapelli.collector.ui.fields;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import uk.ac.ucl.excites.sapelli.collector.R;
import uk.ac.ucl.excites.sapelli.collector.control.Controller;
import uk.ac.ucl.excites.sapelli.collector.control.Controller.LeaveRule;
import uk.ac.ucl.excites.sapelli.collector.io.FileStorageProvider;
import uk.ac.ucl.excites.sapelli.collector.media.AudioRecorder;
import uk.ac.ucl.excites.sapelli.collector.model.Field;
import uk.ac.ucl.excites.sapelli.collector.model.fields.AudioField;
import uk.ac.ucl.excites.sapelli.collector.ui.AndroidControlsUI;
import uk.ac.ucl.excites.sapelli.collector.ui.CollectorView;
import uk.ac.ucl.excites.sapelli.collector.ui.PickerView;
import uk.ac.ucl.excites.sapelli.collector.ui.animation.ClickAnimator;
import uk.ac.ucl.excites.sapelli.collector.ui.items.AudioItem;
import uk.ac.ucl.excites.sapelli.collector.ui.items.FileImageItem;
import uk.ac.ucl.excites.sapelli.collector.ui.items.ImageItem;
import uk.ac.ucl.excites.sapelli.collector.ui.items.Item;
import uk.ac.ucl.excites.sapelli.collector.ui.items.ResourceImageItem;
import uk.ac.ucl.excites.sapelli.collector.util.ColourHelpers;
import uk.ac.ucl.excites.sapelli.collector.util.ScreenMetrics;
import uk.ac.ucl.excites.sapelli.shared.io.FileHelpers;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;

/**
 * A subclass of AndroidMediaUI which allows for the capture and review of 
 * audio recordings from the device's microphone.
 * 
 * @author mstevens, Michalis Vitos, Julia, benelliott
 *
 */
public class AndroidAudioUI extends AndroidMediaUI<AudioField> {

	private volatile Boolean recording = false;

	private static final String TAG = "AndroidAudioUI";
	private static final int VOLUME_DISPLAY_WIDTH_DP = 120;

	private AudioRecorder audioRecorder;
	private AudioReviewPicker audioReviewPicker;
	private VolumeDisplaySurfaceView volumeDisplay;

	public AndroidAudioUI(AudioField field, Controller controller, CollectorView collectorUI) {
		super(field, controller, collectorUI);
	}

	/**
	 * Start writing audio data to the media file, and start displaying the volume level.
	 * @return whether or not recording was started successfully.
	 */
	private boolean startRecording()
	{
		try
		{
			audioRecorder = new AudioRecorder(captureFile);
			audioRecorder.start();
			volumeDisplay.start();
		}
		catch(IOException ioe)
		{
			Log.e(TAG, "Could not get audio file.", ioe);
			attachMedia(null); //TODO remove?
			if (isValid(controller.getCurrentRecord()))
				controller.goForward(false);
			else
				controller.goToCurrent(LeaveRule.UNCONDITIONAL_NO_STORAGE);
			return false;
		}
		catch(Exception e)
		{
			Log.e(TAG, "Could not start audio recording.", e);
			attachMedia(null);
			if (isValid(controller.getCurrentRecord()))
				controller.goForward(false);
			else
				controller.goToCurrent(LeaveRule.UNCONDITIONAL_NO_STORAGE);
			return false; // !!!
		}
		return true;		
	}

	/**
	 * Stop writing audio data to the media file, and stop displaying the volume level.
	 */
	private void stopRecording()
	{
		try
		{
			volumeDisplay.stop();
			audioRecorder.stop();
		}
		catch(Exception e)
		{
			Log.e(TAG, "Error on stopping audio recording.", e);
		}
		finally
		{
			audioRecorder = null;
		}
	}

	/**
	 * If not already recording, start recording. Else stop recording and attach the media file 
	 * to the field.
	 */
	@Override
	void onCapture() {
		synchronized(recording) {
			if (!recording) {
				// start recording
				minimiseCaptureButton(); // show volume levels while recording
				captureFile = field.getNewAttachmentFile(controller.getFileStorageProvider(), controller.getCurrentRecord());
				startRecording();
				recording = true;
			} else {
				// stop recording
				stopRecording();
				// a capture has been made so show it for review:
				attachMedia(captureFile);
				captureFile = null;
				recording = false;
				if (field.isShowReview())
					controller.goToCurrent(LeaveRule.UNCONDITIONAL_WITH_STORAGE);
				else
					controller.goForward(true);
			}
		}
		// always allow other click events after this completes (so recording can be stopped by pressing again):
		releaseClick(); 
	}

	@Override
	void onDiscard() {
		if (audioReviewPicker != null)
			audioReviewPicker.finalise();
	}

	/**
	 * If not currently recording, will return a "start recording" button. If currently recording, will return a
	 * "stop recording" button.
	 */
	@Override
	ImageItem generateCaptureButton(Context context) {
		ImageItem captureButton = null;
		if (!recording) {
			// recording hasn't started yet, so present "record" button
			File captureImgFile = controller.getProject().getImageFile(controller.getFileStorageProvider(),field.getCaptureButtonImageRelativePath());
			if(FileHelpers.isReadableFile(captureImgFile))
				// use a custom audio capture image if available
				captureButton = new FileImageItem(captureImgFile);
			else
				// otherwise just use the default resource
				captureButton = new ResourceImageItem(context.getResources(), R.drawable.button_audio_capture_svg);
		}
		else {
			// recording started, so present "stop" button instead
			File stopImgFile = controller.getProject().getImageFile(controller.getFileStorageProvider(),field.getStopRecImageRelativePath());
			if(FileHelpers.isReadableFile(stopImgFile))
				captureButton = new FileImageItem(stopImgFile);
			else
				captureButton = new ResourceImageItem(context.getResources(), R.drawable.button_stop_audio_svg);
		}
		captureButton.setBackgroundColor(ColourHelpers.ParseColour(field.getBackgroundColor(), Field.DEFAULT_BACKGROUND_COLOR));
		return captureButton;
	}

	@Override
	List<Item> getMediaItems(FileStorageProvider fileStorageProvider, Record record) {
		List<Item> items = new ArrayList<Item>();
		for (File f : field.getAttachments(fileStorageProvider, record)) {
			items.add(new AudioItem(f));
		}
		return items;
	}

	@Override
	void populateCaptureLayout(ViewGroup captureLayout) {
		captureLayout.removeAllViews();
		volumeDisplay = new VolumeDisplaySurfaceView(captureLayout.getContext());
		int width = ScreenMetrics.ConvertDipToPx(captureLayout.getContext(), VOLUME_DISPLAY_WIDTH_DP);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width,LinearLayout.LayoutParams.MATCH_PARENT);
		params.gravity = Gravity.CENTER_HORIZONTAL;
		captureLayout.addView(volumeDisplay, params);
		// not recording yet, so set capture button to take up whole screen:
		maximiseCaptureButton();
	}

	@Override
	void populateReviewLayout(ViewGroup reviewLayout, File mediaFile) {
		reviewLayout.removeAllViews();
		// create buttons for playing the newly captured audio:
		audioReviewPicker = new AudioReviewPicker(reviewLayout.getContext(), mediaFile);
		// add picker to container:
		reviewLayout.addView(audioReviewPicker);
	}

	@Override
	protected void cancel() {
		super.cancel();
		synchronized(recording) {
			if(audioRecorder != null) {
				stopRecording();
			}
			recording = false;
		}
		if (audioReviewPicker != null)
			audioReviewPicker.finalise();
		audioReviewPicker = null;
		volumeDisplay = null;
	}

	/**
	 * A simple subclass of PickerView that provides play/stop functionality when a recording is being reviewed.
	 * 
	 * @author benelliott
	 */
	private class AudioReviewPicker extends PickerView implements OnItemClickListener, MediaPlayer.OnCompletionListener {

		private MediaPlayer mediaPlayer = new MediaPlayer();
		private Runnable buttonAction;
		private final ImageItem playAudioButton;
		private final ImageItem stopAudioButton;
		private volatile Boolean playing = false;


		public AudioReviewPicker(Context context, File audioFile) {
			super(context);
			try {
				mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
				mediaPlayer.setDataSource(context, Uri.fromFile(audioFile));
				mediaPlayer.setOnCompletionListener(this);
			} catch (IOException e) {
				Log.e(TAG, "Could not play audio file.");
				e.printStackTrace();
			}
			File playImgFile = controller.getProject().getImageFile(controller.getFileStorageProvider(),field.getPlayAudioImageRelativePath());
			if(FileHelpers.isReadableFile(playImgFile))
				playAudioButton = new FileImageItem(playImgFile);
			else
				playAudioButton = new ResourceImageItem(context.getResources(), R.drawable.button_play_audio_svg);
			playAudioButton.setBackgroundColor(ColourHelpers.ParseColour(field.getBackgroundColor(), Field.DEFAULT_BACKGROUND_COLOR));

			File stopImgFile = controller.getProject().getImageFile(controller.getFileStorageProvider(),field.getStopAudioImageRelativePath());
			if(FileHelpers.isReadableFile(stopImgFile))
				stopAudioButton = new FileImageItem(stopImgFile);
			else
				stopAudioButton = new ResourceImageItem(context.getResources(), R.drawable.button_stop_audio_svg);
			stopAudioButton.setBackgroundColor(ColourHelpers.ParseColour(field.getBackgroundColor(), Field.DEFAULT_BACKGROUND_COLOR));

			setNumColumns(1);

			// TODO rework this:
			setItemDimensionsPx(
					LayoutParams.MATCH_PARENT,
					collectorUI.getFieldUIPartHeightPx(
							collectorUI.getFieldUIHeightPx() - ScreenMetrics.ConvertDipToPx(context, AndroidControlsUI.CONTROL_HEIGHT_DIP) - collectorUI.getSpacingPx(),1));
			// height of picker = available UI height - height of bottom control bar

			setOnItemClickListener(this);

			buttonAction = new Runnable() {
				// only one button - the play/stop button
				public void run() {
					synchronized(playing) {
						if (!playing) {
							// if not playing, then start playing audio
							playAudio();
							playing = true;
						} 
						else {
							// if already playing, then stop audio
							stopAudio();
							playing = false;
						}
					}
					handlingClick.release();
				}
			};

			getAdapter().addItem(playAudioButton);
		}

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id) {
			if (handlingClick.tryAcquire()) {
				// Execute the "press" animation if allowed, then perform the action: 
				if(controller.getCurrentForm().isClickAnimation())
					(new ClickAnimator(buttonAction, view, collectorUI)).execute(); //execute animation and the action afterwards
				else
					buttonAction.run(); //perform task now (animation is disabled)
			}
		}
		/**
		 * Start playing audio and display the "stop" button.
		 */
		private void playAudio() {
			// play the audio: 
			try {
				mediaPlayer.prepare();
				mediaPlayer.start();
			} catch (IOException e) {
				Log.e(TAG, "Could not play audio file.");
				e.printStackTrace();
			}
			// present the play button to the user:
			getAdapter().clear();
			getAdapter().addItem(stopAudioButton);
			getAdapter().notifyDataSetChanged();
		}

		/**
		 * Stop playing audio and display the "play" button.
		 */
		private void stopAudio() {
			// stop the audio:
			mediaPlayer.stop();
			// present the play button to the user:
			getAdapter().clear();
			getAdapter().addItem(playAudioButton);
			getAdapter().notifyDataSetChanged();
		}

		/**
		 * Release the media player.
		 */
		private void finalise() {
			synchronized(playing) {
				if (mediaPlayer != null)
					mediaPlayer.release();
				mediaPlayer = null;
				playing = false;
			}
		}

		/**
		 * When the media player finishes playing the recording, set it to the "stopped" state
		 * so the user can play it again.
		 */
		@Override
		public void onCompletion(MediaPlayer mp) {
			// called when the media player finishes playing its media file
			synchronized(playing) {
				// go from PlaybackCompleted to Stopped
				stopAudio();
				playing = false;
			}
		}
	}

	/**
	 * A SurfaceView that displays a simple visualisation of the current audio amplitude,
	 * to be used when recording has been started.
	 * 
	 * @author benelliott
	 */
	private class VolumeDisplaySurfaceView extends SurfaceView {

		private static final int COLOR_BACKGROUND = Color.BLACK;
		private static final int COLOR_INACTIVE_LEVEL = Color.DKGRAY;
		private final int COLOR_ACTIVE_LEVEL = Color.rgb(0, 204, 0);
		private static final int UPDATE_FREQUENCY_MILLISEC = 200;
		private static final double MAX_AMPLITUDE = 20000D; // TODO might need tweaking
		private static final int NUM_LEVELS = 50;
		private static final int LEVEL_PADDING = 5;
		Timer timer;
		private Paint paint;
		private int amplitude;
		private int levelsToIlluminate;
		private float levelHeight;

		public VolumeDisplaySurfaceView(Context context) {
			super(context);
			paint = new Paint();
			getHolder().addCallback(new Callback(){

				@Override
				public void surfaceCreated(SurfaceHolder holder) {
				}

				@SuppressLint("WrongCall")
				@Override
				public void surfaceChanged(SurfaceHolder holder, int format,
						int width, int height) {
					Canvas c = holder.lockCanvas(null);
					onDraw(c);
					holder.unlockCanvasAndPost(c);
					levelHeight = ((float)getHeight() / NUM_LEVELS) - LEVEL_PADDING;
				}

				@Override
				public void surfaceDestroyed(SurfaceHolder holder) {
				}});
		}

		/**
		 * Illuminate the number of levels specified by levelsToIlluminate.
		 */
		@Override
		protected void onDraw(Canvas canvas) {
			// wipe everything off: 
			canvas.drawColor(COLOR_BACKGROUND);
			// draw the levels:
			paint.setColor(COLOR_ACTIVE_LEVEL);

			for (int i = 0; i < NUM_LEVELS; i++) {

				if (i == levelsToIlluminate)
					paint.setColor(COLOR_INACTIVE_LEVEL);

				float bottom = getHeight() - i * (levelHeight + LEVEL_PADDING); // remember top-left is (0,0)
				canvas.drawRect(0, bottom + levelHeight, getWidth(), bottom, paint);
			}

		}

		/**
		 * Start the TimerTask to visualise the volume.
		 */
		private void start() {
			timer = new Timer();
			timer.schedule(new VolumeDisplayTask(), 0, UPDATE_FREQUENCY_MILLISEC);
		}

		/**
		 * Stop the TimerTask that visualises the volume.
		 */
		private void stop() {
			timer.cancel();
			timer = null;
		}

		/**
		 * A TimerTask that periodically checks the current audio amplitude, calculates
		 * the number of "levels" that should be illuminated, and then draws them to the screen.
		 * 
		 * @author benelliott
		 */
		private class VolumeDisplayTask extends TimerTask {

			@SuppressLint("WrongCall")
			@Override
			public void run() {
				amplitude = audioRecorder.getMaxAmplitude();
				// see how loud it currently is relative to MAX_AMPLITUDE, then multiply that fraction
				// by the number of available levels:
				levelsToIlluminate = (int) (((double)amplitude / MAX_AMPLITUDE) * (double) NUM_LEVELS);
				if (levelsToIlluminate > NUM_LEVELS)
					levelsToIlluminate = NUM_LEVELS;
				Canvas c = null;
				try {
					c = getHolder().lockCanvas();
					synchronized (getHolder()) {
						if (c != null)
							onDraw(c);
					}
				} finally {
					if (c != null) {
						getHolder().unlockCanvasAndPost(c);
					}
				}
			}	

		}
	}
}
