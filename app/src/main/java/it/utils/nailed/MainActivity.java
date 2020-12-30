package it.utils.nailed;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity
        implements Camera1Manager.OnPicTakenCallBack {

    static String TAG = "MainActivity";

    public static MainActivity activity;

    //TODO at startup should query if service is already running
    private enum BurstState { BURST_ON, BURST_OFF }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MainActivity.activity = this;
        setContentView(R.layout.activity_main);

        getSupportActionBar().hide();

        Button takePicBtn = findViewById(R.id.takePicBtn);
        takePicBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBurst();
            }
        });

        Button stopBurstBtn = findViewById(R.id.stopBurstBtn);
        stopBurstBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopBurst();
            }
        });

        final Button closeAppBtn = findViewById(R.id.closeAppBtn);
        closeAppBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeApp();
            }
        });

        updateMainCameraTextViews();

        TextView outDirTV = findViewById(R.id.outDirTV);
        outDirTV.setText(ImageSaver.getOutputMediaDirDaySpecific().getPath());

        timerHandler.post(BurstInfoTextViewsUpdater);
    }

    //FIXME: it does not work after clicking on stop burst and then on start again
    //
    final Handler timerHandler = new Handler();
    final Runnable BurstInfoTextViewsUpdater = new Runnable() {
        @Override
        public void run() {
            updatePicSizeTV();
            timerHandler.postDelayed(BurstInfoTextViewsUpdater,1000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        updateMainCameraTextViews();
    }

    private void startBurst() {

        if(!mBoundToService) {
            doBindService();
        }

        if(mBoundToService) {
            //TODO call service method to start loop
            // check here oR in the service that the loop is not already started
            //mBoundService.startForeground();
            startPhotoBurstService();
        } else {
            //should try to bind the service, then start it
        }
    }

    private void stopBurst() {

        Log.d(TAG, "Trying to stop burst..");

        if(mBoundToService) {
            //TODO call service method to stop loop
            //mBoundService.stopForeground(true);
            mBoundService.stopBurst(); Log.d(TAG, "mBoundService.stopBurst() called");
            doUnbindService();
            stopPhotoBurstService(); Log.d(TAG, " stopPhotoBurstService() called");
        }
        else {
            Log.e(TAG, "Cannot stop burst, not bound to service");
        }
    }

    private void closeApp() {

        stopBurst();
        if(this.mBoundToService) {
            this.mBoundService.closeCameraManager();
        }

        //this.finishAndRemoveTask();
        this.finish();

        //close other camera resources?
        //close file saver resources?

        //terminate app threads, not only activity
    }

    public void updateMainCameraTextViews() {
        updatePicCountTV();
        updateSkippedPicsCountTV();
        updatePicSizeTV();
        updateItsOn();
    }

    private void updateItsOn() {
        TextView itsOnTV = findViewById(R.id.itsOnTV);
        boolean isBurstOn = false;

        if(mBoundToService && mBoundService.isBurstOn()) {
            isBurstOn = true;
        }

        if(isBurstOn) {
            itsOnTV.setText("IT'S ON");
        } else {
            itsOnTV.setText("");
        }
    }

    private void updatePicSizeTV() {
        TextView picSizeTV = findViewById(R.id.pictureSizeTV);

        String picSizeTxt = "";
        String burstStateTxt = "";

        if(mBoundToService && mBoundService.getBurstInfo() != null) {
            Camera.Size preferredSize = mBoundService.getBurstInfo().preferredSize;
            if(preferredSize != null) {
                picSizeTxt = preferredSize.height + " x " + preferredSize.width;
            }
            burstStateTxt = mBoundService.getBurstInfo().burstState.toString();
        } else {
            picSizeTxt = "mBoundToService: " + mBoundToService;
            if(mBoundService != null) {
                burstStateTxt = "BurstInfo: " + mBoundService.getBurstInfo();
            }
        }

        int min = 0;
        int max = 10;
        int random = (int) (Math.random() * (max - min + 1) + min) % max;
        String randomCounterTxt = "(" + random + ") ";

        picSizeTV.setText(randomCounterTxt + picSizeTxt + "; " + burstStateTxt);
    }

    //TODO haptic feedback at each picture
    //use up/down volume

    //TODO make zipfiles
    //notification for running service
    //TODO ensure that it works as background service
    // (buttons should actually start/stop the service)

    //TODO add confirmation for stop burst

    //TODO check peformance hogs, put time lapse checks
    //check why it slows down after a few hundreds files

    //TODO add counter for skipped photos for failed preview init, preview start, or take picture

    //TODO try switch to android.hardware.camera2 when API >= 21

    //TODO after a series of consecutive skips (10?), reset camera

    //..

    private void updatePicCountTV() {
        TextView picCounterTV = findViewById(R.id.photoCountTV);
        int imgCount = getCurrentImageDirCount();
        picCounterTV.setText("" + imgCount);
    }

    private int getCurrentImageDirCount() {

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //TODO FIXME implement this for Android 10
            return 0;
        } else {
            return ImageSaver.getOutputMediaDirDaySpecific().listFiles().length;
        }
    }

    private void updateSkippedPicsCountTV() {
        TextView skippedPicsCountTV = findViewById(R.id.skippedCountTV);
        skippedPicsCountTV.setText("Skipped: ..");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        Camera1Manager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    //TODO
    // Binding to PhotoBurstService

    @Override
    protected void onStart() {
        super.onStart();

        doBindService();
    }

    void doBindService() {
        // Attempts to establish a connection with the service.  We use an
        // explicit class name because we want a specific service
        // implementation that we know will be running in our own process
        // (and thus won't be supporting component replacement by other
        // applications).
        Intent intent = new Intent(this, PhotoBurstService.class);
        //binds to the service, creating it if necessary
        boolean bindSuccessful = bindService(intent,
                mConnection, Context.BIND_AUTO_CREATE);

        if (bindSuccessful) {
            mShouldUnbind = true;
        } else {
            Log.e("Nailed", "Error: The requested service doesn't " +
                    "exist, or this client isn't allowed access to it.");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        //TODO check for foreground service, keeping it active even when activity goes into the backgroud
        // review activity lifecycle, background status
        doUnbindService();
    }

    // Don't attempt to unbind from the service unless the client has received some
    // information about the service's state.
    private boolean mShouldUnbind;

    // To invoke the bound service, first make sure that this value
    // is not null.
    private PhotoBurstService mBoundService;
    private boolean mBoundToService = false;

    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.
            // Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            // mBoundService = ((PhotoBurstService.LocalBinder)service).getService();

            Log.e(TAG, "onServiceConnected, PhotoBurstService");
            PhotoBurstService.LocalBinder binder = (PhotoBurstService.LocalBinder) service;
            mBoundService = binder.getService();
            mBoundToService = true;
            // Tell the user about this for our demo.
            /*Toast.makeText(MainActivity.this, R.string.local_service_connected,
                    Toast.LENGTH_SHORT).show();*/
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            Log.e(TAG, "onServiceDisconnected, PhotoBurstService");
            mBoundService = null;
            mBoundToService = false;

            //TODO: try to reconnect
            // if burst it's on, call service method to resume burst

            /*Toast.makeText(MainActivity.this, R.string.local_service_disconnected,
                    Toast.LENGTH_SHORT).show();*/
        }
    };

    void doUnbindService() {
        Log.d(TAG, "Trying to unbindservice..");
        if (mShouldUnbind) {
            // Release information about the service's state.
            unbindService(mConnection);
            mShouldUnbind = false;
            Log.d(TAG, "unbind should be successful");
        }
        else {
            Log.e(TAG, "mShouldUnbind is false");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //FIXME: ..
        doUnbindService();

        // need to remove Handler when the activity destroys, else it will leak memory.
        timerHandler.removeCallbacks(BurstInfoTextViewsUpdater);
    }

    private void startPhotoBurstService() {
        //<service android:name=".PhotoBurstService" />

        // TODO fix, check
        // bindService creates the service, but does it start it?
        Intent serviceIntent = new Intent(this, PhotoBurstService.class);
        startService(serviceIntent);
    }

    private void stopPhotoBurstService() {
        //TODO should unbind the service

        Intent serviceIntent = new Intent(this, PhotoBurstService.class);
        //serviceIntent.setAction(Constants.ACTION.STOPFOREGROUND_ACTION);

        if(stopService(serviceIntent)) {
            Log.d(TAG, "stopService(serviceIntent) successful");
        } else {
            Log.e(TAG, "stopService(serviceIntent) failed");
        }
    }

    //TODO
    // the picture taking (camera), and saving should be done in a separate thread
    // (since the service runs on the main thread)

    // Foreground services must display a Notification.

    //TODO
    // check if service is running (regardless if burst on or off)
    // it should always be running

    //TODO
    // query service for status, info

    private void doStuff() {
        mBoundService.getCount();
    }

    //Two ways of starting the service:
    // ..with Intent

    //TODO add widgets for
    // take a single picture (manual mode), activated by volume up/down
    // lock "screen", disable UI widgets events to prevent accidental tapping on buttons
    // button to unlock, ..requesting pin?
    // widgets to change resolution
    // widget to display interval between photos
    // widget to display free disk space left

    

}
