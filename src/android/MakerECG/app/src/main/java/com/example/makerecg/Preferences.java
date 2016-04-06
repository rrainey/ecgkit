package com.example.makerecg;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import com.example.makerecg.MakerECGApplication;

public class Preferences extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
	
	public static final String PREF_LICENSE_TEXT = "license_text";
    public static final String PREF_CLOUD_SYNC = "cloud_sync";
    public static final String PREF_PREFER_BLUETOOTH = "prefer_bluetooth";
    public static final String PREF_BLUETOOTH_AUTOCONNECT = "bluetooth_autoconnect";
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        MakerECGApplication app = (MakerECGApplication) getApplication();
        if (key.equals(PREF_CLOUD_SYNC)) {
            // this feature is not yet fully supported
            app.setSyncToCloud(sharedPreferences.getBoolean(key, true));
        }
        else if (key.equals(PREF_PREFER_BLUETOOTH)) {
            // this feature is not yet fully supported
            app.setPreferBluetooth(sharedPreferences.getBoolean(key, true));
        }
        else if (key.equals(PREF_BLUETOOTH_AUTOCONNECT)) {
            // this feature is not yet fully supported
            app.setBluetoothAutoconnect(sharedPreferences.getBoolean(key, true));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }
}
