package it.utils.nailed;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

public class PhotoBurstService extends Service {

    // Binder given to clients
    private final IBinder binder = new LocalBinder();

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
    }

    public void stopBurst() {
        // throw new IllegalAccessException("Not yet implemented");
    }

    public boolean isBurstOn() throws IllegalAccessException {
        throw new IllegalAccessException("Not yet implemented");
        //return false;
    }
}
