package com.example.makerecg.activity;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

//import com.android.future.usb.UsbAccessory;
//import com.android.future.usb.UsbManager;

import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.content.IntentFilter;
import android.content.Intent;

import com.example.makerecg.ADK;
import com.example.makerecg.ADSampleFrame;
import com.example.makerecg.ADSampleQueue;
import com.example.makerecg.BTConnection;
import com.example.makerecg.Connection;
import com.example.makerecg.ECGRendererGL;
import com.example.makerecg.R;
import com.example.makerecg.UsbConnection;
import com.example.makerecg.Utilities;
import com.example.makerecg.ADSampleQueue.QUEUE_STATE;
import com.example.makerecg.R.id;
import com.example.makerecg.R.layout;
import com.example.makerecg.R.string;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.opengl.GLSurfaceView;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ToggleButton;

public class ECGActivity extends Activity implements Callback, Runnable {
	
	private static final boolean gLogPackets = false;
	
	private Handler mDeviceHandler;
	private Handler mSettingsPollingHandler;

	private UsbManager mUSBManager;
	private Connection mConnection;
	private UsbAccessory mAccessory;
	
	private static String curBtName = "<UNKNOWN>";
	private static ECGActivity sHome = null;
	
	private boolean mIgnorePrefChanges = false;
	private boolean mPollSettings = false;
	
	private byte[] mSettingsBuffer = null;
	private byte[] mSettingsPayload = new byte[8];
	private byte[] mQueryBuffer = new byte[4];
	private byte[] mEmptyPayload = new byte[0];
	
	private boolean mbAverageSampleSeries = false;
	
	static final byte CMD_GET_PROTO_VERSION = 1; // () -> (u8 protocolVersion)
	static final byte CMD_GET_SENSORS = 2; // () -> (sensors:
											// i32,i32,i32,i32,u16,u16,u16,u16,u16,u16,u16,i16,i16,i16,i16,i16,i16)
	static final byte CMD_FILE_LIST = 3; // FIRST: (char name[]) -> (fileinfo or
											// single zero byte) OR NONLATER: ()
											// -> (fileinfo or empty or single
											// zero byte)
	static final byte CMD_FILE_DELETE = 4; // (char name[0-255)) -> (char
											// success)
	static final byte CMD_FILE_OPEN = 5; // (char name[0-255]) -> (char success)
	static final byte CMD_FILE_WRITE = 6; // (u8 data[]) -> (char success)
	static final byte CMD_FILE_CLOSE = 7; // () -> (char success)
	static final byte CMD_GET_UNIQ_ID = 8; // () -> (u8 uniq[16])
	static final byte CMD_BT_NAME = 9; // (char name[]) -> () OR () -> (char
										// name[])
	static final byte CMD_BT_PIN = 10; // (char PIN[]) -> () OR () -> (char
										// PIN[])
	static final byte CMD_TIME = 11; // (timespec) -> (char success)) OR () >
										// (timespec)
	static final byte CMD_SETTINGS = 12; // () ->
											// (alarm:u8,u8,u8,brightness:u8,color:u8,u8,u8:volume:u8)
											// or
											// (alarm:u8,u8,u8,brightness:u8,color:u8,u8,u8:volume:u8)
											// > (char success)
	static final byte CMD_ALARM_FILE = 13; // () -> (char file[0-255]) OR (char
											// file[0-255]) > (char success)
	static final byte CMD_GET_LICENSE = 14; // () -> (u8 licensechunk[]) OR ()
											// if last sent
	static final byte CMD_DISPLAY_MODE = 15; // () -> (u8) OR (u8) -> ()
	static final byte CMD_LOCK = 16; // () -> (u8) OR (u8) -> ()
	
	static final byte CMD_X_TELEMETRY_STATE = 17; // 
	                                    // u8 (1=on/0=off), u16 (channel mask), u32 (poll interval_ms), u16 (max pipeline delay_ms)
										// turns on/off sampling; accessory replies with the full contents of the original message to ack that it has been processed
	static final byte CMD_X_TELEMETRY_BLOCK = 18; // 
    									// u32 (poll interval_ms), u32 (start_timestamp_ms), u32 (end_timestamp_ms), u16(channel mask), u16 (sample count, N), num channels * N * i16 (sample array)
										// these messages are only sent from the accessory to the Android device, and only sent while sampling state is "on".
	/*
	 * TODO: Add extra message types here (both output and input messages)
	 */
	

