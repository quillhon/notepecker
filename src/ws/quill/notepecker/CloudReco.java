/*==============================================================================
            Copyright (c) 2012 QUALCOMM Austria Research Center GmbH.
            All Rights Reserved.
            Qualcomm Confidential and Proprietary

@file
    CloudReco.java

@brief
    CloudReco Activity - Augmentation view - for CloudReco Sample Application

==============================================================================*/

package ws.quill.notepecker;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import org.apache.http.util.ByteArrayBuffer;

import ws.quill.notepecker.db.DatabaseContract;
import ws.quill.notepecker.db.DatabaseHandler;
import ws.quill.notepecker.model.Note;
import ws.quill.notepecker.utils.DebugLog;
import ws.quill.notepecker.view.NoteOverylay;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.qualcomm.QCAR.QCAR;

/** The main activity for the CloudReco sample. */
public class CloudReco extends Activity {
	// Defines the Server URL to get the books data
	private static final String mServerURL = "https://ar.qualcomm.at/samples/cloudreco/json/";

	// Different screen orientations supported by the CloudReco system.
	public static final int SCREEN_ORIENTATION_LANDSCAPE = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
	public static final int SCREEN_ORIENTATION_PORTRAIT = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
	public static final int SCREEN_ORIENTATION_AUTOROTATE = ActivityInfo.SCREEN_ORIENTATION_SENSOR;

	// Change this value to switch between different screen orientations
	public static int screenOrientation = SCREEN_ORIENTATION_AUTOROTATE;

	// Application status constants:
	private static final int APPSTATUS_UNINITED = -1;
	private static final int APPSTATUS_INIT_APP = 0;
	private static final int APPSTATUS_INIT_QCAR = 1;
	private static final int APPSTATUS_INIT_TRACKER = 2;
	private static final int APPSTATUS_INIT_APP_AR = 3;
	private static final int APPSTATUS_INIT_CLOUDRECO = 4;
	private static final int APPSTATUS_INITED = 5;
	private static final int APPSTATUS_CAMERA_STOPPED = 6;
	private static final int APPSTATUS_CAMERA_RUNNING = 7;

	// Focus modes:
	private static final int FOCUS_MODE_NORMAL = 0;
	private static final int FOCUS_MODE_CONTINUOUS_AUTO = 1;

	// Name of the native dynamic libraries to load:
	private static final String NATIVE_LIB_SAMPLE = "CloudReco";
	private static final String NATIVE_LIB_QCAR = "QCAR";

	// Stores the current status of the target ( if is being displayed or not )
	private static final int BOOKINFO_NOT_DISPLAYED = 0;
	private static final int BOOKINFO_IS_DISPLAYED = 1;

	// These codes match the ones defined in TargetFinder.h
	static final int INIT_SUCCESS = 2;
	static final int INIT_ERROR_NO_NETWORK_CONNECTION = -1;
	static final int INIT_ERROR_SERVICE_NOT_AVAILABLE = -2;
	static final int UPDATE_ERROR_AUTHORIZATION_FAILED = -1;
	static final int UPDATE_ERROR_PROJECT_SUSPENDED = -2;
	static final int UPDATE_ERROR_NO_NETWORK_CONNECTION = -3;
	static final int UPDATE_ERROR_SERVICE_NOT_AVAILABLE = -4;
	static final int UPDATE_ERROR_BAD_FRAME_QUALITY = -5;
	static final int UPDATE_ERROR_UPDATE_SDK = -6;
	static final int UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE = -7;
	static final int UPDATE_ERROR_REQUEST_TIMEOUT = -8;

	// Handles Codes to display/Hide views
	static final int HIDE_STATUS_BAR = 0;
	static final int SHOW_STATUS_BAR = 1;

	static final int HIDE_2D_OVERLAY = 0;
	static final int SHOW_2D_OVERLAY = 1;

	static final int HIDE_LOADING_DIALOG = 0;
	static final int SHOW_LOADING_DIALOG = 1;

	// Augmented content status
	private int mBookInfoStatus = BOOKINFO_NOT_DISPLAYED;

	// Status Bar Text
	private String mStatusBarText;

	// Active Book Data
	private ArrayList<Note> mNotesData;
	private int mPageNumber;
	private int mBookID;
	private View mLoadingDialogContainer;
	private Texture mBookDataTexture;

	// Indicates if the app is currently loading the book data
	private boolean mIsLoadingBookData = false;

	// AsyncTask to get book data from a json object
	private GetBookDataTask mGetBookDataTask;

	// Our OpenGL view:
	private QCARSampleGLView mGlView;

	// Our renderer:
	private CloudRecoRenderer mRenderer;

	// Display size of the device
	private int mScreenWidth = 0;
	private int mScreenHeight = 0;

	// Constant representing invalid screen orientation to trigger a query:
	private static final int INVALID_SCREEN_ROTATION = -1;

	// Last detected screen rotation:
	private int mLastScreenRotation = INVALID_SCREEN_ROTATION;

	// The current application status
	private int mAppStatus = APPSTATUS_UNINITED;

	// The async tasks to initialize the QCAR SDK
	private InitQCARTask mInitQCARTask;
	private InitCloudRecoTask mInitCloudRecoTask;

	// An object used for synchronizing QCAR initialization, dataset loading and
	// the Android onDestroy() life cycle event. If the application is destroyed
	// while a data set is still being loaded, then we wait for the loading
	// operation to finish before shutting down QCAR.
	private Object mShutdownLock = new Object();

