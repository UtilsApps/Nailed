package it.utils.nailed;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

public class PhotoBurstService extends Service implements BurstInfoReceiver {

    static String TAG = "PhotoBurstService";
    private static int FOREGROUND_ID=11235;
    private static final int ONGOING_NOTIFICATION_ID = 1;
    private static final String BURST_NOTIFICATION_CHANNEL_ID = "BURST_NOTIFICATION_CHANNEL_ID";
    private static final String CHANNEL_DEFAULT_IMPORTANCE = "Running";

    // Binder given to clients
    private final IBinder binder = new LocalBinder();

    private Looper serviceLooper;
    private BurstServiceHandler serviceHandler;
    private MainActivity mainActivity;

    BurstInfo myBurstInfo;

    /*public void setBurstInfo(BurstInfo burstInfo) {
        this.myBurstInfo = burstInfo;
    }*/

    public void updateBurstInfo(Camera1ManagerStateBased.BurstState burstState) {
        this.myBurstInfo.burstState = burstState;
    }

    public void updateBurstInfo(Camera.Size preferredSize) {
        this.myBurstInfo.preferredSize = preferredSize;
    }

    public void setIsBurstOn(boolean isBurstOn) {
        this.myBurstInfo.isBurstOn = isBurstOn;
    }

    public BurstInfo getBurstInfo() {
        return this.myBurstInfo;
    }

    //TODO FIXME
    // service always running and binded
    // gets unbinden only when activity is destroyed
    // clicking on burst on button causes to put service in foreground
    // and to send message to handler to start burst
    // clicking on stop burst causes to remove service from foreground
    // and terminate handler or send message to stop burst

    // Handler that receives messages from the thread
    private final class BurstServiceHandler extends Handler {

        Camera1Manager cameraManager;
        BurstInfoReceiver burstInfoReceiver;

        //This must be called before handlemessage
        public void initialize(Camera1Manager cameraManager, BurstInfoReceiver burstInfoReceiver) {
            this.cameraManager = cameraManager;
            Log.i(TAG, "Camera manager set into service handler");

            this.burstInfoReceiver = burstInfoReceiver;
        }

        public BurstServiceHandler(Looper looper) {
            super(looper);
        }

        public void stopBurst() {
            if(this.cameraManager != null) {
                this.cameraManager.stopBurst();
                this.burstInfoReceiver.setIsBurstOn(false);
            }
        }

        public void startBurst() {
            if(this.cameraManager != null) {
                this.cameraManager.startBurst();
                this.burstInfoReceiver.setIsBurstOn(true);
            }
            else {
                //FIXME
                // this shouldn't happen
                Log.e(TAG, "Null camera");
            }
        }

        @Override
        public void handleMessage(Message msg) {

            // Not implemented, using this.startBurst() instead

            /*try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                // Restore interrupt status.
                Thread.currentThread().interrupt();
            }*/
            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf(msg.arg1);
        }
    }

    @Override
    public void onCreate() {
        this.myBurstInfo = new BurstInfo();

        // Start up the thread running the service. Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block. We also make it
        // background priority so CPU-intensive work doesn't disrupt our UI.
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        serviceLooper = thread.getLooper();
        serviceHandler = new BurstServiceHandler(serviceLooper);
        Log.i(TAG, "Service created");

        // get camera manager and pass it to serviceHandler
        Log.i(TAG, "Creating camera manager..");
        Camera1Manager cameraManager = new Camera1Manager(this,
                this.mainActivity,
                this);
        Log.i(TAG, "Camera manager should be created: " + cameraManager.toString());
        this.serviceHandler.initialize(cameraManager, this);
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "service starting");
        this.mainActivity = MainActivity.activity;

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        /*Message msg = this.serviceHandler.obtainMessage();
        msg.arg1 = startId;
        this.serviceHandler.sendMessage(msg);*/

        // FIXME do not start burst when service start
        //this.startBurst();

        this.myBurstInfo.isServiceStarted = true;

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        //TODO FIXME make sure handler is removed too

        this.myBurstInfo.isServiceStarted = false;
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        PhotoBurstService getService() {
            // Return this instance of LocalService so clients can call public methods
            return PhotoBurstService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void passMainActivity(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    public int getCount() {
        // throw new IllegalAccessException("Not yet implemented");
        return 0;
    }

    //TODO count average time of last n (n=7) pictures taken
    // save the timestamp of last n (n=7) pictures taken
    // before taking each picture, check that previous one was at least x seconds ago
    // (with x = setting of delay between pictures)
    // if less, wait x - elapsed time since last picture

    public void startBurst() {
        // throw new IllegalAccessException("Not yet implemented");
        requestRunInForeground();
        serviceHandler.startBurst();
    }

    public void stopBurst() {
        //TODO: actually stop taking pictures

        serviceHandler.stopBurst();

        // throw new IllegalAccessException("Not yet implemented");
        stopForeground(true);
        Log.d(TAG, "stopForeground(true) called");
        /*stopSelf();
        Log.d(TAG, "stopSelf() called");*/
    }

    public void closeCameraManager() {
       serviceHandler.cameraManager.close();
    }

    public int getImagesCount() {
        //TODO FIXME implement this
        return 0;
    }

    private void requestRunInForeground() {
        Intent notificationIntent = new Intent(this, PhotoBurstService.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        //if API >= 26
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(mainActivity);
            Notification notification =
                    new Notification.Builder(this, BURST_NOTIFICATION_CHANNEL_ID)
                            .setContentTitle(getText(R.string.notification_title))
                            .setContentText(getText(R.string.notification_message))
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentIntent(pendingIntent)
                            .setTicker(getText(R.string.ticker_text))
                            .build();

            // Notification ID cannot be 0.
            startForeground(ONGOING_NOTIFICATION_ID, notification);
        }
        else { //if API <= 25
            Notification notification =
                    new Notification.Builder(this)
                            .setContentTitle("Burst")
                            .setContentText("ON")
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            //.setLargeIcon(aBitmap)
                            .build();

            startForeground(ONGOING_NOTIFICATION_ID, notification);
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public static void createNotificationChannel(@NonNull final Context context) {

        final NotificationChannel channel =
                new NotificationChannel(PhotoBurstService.BURST_NOTIFICATION_CHANNEL_ID,
                        context.getString(R.string.notification_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT);//IMPORTANCE_LOW
        channel.setDescription(context.getString(R.string.burst_notification_channel_description));
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        final NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }

        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.notification_channel_name);
            String description = getString(R.string.burst_notification_channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(BURST_NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }*/
    }
}
