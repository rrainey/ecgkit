/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.example.makerecg;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.accounts.Account;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

/**
 * Provides utility methods for communicating with the server.
 */
final public class NetworkUtilities {
    /** The tag used to log to adb console. */
    private static final String TAG = "NetworkUtilities";
    /** POST parameter name for the user's account name */
    public static final int HTTP_REQUEST_TIMEOUT_MS = 30 * 1000;
    /** Base URL for the v2 Sample Sync Service */
    public static final String BASE_URL = "https://app1.websimulations.com:9443";
    /** URI for authentication service */
    public static final String AUTH_URI = BASE_URL + "/oauth/token";
    /** URI for sync service */
    public static final String SYNC_URI = BASE_URL + "/api/sampleframe";

    private NetworkUtilities() {
    }

    /**
     * Configures the httpClient to connect to the URL provided.
     */
    public static HttpClient getHttpClient() {
        HttpClient httpClient = new DefaultHttpClient();
        final HttpParams params = httpClient.getParams();
        HttpConnectionParams.setConnectionTimeout(params, HTTP_REQUEST_TIMEOUT_MS);
        HttpConnectionParams.setSoTimeout(params, HTTP_REQUEST_TIMEOUT_MS);
        ConnManagerParams.setTimeout(params, HTTP_REQUEST_TIMEOUT_MS);
        return httpClient;
    }

    /**
     * Connects to the SampleSync test server, authenticates the provided
     * username and password.
     * This is basically a standard OAuth2 password grant interaction.
     *
     * @param username The server account username
     * @param password The server account password
     * @return String The authentication token returned by the server (or null)
     */
    public static String authenticate(String username, String password) {
        String token = null;

        try {

            Log.i(TAG, "Authenticating to: " + AUTH_URI);
            URL urlToRequest = new URL(AUTH_URI);
            HttpURLConnection conn = (HttpURLConnection) urlToRequest.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("grant_type", "password"));
            params.add(new BasicNameValuePair("client_id", "CLIENT_ID"));
            params.add(new BasicNameValuePair("username", username));
            params.add(new BasicNameValuePair("password", password));

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            writer.write(getQuery(params));
            writer.flush();
            writer.close();
            os.close();

            int responseCode = conn.getResponseCode();

            if (responseCode == HttpsURLConnection.HTTP_OK) {
                String response = "";
                String line;
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                while ((line = br.readLine()) != null) {
                    response += line;
                }

                // Response body will look something like this:
                // {
                //    "token_type": "bearer",
                //    "access_token": "0dd18fd38e84fb40e9e34b1f82f65f333225160a",
                //    "expires_in": 3600
                //  }

                JSONObject jresp = new JSONObject(new JSONTokener(response));

                token = jresp.getString("access_token");
            } else {
                Log.e(TAG, "Error authenticating");
                token = null;
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Log.v(TAG, "getAuthtoken completing");
        }

        return token;

    }

    private static String getQuery(List<NameValuePair> params) throws UnsupportedEncodingException
    {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (NameValuePair pair : params)
        {
            if (first)
                first = false;
            else
                result.append("&");

            result.append(URLEncoder.encode(pair.getName(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(pair.getValue(), "UTF-8"));
        }

        return result.toString();
    }

    /**
     * Perform 2-way sync with the server-side ADSampleFrames. We send a request that
     * includes all the locally-dirty contacts so that the server can process
     * those changes, and we receive (and return) a list of contacts that were
     * updated on the server-side that need to be updated locally.
     *
     * @param account The account being synced
     * @param authtoken The authtoken stored in the AccountManager for this
     *            account
     * @param serverSyncState A token returned from the server on the last sync
     * @param dirtyFrames A list of the frames to send to the server
     * @return A list of frames that we need to update locally
     */
    public static List<ADSampleFrame> syncSampleFrames(
            Account account, String authtoken, long serverSyncState, List<ADSampleFrame> dirtyFrames)
            throws JSONException, ParseException, IOException, AuthenticationException {

        List<JSONObject> jsonFrames = new ArrayList<JSONObject>();
        for (ADSampleFrame frame : dirtyFrames) {
            jsonFrames.add(frame.toJSONObject());
        }

        JSONArray buffer = new JSONArray(jsonFrames);
        JSONObject top = new JSONObject();

        top.put("data", buffer);

        // Create an array that will hold the server-side ADSampleFrame
        // that have been changed (returned by the server).
        final ArrayList<ADSampleFrame> serverDirtyList = new ArrayList<ADSampleFrame>();

        // Send the updated frames data to the server
        Log.i(TAG, "Syncing to: " + SYNC_URI);
        URL urlToRequest = new URL(SYNC_URI);
        HttpURLConnection conn = (HttpURLConnection) urlToRequest.openConnection();
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + authtoken);

        OutputStream os = conn.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
        writer.write(top.toString(1));
        writer.flush();
        writer.close();
        os.close();

        Log.i(TAG, "body="+top.toString(1));

        int responseCode = conn.getResponseCode();

        /*

        final HttpPost post = new HttpPost(SYNC_URI);
        post.addHeader("Content-Type", "application/json");
        post.addHeader("Authorization", "Bearer " + authtoken);
        post.setEntity(new ByteArrayEntity(top.toString().getBytes("UTF8")));

        final HttpResponse resp = getHttpClient().execute(post);
        final String response = EntityUtils.toString(resp.getEntity());
        if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
        */
        if (responseCode == HttpsURLConnection.HTTP_OK) {
            // Our request to the server was successful - so we assume
            // that they accepted all the changes we sent up, and
            // that the response includes the contacts that we need
            // to update on our side...

            // TODO: Only uploading data for now ...
            /*
            final JSONArray serverContacts = new JSONArray(response);
            Log.d(TAG, response);
            for (int i = 0; i < serverContacts.length(); i++) {
                ADSampleFrame rawContact = ADSampleFrame.valueOf(serverContacts.getJSONObject(i));
                if (rawContact != null) {
                    serverDirtyList.add(rawContact);
                }
            }
            */
        } else {
            if (responseCode == HttpsURLConnection.HTTP_UNAUTHORIZED) {
                Log.e(TAG, "Authentication exception in while uploading data");
                throw new AuthenticationException();
            } else {
                Log.e(TAG, "Server error in sending sample frames: " + responseCode);
                throw new IOException();
            }
        }

        return null;
    }

}
