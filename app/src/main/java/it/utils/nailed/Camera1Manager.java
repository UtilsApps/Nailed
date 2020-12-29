package it.utils.nailed;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static android.content.Context.VIBRATOR_SERVICE;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;

//import static android.provider.Settings.System.getString;

//https://developer.android.com/guide/topics/media/camera.html#java

public class Camera1Manager extends Camera1ManagerStateBased {

    static final String TAG = "Camera1Manager";
    public static final int MY_PERMISSIONS_REQUESTS = 400;

    //private SurfaceView preview;

    interface OnPicTakenCallBack {
        void updateMainCameraTextViews();
    }

    //TODO set photo size
    // allow setting it
    // allow change size with volume up/down, and take continuous pics on and off with screen tap

    public Camera1Manager(Context context, Activity activity,
                          OnPicTakenCallBack onPicTakenCallBack, BurstType burstType) {
        super(context);
        this.myBurstType = burstType;

        if(this.myBurstType == BurstType.THREAD_SLEEP_BASED) {
            this.burstShouldStop = false;
        }

        this.onPicTakenCallBack = onPicTakenCallBack;
        this.checkForCameraPermission(context, activity);

        this.vibeHapticFeedback = ( Vibrator ) context.getSystemService( VIBRATOR_SERVICE );

        resetCamera();
        resetTimer();
    }

    public Camera1Manager(Context context, Activity activity,
                          OnPicTakenCallBack onPicTakenCallBack) {

        this(context, activity, onPicTakenCallBack, DEFAULT_BURST_TYPE);
    }

    @Override
    public boolean startCameraPreview() {
        return super.startCameraPreview();
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    public void checkForCameraPermission(Context context, Activity activity) {

        String[] permissionsToRequest = {Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};

        if (!hasPermissions(context, permissionsToRequest)) {
            Log.e(TAG, "CAMERA or other permission not granted");

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.CAMERA)
                || ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
            ) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Log.w(TAG, "Should show an explanation to the user");
            } else {
                // No explanation needed; request the permission

                Log.w(TAG, "Requesting camera and other permissions..");
                ActivityCompat.requestPermissions(activity,
                        permissionsToRequest,
                        MY_PERMISSIONS_REQUESTS);

                // MY_PERMISSIONS_REQUESTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
    }

    public void startBurst() {
        //Set the amount of time between each execution (in milliseconds)
        int periodMillis = 700;

        if(this.myBurstType == BurstType.THREAD_SLEEP_BASED) {
            this.burstShouldStop = false;
            doSleepBasedBurst(periodMillis);
        }
        else {
            startTimerBasedBurst(periodMillis);
        }
    }

    void doSleepBasedBurst(int periodMillis) {

        while(!this.burstShouldStop) {
            // loop (while until not stopped, thread stop):
            // take timestamp
            Long tsBegin, tsEnd;
            tsBegin = System.currentTimeMillis();
            // take pic
            if (!this.isBurstPicPending()) {
                takeSinglePicture();
                // thread sleep period-elapsed time
                tsEnd = System.currentTimeMillis();
                Long elapsedTIme = tsEnd - tsBegin;
                Log.d(TAG, "Pic taken, elapsed time: " + elapsedTIme);
                Long remainingMillis = periodMillis - elapsedTIme;
                if(remainingMillis > 0) {
                    Log.d(TAG, "Sleeping " + remainingMillis + " millis..");
                    try {
                        Thread.sleep(remainingMillis);
                    }
                    catch (InterruptedException e) {
                        Log.e(TAG, "Thread interruped while bursting.");
                        return;
                    }
                }
            }
            else {
                //TODO FIXME
                // this happens too often, add tests for use of pending state
                // test if we are still on main thread, because warning about that gets logged
                Log.w(TAG, "Pic pending, sleeping 100 millis");
                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException e) {
                    Log.e(TAG, "Thread interruped while bursting.");
                    return;
                }
            }
        }

        //thread quit
        //Thread.currentThread().stop();

        //Test: check which thread this is, which thread is the main activity,
        // and which the service
    }

    void startTimerBasedBurst(int periodMillis) {

        //TODO FIXME: check, commenting this because duplicated, we already call start preview before taking picture
        //this.startCameraPreview();

        //TODO FIXME refactor this: store getCurrentImageDirCount into the service,
        // then it's up to the main activity to retrieve the info
        //this.onPicTakenCallBack.updateMainCameraTextViews();

        //Set how long before to start calling the TimerTask (in milliseconds)
        int delay = 0;

        resetTimer();

        //Set the schedule function and rate
        getTimer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                //TODO check what the BufferQueueProducer does, SurfaceTexture

                if (!isBurstPicPending()) {
                    takeSinglePicture();
                }
                else {
                    Log.w(TAG, "Pic pending, skipping..");
                }
            }
        },delay,periodMillis);
    }

    //TODO FIXME
    // upadtes to the skipped counter seem to go in 10 batches
    // (it doesn't update each time)



    private void takeSinglePicture() {
        takePicture();
    }

    public static void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUESTS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    Log.i(TAG, "CAMERA permission granted");
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Log.e(TAG, "CAMERA permission denied");
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

    @Deprecated
    private boolean safeCameraOpen(int id) {
        boolean qOpened = false;

        try {
            releaseCameraAndPreview();
            camera = Camera.open(id);
            qOpened = (camera != null);
        } catch (Exception e) {
            Log.e("Nailed", "failed to open Camera");
            e.printStackTrace();
        }

        return qOpened;
    }

    /** Check if this device has a camera */
    @Deprecated
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    @Deprecated
    private void checkCameraFeatures() {

        // get Camera parameters
        Camera.Parameters params = camera.getParameters();

        List<String> focusModes = params.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            // Autofocus mode is supported
        }
    }

}
