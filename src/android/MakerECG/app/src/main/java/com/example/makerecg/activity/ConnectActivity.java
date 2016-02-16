/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.example.makerecg.activity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.example.makerecg.Constants;
import com.example.makerecg.MyOAuthAuthenticatorService;
import com.example.makerecg.R;
import android.util.Log;

public class ConnectActivity extends Activity implements OnClickListener {
	private Button mBluetoothButton;
    private static final String TAG = "ConnectActivity";

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        setContentView(R.layout.connect);
        mBluetoothButton = (Button) findViewById(R.id.connect_bluetooth_button);
		mBluetoothButton.setOnClickListener(this);

        findSyncAccount();
	}

    protected void findSyncAccount() {
        // Handle the most common case for now -- exactly one sync account
        AccountManager am = AccountManager.get(this);
        Account [] list = am.getAccountsByType(Constants.ACCOUNT_TYPE);
        if (list.length == 1) {
            MyOAuthAuthenticatorService.setSyncAccount(list[0]);
            Log.i(TAG, "Selected " + list[0].name + " as the sync account");
        }
    }

    /*
    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean previouslyStarted = prefs.getBoolean(Constants.PREF_PREVIOUSLY_STARTED, false);
        if(!previouslyStarted) {
            SharedPreferences.Editor edit = prefs.edit();
            edit.putBoolean(Constants.PREF_PREVIOUSLY_STARTED, Boolean.TRUE);
            edit.commit();
            showHelp();
        }
    }
    */

    public void onClick(View v) {
		if (v.getId() == R.id.connect_bluetooth_button) {
			startActivity(new Intent(this, BTDeviceListActivity.class));
		}
	}

}
