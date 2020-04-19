package it.utils.nailed;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.renderscript.ScriptGroup;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity
        implements Camera1Manager.OnPicTakenCallBack {

    Camera1Manager cameraManager;

    //TODO at startup should query if service is already running
    private enum BurstState { BURST_ON, BURST_OFF }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportActionBar().hide();

        //replace cameraManager with service, which calls it
        //pass parameters to service on pic res etc
        this.cameraManager = new Camera1Manager(this, this, this);

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
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateMainCameraTextViews();
    }

    private void startBurst() {
        this.cameraManager.startBurst();
    }

    private void stopBurst() {
        this.cameraManager.stopBurst();
    }

    private void closeApp() {

        cameraManager.close();

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
        boolean isBurstOn = cameraManager.isBurstOn();

        if(isBurstOn) {
            itsOnTV.setText("IT'S ON");
        } else {
            itsOnTV.setText("");
        }
    }

    private void updatePicSizeTV() {
        TextView picSizeTV = findViewById(R.id.pictureSizeTV);
        Camera.Size picSize = cameraManager.getPreferredPhotoSize();

        if(picSize != null) {
            picSizeTV.setText(picSize.height + " x " + picSize.width);
        }
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

        if(Build.VERSION.SDK_INT >= 29) {
            //TODO FIXME implement this for Android 10
            return 0;
        } else {
            return ImageSaver.getOutputMediaDirDaySpecific().listFiles().length;
        }
    }

    private void updateSkippedPicsCountTV() {
        TextView skippedPicsCountTV = findViewById(R.id.skippedCountTV);
        skippedPicsCountTV.setText("Skipped: " + cameraManager.getSkippedPicsCount());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        Camera1Manager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    //TODO
    // Binding to PhotoBurstService

    // Don't attempt to unbind from the service unless the client has received some
    // information about the service's state.
    private boolean mShouldUnbind;

    // To invoke the bound service, first make sure that this value
    // is not null.
    private PhotoBurstService mBoundService;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((PhotoBurstService.LocalBinder)service).getService();

            // Tell the user about this for our demo.
            /*Toast.makeText(MainActivity.this, R.string.local_service_connected,
                    Toast.LENGTH_SHORT).show();*/
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null;
            /*Toast.makeText(MainActivity.this, R.string.local_service_disconnected,
                    Toast.LENGTH_SHORT).show();*/
        }
    };

    void doBindService() {
        // Attempts to establish a connection with the service.  We use an
        // explicit class name because we want a specific service
        // implementation that we know will be running in our own process
        // (and thus won't be supporting component replacement by other
        // applications).
        if (bindService(new Intent(MainActivity.this, PhotoBurstService.class),
                mConnection, Context.BIND_AUTO_CREATE)) {
            mShouldUnbind = true;
        } else {
            Log.e("Nailed", "Error: The requested service doesn't " +
                    "exist, or this client isn't allowed access to it.");
        }
    }

    void doUnbindService() {
        if (mShouldUnbind) {
            // Release information about the service's state.
            unbindService(mConnection);
            mShouldUnbind = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //FIXME: ..
        doUnbindService();
    }

    private void startPhotoBurstService() {
        //<service android:name="com.example.ServiceName"></service>
        Intent serviceIntent = new Intent(this, PhotoBurstService.class);
        startService(serviceIntent);

        //startForegroundService()
    }

    private void stopPhotoBurstService() {
        Intent serviceIntent = new Intent(this, PhotoBurstService.class);
        stopService(serviceIntent);

        //startForegroundService()
    }

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
