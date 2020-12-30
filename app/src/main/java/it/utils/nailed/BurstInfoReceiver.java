package it.utils.nailed;

import android.hardware.Camera;

public interface BurstInfoReceiver {

    public void updateBurstInfo(Camera1ManagerStateBased.BurstState burstState);
    public void setOneMorePicTaken();
    public void setOneMorePicSaved();

    public void updateBurstInfo(Camera.Size preferredSize);

    public void setIsBurstOn(boolean isBurstOn);

}