	// QCAR initialization flags
	private int mQCARFlags = 0;

	// View overlays to be displayed in the Augmented View
	private RelativeLayout mUILayout;
	private TextView mStatusBar;
	private Button mCloseButton;

	// Error message handling:
	private int mlastErrorCode = 0;

	// Alert Dialog used to display SDK errors
	private AlertDialog mErrorDialog;

	// Contextual Menu Options for Camera Flash - Autofocus
	private boolean mFlash = false;
	private boolean mContAutofocus = false;

	// Detects the double tap gesture for launching the Camera menu
	private GestureDetector mGestureDetector;

	// size of the Texture to be generated with the book data
	private static int mTextureSize = 768;

	// Database helper
	private SQLiteOpenHelper mDBHelper;

	/** Static initializer block to load native libraries on start-up. */
	static {
		loadLibrary(NATIVE_LIB_QCAR);
		loadLibrary(NATIVE_LIB_SAMPLE);
	}

	/** Activates the Flash */
	private native boolean activateFlash(boolean flash);

	/** Applies auto focus if supported by the current device */
	private native boolean autofocus();

	/** Setups the focus mode */
	private native boolean setFocusMode(int mode);

	/** Checks if the CloudReco is already Started */
	private native boolean isCloudRecoStarted();

	/** Native tracker initialization and deinitialization. */
	public native int initTracker();

	public native void deinitTracker();

	/** Native functions to init and deinit cloud-based recognition. */
	public native int initCloudReco();

	public native void deinitCloudReco();

	/** Native function to enter CloudReco scanning mode */
	public native void enterScanningModeNative();

	/** Native function to stop CloudReco scanning mode */
	public native void enterContentModeNative();

	/** Native methods for starting and stopping the camera. */
	private native void startCamera();

	private native void stopCamera();

	/**
	 * Native method for setting / updating the projection matrix for AR content
	 * rendering
	 */
	private native void setProjectionMatrix();

	/** Native function to de-initialize the application. */
	private native void deinitApplicationNative();

	/** Tells native code whether we are in portrait or landscape mode */
	private native void setActivityPortraitMode(boolean isPortrait);

	/** Native function to initialize the application. */
	private native void initApplicationNative(int width, int height);

	/**
	 * Native function to generate the OpenGL Texture Object in the renderFrame
	 * thread
	 */
	public native void productTextureIsCreated();

	/** Sets current device Scale factor based on screen dpi */
	public native void setDeviceDPIScaleFactor(float dpiScaleIndicator);

	/** Cleans the lastTargetTrackerId variable in Native Code */
	public native void cleanTargetTrackedId();

	/**
	 * Crates a Handler to Show/Hide the status bar overlay from an UI Thread
	 */
	static class StatusBarHandler extends Handler {
		private final WeakReference<CloudReco> mCloudReco;

		StatusBarHandler(CloudReco cloudReco) {
			mCloudReco = new WeakReference<CloudReco>(cloudReco);
		}

		public void handleMessage(Message msg) {
			CloudReco cloudReco = mCloudReco.get();
			if (cloudReco == null) {
				return;
			}

			if (msg.what == SHOW_STATUS_BAR) {
				cloudReco.mStatusBar.setText(cloudReco.mStatusBarText);
				cloudReco.mStatusBar.setVisibility(View.VISIBLE);
			} else {
				cloudReco.mStatusBar.setVisibility(View.GONE);
			}
		}
	}

	private Handler statusBarHandler = new StatusBarHandler(this);

	/**
	 * Creates a handler to Show/Hide the UI Overlay from an UI thread
	 */
	static class Overlay2dHandler extends Handler {
		private final WeakReference<CloudReco> mCloudReco;

		Overlay2dHandler(CloudReco cloudReco) {
			mCloudReco = new WeakReference<CloudReco>(cloudReco);
		}

		public void handleMessage(Message msg) {
			CloudReco cloudReco = mCloudReco.get();
			if (cloudReco == null) {
				return;
			}

			if (cloudReco.mCloseButton != null) {
				if (msg.what == SHOW_2D_OVERLAY) {
					cloudReco.mCloseButton.setVisibility(View.VISIBLE);
				} else {
					cloudReco.mCloseButton.setVisibility(View.GONE);
				}
			}
		}
	}

	private Handler overlay2DHandler = new Overlay2dHandler(this);

	/**
	 * Creates a handler to update the status of the Loading Dialog from an UI
	 * thread
	 */
	static class LoadingDialogHandler extends Handler {
		private final WeakReference<CloudReco> mCloudReco;

		LoadingDialogHandler(CloudReco cloudReco) {
			mCloudReco = new WeakReference<CloudReco>(cloudReco);
		}

		public void handleMessage(Message msg) {
			CloudReco cloudReco = mCloudReco.get();
			if (cloudReco == null) {
				return;
			}

			if (msg.what == SHOW_LOADING_DIALOG) {
				cloudReco.mLoadingDialogContainer.setVisibility(View.VISIBLE);

			} else if (msg.what == HIDE_LOADING_DIALOG) {
				cloudReco.mLoadingDialogContainer.setVisibility(View.GONE);
			}
		}
	}

	private Handler loadingDialogHandler = new LoadingDialogHandler(this);

	/** An async task to initialize QCAR asynchronously. */
	private class InitQCARTask extends AsyncTask<Void, Integer, Boolean> {
		// Initialize with invalid value
		private int mProgressValue = -1;

