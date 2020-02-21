package it.utils.nailed;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    CameraManager cameraManager;

    public Timer getTimer() {
        return _timer;
    }

    public void resetTimer() {

        if(_timer != null) {
            _timer.cancel();
        }
        this._timer = new Timer();
    }

    Timer _timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.cameraManager = new CameraManager(this, this);

        resetTimer();

        Button takePicBtn = findViewById(R.id.takePicBtn);
        takePicBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBurst(getTimer());
            }
        });

        Button stopBurstBtn = findViewById(R.id.stopBurstBtn);
        stopBurstBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopBurst();
            }
        });

        updateMainCameraTextViews();

        TextView outDirTV = findViewById(R.id.outDirTV);
        outDirTV.setText(ImageSaver.getOutputMediaDirDaySpecific().getPath());
    }

    private void updateMainCameraTextViews() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updatePicCountTV();
                updatePicSizeTV();
            }
        });
    }

    private void startBurst(Timer timer) {

        //Set how long before to start calling the TimerTask (in milliseconds)
        int delay = 0;

        //Set the amount of time between each execution (in milliseconds)
        int periodMillis = 1000;

        //Set the schedule function and rate
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                  takeSinglePicture();
            }
        },delay,periodMillis);
    }

    private void stopBurst() {
        resetTimer();
    }

    private void takeSinglePicture() {
        cameraManager.takePicture();
        updateMainCameraTextViews();
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


    private void updatePicCountTV() {
        TextView picCounterTV = findViewById(R.id.photoCountTV);
        int imgCount = ImageSaver.getOutputMediaDirDaySpecific().listFiles().length;
        picCounterTV.setText("" + imgCount);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        CameraManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
