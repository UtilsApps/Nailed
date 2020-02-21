package it.utils.nailed;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    CameraManager cameraManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.cameraManager = new CameraManager(this, this);

        final Context myContext = this;
        Button takePicBtn = findViewById(R.id.takePicBtn);
        takePicBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraManager.takePicture(myContext);
            }
        });

        TextView picCounterTV = findViewById(R.id.photoCountTV);

        TextView picSizeTV = findViewById(R.id.pictureSizeTV);
        Camera.Size picSize = cameraManager.getPreferredPhotoSize();
        picSizeTV.setText(picSize.height + " x " + picSize.width);

        TextView outDirTV = findViewById(R.id.outDirTV);
        outDirTV.setText(ImageSaver.getOutputMediaDir().getPath());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        CameraManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
