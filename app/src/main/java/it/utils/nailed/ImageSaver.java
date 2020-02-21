package it.utils.nailed;

import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ImageSaver {

    private static final String TAG = "ImageSaver";

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    /** Create a file Uri for saving an image or video */
    private static Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile(type));
    }

    private static File getMainOutputMediaDir() {

        File mediaStorageMainDir;

        if(Environment.isExternalStorageEmulated ()) {
            mediaStorageMainDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES);
        }
        else {
            mediaStorageMainDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES);
        }

        File mediaStorageSubDir = new File(mediaStorageMainDir, "MyCameraApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageSubDir.exists()){
            if (! mediaStorageSubDir.mkdirs()){
                Log.d(TAG, "failed to create directory");
                return null;
            }
        }

        return mediaStorageSubDir;
    }

    public static File getOutputMediaDirDaySpecific() {
        File mainOutputMediaDir = getMainOutputMediaDir();

        String yearPattern = "yyyy";
        File yearSubDir = getTimeStampSubDir(mainOutputMediaDir, yearPattern);

        String monthPattern = "yyyy.MM";
        File monthSubDir = getTimeStampSubDir(yearSubDir, monthPattern);

        String dayPattern = "yyyy.MM.dd";
        File daySubDir = getTimeStampSubDir(monthSubDir, dayPattern);

        return daySubDir;
    }

    private static File getTimeStampSubDir(File parentDir, String datePattern) {

        String timeStamp = new SimpleDateFormat(datePattern).format(new Date());
        File subDir = new File(parentDir, timeStamp);

        if (!subDir.exists()){
            if (! subDir.mkdirs()){
                Log.d(TAG, "failed to create directory");
                return null;
            }
        }

        return subDir;
    }

    /** Create a File for saving an image or video */
    public static File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = getOutputMediaDirDaySpecific();

        final String fileNamePattern = "yyyy.MM.dd_'h'HH.mm.ss";

                // Create a media file name
        String timeStamp = new SimpleDateFormat(fileNamePattern).format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }
}