		protected Boolean doInBackground(Void... params) {
			// Prevent the onDestroy() method to overlap with initialization:
			synchronized (mShutdownLock) {
				QCAR.setInitParameters(CloudReco.this, mQCARFlags);

				do {
					// QCAR.init() blocks until an initialization step is
					// complete,
					// then it proceeds to the next step and reports progress in
					// percents (0 ... 100%)
					// If QCAR.init() returns -1, it indicates an error.
					// Initialization is done when progress has reached 100%.
					mProgressValue = QCAR.init();

					// Publish the progress value:
					publishProgress(mProgressValue);

					// We check whether the task has been canceled in the
					// meantime
					// (by calling AsyncTask.cancel(true))
					// and bail out if it has, thus stopping this thread.
					// This is necessary as the AsyncTask will run to completion
					// regardless of the status of the component that started
					// is.
				} while (!isCancelled() && mProgressValue >= 0
						&& mProgressValue < 100);

				return (mProgressValue > 0);
			}
		}

		protected void onProgressUpdate(Integer... values) {
			// Do something with the progress value "values[0]", e.g. update
			// splash screen, progress bar, etc.
		}

		protected void onPostExecute(Boolean result) {
			// Done initializing QCAR, proceed to next application
			// initialization status:
			if (result) {
				DebugLog.LOGD("InitQCARTask::onPostExecute: QCAR initialization"
						+ " successful");

				updateApplicationStatus(APPSTATUS_INIT_TRACKER);
			} else {
				// Create dialog box for display error:
				AlertDialog dialogError = new AlertDialog.Builder(
						CloudReco.this).create();

				dialogError.setButton(DialogInterface.BUTTON_POSITIVE,
						getString(R.string.button_OK),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								// Exiting application
								System.exit(1);
							}
						});

				String logMessage;

				// NOTE: Check if initialization failed because the device is
				// not supported. At this point the user should be informed
				// with a message.
				if (mProgressValue == QCAR.INIT_DEVICE_NOT_SUPPORTED) {
					logMessage = "Failed to initialize QCAR because this "
							+ "device is not supported.";
				} else {
					logMessage = "Failed to initialize QCAR.";
				}

				// Log error:
				DebugLog.LOGE("InitQCARTask::onPostExecute: " + logMessage
						+ " Exiting.");

