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

public class CameraManager {

    static final String TAG = "CameraManager";
    public static final int MY_PERMISSIONS_REQUESTS = 400;

    private Camera camera;
    private SurfaceView preview;

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

    public CameraManager(Context context, Activity activity) {
        this.checkForCameraPermission(context, activity);

        if(this.camera == null) {
            this.camera = getCameraInstance();
        }

        setCameraParams();
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

    private void releaseCamera() {
        if (camera != null){
            camera.release();        // release the camera for other applications
            camera = null;
        }
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

    public void takePicture(Context context) {

        // get an image from the camera
        if(this.camera == null) {
            this.camera = getCameraInstance();
        }

        try {
            //You can't take a picture without a preview,
            // but you don't have to show the preview on screen.
            // You can direct the output to a SurfaceTexture instead (API 11+).
            camera.setPreviewTexture(new SurfaceTexture(10));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        //optional: set parameters
        camera.startPreview();

        camera.takePicture(null, null, null, jpegCallback);
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

    private void releaseCameraAndPreview() {

        //preview.setCamera(null);

        if (camera != null) {
            camera.release();
            camera = null;
        }
    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance() {
        Camera c = null;
        try {

            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
            Log.e("Nailed", "failed to getCameraInstance");
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
        Camera.Parameters params = camera.getParameters();
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
}
