package com.example.makerecg;

import android.app.Application;

/**
 * Globals used by this application.
 *
 * Created by Riley Rainey on 3/21/16.
 */
public class MakerECGApplication extends Application {
    private Boolean _syncToCloud = true;
    private Boolean _preferBluetooth = false;
    private Boolean _bluetoothAutoconnect = true;
    private String _cloudHostname = "";
    private int _cloudPort = 0;

    public int getCloudPort() {
        return _cloudPort;
    }

    public Boolean getSyncToCloud() {
        return _syncToCloud;
    }

    public void setSyncToCloud(Boolean value) {
        _syncToCloud = value;
    }

    public Boolean getPreferBluetooth() {
        return _preferBluetooth;
    }

    public void setPreferBluetooth(boolean value) {
        _preferBluetooth = value;
    }

    public Boolean getBluetoothAutoconnect() {
        return _bluetoothAutoconnect;
    }

    public void setBluetoothAutoconnect( boolean value ) {
        _bluetoothAutoconnect = value;
    }


    public String getCloudHostname() {
        return _cloudHostname;
    }
}
