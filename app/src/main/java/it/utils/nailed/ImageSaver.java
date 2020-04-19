package it.utils.nailed;

import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class ImageSaver {

    private static final String TAG = "ImageSaver";

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    public static int FILE_COUNT_LIMIT = 5000;//5000;

    private static File mediaStorageMainDir;

    final static FileFilter DIRS_ONLY_FILTER = new FileFilter() {
        @Override
        public boolean accept(File path) {
            return path.isDirectory();
        }
    };

    static {

        if(Build.VERSION.SDK_INT >= 29) {
            mediaStorageMainDir = null;
            //mediaStorageMainDir = MediaStore.Images.Media.RELATIVE_PATH
            // + Environment.DIRECTORY_PICTURES
        }
        else {
            if(Environment.isExternalStorageEmulated ()) {
                mediaStorageMainDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES);
            }
            else {
                mediaStorageMainDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES);
            }
        }
    }

    /** Create a file Uri for saving an image or video */
    /*private static Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile(type));
    }*/

    private static File getMainOutputMediaDir() {

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

    public static String getDirNameByTodayDate() {
        String fullDatePattern = "yyyy.MM.dd";
        String timeStamp = new SimpleDateFormat(fullDatePattern).format(new Date());

        return timeStamp;
    }

    //TODO needs tests
    public static File getOutputMediaDirDaySpecific() {
        File mainOutputMediaDir = getMainOutputMediaDir();

        //TODO check false on rename
        //TODO check errors/fail on creating folders

        String yearPattern = "yyyy";
        File yearSubDir = getTimeStampSubDir(mainOutputMediaDir, yearPattern, true);

        String monthPattern = "yyyy.MM";
        File monthSubDir = getTimeStampSubDir(yearSubDir, monthPattern, true);

        String dayPattern = "yyyy.MM.dd";
        File daySubDir = getTimeStampSubDir(monthSubDir, dayPattern, true);

        //TODO check if there are no errors when the path is an emulated external storage
        File bottomDir = new File(daySubDir.getPath());

        //TODO set a separate task to move files

        if(isExceedingFileCount(daySubDir)) {

            Log.i(TAG, "Day dir is has more than " + FILE_COUNT_LIMIT + " files");

            //FIXME renaming files, try use a tmp folder, don't directly move as its own subdir
            File renamedAsTimeStampSubFolder = getNextTimeStampSubDir(daySubDir);
            boolean renameSuccessful = daySubDir.renameTo(renamedAsTimeStampSubFolder);
            if(!renameSuccessful) {
                Log.e(TAG, "Failed renaming to " + renamedAsTimeStampSubFolder.toString());
            }
            bottomDir = renamedAsTimeStampSubFolder;
            Log.i(TAG, "New bottom dir: " + bottomDir.toString());
        }

        File[] timeSubDirs = daySubDir.listFiles(DIRS_ONLY_FILTER);
        if(timeSubDirs != null) {
            boolean hasTimeSubDirs = timeSubDirs.length > 0;

            if(hasTimeSubDirs) {
                Arrays.sort(timeSubDirs);
                int lastIdx = timeSubDirs.length - 1;
                File lastTimeSubDir = timeSubDirs[lastIdx];
                bottomDir = lastTimeSubDir;

                if(isExceedingFileCount(lastTimeSubDir)) {

                    Log.i(TAG, "Time subdir is has more than " + FILE_COUNT_LIMIT + " files");

                    File nextTimeStampSubDir = getNextTimeStampSubDir(daySubDir);
                    bottomDir = nextTimeStampSubDir;
                    createIfNotExists(bottomDir);
                    Log.i(TAG, "New bottom dir: " + bottomDir.toString());
                }
            }
        }

        return daySubDir;
    }

    private static File getNextTimeStampSubDir(File daySubDir) {

        File[] timeSubDirs = daySubDir.listFiles(DIRS_ONLY_FILTER);
        int existingSubDirsCount = timeSubDirs.length;
        String subDirNameCountPrefix = "'0" + existingSubDirsCount+1 + "'";

        String timePattern = "yyyy.MM.dd " + subDirNameCountPrefix + " 'from h' HH.mm";
        File newTimeSubDir = getTimeStampSubDir(daySubDir, timePattern, false);

        return newTimeSubDir;
    }

    public static boolean isExceedingFileCount(File dir) {

        File[] listFiles = dir.listFiles();

        if(listFiles == null) {
            //TODO see why this happens in android 10
            return false;
        }
        else {
            return listFiles.length >= FILE_COUNT_LIMIT;
        }
    }

    private static File getTimeStampSubDir(File parentDir, String datePattern, boolean create) {

        String timeStamp = new SimpleDateFormat(datePattern).format(new Date());
        File subDir = new File(parentDir, timeStamp);

        if (create) {
            createIfNotExists(subDir);
        }

        return subDir;
    }

    public static boolean createIfNotExists(File dir) {
        if (!dir.exists()){
            if (! dir.mkdirs()){
                Log.d(TAG, "failed to create directory " + dir.toString());
                return false;
            }
        }

        return true;
    }

    /** Create a File for saving an image or video */
    public static File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = getOutputMediaDirDaySpecific();

        // Create a media file name

        File mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                getOutputMediaFileName(type));

        return mediaFile;
    }

    public static String getOutputMediaFileName(int type){

        final String fileNamePattern = "yyyy.MM.dd_'h'HH.mm.ss";
        String timeStamp = new SimpleDateFormat(fileNamePattern).format(new Date());

        String fileNamePrefix = "";
        String fileNameExtension = "";
        String fileName;
        if (type == MEDIA_TYPE_IMAGE){
            fileNamePrefix = "IMG_";
            fileNameExtension = ".jpg";
        } else if(type == MEDIA_TYPE_VIDEO) {
            fileNamePrefix = "VID_";
            fileNameExtension = ".mp4";
        }

        fileName = fileNamePrefix + timeStamp + fileNameExtension;

        return fileName;
    }
}
