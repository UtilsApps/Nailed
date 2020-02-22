package it.utils.nailed;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;

//import static android.provider.Settings.System.getString;

//https://developer.android.com/guide/topics/media/camera.html#java

public class Camera1Manager {

    static final String TAG = "Camera1Manager";
    public static final int MY_PERMISSIONS_REQUESTS = 400;

    private Camera camera;
    //private SurfaceView preview;
    private SurfaceTexture defaultSurfaceTexture;
    private boolean previewInitialized;
    private boolean previewStarted;
    private int consecutivePicSkips;
    private static final int MAX_SKIPS = 10;

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

            Log.d(TAG, "onPictureTaken");

            File pictureFile = ImageSaver.getOutputMediaFile(MEDIA_TYPE_IMAGE);
            if (pictureFile == null){
                Log.d(TAG, "Error creating media file, check storage permissions");
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }


        }
    };

    public Camera1Manager(Context context, Activity activity) {
        this.checkForCameraPermission(context, activity);

        this.defaultSurfaceTexture = new SurfaceTexture(10);

        resetCamera();
    }

    public boolean startCameraPreview() {

        if(this.camera == null) {
            this.camera = getCameraInstance();
        }

        try {
            //You can't take a picture without a preview,
            // but you don't have to show the preview on screen.
            // You can direct the output to a SurfaceTexture instead (API 11+).
            camera.setPreviewTexture(this.defaultSurfaceTexture);
            this.previewInitialized = true;
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            this.previewInitialized = false;
        }

        if(previewInitialized) {
            try {
                camera.stopPreview();
                camera.startPreview();
                camera.setPreviewCallback(null);
                this.previewStarted = true;
            } catch (RuntimeException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                this.previewStarted = false;
            }
        }

        return previewStarted;
    }

    public boolean stopCameraPreview() {
        try {
            camera.stopPreview();
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

    public void close() {
        releaseCameraAndPreview();
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
                camera.takePicture(null, null, null, jpegCallback);
                this.consecutivePicSkips = 0;
            } catch (RuntimeException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                this.incrementSkippedPicsCount();
                this.consecutivePicSkips++;

                //resetting the params in case that's why preview fails
                setCameraParams();
                startCameraPreview();
            }
        }
        else {
            Log.e(TAG, "Skipping take picture");
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
        }
    }

    private void resetCamera() {
        this.releaseCameraAndPreview();
        this.camera = getCameraInstance();
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
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
            Log.e(TAG, "failed to get Camera Instance: " + e.toString());
            e.printStackTrace();
        }
        return c; // returns null if camera is unavailable
    }

    /** Check if this device has a camera */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

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
