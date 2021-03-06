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

    public enum BurstState { ON_IDLE, ON_PREVIEWING, ON_PIC_PENDING, SAVING, OFF }
    private BurstState myBurstState;

    protected enum BurstType { TIMER_BASED, THREAD_SLEEP_BASED};
    protected static final BurstType DEFAULT_BURST_TYPE = BurstType.THREAD_SLEEP_BASED;
    protected BurstType myBurstType;
    //Camera1Manager.OnPicTakenCallBack onPicTakenCallBack;

    public Camera1ManagerStateBased(Context context, BurstInfoReceiver burstInfoReceiver) {
        this.context = context;
        this.mBurstInfoReceiver = burstInfoReceiver;
        moveToOFFBurstState();
    }

    protected void moveToOFFBurstState() {
        this.myBurstState = BurstState.OFF;
        this.mBurstInfoReceiver.updateBurstInfo(this.myBurstState);
    }

    protected void moveToONPREVIEWINGBurstState() {
        this.myBurstState = BurstState.ON_PREVIEWING;
        this.mBurstInfoReceiver.updateBurstInfo(this.myBurstState);
    }

    protected void moveToONIDLEBurstState() {
        this.myBurstState = BurstState.ON_IDLE;
        this.mBurstInfoReceiver.updateBurstInfo(this.myBurstState);
    }

    protected void moveToONPICPENDINGBurstState() {
        this.myBurstState = BurstState.ON_PIC_PENDING;
        this.mBurstInfoReceiver.updateBurstInfo(this.myBurstState);
    }

    protected void moveToSAVINGBurstState() {
        this.myBurstState = BurstState.SAVING;
        this.mBurstInfoReceiver.updateBurstInfo(this.myBurstState);
    }

    protected boolean isBurstPicPending() {
        return myBurstState == BurstState.ON_PIC_PENDING;
    }

    public void incrementSkippedPicsCount() {
        this._skippedPicsCount++;
    }

    protected boolean startCameraPreview() {
        this.moveToONPREVIEWINGBurstState();

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
            this.moveToONIDLEBurstState();
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
                this.moveToONIDLEBurstState();
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
            this.moveToONIDLEBurstState();
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
                this.moveToONPICPENDINGBurstState();

                //TODO FIXME on real phone, the callback is often never called
                Log.d(TAG, "Taking picture, asynchronously..");
                this.lastTakePictureCallTime = System.currentTimeMillis();
                camera.takePicture(null, null, null, jpegCallback);
                this.consecutivePicSkips = 0;
            } catch (RuntimeException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                this.moveToONIDLEBurstState();
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
            this.moveToONIDLEBurstState();
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

    protected Camera1ManagerBasics getCameraManagerInstance() {
        return this;
    }

    protected Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            mBurstInfoReceiver.setOneMorePicTaken();

            Long elapsedMillis = System.currentTimeMillis() - lastTakePictureCallTime;
            if(elapsedMillis > 200) {
                Log.e(TAG, "Callback for takePicture returned after " + elapsedMillis);
            }
            else {
                Log.d(TAG, "Callback for takePicture returned after " + elapsedMillis);
            }

            // we save the picture file here
            moveToSAVINGBurstState();
            Log.d(TAG, "onPictureTaken, burst state should be SAVING now");

            saveImageFile(data, context, getCameraManagerInstance());
            mBurstInfoReceiver.setOneMorePicSaved();

            //TODO FIXME: is preview usually still open after a picture has been taken?
            moveToONIDLEBurstState();
            //onPicTakenCallBack.updateMainCameraTextViews();
            continueBurst();
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
        this.moveToOFFBurstState();
        //this.onPicTakenCallBack.updateMainCameraTextViews();
    }
}