package com.example.activityprofilesamplegathering;

// Some code taken from http://www.egr.msu.edu/classes/ece480/capstone/spring14/group01/docs/appnote/Wirsing-SendingAndReceivingDataViaBluetoothWithAnAndroidDevice.pdf
// Other code from developer.android.com and stackoverflow.com

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

import android.support.v7.app.ActionBarActivity;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

@SuppressLint("NewApi")
public class MainActivity extends ActionBarActivity {
	
	private static final String myBluetoothAddress = "20:15:07:27:98:01";
	private static final String DTAG = "Debugging Stuff";
	private GravityFilter gravFilter;
	private ConnectedThread myConnectedThread;
	private FileOutputStream dataFileStream;
	private boolean isSampling, connectingToArduino, connectedToArduino, deviceFound, enableDiscovery, readyToCommunicate;
	private File dataFile, sampleDirectory;
	private static final int TURN_ON_BLUETOOTH_REQUEST = 1;
	private TextView statusText;
	private final int interval = 3000; // 3 Seconds

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		dataFileStream = null;
		isSampling = false;
		dataFile = null;
		connectedToArduino = false;
		deviceFound = false;
		enableDiscovery = true;
		readyToCommunicate = true;
		
		// make directory to store sample data
		sampleDirectory = new File("/sdcard/ActivityProfileSampleData/");
		sampleDirectory.mkdirs();
		
		// register filters
		IntentFilter filter1 = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
	    IntentFilter filter2 = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
	    IntentFilter filter3 = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
	    IntentFilter filter4 = new IntentFilter(BluetoothDevice.ACTION_FOUND);
	    this.registerReceiver(mReceiver, filter1);
	    this.registerReceiver(mReceiver, filter2);
	    this.registerReceiver(mReceiver, filter3);
	    this.registerReceiver(mReceiver, filter4);
	    
	    // status textview for bluetooth status updates
	    statusText = (TextView) findViewById(R.id.status_text);
		
