package it.utils.nailed;

import android.hardware.Camera;

public class BurstInfo {

    //pic resolution
    public Camera.Size preferredSize;

    //current burst state
    Camera1ManagerStateBased.BurstState burstState;

    //Other info: ..burst on, service bound, service running, service looping
    boolean isBurstOn = false;

    boolean isServiceStarted = false;

    //last pic elapsed time
    //average elapsed time of last 7 pics

    //folder
    //number of pics in folder
}
