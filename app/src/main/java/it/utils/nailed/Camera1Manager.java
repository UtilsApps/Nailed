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

public class Camera1Manager {

    static final String TAG = "Camera1Manager";
    public static final int MY_PERMISSIONS_REQUESTS = 400;

    private Context context;
    private Camera camera;
    //private SurfaceView preview;
    private SurfaceTexture dummySurfaceTexture;
    private boolean previewInitialized;
    private boolean previewStarted;
    private int consecutivePicSkips;
    private static final int MAX_SKIPS = 10;

    private enum BurstState { ON_IDLE, ON_PREVIEWING, ON_PIC_PENDING, SAVING, OFF }
    private BurstState myBurstState;

    public boolean isBurstOn() {
        return this.myBurstState != BurstState.OFF;
    }

    private enum CameraState { OPENED_IDLE, PREVIEWING, RELEASED }
    private CameraState myCameraState;

    private Vibrator vibeHapticFeedback;

    interface OnPicTakenCallBack {
        void updateMainCameraTextViews();
    }

    OnPicTakenCallBack onPicTakenCallBack;

    private Timer getTimer() {
        return _timer;
    }

    private void resetTimer() {

        if(_timer != null) {
            _timer.cancel();
        }
        this._timer = new Timer();
    }

    private Timer _timer;

    public int getSkippedPicsCount() {
        return _skippedPicsCount;
    }

    public void incrementSkippedPicsCount() {
        this._skippedPicsCount++;
    }

    private int _skippedPicsCount;

    //TODO set photo size
    // allow setting it
    // allow change size with volume up/down, and take continuous pics on and off with screen tap

    private Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            // we save the picture file here

            myBurstState = BurstState.SAVING;
            Log.d(TAG, "onPictureTaken");

            if(Build.VERSION.SDK_INT < 29) {
                File pictureFile = ImageSaver.getOutputMediaFile(MEDIA_TYPE_IMAGE);
                if (pictureFile == null){
                    Log.d(TAG, "Error creating media file, check storage permissions");
                    return;
                }

                try {
                    FileOutputStream fos = new FileOutputStream(pictureFile);
                    fos.write(data);
                    fos.close();
                    hapticFeedBack();
                } catch (FileNotFoundException e) {
                    Log.d(TAG, "File not found: " + e.getMessage());
                } catch (IOException e) {
                    Log.d(TAG, "Error accessing file: " + e.getMessage());
                }
            }
            else {
                // Android 10 and above

                // Add a specific media item.
                ContentResolver resolver = context.getContentResolver();

                // Find all image files on the primary external storage device.
                Uri imageCollection = MediaStore.Images.Media.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY);

                // Publish a new picture/image
                ContentValues newImageDetails = new ContentValues();
                String pictureFileName = ImageSaver.getOutputMediaFileName(MEDIA_TYPE_IMAGE);
                newImageDetails.put(MediaStore.Images.Media.DISPLAY_NAME, pictureFileName);
                newImageDetails.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                String dirName = "Pictures/" + ImageSaver.getDirNameByTodayDate();
                newImageDetails.put(MediaStore.Images.Media.RELATIVE_PATH, dirName);
                newImageDetails.put(MediaStore.Images.Media.IS_PENDING, 1);

                // Keeps a handle to the new image's URI in case we need to modify it later.
                Uri newImageUri = resolver.insert(imageCollection, newImageDetails);

