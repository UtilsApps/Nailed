package it.utils.nailed;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import java.util.List;
import java.util.Timer;

abstract class Camera1ManagerBasics {

    static final String TAG = "Cam1ManagerBasics";

    protected Vibrator vibeHapticFeedback;
    protected Context context;
    protected Camera camera;
    protected enum CameraState { OPENED_IDLE, PREVIEWING, RELEASED }
    protected CameraState myCameraState;
    protected SurfaceTexture dummySurfaceTexture;
    protected boolean previewInitialized;
    protected boolean previewStarted;
    protected boolean burstShouldStop;
    private static Timer _timer;
    protected int consecutivePicSkips;
    protected static final int MAX_SKIPS = 10;
    protected int _skippedPicsCount;

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
            Log.d(TAG, "Successfully obtained Camera instance: " + c.toString());
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
            Log.e(TAG, "failed to get Camera Instance: " + e.toString());
            e.printStackTrace();
        }
        return c; // returns null if camera is unavailable
    }

    protected void hapticFeedBack() {

        if(this.vibeHapticFeedback != null
                && this.vibeHapticFeedback.hasVibrator()) {

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                //&& this.vibeHapticFeedback.hasAmplitudeControl()
            ){
                VibrationEffect vibe;

                try {
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibe = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
                    }
                    else {
                        //vibe = VibrationEffect.createOneShot(VibrationEffect.EFFECT_CLICK,
                        //        VibrationEffect.DEFAULT_AMPLITUDE);

                        vibe = VibrationEffect.createOneShot(20,
                                50);
                    }

                    this.vibeHapticFeedback.vibrate(vibe);
                } catch (Exception e) {
                    //Log.e(TAG, "Unable to vibrate " + e.toString());
                    defaultVibration();
                }
            } else {
                defaultVibration();
            }
        }
    }

    private void defaultVibration() {
        final int DEFAULT_VIBRATION_MILLIS = 20;

        //this.vibeHapticFeedback.vibrate(VibrationEffect.EFFECT_CLICK);

        this.vibeHapticFeedback.vibrate(DEFAULT_VIBRATION_MILLIS);
    }

    //TODO FIXME check why this is called when stopping burst instead of app exiting
    public void close() {
        releaseCameraAndPreview();
        this.resetTimer();
    }

    public void releaseCameraAndPreview() {

        //preview.setCamera(null);

        if (camera != null) {
            camera.release();
            camera = null;
            this.myCameraState = CameraState.RELEASED;
        }

        if(this.dummySurfaceTexture != null) {
            this.dummySurfaceTexture.release();
        }
    }

    protected Timer getTimer() {
        return _timer;
    }

    protected void resetTimer() {

        if(_timer != null) {
            _timer.cancel();
            _timer.purge();
        }
        this._timer = new Timer();
    }

    protected void setCameraParams() {

        try {
            // get Camera parameters
            Camera.Parameters params = camera.getParameters();
            // set the focus mode
            //params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            //params.setJpegQuality(95);

            Camera.Size preferredSize = getPreferredPhotoSize();

            params.setPictureSize(preferredSize.width, preferredSize.height);

            // set Camera parameters
            camera.setParameters(params);
        }
        catch (Exception e) {
            Log.e(TAG, "Unable to set camera params: " + e.toString());
        }
    }

    public Camera.Size getPreferredPhotoSize() {

        if(camera == null) {
            return null;
        }

        Camera.Parameters params;

        try {
            params = camera.getParameters();
        }
        catch (RuntimeException e) {
            Log.e(TAG, "Failed getting camera params, " + e.toString());
            return null;
        }

        List<Camera.Size> supportedSizes = params.getSupportedPictureSizes();

        int preferredSizeIdx = 11;

        Camera.Size defaultMinSize = supportedSizes.get(supportedSizes.size()-1);
        Camera.Size preferredSize = defaultMinSize;
        if(supportedSizes.size() >preferredSizeIdx ) {
            preferredSize = supportedSizes.get(preferredSizeIdx);
        }

        int preferredHeight = 720;
        for(Camera.Size size:supportedSizes) {
            if(size.height == preferredHeight) {
                preferredSize = size;
                break;
            }
        }

        return preferredSize;
    }

    protected void resetCamera() {
        this.releaseCameraAndPreview();
        this.dummySurfaceTexture = new SurfaceTexture(1);

        this.camera = getCameraInstance();
        if(this.camera != null) {
            this.myCameraState = CameraState.OPENED_IDLE;
        }

        this.previewInitialized = false;
        this.previewStarted = false;
        this.consecutivePicSkips = 0;

        setCameraParams();
    }
}

