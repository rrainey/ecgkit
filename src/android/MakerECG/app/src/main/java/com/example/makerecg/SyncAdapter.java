package com.example.makerecg;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.net.ParseException;
import android.os.Bundle;
import android.util.Log;

import org.apache.http.auth.AuthenticationException;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;

/**
 * Handle the transfer of data between a server and an
 * app, using the Android sync adapter framework.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {

    private AccountManager mAccountManager;
    private static final boolean NOTIFY_AUTH_FAILURE = true;
    static final String TAG = "SyncAdapter";

    /**
     * Set up the sync adapter
     */
    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mAccountManager = AccountManager.get(context);
    }
    //...

    /**
     * Set up the sync adapter. This form of the
     * constructor maintains compatibility with Android 3.0
     * and later platform versions
     */
    public SyncAdapter(
            Context context,
            boolean autoInitialize,
            boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        mAccountManager = AccountManager.get(context);
    }

    @Override
    public void onPerformSync(
            Account account,
            Bundle extras,
            String authority,
            ContentProviderClient provider,
            SyncResult syncResult) {

        try {

            List<ADSampleFrame> updatedFrames = null;

            final String authtoken = mAccountManager.blockingGetAuthToken(account,
                    Constants.AUTHTOKEN_TYPE, NOTIFY_AUTH_FAILURE);

            ECGContentUtilities ecgu = ECGContentUtilities.getInstance(this.getContext());

            // Upload up to 200 frames at a time
            List<ADSampleFrame> dirtyFrames = ecgu.uploadPendingFrames(200);

            // Since we currently only are uploading frames, we can skip calling
            // the service where nothing needs to be uploaded.

            if (dirtyFrames.size() > 0 ) {
                updatedFrames = NetworkUtilities.syncSampleFrames(
                        account,
                        authtoken,
                        0,
                        dirtyFrames);
            }

            // We won't currently get any updated frames returned, so we are done.

        } catch (final AuthenticatorException e) {
            Log.e(TAG, "AuthenticatorException", e);
            syncResult.stats.numParseExceptions++;
        } catch (final OperationCanceledException e) {
            Log.e(TAG, "OperationCanceledExcetpion", e);
        } catch (final IOException e) {
            Log.e(TAG, "IOException", e);
            syncResult.stats.numIoExceptions++;
        } catch (final AuthenticationException e) {
            Log.e(TAG, "AuthenticationException", e);
            syncResult.stats.numAuthExceptions++;
        } catch (final ParseException e) {
            Log.e(TAG, "ParseException", e);
            syncResult.stats.numParseExceptions++;
        } catch (final JSONException e) {
            Log.e(TAG, "JSONException", e);
            syncResult.stats.numParseExceptions++;
        }

    }
}