	public static ECGActivity get() {
		return sHome;
	}
	
	public Object getAccessory() {
		return mAccessory;
	}
	
	public boolean startPollingSettings() {
		boolean wasPolling = mPollSettings;
		mPollSettings = true;
		if (!wasPolling) {
			pollSettings();
		}
		return wasPolling;
	}

	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.main);
        
		mDeviceHandler = new Handler(this);
		mSettingsPollingHandler = new Handler(this);

		//mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		//mPreferences.registerOnSharedPreferenceChangeListener(this);

		mUSBManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        
        GLSurfaceView glSurfaceView =
                (GLSurfaceView) findViewById(R.id.WaveformArea);
       glSurfaceView.setRenderer(new ECGRendererGL(getBaseContext()));
       glSurfaceView.setRenderMode( GLSurfaceView.RENDERMODE_CONTINUOUSLY );
        
       /*
        * Start with scope controls disabled -- enable them once we're connected
        */
       ToggleButton toggle = (ToggleButton) findViewById(R.id.tbtn_runtoggle);
       toggle.setEnabled(true);
       toggle.setChecked(false);
            
       connectToAccessory();
        
       sHome = this;

       //startPollingSettings();
        
       startECGTelemetry();
    }
    /*
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    
        this.requestWindowFeature(Window.FEATURE_NO_TITLE); // (NEW)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN); // (NEW)
        
 		GLSurfaceView view = new GLSurfaceView(this);
   		view.setRenderer(new ECGRenderer());
   		setContentView(view);
    }
    */
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_main, menu);
        return true;
    }
    
    public void runToggle(View view) {
    	// Is the toggle on?
    	ToggleButton t = ((ToggleButton) view);
        boolean bChecked = t.isChecked();
        
        Log.d(ADK.TAG, "runToggle called: bChecked =" + bChecked);
        
        if (bChecked) {
        	ADSampleQueue.getSingletonObject().thaw();
        	//t.setChecked(false);
        } 
        else {
        	ADSampleQueue.getSingletonObject().freeze();
        	//t.setChecked(true);
        }
    }
    
    public void filterAToggle(View view) {
    	// Is the toggle on?
    	ToggleButton t = ((ToggleButton) view);
        boolean bChecked = t.isChecked();
        
        Log.d(ADK.TAG, "filterAToggle called: bChecked =" + bChecked);
        
        if (bChecked) {
        	mbAverageSampleSeries = true;
        } 
        else {
        	mbAverageSampleSeries = false;
        }
        
        // State changed ... update accessory
        QUEUE_STATE s = ADSampleQueue.getSingletonObject().getQueueState();
        
        if ( mConnection != null && s != QUEUE_STATE.FROZEN ) {
        	startECGTelemetry();
        }
    }
    
    public void filterBToggle(View view) {
    	// Is the toggle on?
    	ToggleButton t = ((ToggleButton) view);
        boolean bChecked = t.isChecked();
        
        Log.d(ADK.TAG, "filterBToggle called: bChecked =" + bChecked);
        
    }

    /** Called when the user touches the button */
    public void sendMessage(View view) {
        // Do something in response to button click
    }
    
    private void changeBtName() {

		// This example shows how to add a custom layout to an AlertDialog
		LayoutInflater factory = LayoutInflater.from(this);
		final View textEntryView = factory.inflate(R.layout.alert_dialog, null);
		final EditText e = (EditText) textEntryView
				.findViewById(R.id.btname_edit);

		AlertDialog ad = new AlertDialog.Builder(this)
				/*.setIconAttribute(android.R.attr.alertDialogIcon)*/
				.setTitle("Set ADK Bluetooth Name")
				.setView(textEntryView)
				.setPositiveButton(R.string.set_bt_name_ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {

								curBtName = e.getText().toString();
								if (curBtName.equals(""))
									curBtName = "ADK 2012";

								byte b[] = null;
								try {
									b = curBtName.getBytes("UTF-8");
								} catch (UnsupportedEncodingException e1) {
									// well aren't you SOL....
									e1.printStackTrace();
								}
								byte b2[] = new byte[b.length + 1];
								for (int i = 0; i < b.length; i++)
									b2[i] = b[i];
								b2[b.length] = 0;

								sendCommand(CMD_BT_NAME, CMD_BT_NAME, b2);
							}
						})
				.setNegativeButton(R.string.set_bt_name_cancel,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {

								// user cancels
							}
						}).create();

		e.setText(curBtName);
		ad.show();
	}
    
	public void connectToAccessory() {

		// bail out if we're already connected
		if (mConnection != null) {
			return;
		}

		if (getIntent().hasExtra(BTDeviceListActivity.EXTRA_DEVICE_ADDRESS)) {
			String address = getIntent().getStringExtra(
					BTDeviceListActivity.EXTRA_DEVICE_ADDRESS);
			Log.i(ADK.TAG, "connecting to " + address);
			mConnection = new BTConnection(address);
			performPostConnectionTasks();
		} 
		else {
			// assume only one accessory (currently safe assumption)
			//UsbAccessory[] accessories = mUSBManager.getAccessoryList();
			//UsbAccessory accessory = (accessories == null) ? null : accessories[0];
			/*
			UsbAccessory accessory = (UsbAccessory) getIntent().getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
			if (accessory != null) {
				if (mUSBManager.hasPermission(accessory)) {
					Log.d(ADK.TAG, "Had permission already in connectToAccessory()");
					openAccessory( accessory );
				} 
				else {
					// Request permission
					PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(UsbConnection.ACTION_USB_PERMISSION), 0);
					IntentFilter filter = new IntentFilter(UsbConnection.ACTION_USB_PERMISSION);
					registerReceiver(((UsbConnection)mConnection).getReceiver(), filter);
				}
			} else {
				Log.d(ADK.TAG, "connectToAccessory() Assertion error: mAccessory is null");
			}
			*/
			// assume only one accessory (currently safe assumption)
			UsbAccessory[] accessories = mUSBManager.getAccessoryList();
			UsbAccessory accessory = (accessories == null) ? null : accessories[0];
			if (accessory != null) {
				if (mUSBManager.hasPermission(accessory)) {
					openAccessory(accessory);
					Log.d(ADK.TAG, "connectToAccessory(): accessory opened");
				} 
				else {
					Log.d(ADK.TAG, "connectToAccessory() Assertion error: no permission to open accessory");
					// synchronized (mUsbReceiver) {
					// if (!mPermissionRequestPending) {
					// mUsbManager.requestPermission(accessory,
					// mPermissionIntent);
					// mPermissionRequestPending = true;
					// }
					// }
				}
			} 
			else {
				Log.d(ADK.TAG, "connectToAccessory() Assertion error: mAccessory is null");
			}
		}

	}
	
	private void disconnect() {
		finish();
	}
	
	@Override
	public void onPause() {
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		pollSettings();
	}

	@Override
	public void onDestroy() {
		sHome = null;
		if (mConnection != null) {
			try {
				mConnection.close();
			} catch (IOException e) {
			} finally {
				mConnection = null;
			}
		}
		super.onDestroy();
	}
	
	private void pollSettings() {
		/*
		if (mPollSettings) {
			sendCommand(CMD_SETTINGS, CMD_SETTINGS);
			sendCommand(CMD_DISPLAY_MODE, CMD_DISPLAY_MODE);
			sendCommand(CMD_LOCK, CMD_LOCK);
			Message msg = mSettingsPollingHandler.obtainMessage(99);
			if (!mSettingsPollingHandler.sendMessageDelayed(msg, 500)) {
				Log.e(ADK.TAG, "failed to queue settings message");
			}
		}
		*/
	}
	
	public void startECGTelemetry() {
		// u8 (1=on/0=off), u16 (channel mask), u32 (poll interval_ms), u16 (max pipeline delay_ms)
		byte b[] = new byte[9];
		
		if ( mbAverageSampleSeries ) {
			b[0] = (byte) 0x81;
		}
		else {
			b[0] = (byte) 0x01;
		}
		
		b[1] = 0;				/* u16 - low byte */
		b[2] = (byte) 0x80;	/* u16 - high byte */
		
		b[3] = 10;
		b[4] = 0;
		b[5] = 0;
		b[6] = 0;
		
		b[7] = 100;	/* u16 - low byte */
		b[8] = 0;		/* u16 - high byte */
		
		sendCommand( CMD_X_TELEMETRY_STATE, CMD_X_TELEMETRY_STATE, b );
	}
	
	public void stopECGTelemetry() {
		// u8 (1=on/0=off), u16 (channel mask), u32 (poll interval_ms), u16 (max pipeline delay_ms)
		byte b[] = new byte[9];
		b[0] = 0;
		
		b[1] = 0;	/* u16 - low byte */
		b[2] = 0;	/* u16 - high byte */
		
		b[3] = 0;
		b[4] = 0;
		b[5] = 0;
		b[6] = 0;
		
		b[7] = 0;	/* u16 - low byte */
		b[8] = 0;	/* u16 - high byte */
		
		sendCommand( CMD_X_TELEMETRY_STATE, CMD_X_TELEMETRY_STATE, b );
	}

	public void stopPollingSettings() {
		mPollSettings = false;
	}

	public void disconnectFromAccessory() {
		closeAccessory();
	}

	private void openAccessory(UsbAccessory accessory) {
		mConnection = new UsbConnection(this, mUSBManager, accessory);
		performPostConnectionTasks();
	}

	private void performPostConnectionTasks() {
		//sendCommand(CMD_GET_PROTO_VERSION, CMD_GET_PROTO_VERSION);
		//sendCommand(CMD_SETTINGS, CMD_SETTINGS);
		//sendCommand(CMD_BT_NAME, CMD_BT_NAME);
		//sendCommand(CMD_ALARM_FILE, CMD_ALARM_FILE);
		//listDirectory(TUNES_FOLDER);

		Thread thread = new Thread(null, this, "Maker ECG 2012");
		thread.start();
	}

	public void closeAccessory() {
		try {
			mConnection.close();
		} catch (IOException e) {
		} finally {
			mConnection = null;
		}
		
		/*
        * Start with scope controls disabled -- enable them once we're connected
        */
       ToggleButton toggle = (ToggleButton) findViewById(R.id.tbtn_runtoggle);
       toggle.setEnabled(false);
       toggle.setChecked(false);
	}
	
	public void getSensors() {
		sendCommand(CMD_GET_SENSORS, CMD_GET_SENSORS);
	}

	/**
	 * 
	 * @param command
	 * @param sequence
	 * @param payload
	 * @param buffer
	 * @return
	 */
	public byte[] sendCommand(int command, int sequence, byte[] payload,
			byte[] buffer) {
		int bufferLength = payload.length + 4;
		if (buffer == null || buffer.length < bufferLength) {
			Log.i(ADK.TAG, "allocating new command buffer of length "
					+ bufferLength);
			buffer = new byte[bufferLength];
		}

		buffer[0] = (byte) command;
		buffer[1] = (byte) sequence;
		buffer[2] = (byte) (payload.length & 0xff);
		buffer[3] = (byte) ((payload.length & 0xff00) >> 8);
		if (payload.length > 0) {
			System.arraycopy(payload, 0, buffer, 4, payload.length);
		}
		if (mConnection != null && buffer[1] != -1) {
			try {
				if (gLogPackets) {
					Log.i(ADK.TAG, "sendCommand: "
									+ Utilities.dumpBytes(buffer, buffer.length));
				}
				mConnection.getOutputStream().write(buffer);
			} 
			catch (IOException e) {
				Log.e(ADK.TAG, "accessory write failed", e);
			}
		}
		return buffer;
	}

	public void sendCommand(int command, int sequence, byte[] payload) {
		sendCommand(command, sequence, payload, null);
	}

	private void sendCommand(int command, int sequence) {
		sendCommand(command, sequence, mEmptyPayload, mQueryBuffer);
	}
	
	/* Thread managing incoming messages from the device.
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		int nRet = 0;
		byte[] buffer = new byte[8192];
		int nPos = 0;
		
		try {
			
			InputStream s = mConnection.getInputStream();
	
			while (nRet >= 0) {
				nRet = s.read(buffer, nPos, buffer.length - nPos);
				if ( nRet > 0 ) {
					nPos += nRet;
					int nBytesRemaining = processInboundMessage(buffer, nPos);
					if (nBytesRemaining > 0) {
						System.arraycopy(buffer, nPos - nBytesRemaining, buffer, 0, nBytesRemaining);
						nPos = nBytesRemaining;
						Log.w(ADK.TAG, "residual bytes " + nBytesRemaining);
					} 
					else {
						nPos = 0;
					}
				}
				else if ( nRet == 0 ) {
					// allow GL to draw
					Thread.sleep(5);
				}
			}
			
		}
		catch (IOException e) {
		}
		catch (InterruptedException e) {
		}
		
		// Lost connection (probably)
		Intent connectIntent = new Intent(this, ConnectActivity.class);
		connectIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(connectIntent);
	}
	
	/**
	 * Process an inbound message from the Arduino
	 * @param buffer
	 * @param nBytes
	 * @return
	 */
	public int processInboundMessage(byte[] buffer, int nBytes) {

		if (gLogPackets) {
			Log.i(ADK.TAG,
					"read " + nBytes + " bytes: "
							+ Utilities.dumpBytes(buffer, nBytes));
		}

		// TODO: this could be more efficient.
		ByteArrayInputStream inputStream = new ByteArrayInputStream(buffer, 0, nBytes);
		InboundMessageHandler ph = new InboundMessageHandler(mDeviceHandler, inputStream);
		ph.processInboundMessage();
		return inputStream.available();
	}

   
	private static class InboundMessageHandler {

		InputStream mInputStream;
		Handler mHandler;

		public InboundMessageHandler(Handler handler, InputStream inputStream) {
			mHandler = handler;
			mInputStream = inputStream;
		}

		int readByte() throws IOException {
			int retVal = mInputStream.read();
			if (retVal == -1) {
				throw new RuntimeException("End of stream reached.");
			}
			return retVal;
		}

		int readInt16() throws IOException {
			int low = readByte();
			int high = readByte();
			if (gLogPackets) {
				Log.i(ADK.TAG, "readInt16 low=" + low + " high=" + high);
			}
			return low | (high << 8);
		}


		byte[] readBuffer(int bufferSize) throws IOException {
			byte readBuffer[] = new byte[bufferSize];
			int index = 0;
			int bytesToRead = bufferSize;
			while (bytesToRead > 0) {
				int amountRead = mInputStream.read(readBuffer, index,
						bytesToRead);
				if (amountRead == -1) {
					throw new RuntimeException("End of stream reached.");
				}
				bytesToRead -= amountRead;
				index += amountRead;
			}
			return readBuffer;
		}

		/**
		 * Process incoming message from device
		 */
		public void processInboundMessage() {
			mInputStream.mark(0);
			try {
				while (mInputStream.available() > 0) {
					int opCode = readByte();
	
					if (gLogPackets)
						Log.d(ADK.TAG, "opCode = " + opCode);

					if (isValidOpCode(opCode)) {
						int sequence = readByte();
						if (gLogPackets)
							Log.i(ADK.TAG, "sequence = " + sequence);
						int replySize = readInt16();
						if (gLogPackets)
							Log.i(ADK.TAG, "replySize = " + replySize);
						byte[] replyBuffer = readBuffer(replySize);
						if (gLogPackets) {
							Log.i(ADK.TAG,
									"replyBuffer: "
											+ Utilities.dumpBytes(replyBuffer,
													replyBuffer.length));
						}
						processReply(opCode & 0x7f, sequence, replyBuffer);
						mInputStream.mark(0);
					}
				}
				mInputStream.reset();
			} catch (IOException e) {
				Log.i(ADK.TAG, "ProtocolHandler error " + e.toString());
			}
		}

		boolean isValidOpCode(int opCodeWithReplyBitSet) {
			//if ((opCodeWithReplyBitSet & 0x80) != 0) {
				int opCode = opCodeWithReplyBitSet & 0x7f;
				return ((opCode >= CMD_GET_PROTO_VERSION) && (opCode <= CMD_X_TELEMETRY_BLOCK));
			//}
			//return false;
		}

		private void processReply(int opCode, int sequence, byte[] replyBuffer) {
			Message msg = mHandler.obtainMessage(opCode, sequence, 0, replyBuffer);
			mHandler.sendMessage(msg);
		}
	}

	@Override
	public boolean handleMessage(Message msg) {
		if (msg.getTarget() == mDeviceHandler) {
			return handleDeviceMethod(msg);
		} else {
			//pollSettings();
			return true;
		}
	}
	
	private void handleGetSensorsCommand(byte[] sensorBytes) {
		if (gLogPackets)
			Log.d(ADK.TAG,
					"handleGetSensorsCommand: "
							+ Utilities.dumpBytes(sensorBytes,
									sensorBytes.length));
		if (sensorBytes.length > 23) {
			int sensorValues[] = Utilities.byteArrayToIntArray(sensorBytes);
			int proxNormalized[] = {
					sensorValues[20] | (sensorValues[21] << 8),
					sensorValues[22] | (sensorValues[23] << 8),
					sensorValues[24] | (sensorValues[25] << 8) };
			proxNormalized[2] *= 3;
			// find max
			int proxMax = 0;
			for (int i = 0; i < 3; i++)
				if (proxMax < proxNormalized[i])
					proxMax = proxNormalized[i];
			proxMax++;
			// normalize to 8-bits
			for (int i = 0; i < 3; i++)
				proxNormalized[i] = (proxNormalized[i] << 8) / proxMax;
			final int exp[] = { 0, 19, 39, 59, 79, 100, 121, 143, 165, 187,
					209, 232, 255, 279, 303, 327, 352, 377, 402, 428, 454, 481,
					508, 536, 564, 592, 621, 650, 680, 710, 741, 772, 804, 836,
					869, 902, 936, 970, 1005, 1040, 1076, 1113, 1150, 1187,
					1226, 1264, 1304, 1344, 1385, 1426, 1468, 1511, 1554, 1598,
					1643, 1688, 1734, 1781, 1829, 1877, 1926, 1976, 2026, 2078,
					2130, 2183, 2237, 2292, 2348, 2404, 2461, 2520, 2579, 2639,
					2700, 2762, 2825, 2889, 2954, 3020, 3088, 3156, 3225, 3295,
					3367, 3439, 3513, 3588, 3664, 3741, 3819, 3899, 3980, 4062,
					4146, 4231, 4317, 4404, 4493, 4583, 4675, 4768, 4863, 4959,
					5057, 5156, 5257, 5359, 5463, 5568, 5676, 5785, 5895, 6008,
					6122, 6238, 6355, 6475, 6597, 6720, 6845, 6973, 7102, 7233,
					7367, 7502, 7640, 7780, 7922, 8066, 8213, 8362, 8513, 8666,
					8822, 8981, 9142, 9305, 9471, 9640, 9811, 9986, 10162,
					10342, 10524, 10710, 10898, 11089, 11283, 11480, 11681,
					11884, 12091, 12301, 12514, 12731, 12951, 13174, 13401,
					13632, 13866, 14104, 14345, 14591, 14840, 15093, 15351,
					15612, 15877, 16147, 16421, 16699, 16981, 17268, 17560,
					17856, 18156, 18462, 18772, 19087, 19407, 19733, 20063,
					20398, 20739, 21085, 21437, 21794, 22157, 22525, 22899,
					23279, 23666, 24058, 24456, 24861, 25272, 25689, 26113,
					26544, 26982, 27426, 27878, 28336, 28802, 29275, 29756,
					30244, 30740, 31243, 31755, 32274, 32802, 33338, 33883,
					34436, 34998, 35568, 36148, 36737, 37335, 37942, 38559,
					39186, 39823, 40469, 41126, 41793, 42471, 43159, 43859,
					44569, 45290, 46023, 46767, 47523, 48291, 49071, 49863,
					50668, 51486, 52316, 53159, 54016, 54886, 55770, 56668,
					57580, 58506, 59447, 60403, 61373, 62359, 63361, 64378,
					65412 };
			for (int i = 0; i < 3; i++)
				proxNormalized[i] = (exp[proxNormalized[i]] + 128) >> 8;

			SharedPreferences.Editor editor = PreferenceManager
					.getDefaultSharedPreferences(this).edit();
			int color = (proxNormalized[0] << 16) | (proxNormalized[1] << 8)
					| proxNormalized[2];
			/* TODO: disabled
			editor.putInt(Preferences.PREF_COLOR_SENSOR, color);
			*/
			editor.commit();
		}
	}

	private void handleLockCommand(byte[] lockedBytes) {
		if (gLogPackets)
			Log.d(ADK.TAG,
					"lockBytes: "
							+ Utilities.dumpBytes(lockedBytes,
									lockedBytes.length));
		if (lockedBytes.length > 0 && lockedBytes[0] != 1) {
			mIgnorePrefChanges = true;
			//SharedPreferences.Editor editor = PreferenceManager
			//		.getDefaultSharedPreferences(this).edit();
			/* TODO: removed
			editor.putBoolean(Preferences.PREF_LOCKED, lockedBytes[0] == 2);
			editor.commit();
			*/
			mIgnorePrefChanges = false;
		}
	}
	
	/**
	 * @param lockedBytes
	 */
	private void handleStateCommandReply(byte[] lockedBytes) {
		//if (gLogPackets)
			Log.d(ADK.TAG,
					"state command reply bytes: "
							+ Utilities.dumpBytes(lockedBytes,
									lockedBytes.length));
		/*
		 * Reset data queue on receipt of acknowledgment
		 */
		if (lockedBytes.length > 0 && lockedBytes[0] != 0) {
			ADSampleQueue.getSingletonObject().restart();
			
			ToggleButton toggle = (ToggleButton) findViewById(R.id.tbtn_runtoggle);
			toggle.setChecked(true);
			toggle.setEnabled(true);
		}
	}
	
	private void handleTelemetryCommand(byte[] bufferBytes) {
		
		ADSampleFrame f;
		short [] s = new short[1024];
		int n;
		int i;
		
		if (gLogPackets) {
			Log.d(ADK.TAG,
					"telemetryBytes: " + Utilities.dumpBytes(bufferBytes, bufferBytes.length));
		}
		
		/*
		 * TODO: parse A/D values and store them.
		 */
		
		//int a = ((bufferBytes[1] & 0xff) << 8) | (bufferBytes[0] & 0xff);
		long b = ((bufferBytes[5] & 0xff) << 24) | ((bufferBytes[4] & 0xff) << 16) | ((bufferBytes[3] & 0xff) << 8) | (bufferBytes[2] & 0xff);
		long c = ((bufferBytes[9] & 0xff) << 24) | ((bufferBytes[8] & 0xff) << 16) | ((bufferBytes[7] & 0xff) << 8) | (bufferBytes[6] & 0xff);
		//int d = (bufferBytes[11] << 8) | (bufferBytes[10] & 0xff);
		int nSampleCount = ((bufferBytes[13] & 0xff) << 8) | (bufferBytes[12] & 0xff);
		
		n = 14;
		
		for (i=0; i<nSampleCount; ++i) {
			s[i] = (short) (((bufferBytes[n+1] & 0xff) << 8) | (bufferBytes[n] & 0xff));
			n += 2;
		}

		if (gLogPackets) {
			Log.d(ADK.TAG, "start timestamp(ms) " + b );
			Log.d(ADK.TAG, "  end timestamp(ms) " + c );
			Log.d(ADK.TAG, "       sample count " + nSampleCount );
			Log.d(ADK.TAG, "avg sample time (ms) " + (double) ( c - b ) / (double) nSampleCount );
		}
		
		/*
		 * Create a sample frame object and queue it for display
		 */
		f = new ADSampleFrame(b, c, nSampleCount, s);
		ADSampleQueue.getSingletonObject().addFrame( f );
		
	}
	
	/**
	 * Process a message coming back from the Arduino
	 * @param msg
	 * @return
	 */
	private boolean handleDeviceMethod(Message msg) {
		switch (msg.what) {
		/*
		case CMD_SETTINGS:
			handleSettingsCommand((byte[]) msg.obj);
			return true;
		case CMD_BT_NAME:
			handleBtNameCommand((byte[]) msg.obj);
		case CMD_GET_LICENSE:
			handleLicenseTextCommand((byte[]) msg.obj);
			return true;
		case CMD_FILE_LIST:
			handleFileListCommand((byte[]) msg.obj);
			return true;
		case CMD_ALARM_FILE:
			handleAlarmFileCommand((byte[]) msg.obj);
			return true;
		*/
		case CMD_GET_SENSORS:
			handleGetSensorsCommand((byte[]) msg.obj);
			return true;
		case CMD_LOCK:
			handleLockCommand((byte[]) msg.obj);
			return true;
		case CMD_X_TELEMETRY_STATE:
			handleStateCommandReply((byte[]) msg.obj);
			return true;
		case CMD_X_TELEMETRY_BLOCK:
			handleTelemetryCommand((byte[]) msg.obj);
			return true;
		/*
		 * TODO: Add extra handlers for messages from the device here.
		 */
		}
		return false;
	}
}