package it.utils.nailed;

import androidx.appcompat.app.AppCompatActivity;

import android.hardware.Camera;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements Camera1Manager.OnPicTakenCallBack {

    Camera1Manager cameraManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportActionBar().hide();

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
        int imgCount = ImageSaver.getOutputMediaDirDaySpecific().listFiles().length;
        picCounterTV.setText("" + imgCount);
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
}