	    // toggle button for sampling
		final ToggleButton toggle = (ToggleButton) findViewById(R.id.sample_toggle);
		toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		        if (isChecked && connectedToArduino && readyToCommunicate) {
		            // The toggle is enabled
		        	myConnectedThread.writeString("start_sample");
		        	int sampleNum = 0;
		        	try {
		        		File directoryFiles[] = sampleDirectory.listFiles();
		        		for (int i=0; i < directoryFiles.length; i++)
		        		{
		        		    Log.d(DTAG, "FileName:" + directoryFiles[i].getName());
		        		}
		        		if (directoryFiles.length > 0) {
		        			int maxNum = 0;
		        			for (int i=0; i < directoryFiles.length; i++)
			        		{
		        				String name = directoryFiles[i].getName();
		        				int nameInt = Integer.parseInt(name.substring(name.length()-1));
			        		    if (nameInt > maxNum) {
			        		    	maxNum = nameInt;
			        		    }
			        		}
		        			sampleNum = maxNum + 1;
		        		}
		        		File newSampleDir = new File(sampleDirectory, "Sample" + sampleNum);
	        			newSampleDir.mkdirs();
	        			dataFile = new File(newSampleDir, "Sample" + sampleNum + ".txt");
		        		Log.d(DTAG, "Directory: " +getApplicationContext().getFilesDir());
						dataFileStream = new FileOutputStream(dataFile);
						isSampling = true;
						Log.d(DTAG, "Starting sampling");
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						isSampling = false;
					}
		        } else if (!isChecked && connectedToArduino && readyToCommunicate){
		            // The toggle is disabled
		        	myConnectedThread.writeString("stop_sample");
		        	timeHandler.postDelayed(runnable, interval);
					Log.d(DTAG, "Stopping sampling");
		        } else {
		        	showToast("Not Connected to Arduino or Not Ready to Communicate");
		        	toggle.setChecked(false);
		        }
		    }
		});
		
	}
	
	@Override
	public void onDestroy() {
	    if (mReceiver != null) {
	        this.unregisterReceiver(mReceiver);
	    }
	    super.onDestroy();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		enableDiscovery = true;
		if (!connectedToArduino) {
			Log.d(DTAG, "not connected to arduino");
			enableAndDiscoverBluetooth();
		} else {
			Log.d(DTAG, "Still connected to arduino");
		}
	}
	
	private void startDiscoverBluetooth() {
		enableDiscovery = true;
    	updateStatus("Looking for Arduino...");
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    	mBluetoothAdapter.startDiscovery();
	}
	
	private void stopDiscoverBluetooth() {
		enableDiscovery = false;
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    	mBluetoothAdapter.cancelDiscovery();
	}
	
	@Override
	protected void onPause() {
	    super.onPause();
	    stopDiscoverBluetooth();
	}
	
	private void setFoundArduino() {
		deviceFound = true;
		updateStatus("Found Arduino.");
	}
	
	//The BroadcastReceiver that listens for bluetooth broadcasts
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();
	        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

	        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
	        	//Device found
	        	if (device.getAddress().equalsIgnoreCase(myBluetoothAddress)) {
	        		Log.d(DTAG, "found arduino");
	        		setFoundArduino();
	        		if (connectedToArduino) {
	        			setConnectedToArduino();
	        		} else {
	        			Log.d(DTAG, "connecting...");
	        			setConnectingToArduino();
	        			ConnectThread mConnectThread = new ConnectThread(device);
						mConnectThread.start();
	        		}
	        	}
	        }
	        else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
	        	//Device is now connected
	        	Log.d(DTAG, device.getName());
	        	if (device.getAddress().equalsIgnoreCase(myBluetoothAddress)) {
	        		setConnectedToArduino();
	        		Log.d(DTAG, "Connected to Arduino.");
	        	}
	        }
	        else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
	        	//Done searching
	        	Log.d(DTAG, "done searching");
	    		if (!deviceFound && enableDiscovery) {
	    			Log.d(DTAG, "no device found, starting discovery again");
	    			startDiscoverBluetooth();
	        	}
	        }
	        else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
	        	//Device has disconnected
	        	if (device.getAddress().equalsIgnoreCase(myBluetoothAddress)) {
	        		setDisconnectedFromArduino();
	        		Log.d(DTAG, "disconnected from arduino");
	        		if (enableDiscovery) {
	        			Log.d(DTAG, "starting discovery");
	        			startDiscoverBluetooth();
	        		} else {
	        			Log.d(DTAG, "discovery disabled");
	        		}
	        	}
	        }
	    }
	};
	
	private void setDisconnectedFromArduino() {
		connectingToArduino = false;
		connectedToArduino = false;
		deviceFound = false;
		readyToCommunicate = false;
		updateStatus("Disconnected from Arduino...");
	}
	
	private void setConnectedToArduino() {
		showToast("Connected to Arduino");
    	connectingToArduino = false;
    	connectedToArduino = true;
    	updateStatus("Connected to Arduino.");
	}
	
	private void setConnectingToArduino() {
		connectingToArduino = true;
		connectedToArduino = false;
		updateStatus("Connecting to Arduino...");
	}
	
	private void enableAndDiscoverBluetooth() {
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter != null) {
			// Device does support Bluetooth
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableBtIntent, TURN_ON_BLUETOOTH_REQUEST);
			} else {
				Log.d(DTAG, "bluetooth enabled, starting discovery");
				startDiscoverBluetooth();
			}
		}
	}
	
	// set the bluetooth status update
	private void updateStatus(final String text) {
		runOnUiThread(new Runnable() {
	        public void run()
	        {
	        	statusText.setText(text);
	        }
	    });
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	    // Check which request we're responding to
	    if (requestCode == TURN_ON_BLUETOOTH_REQUEST) {
	        // Make sure the request was successful
	        if (resultCode == RESULT_OK) {
	        	Log.d(DTAG, "starting discovery");
	        	startDiscoverBluetooth();
	        }
	    }
	}
	
	private class ConnectThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;
		private UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
		public ConnectThread(BluetoothDevice device) {
		BluetoothSocket tmp = null;
		mmDevice = device;
		try {
			tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
		} catch (IOException e) { }
			mmSocket = tmp;
		}
		public void run() {
			BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			mBluetoothAdapter.cancelDiscovery();
			try {
				mmSocket.connect();
			} catch (IOException connectException) {
				try {
					mmSocket.close();
				} catch (IOException closeException) { }
				return;
			}
			myConnectedThread = new ConnectedThread(mmSocket);
			myConnectedThread.start();
		}
		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) { }
		}
	}
	
	public void showToast(final String toast)
	{
	    runOnUiThread(new Runnable() {
	        public void run()
	        {
	        	Toast.makeText(getApplicationContext(), toast, Toast.LENGTH_LONG).show();
	        }
	    });
	}
	
	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;
		public ConnectedThread(BluetoothSocket socket) {
		mmSocket = socket;
		InputStream tmpIn = null;
		OutputStream tmpOut = null;
		try {
			tmpIn = socket.getInputStream();
			tmpOut = socket.getOutputStream();
		} catch (IOException e) { }
			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}
		public void run() {
			readyToCommunicate = true;
			byte[] buffer = new byte[1024];
			int begin = 0;
			int bytes = 0;
			while (true) {
				try {
					bytes += mmInStream.read(buffer, bytes, buffer.length - bytes);
					for(int i = begin; i < bytes; i++) {
						if(buffer[i] == "#".getBytes()[0]) {
							mHandler.obtainMessage(1, begin, i, buffer).sendToTarget();
							begin = i + 1;
							if(i == bytes - 1) {
								bytes = 0;
								begin = 0;
							}
						}
					}
				} catch (IOException e) {
					break;
				}
			}
		}
		public void write(byte[] bytes) {
			try {
				mmOutStream.write(bytes);
				mmOutStream.flush();
				Log.d(DTAG, "wrote bytes");
			} catch (IOException e) { }
		}
		public void writeString(String string) {
			PrintStream printStream = new PrintStream(mmOutStream);
			printStream.print(string);
		}
		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) { }
		}
	}
	
	private Handler timeHandler = new Handler();
	private Runnable runnable = new Runnable(){
	    public void run() {
	        if (isSampling) {
	        	Log.d(DTAG, "Still sampling");
	        	myConnectedThread.writeString("stop_sample");
	        	timeHandler.postDelayed(runnable, interval);
				Log.d(DTAG, "Sending stop sampling command again");
	        } else {
	        	Log.d(DTAG, "Not sampling");
	        }
	    }
	};
	
	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
		byte[] writeBuf = (byte[]) msg.obj;
		int begin = (int)msg.arg1;
		int end = (int)msg.arg2;

		switch(msg.what) {
			case 1:
				String writeMessage = new String(writeBuf);
				writeMessage = writeMessage.substring(begin, end);
				//Log.d(DTAG, "writeMessage: " + writeMessage);
				if (writeMessage.length() != 0 && writeMessage.substring(0,1).equals("S")) {
//					int accelData[] = parseAccelData(writeMessage);
//					accelData = gravFilter.filter(accelData);
					if (isSampling) {
						try {
							dataFileStream.write(writeMessage.getBytes());
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					//Log.d(DTAG, "X: " + accelData[0] + ", Y: " + accelData[1] + ", Z: " + accelData[2]);
				} else if(writeMessage.length() != 0 && writeMessage.equals("stopped_sample")) {
					Log.d(DTAG, "Stopped Sampling confirmation");
					isSampling = false;
					try {
						dataFileStream.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					showToast("Sampling has Stopped");
				}
				break;
			}
		}
	};
	
	private int[] parseAccelData(String accelData) {
		int accelArray[] = new int[3];
		String temp = accelData.substring(accelData.indexOf('X') + 1, accelData.indexOf('Y'));
		if (temp != null) {
			accelArray[0] = Integer.parseInt(temp);
		}
		temp = accelData.substring(accelData.indexOf('Y') + 1, accelData.indexOf('Z'));
		if (temp != null) {
			accelArray[1] = Integer.parseInt(temp);
		}
		temp = accelData.substring(accelData.indexOf('Z') + 1);
		if (temp != null) {
			accelArray[2] = Integer.parseInt(temp);
		}
		return accelArray;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
