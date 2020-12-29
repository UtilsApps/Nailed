package it.utils.nailed;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;

abstract class Camera1ManagerStateBased extends Camera1ManagerBasics{
    static final String TAG = "Cam1ManagerStateBased";

    protected enum BurstState { ON_IDLE, ON_PREVIEWING, ON_PIC_PENDING, SAVING, OFF }
    private BurstState myBurstState;
    protected enum BurstType { TIMER_BASED, THREAD_SLEEP_BASED};
    protected static final BurstType DEFAULT_BURST_TYPE = BurstType.THREAD_SLEEP_BASED;
    protected BurstType myBurstType;
    Camera1Manager.OnPicTakenCallBack onPicTakenCallBack;

    public Camera1ManagerStateBased(Context context) {
        this.context = context;
        this.myBurstState = BurstState.OFF;
    }

    public boolean isBurstOn() {
        return this.myBurstState != BurstState.OFF;
    }

    protected BurstState getBurstState() {
        return this.myBurstState;
    }

    protected boolean isBurstPicPending() {
        return myBurstState == BurstState.ON_PIC_PENDING;
    }

    public int getSkippedPicsCount() {
        return _skippedPicsCount;
    }

    public void incrementSkippedPicsCount() {
        this._skippedPicsCount++;
    }

    protected boolean startCameraPreview() {
        this.myBurstState = BurstState.ON_PREVIEWING;

        if(this.camera == null) {
            this.camera = getCameraInstance();

            if(this.camera != null) {
                this.myCameraState = Camera1Manager.CameraState.OPENED_IDLE;
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
                if(this.myCameraState == Camera1Manager.CameraState.PREVIEWING) {
                    if(camera == null) {
                        Log.e(TAG, "camera is null after previewInitialized");
                    }
                    camera.stopPreview();
                }

                camera.startPreview();
                this.myCameraState = Camera1Manager.CameraState.PREVIEWING;

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

    protected Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            // we save the picture file here

            myBurstState = BurstState.SAVING;
            Log.d(TAG, "onPictureTaken");

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

    public void stopBurst() {

        if(this.myBurstType == Camera1Manager.BurstType.THREAD_SLEEP_BASED) {
            this.burstShouldStop = true;
        }
        else {
            resetTimer();
        }

        this.stopCameraPreview();

        //TODO FIXME
        // start preview fails after stopping and starting again
        // after 10 skips it auto resets and works again

        //TODO FIXME check why this is called when stopping burst instead of app exiting
        this.close();
        this.myBurstState = Camera1ManagerStateBased.BurstState.OFF;
        this.onPicTakenCallBack.updateMainCameraTextViews();
    }
}