				// Show dialog box with error message:
				dialogError.setMessage(logMessage);
				dialogError.show();
			}
		}
	}

	/** An async task to initialize cloud-based recognition asynchronously. */
	private class InitCloudRecoTask extends AsyncTask<Void, Integer, Boolean> {
		// Initialize with invalid value
		private int mInitResult = -1;

		protected Boolean doInBackground(Void... params) {
			// Prevent the onDestroy() method to overlap:
			synchronized (mShutdownLock) {
				// Init cloud-based recognition:
				mInitResult = initCloudReco();
				return mInitResult == INIT_SUCCESS;
			}
		}

		protected void onPostExecute(Boolean result) {
			DebugLog.LOGD("InitCloudRecoTask::onPostExecute: execution "
					+ (result ? "successful" : "failed"));

			if (result) {
				// Done loading the tracker, update application status:
				updateApplicationStatus(APPSTATUS_INITED);

				// Hides the Loading Dialog
				loadingDialogHandler.sendEmptyMessage(HIDE_LOADING_DIALOG);

				mUILayout.setBackgroundColor(Color.TRANSPARENT);
			} else {
				// Create dialog box for display error:
				AlertDialog dialogError = new AlertDialog.Builder(
						CloudReco.this).create();
				dialogError.setButton(DialogInterface.BUTTON_POSITIVE,
						getString(R.string.button_OK),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								// Exiting application
								System.exit(1);
							}
						});

				// Show dialog box with error message:
				String logMessage = "Failed to initialize CloudReco.";

				// NOTE: Check if initialization failed because the device is
				// not supported. At this point the user should be informed
				// with a message.
				if (mInitResult == INIT_ERROR_NO_NETWORK_CONNECTION)
					logMessage = "Failed to initialize CloudReco because "
							+ "the device has no network connection.";
				else if (mInitResult == INIT_ERROR_SERVICE_NOT_AVAILABLE)
					logMessage = "Failed to initialize CloudReco because "
							+ "the service is not available.";

				dialogError.setMessage(logMessage);
				dialogError.show();
			}
		}
	}

	/** Stores screen dimensions */
	private void storeScreenDimensions() {
		// Query display dimensions
		DisplayMetrics metrics = new DisplayMetrics();

		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		mScreenWidth = metrics.widthPixels;
		mScreenHeight = metrics.heightPixels;
	}

	/**
	 * Called when the activity first starts or needs to be recreated after
	 * resuming the application or a configuration change.
	 */
	protected void onCreate(Bundle savedInstanceState) {
		DebugLog.LOGD("CloudReco::onCreate");
		super.onCreate(savedInstanceState);

		// Get book ID
		// mBookID = getIntent().getExtras().getInt("bookID");
		mBookID = 1;

		// Query the QCAR initialization flags:
		mQCARFlags = getInitializationFlags();

		// Update the application status to start initializing application
		updateApplicationStatus(APPSTATUS_INIT_APP);

		// Creates the GestureDetector listener for processing double tap
		mGestureDetector = new GestureDetector(this, new GestureListener());

		// Gets the current device screen density
		float dpiScaleIndicator = getApplicationContext().getResources()
				.getDisplayMetrics().density;

		// Sets the device scale density to the native code
		setDeviceDPIScaleFactor(dpiScaleIndicator);
	}

	/** Configure QCAR with the desired version of OpenGL ES. */
	private int getInitializationFlags() {
		return QCAR.GL_20;
	}

	/** Called when the activity will start interacting with the user. */
	protected void onResume() {
		DebugLog.LOGD("CloudReco::onResume");
		super.onResume();

		// QCAR-specific resume operation
		QCAR.onResume();

		// We may start the camera only if the QCAR SDK has already been
		// initialized
		if (mAppStatus == APPSTATUS_CAMERA_STOPPED) {
			updateApplicationStatus(APPSTATUS_CAMERA_RUNNING);

			// Reactivate flash if it was active before pausing the app
			if (mFlash) {
				boolean result = activateFlash(mFlash);
				DebugLog.LOGI("Turning flash " + (mFlash ? "ON" : "OFF") + " "
						+ (result ? "WORKED" : "FAILED") + "!!");
			}
		}

		// Resume the GL view:
		if (mGlView != null) {
			mGlView.setVisibility(View.VISIBLE);
			mGlView.onResume();
		}

		mBookInfoStatus = BOOKINFO_NOT_DISPLAYED;

		// By default the 2D Overlay is hidden
		hide2DOverlay();
	}

	/** Callback for configuration changes the activity handles itself */
	public void onConfigurationChanged(Configuration config) {
		DebugLog.LOGD("CloudReco::onConfigurationChanged");
		super.onConfigurationChanged(config);

		// updates screen orientation
		updateActivityOrientation();

		storeScreenDimensions();

		// Invalidate screen rotation to trigger query upon next render call:
		mLastScreenRotation = INVALID_SCREEN_ROTATION;
	}

	/** Called when the system is about to start resuming a previous activity. */
	protected void onPause() {
		DebugLog.LOGD("CloudReco::onPause");
		super.onPause();

		// Pauses the OpenGLView
		if (mGlView != null) {
			mGlView.setVisibility(View.INVISIBLE);
			mGlView.onPause();
		}

		// Updates the Application current Status
		if (mAppStatus == APPSTATUS_CAMERA_RUNNING) {
			updateApplicationStatus(APPSTATUS_CAMERA_STOPPED);
		}

		// Disable flash when paused
		if (mFlash) {
			mFlash = false;
			activateFlash(mFlash);
		}

		// QCAR-specific pause operation
		QCAR.onPause();
	}

	/** The final call you receive before your activity is destroyed. */
	protected void onDestroy() {
		DebugLog.LOGD("CloudReco::onDestroy");
		super.onDestroy();

		// Cancel potentially running tasks
		if (mInitQCARTask != null
				&& mInitQCARTask.getStatus() != InitQCARTask.Status.FINISHED) {
			mInitQCARTask.cancel(true);
			mInitQCARTask = null;
		}

		if (mInitCloudRecoTask != null
				&& mInitCloudRecoTask.getStatus() != InitCloudRecoTask.Status.FINISHED) {
			mInitCloudRecoTask.cancel(true);
			mInitCloudRecoTask = null;
		}

		// Ensure that all asynchronous operations to initialize QCAR and
		// loading
		// the tracker datasets do not overlap:
		synchronized (mShutdownLock) {

			// Do application deinitialization in native code
			deinitApplicationNative();

			// Destroy the tracking data set:
			deinitCloudReco();

			// Deinit the tracker:
			deinitTracker();

			// Deinitialize QCAR SDK
			QCAR.deinit();
		}

		System.gc();
	}

	/**
	 * NOTE: this method is synchronized because of a potential concurrent
	 * access by VisualSearch::onResume() and InitQCARTask::onPostExecute().
	 */
	private synchronized void updateApplicationStatus(int appStatus) {
		// Exit if there is no change in status
		if (mAppStatus == appStatus)
			return;

		// Store new status value
		mAppStatus = appStatus;

		// Execute application state-specific actions
		switch (mAppStatus) {
		case APPSTATUS_INIT_APP:
			// Initialize application elements that do not rely on QCAR
			// initialization
			initApplication();

			// Proceed to next application initialization status
			updateApplicationStatus(APPSTATUS_INIT_QCAR);
			break;

		case APPSTATUS_INIT_QCAR:
			// Initialize QCAR SDK asynchronously to avoid blocking the
			// main (UI) thread.
			// This task instance must be created and invoked on the UI
			// thread and it can be executed only once!
			try {
				mInitQCARTask = new InitQCARTask();
				mInitQCARTask.execute();
			} catch (Exception e) {
				DebugLog.LOGE("Initializing QCAR SDK failed");
			}
			break;

		case APPSTATUS_INIT_TRACKER:
			// Initialize the ImageTracker
			if (initTracker() > 0) {
				// Proceed to next application initialization status
				updateApplicationStatus(APPSTATUS_INIT_APP_AR);

			}
			break;

		case APPSTATUS_INIT_APP_AR:
			// Initialize Augmented Reality-specific application elements
			// that may rely on the fact that the QCAR SDK has been
			// already initialized
			initApplicationAR();

			// Proceed to next application initialization status
			updateApplicationStatus(APPSTATUS_INIT_CLOUDRECO);
			break;

		case APPSTATUS_INIT_CLOUDRECO:
			// Initialize visual search
			//
			// This task instance must be created and invoked on the UI
			// thread and it can be executed only once!
			try {
				mInitCloudRecoTask = new InitCloudRecoTask();
				mInitCloudRecoTask.execute();
			} catch (Exception e) {
				DebugLog.LOGE("Failed to initialize CloudReco");
			}
			break;

		case APPSTATUS_INITED:
			// Hint to the virtual machine that it would be a good time to
			// run the garbage collector.
			//
			// NOTE: This is only a hint. There is no guarantee that the
			// garbage collector will actually be run.
			System.gc();

			// Activate the renderer
			mRenderer.mIsActive = true;

			// Now add the GL surface view. It is important
			// that the OpenGL ES surface view gets added
			// BEFORE the camera is started and video
			// background is configured.
			addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT,
					LayoutParams.MATCH_PARENT));

			// Start the camera:
			updateApplicationStatus(APPSTATUS_CAMERA_RUNNING);
			mUILayout.bringToFront();

			break;

		case APPSTATUS_CAMERA_STOPPED:
			// Call the native function to stop the camera
			stopCamera();
			break;

		case APPSTATUS_CAMERA_RUNNING:
			// Call the native function to start the camera
			startCamera();

			// Set continuous auto-focus if supported by the device,
			// otherwise default back to regular auto-focus mode.
			// This will be activated by a tap to the screen in this
			// application.
			if (!setFocusMode(FOCUS_MODE_CONTINUOUS_AUTO)) {
				setFocusMode(FOCUS_MODE_NORMAL);
				mContAutofocus = false;
			} else {
				mContAutofocus = true;
			}
			break;

		default:
			throw new RuntimeException("Invalid application state");
		}
	}

	/** Initialize application GUI elements that are not related to AR. */
	private void initApplication() {
		// create the SQL helper object;
		mDBHelper = new DatabaseHandler(this);

		// Set the screen orientation from activity setting:
		int screenOrientation = CloudReco.screenOrientation;

		// This is necessary for enabling AutoRotation in the Augmented View
		if (screenOrientation == CloudReco.SCREEN_ORIENTATION_AUTOROTATE) {
			// NOTE: We use reflection here to see if the current platform
			// supports the full sensor mode (available only on Gingerbread
			// and above.
			try {
				// SCREEN_ORIENTATION_FULL_SENSOR is required to allow all
				// 4 screen rotations if API level >= 9:
				Field fullSensorField = ActivityInfo.class
						.getField("SCREEN_ORIENTATION_FULL_SENSOR");
				screenOrientation = fullSensorField.getInt(null);
			} catch (NoSuchFieldException e) {
				// App is running on API level < 9, do nothing.
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// Apply screen orientation
		setRequestedOrientation(screenOrientation);

		updateActivityOrientation();

		storeScreenDimensions();

		// As long as this window is visible to the user, keep the device's
		// screen turned on and bright.
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	private void updateActivityOrientation() {
		Configuration config = getResources().getConfiguration();

		boolean isPortrait = false;

		switch (config.orientation) {
		case Configuration.ORIENTATION_PORTRAIT:
			isPortrait = true;
			break;
		case Configuration.ORIENTATION_LANDSCAPE:
			isPortrait = false;
			break;
		case Configuration.ORIENTATION_UNDEFINED:
		default:
			break;
		}

		DebugLog.LOGI("Activity is in "
				+ (isPortrait ? "PORTRAIT" : "LANDSCAPE"));
		setActivityPortraitMode(isPortrait);
	}

	/**
	 * Updates projection matrix and viewport after a screen rotation change was
	 * detected.
	 */
	public void updateRenderView() {
		int currentScreenRotation = getWindowManager().getDefaultDisplay()
				.getRotation();

		if (currentScreenRotation != mLastScreenRotation) {
			// Set projection matrix if there is already a valid one:
			if (QCAR.isInitialized()
					&& (mAppStatus == APPSTATUS_CAMERA_RUNNING)) {
				DebugLog.LOGD("CloudReco::updateRenderView");

				// Query display dimensions:
				storeScreenDimensions();

				// Update viewport via renderer:
				mRenderer.updateRendering(mScreenWidth, mScreenHeight);

				// Update projection matrix:
				setProjectionMatrix();

				// Cache last rotation used for setting projection matrix:
				mLastScreenRotation = currentScreenRotation;
			}
		}
	}

	/** Initializes AR application components. */
	private void initApplicationAR() {
		// Do application initialization in native code (e.g. registering
		// callbacks, etc.)
		initApplicationNative(mScreenWidth, mScreenHeight);

		// Create OpenGL ES view:
		int depthSize = 16;
		int stencilSize = 0;
		boolean translucent = QCAR.requiresAlpha();

		// Initialize the GLView with proper flags
		mGlView = new QCARSampleGLView(this);
		mGlView.init(mQCARFlags, translucent, depthSize, stencilSize);

		// Setups the Renderer of the GLView
		mRenderer = new CloudRecoRenderer();
		mRenderer.mActivity = this;
		mGlView.setRenderer(mRenderer);

		// Inflates the Overlay Layout to be displayed above the Camera View
		LayoutInflater inflater = LayoutInflater.from(this);
		mUILayout = (RelativeLayout) inflater.inflate(R.layout.camera_overlay,
				null, false);

		mUILayout.setVisibility(View.VISIBLE);
		mUILayout.setBackgroundColor(Color.BLACK);

		addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.MATCH_PARENT));

		// Gets a Reference to the Bottom Status Bar
		mStatusBar = (TextView) mUILayout.findViewById(R.id.overlay_status);

		// By default
		mLoadingDialogContainer = mUILayout.findViewById(R.id.loading_layout);
		mLoadingDialogContainer.setVisibility(View.VISIBLE);

		// Gets a reference to the Close Button
		mCloseButton = (Button) mUILayout
				.findViewById(R.id.overlay_close_button);

		// Sets the Close Button functionality
		mCloseButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// Updates application status
				mBookInfoStatus = BOOKINFO_NOT_DISPLAYED;

				loadingDialogHandler.sendEmptyMessage(HIDE_LOADING_DIALOG);

				// Checks if the app is currently loading a book data
				if (mIsLoadingBookData) {

					// Cancels the AsyncTask
					mGetBookDataTask.cancel(true);
					mIsLoadingBookData = false;

					// Cleans the Target Tracker Id
					cleanTargetTrackedId();
				}

				// Enters Scanning Mode
				enterScanningMode();
			}
		});

		// As default the 2D overlay and Status bar are hidden when application
		// starts
		hide2DOverlay();
		hideStatusBar();
	}

	/** Sets the Status Bar Text in a UI thread */
	public void setStatusBarText(String statusText) {
		mStatusBarText = statusText;
		statusBarHandler.sendEmptyMessage(SHOW_STATUS_BAR);
	}

	/** Hides the Status bar 2D Overlay in a UI thread */
	public void hideStatusBar() {
		if (mStatusBar.getVisibility() == View.VISIBLE) {
			statusBarHandler.sendEmptyMessage(HIDE_STATUS_BAR);
		}
	}

	/** Shows the Status Bar 2D Overlay in a UI thread */
	public void showStatusBar() {
		if (mStatusBar.getVisibility() == View.GONE) {
			statusBarHandler.sendEmptyMessage(SHOW_STATUS_BAR);
		}
	}

	/** Returns the error message for each error code */
	private String getStatusDescString(int code) {
		if (code == UPDATE_ERROR_AUTHORIZATION_FAILED)
			return getString(R.string.UPDATE_ERROR_AUTHORIZATION_FAILED_DESC);
		if (code == UPDATE_ERROR_PROJECT_SUSPENDED)
			return getString(R.string.UPDATE_ERROR_PROJECT_SUSPENDED_DESC);
		if (code == UPDATE_ERROR_NO_NETWORK_CONNECTION)
			return getString(R.string.UPDATE_ERROR_NO_NETWORK_CONNECTION_DESC);
		if (code == UPDATE_ERROR_SERVICE_NOT_AVAILABLE)
			return getString(R.string.UPDATE_ERROR_SERVICE_NOT_AVAILABLE_DESC);
		if (code == UPDATE_ERROR_UPDATE_SDK)
			return getString(R.string.UPDATE_ERROR_UPDATE_SDK_DESC);
		if (code == UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE)
			return getString(R.string.UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE_DESC);
		if (code == UPDATE_ERROR_REQUEST_TIMEOUT)
			return getString(R.string.UPDATE_ERROR_REQUEST_TIMEOUT_DESC);
		if (code == UPDATE_ERROR_BAD_FRAME_QUALITY)
			return getString(R.string.UPDATE_ERROR_BAD_FRAME_QUALITY_DESC);
		else {
			return getString(R.string.UPDATE_ERROR_UNKNOWN_DESC);
		}
	}

	/** Returns the error message for each error code */
	private String getStatusTitleString(int code) {
		if (code == UPDATE_ERROR_AUTHORIZATION_FAILED)
			return getString(R.string.UPDATE_ERROR_AUTHORIZATION_FAILED_TITLE);
		if (code == UPDATE_ERROR_PROJECT_SUSPENDED)
			return getString(R.string.UPDATE_ERROR_PROJECT_SUSPENDED_TITLE);
		if (code == UPDATE_ERROR_NO_NETWORK_CONNECTION)
			return getString(R.string.UPDATE_ERROR_NO_NETWORK_CONNECTION_TITLE);
		if (code == UPDATE_ERROR_SERVICE_NOT_AVAILABLE)
			return getString(R.string.UPDATE_ERROR_SERVICE_NOT_AVAILABLE_TITLE);
		if (code == UPDATE_ERROR_UPDATE_SDK)
			return getString(R.string.UPDATE_ERROR_UPDATE_SDK_TITLE);
		if (code == UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE)
			return getString(R.string.UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE_TITLE);
		if (code == UPDATE_ERROR_REQUEST_TIMEOUT)
			return getString(R.string.UPDATE_ERROR_REQUEST_TIMEOUT_TITLE);
		if (code == UPDATE_ERROR_BAD_FRAME_QUALITY)
			return getString(R.string.UPDATE_ERROR_BAD_FRAME_QUALITY_TITLE);
		else {
			return getString(R.string.UPDATE_ERROR_UNKNOWN_TITLE);
		}
	}

	/** Shows error messages as System dialogs */
	public void showErrorMessage(int errorCode) {
		mlastErrorCode = errorCode;

		runOnUiThread(new Runnable() {
			public void run() {
				if (mErrorDialog != null) {
					mErrorDialog.dismiss();
				}

				// Generates an Alert Dialog to show the error message
				AlertDialog.Builder builder = new AlertDialog.Builder(
						CloudReco.this);
				builder.setMessage(
						getStatusDescString(CloudReco.this.mlastErrorCode))
						.setTitle(
								getStatusTitleString(CloudReco.this.mlastErrorCode))
						.setCancelable(false)
						.setIcon(0)
						.setPositiveButton(getString(R.string.button_OK),
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int id) {
										dialog.dismiss();
									}
								});

				mErrorDialog = builder.create();
				mErrorDialog.show();
			}
		});
	}

	/**
	 * Generates a texture for the book data fecthing the book info from the
	 * specified book URL
	 */
	public void createProductTexture(String pageNumber) {
		// gets book url from parameters
		mPageNumber = Integer.parseInt(pageNumber);

		// Cleans old texture reference if necessary
		if (mBookDataTexture != null) {
			mBookDataTexture = null;

			System.gc();
		}

		// Searches for the book data in an AsyncTask
		mGetBookDataTask = new GetBookDataTask();
		mGetBookDataTask.execute();
	}

	/** Gets the book data from a JSON Object */
	private class GetBookDataTask extends AsyncTask<Void, Void, Void> {
		private static final String CHARSET = "UTF-8";

		protected void onPreExecute() {
			mIsLoadingBookData = true;

			// Shows the loading dialog
			loadingDialogHandler.sendEmptyMessage(SHOW_LOADING_DIALOG);
		}

		protected Void doInBackground(Void... params) {
			SQLiteDatabase db;

			try {
				db = mDBHelper.getReadableDatabase();

				String[] projection = { DatabaseContract.Notes.COLUMN_ID,
						DatabaseContract.Notes.COLUMN_BOOK_ID,
						DatabaseContract.Notes.CLOUMN_TEXT };

				String selection = DatabaseContract.Notes.COLUMN_BOOK_ID + "=?";
				String[] selectionArgs = { Integer.toString(mBookID) };

				Cursor c = db.query(DatabaseContract.Notes.TABLE_NAME,
						projection, selection, selectionArgs, null, null, null);

				// Generates a new Book Object with the JSON object data
				mNotesData = new ArrayList<Note>();

				if (c.moveToFirst()) {
					do {
						Note n = new Note();
						n.setId(c.getInt(0));
						n.setBookID(c.getInt(1));
						n.setText(c.getString(2));

						mNotesData.add(n);
					} while (c.moveToNext());
				}
			} catch (Exception e) {
				DebugLog.LOGD("Couldn't get notes. e: " + e);
			}

			return null;
		}

		protected void onProgressUpdate(Void... values) {

		}

		protected void onPostExecute(Void result) {
			if (mNotesData != null) {
				// Generates a View to display the book data
				NoteOverylay noteView = new NoteOverylay(CloudReco.this);
				
				// Draws the View into a Bitmap. Note we are allocating several
				// large memory buffers thus attempt to clear them as soon as
				// they are no longer required:
				Bitmap bitmap = Bitmap.createBitmap(2480, 3508,
						Bitmap.Config.ARGB_8888);

				Canvas c = new Canvas(bitmap);
				noteView.draw(c);

				// Clear the product view as it is no longer needed
				noteView = null;
				System.gc();

				// Allocate int buffer for pixel conversion and copy pixels
				int width = bitmap.getWidth();
				int height = bitmap.getHeight();

				int[] data = new int[bitmap.getWidth() * bitmap.getHeight()];
				bitmap.getPixels(data, 0, bitmap.getWidth(), 0, 0,
						bitmap.getWidth(), bitmap.getHeight());

				// Recycle the bitmap object as it is no longer needed
				bitmap.recycle();
				bitmap = null;
				c = null;
				System.gc();

				// Generates the Texture from the int buffer
				mBookDataTexture = Texture.loadTextureFromIntBuffer(data,
						width, height);

				// Clear the int buffer as it is no longer needed
				data = null;
				System.gc();

				// Hides the loading dialog from a UI thread
				loadingDialogHandler.sendEmptyMessage(HIDE_LOADING_DIALOG);

				mIsLoadingBookData = false;

				productTextureIsCreated();
			}
		}
	}

	/**
	 * Downloads and image from an Url specified as a paremeter returns the
	 * array of bytes with the image Data for storing it on the Local Database
	 */
	private byte[] downloadImage(final String imageUrl) {
		ByteArrayBuffer baf = null;

		try {
			URL url = new URL(imageUrl);
			URLConnection ucon = url.openConnection();
			InputStream is = ucon.getInputStream();
			BufferedInputStream bis = new BufferedInputStream(is, 128);
			baf = new ByteArrayBuffer(128);

			// get the bytes one by one
			int current = 0;
			while ((current = bis.read()) != -1) {
				baf.append((byte) current);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (baf == null) {
			return null;
		} else {
			return baf.toByteArray();
		}
	}

	/** Returns the current Book Data Texture */
	private Texture getProductTexture() {
		return mBookDataTexture;
	}

	/**
	 * Starts application content Mode Displays UI OVerlays and turns CloudReco
	 * off
	 */
	public void enterContentMode() {
		// Updates state variables
		mBookInfoStatus = BOOKINFO_IS_DISPLAYED;

		// Shows the 2D Overlay
		show2DOverlay();

		// Enters content mode to disable CloudReco in Native
		enterContentModeNative();
	}

	/** Hides the 2D Overlay view and starts CloudReco service again */
	private void enterScanningMode() {
		// Hides the 2D Overlay
		hide2DOverlay();

		// Enables CloudReco Scanning Mode in Native code
		enterScanningModeNative();
	}

	/** Displays the 2D Book Overlay */
	public void show2DOverlay() {
		// Sends the Message to the Handler in the UI thread
		overlay2DHandler.sendEmptyMessage(SHOW_2D_OVERLAY);
	}

	/** Hides the 2D Book Overlay */
	public void hide2DOverlay() {
		// Sends the Message to the Handler in the UI thread
		overlay2DHandler.sendEmptyMessage(HIDE_2D_OVERLAY);
	}

	/** A helper for loading native libraries stored in "libs/armeabi*". */
	public static boolean loadLibrary(String nLibName) {
		try {
			System.loadLibrary(nLibName);

			DebugLog.LOGI("Native library lib" + nLibName + ".so loaded");
			return true;

		} catch (UnsatisfiedLinkError ulee) {

			DebugLog.LOGE("The library lib" + nLibName
					+ ".so could not be loaded");
		} catch (SecurityException se) {

			DebugLog.LOGE("The library lib" + nLibName
					+ ".so was not allowed to be loaded");
		}

		return false;
	}

	/** Shows the Camera Options Dialog when the Menu Key is pressed */
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU) {
			showCameraOptionsDialog();
			return true;
		}

		return super.onKeyUp(keyCode, event);
	}

	public boolean onTouchEvent(MotionEvent event) {
		// Process the Gestures
		return mGestureDetector.onTouchEvent(event);
	}

	/** Process Double Tap event for showing the Camera options menu */
	private class GestureListener extends
			GestureDetector.SimpleOnGestureListener {
		public boolean onDown(MotionEvent e) {
			return true;
		}

		public boolean onSingleTapUp(MotionEvent e) {

			// If the book info is not displayed it performs an Autofocus
			if (mBookInfoStatus == BOOKINFO_NOT_DISPLAYED) {
				// Calls the Autofocus Native Method
				autofocus();

				// Triggering manual auto focus disables continuous
				// autofocus
				mContAutofocus = false;

				// If the book info is displayed it shows the book data web view
			} else if (mBookInfoStatus == BOOKINFO_IS_DISPLAYED) {

				float x = e.getX(0);
				float y = e.getY(0);

				// Creates a Bounding box for detecting touches
				float screenLeft = mScreenWidth / 8.0f;
				float screenRight = mScreenWidth * 0.8f;
				float screenUp = mScreenHeight / 7.0f;
				float screenDown = mScreenHeight * 0.7f;

				// Checks touch inside the bounding box
				if (x < screenRight && x > screenLeft && y < screenDown
						&& y > screenUp) {
					// Starts the webView
					// startWebView(0);
				}
			}

			return true;
		}

		// Event when double tap occurs
		public boolean onDoubleTap(MotionEvent e) {
			// Shows the Camera options
			showCameraOptionsDialog();
			return true;
		}
	}

	/**
	 * Shows an AlertDialog with the camera options available
	 */
	private void showCameraOptionsDialog() {
		// Only show camera options dialog box if app has been already inited
		if (mAppStatus < APPSTATUS_INITED) {
			return;
		}

		final int itemCameraIndex = 0;
		final int itemAutofocusIndex = 1;

		AlertDialog cameraOptionsDialog = null;

		CharSequence[] items = { getString(R.string.menu_flash_on),
				getString(R.string.menu_contAutofocus_off) };

		// Updates list titles according to current state of the options
		if (mFlash) {
			items[itemCameraIndex] = (getString(R.string.menu_flash_off));
		} else {
			items[itemCameraIndex] = (getString(R.string.menu_flash_on));
		}

		if (mContAutofocus) {
			items[itemAutofocusIndex] = (getString(R.string.menu_contAutofocus_off));
		} else {
			items[itemAutofocusIndex] = (getString(R.string.menu_contAutofocus_on));
		}

		// Builds the Alert Dialog
		AlertDialog.Builder cameraOptionsDialogBuilder = new AlertDialog.Builder(
				CloudReco.this);
		cameraOptionsDialogBuilder
				.setTitle(getString(R.string.menu_camera_title));
		cameraOptionsDialogBuilder.setItems(items,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						if (item == itemCameraIndex) {
							// Turns focus mode on/off by calling native
							// method
							if (activateFlash(!mFlash)) {
								mFlash = !mFlash;
							} else {
								Toast.makeText(
										CloudReco.this,
										"Unable to turn "
												+ (mFlash ? "off" : "on")
												+ " flash", Toast.LENGTH_SHORT)
										.show();
							}

							// Dismisses the dialog
							dialog.dismiss();
						} else if (item == itemAutofocusIndex) {
							if (mContAutofocus) {
								// Sets the Focus Mode by calling the native
								// method
								if (setFocusMode(FOCUS_MODE_NORMAL)) {
									mContAutofocus = false;
								} else {
									Toast.makeText(
											CloudReco.this,
											"Unable to deactivate Continuous Auto-Focus",
											Toast.LENGTH_SHORT).show();
								}
							} else {
								// Sets the focus mode by calling the native
								// method
								if (setFocusMode(FOCUS_MODE_CONTINUOUS_AUTO)) {
									mContAutofocus = true;
								} else {
									Toast.makeText(
											CloudReco.this,
											"Unable to activate Continuous Auto-Focus",
											Toast.LENGTH_SHORT).show();
								}
							}

							// Dismisses the dialog
							dialog.dismiss();
						}
					}
				});

		// Shows the dialog
		cameraOptionsDialog = cameraOptionsDialogBuilder.create();
		cameraOptionsDialog.show();
	}
}