                try(OutputStream os = resolver.openOutputStream(newImageUri)) {
                    os.write(data);
                    os.close();
                    hapticFeedBack();
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "File not found to save image: " + newImageUri.toString());
                } catch (IOException e) {
                    Log.e(TAG, "Error while trying to open file to save image" + e.toString());
                }

                //bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)

                newImageDetails.clear();
                newImageDetails.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(newImageUri, newImageDetails, null, null);
            }

            //TODO FIXME: is preview usually still open after a picture has been taken?
            myBurstState = BurstState.ON_IDLE;
            onPicTakenCallBack.updateMainCameraTextViews();
        }
    };

    private void hapticFeedBack() {

        if(this.vibeHapticFeedback != null
                && this.vibeHapticFeedback.hasVibrator()) {

            if(Build.VERSION.SDK_INT >= 26
                    //&& this.vibeHapticFeedback.hasAmplitudeControl()
            ){
                VibrationEffect vibe;

                try {
                    if(Build.VERSION.SDK_INT >= 29) {
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

    public Camera1Manager(Context context, Activity activity,
                          OnPicTakenCallBack onPicTakenCallBack) {

        this.context = context;
        this.myBurstState = BurstState.OFF;

        this.onPicTakenCallBack = onPicTakenCallBack;
        this.checkForCameraPermission(context, activity);


        this.vibeHapticFeedback = ( Vibrator ) context.getSystemService( VIBRATOR_SERVICE );

        resetCamera();
        resetTimer();
    }

    public boolean startCameraPreview() {

        this.myBurstState = BurstState.ON_PREVIEWING;

        if(this.camera == null) {
            this.camera = getCameraInstance();

            if(this.camera != null) {
                this.myCameraState = CameraState.OPENED_IDLE;
            }
        }

        try {
            //You can't take a picture without a preview,
            // but you don't have to show the preview on screen.
            // You can direct the output to a SurfaceTexture instead (API 11+).
            camera.setPreviewTexture(this.dummySurfaceTexture);
            this.previewInitialized = true;
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            this.previewInitialized = false;
            this.myBurstState = BurstState.ON_IDLE;
        }

        if(previewInitialized) {
            try {
                if(this.myCameraState == CameraState.PREVIEWING) {
                    if(camera == null) {
                        Log.e(TAG, "camera is null after previewInitialized");
                    }
                    camera.stopPreview();
                }

                camera.startPreview();
                this.myCameraState = CameraState.PREVIEWING;

                camera.setPreviewCallback(null);
                this.previewStarted = true;
            } catch (RuntimeException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                this.previewStarted = false;
                this.myBurstState = BurstState.ON_IDLE;
            }
        }

        return previewStarted;
    }

    public boolean stopCameraPreview() {
        try {
            if(camera == null) {
                Log.e(TAG, "camera is null before stopping preview");
            }
            camera.stopPreview();
            this.myCameraState = CameraState.OPENED_IDLE;
            this.myBurstState = BurstState.ON_IDLE;
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, e.toString());
            return false;
        }
        //camera.setPreviewCallback(null);
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

        //TODO FIXME: check, commenting this because duplicated, we already call start preview before taking picture
        //this.startCameraPreview();

        this.onPicTakenCallBack.updateMainCameraTextViews();

        //Set how long before to start calling the TimerTask (in milliseconds)
        int delay = 0;

        //Set the amount of time between each execution (in milliseconds)
        int periodMillis = 700;

        resetTimer();

        //Set the schedule function and rate
        getTimer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                //TODO check what the BufferQueueProducer does, SurfaceTexture

                if (myBurstState != BurstState.ON_PIC_PENDING) {
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

    public void stopBurst() {
        resetTimer();
        this.stopCameraPreview();

        //TODO FIXME
        // start preview fails after stopping and starting again
        // after 10 skips it auto resets and works again

        //TODO FIXME check why this is called when stopping burst instead of app exiting
        this.close();
        this.myBurstState = BurstState.OFF;
        this.onPicTakenCallBack.updateMainCameraTextViews();
    }

    //TODO FIXME check why this is called when stopping burst instead of app exiting
    public void close() {
        releaseCameraAndPreview();
        this.resetTimer();
    }

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

    public void takePicture() {

        startCameraPreview();

        if(previewInitialized && previewStarted) {
            try {
                this.myBurstState = BurstState.ON_PIC_PENDING;
                camera.takePicture(null, null, null, jpegCallback);
                this.consecutivePicSkips = 0;
            } catch (RuntimeException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                this.myBurstState = BurstState.ON_IDLE;
                this.incrementSkippedPicsCount();
                this.consecutivePicSkips++;

                //resetting the params in case that's why preview fails
                setCameraParams();

                //Duplicate, we already start preview before taking picture
                //startCameraPreview();
            }
        }
        else {
            Log.e(TAG, "Skipping take picture");
            this.myBurstState = BurstState.ON_IDLE;
            this.incrementSkippedPicsCount();
            this.consecutivePicSkips++;
            setCameraParams();//resetting the params in case that's why preview fails
        }

        if(this.consecutivePicSkips >= MAX_SKIPS) {
            Log.e(TAG, "Too many consecutive skips ("
                    + this.consecutivePicSkips + "), resetting camera..");
            this.resetCamera();
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

    private void resetCamera() {
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

    private void setCameraParams() {

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
}
