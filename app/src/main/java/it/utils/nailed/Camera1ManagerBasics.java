package it.utils.nailed;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Timer;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;

abstract class Camera1ManagerBasics {

    static final String TAG = "Cam1ManagerBasics";

    protected Long lastTakePictureCallTime = System.currentTimeMillis();
    protected int preferredPeriodMillis = 700;
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
    protected List<Camera.Size> supportedPictureSizes;
    protected BurstInfoReceiver mBurstInfoReceiver;
    protected BurstInfo myBurstInfo;

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

    abstract boolean isBurstPicPending();
    abstract void takeSinglePicture();

    protected void continueBurst() {
        if(this.burstShouldStop) {
            return;
        }
        else {
            //Long tsBegin = System.currentTimeMillis();
            if (this.isBurstPicPending()) {
                Log.e(TAG, "continueBurst called while Burst state PicPending");
            }

            Long elapsedTIme = System.currentTimeMillis() - lastTakePictureCallTime;
            Long remainingMillis = preferredPeriodMillis - elapsedTIme;
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
            takeSinglePicture();
        }
    }


    protected static void saveImageFile(byte[] data, Context context,
                                        Camera1ManagerBasics camera1ManagerBasics) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            File pictureFile = ImageSaver.getOutputMediaFile(MEDIA_TYPE_IMAGE);
            if (pictureFile == null){
                Log.d(TAG, "Error creating media file, check storage permissions");
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
                hapticFeedBack(camera1ManagerBasics);
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
                hapticFeedBack(camera1ManagerBasics);
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
    }

    protected static void hapticFeedBack(Camera1ManagerBasics camera1ManagerBasics) {

        if(camera1ManagerBasics.vibeHapticFeedback != null
                && camera1ManagerBasics.vibeHapticFeedback.hasVibrator()) {

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

                    camera1ManagerBasics.vibeHapticFeedback.vibrate(vibe);
                } catch (Exception e) {
                    //Log.e(TAG, "Unable to vibrate " + e.toString());
                    camera1ManagerBasics.defaultVibration();
                }
            } else {
                camera1ManagerBasics.defaultVibration();
            }
        }
    }

    private void defaultVibration() {
        final int DEFAULT_VIBRATION_MILLIS = 20;

        //this.vibeHapticFeedback.vibrate(VibrationEffect.EFFECT_CLICK);

        vibeHapticFeedback.vibrate(DEFAULT_VIBRATION_MILLIS);
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
            this.supportedPictureSizes = params.getSupportedPictureSizes();
            // set the focus mode
            //params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            //params.setJpegQuality(95);

            Camera.Size preferredSize = getPreferredPhotoSize();

            params.setPictureSize(preferredSize.width, preferredSize.height);
            this.mBurstInfoReceiver.updateBurstInfo(preferredSize);

            // set Camera parameters
            Log.d(TAG, "Setting camera params as " + params.toString() + " ..");
            camera.setParameters(params);
            Log.d(TAG, "Camera params set as " + params.toString());
        }
        catch (Exception e) {
            Log.e(TAG, "Unable to set camera params: " + e.toString());
        }
    }

    public Camera.Size getPreferredPhotoSize() {
        /*
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
        */
        int preferredSizeIdx = 11;

        Camera.Size defaultMinSize = this.supportedPictureSizes.get(this.supportedPictureSizes.size()-1);
        Camera.Size preferredSize = defaultMinSize;
        if(this.supportedPictureSizes.size() >preferredSizeIdx ) {
            preferredSize = this.supportedPictureSizes.get(preferredSizeIdx);
        }

        int preferredHeight = 720;
        for(Camera.Size size:this.supportedPictureSizes) {
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

