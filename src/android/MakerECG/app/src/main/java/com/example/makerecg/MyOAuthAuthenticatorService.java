package com.example.makerecg;

import android.accounts.Account;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * A bound Service that instantiates the authenticator
 * when started.
 */
public class MyOAuthAuthenticatorService extends Service {

    static Account mActiveAccount = null;

    public static Account getSyncAccount() {
        return mActiveAccount;
    }

    public static void setSyncAccount(Account account) {
        mActiveAccount = account;
    }

    //...
    // Instance field that stores the authenticator object
    private MyOAuthAuthenticator mAuthenticator;
    @Override
    public void onCreate() {
        // Create a new authenticator object
        mAuthenticator = new MyOAuthAuthenticator(this);
    }
    /*
     * When the system binds to this Service to make the RPC call
     * return the authenticator's IBinder.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mAuthenticator.getIBinder();
    }
}
