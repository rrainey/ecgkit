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
package com.example.makerecg;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

public class BTConnection extends Connection {

	private final BluetoothAdapter mAdapter;
	private BluetoothSocket mSocket;

	private static final UUID MY_UUID_INSECURE = UUID
			.fromString("1dd35050-a437-11e1-b3dd-0800200c9a66");

	public BTConnection(String address) {

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = mAdapter.getRemoteDevice(address);

        try {
            mSocket = device.createRfcommSocketToServiceRecord(MY_UUID_INSECURE);
        } catch (Exception e) {Log.e("","Error creating socket");}

        try {
            mSocket.connect();
            Log.e("","Connected to " + address);
        } catch (IOException e) {

            Log.e("", e.getMessage());
            try {
                Log.e("", "trying fallback...");

                mSocket = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(device, 1);
                mSocket.connect();

                Log.e("", "Connected to BT " + address);

            } catch (InvocationTargetException e1) {
                e1.printStackTrace();
            } catch (NoSuchMethodException e1) {
                e1.printStackTrace();
            } catch (IOException e1) {
                e1.printStackTrace();
            } catch (IllegalAccessException e1) {
                e1.printStackTrace();
            }

        }
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return mSocket.getInputStream();
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		return mSocket.getOutputStream();
	}

	@Override
	public void close() throws IOException {
		mSocket.close();
	}

}
