/*The contents of this file are subject to the Mozilla Public License
Version 1.1 (the "License"); you may not use this file except in
compliance with the License. You may obtain a copy of the License at
http://www.mozilla.org/MPL/

Software distributed under the License is distributed on an "AS IS"
basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
License for the specific language governing rights and limitations
under the License.

The Original Code is copyright (c) Cool & Groovy Toy Company Ltd.

The Initial Developer of the Original Code is Cool & Groovy Toy Company Ltd.
*/
package uk.toy;

import java.io.IOException;
import java.util.UUID;

import uk.toy.bt.BluetoothReader;
import uk.toy.bt.BluetoothWriterThread;
import uk.toy.messageproviders.MessageProvider;
import uk.toy.messageproviders.MessageProviderFactory;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * Background worker thread.
 * 
 * Handles Bluetooth communication and message retrieval.
 * 
 * 
 * @author Some Guy
 *
 */
public class WorkerService extends Service implements Runnable {

	private MessageProvider currentProvider;
	private BluetoothSocket btSocket;
	private BluetoothServerSocket btServerSocket;
	private Thread currentProviderThread;
	private BluetoothReader btrRunnable;
	private BluetoothWriterThread talker;
	private Thread btReaderThread;
	private Thread workerThread = new Thread(this);
	private BroadcastReceiver receiver = null;
	private boolean stop = false;
	
	public static final int MESSAGE_SERVICE_CONNECTED = 0;
	public static final int MESSAGE_SERVICE_DISCONNECTED = 1;
	// we were connected (or at least in a state where we should have been
	// and there was fail
	public static final int MESSAGE_SERVICE_IO_FAIL = 2;
	

	/**
	 * Used to provide a handle on a WorkerService instance for use by the UI.
	 */
	private final IBinder mBinder = new LocalBinder();

	/**
	 * Flag indicating whether we are busy obtaining a socket or not.
	 */
	private boolean obtainingSocket = false;
	/**
	 * Message handler used to propagate state back to UI.
	 */
	private Handler messageHandlerUI = null;
	
	private Handler messageHandlerService = null;
	/**
	 * How long the device is supposed to stay discoverable, in seconds.
	 * Max is 300s according to API documentation.
	 */
	private static final int LENGTH_BT_DISCOVERY = 300;

	/**
	 * UUID for the service record. The toy looks for this service.
	 * This is the UUID for the DUN Bluetooth profile.
	 * Do not change this unless you know what you're doing.
	 */
	private static final UUID MY_UUID = UUID
			.fromString("00001103-0000-1000-8000-00805F9B34FB");

	private static final String LOGTAG = "WorkerService";

	@Override
	public int onStartCommand(Intent intent, int flags, int startID) {
		if (intent == null) {
			Log
					.i(
							LOGTAG,
							"Got started with null intent. Perhaps after an exception killed us. Stopping ourselves");
			this.stopSelf();
			return Service.START_NOT_STICKY;
		}
		Log.d(LOGTAG, "WorkerService got onStartCommand");
		this.receiver = new BTBroadcastReceiver();
		this.registerReceiver(this.receiver, new IntentFilter(
				BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
		Log.d(LOGTAG, "BTBroadcastReceiver registered");
		// TODO handle flags
		// if we run makeDiscoverable inside run(), there will be complainints
		// about a missing Looper for this thread. So let's just call it here.
		this.makeDiscoverable();
		// get MessageProvider
		int id = intent.getIntExtra(Main.PREF_MESSAGEPROVIDER_KEY, -1);
		this.currentProvider = MessageProviderFactory.getMessageProvider(id,
				this);
		return START_STICKY;
	}

	/**
	 * Dispatches MessageProvider and Bluetoothtalker threads.
	 * 
	 * These talk to each other using intents.
	 */
	@Override
	public void run() {
		if (this.messageHandlerUI == null) {
			throw new IllegalStateException(
					"Call setHandler before starting the thread!");
		}
		// fire them up
		Looper.prepare();
		this.getBluetoothSocket();
		if (this.btSocket == null) {
			// TODO: UI needs to reflect error state accordingly
			Log.e(LOGTAG, "Could not obtain bluetooth socket.");
			this.sendBluetoothConnectionFailedMessage();
			return;
		}
		this.sendBluetoothSocketObtainedMessage();
		this.currentProviderThread = new Thread(this.currentProvider);
		this.messageHandlerService = new ServiceHandler();
		talker = new BluetoothWriterThread(this.messageHandlerService, this.btSocket);
		talker.start();
		this.btrRunnable = new BluetoothReader(this.btSocket,
				talker, this, this.messageHandlerService);
		this.btReaderThread = new Thread(btrRunnable);
		this.currentProviderThread.start();
		this.btReaderThread.start();
	}

	/**
	 * Allows the Toy to discover the handset.
	 * 
	 * TODO: there is no error handling right now! this is a regression from
	 * previous behaviour. Need to define callback!
	 */
	private void makeDiscoverable() {
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter
				.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
			Log.e(LOGTAG, "Device does not support bluetooth!");
			this.sendBluetoothConnectionFailedMessage();
		}
		Intent discoverableIntent = new Intent(
				BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		discoverableIntent.putExtra(
				BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
				LENGTH_BT_DISCOVERY);
		// Needs NEW_TASK flag because we're calling from a Service, not from an
		// Activity
		Log.i(LOGTAG, "Making phone discoverable (launching intent");
		discoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		// TODO: if user answer "no" to our request,
		// then we should sendBluetoothConnectionFailedMessage
		// this requires additional state tracking and modification
		// of BTBroadcastReceiver
		startActivity(discoverableIntent);
		// if we're already discoverable, then try to obtain BT socket right
		// away
		// else, we will do that in the BTBroadcastReceiver

		if (mBluetoothAdapter.isEnabled()
				&& mBluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			Log.d(LOGTAG, "BT is already discoverable, starting thread");
			this.workerThread.start();
		}
	}

	/**
	 * Listens for connections from the Toy. Assumes BT discoverability has been
	 * enabled.
	 * 
	 * @return connected BluetoothSocket (can be null)
	 */
	private void getBluetoothSocket() {
		// TODO: following logic breaks if we re-use the same service instance
		// to handle reconnects
		if (this.obtainingSocket) {
			Log
					.e(LOGTAG,
							"called getBluetoothSocket twice. Bad! Refusing to do work.");
			return;
		}
		this.obtainingSocket = true;
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter
				.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
			Log.e(LOGTAG, "Device does not support bluetooth!");
		}
		Log.d(LOGTAG, "Cancelling discovery");
		mBluetoothAdapter.cancelDiscovery();
		BluetoothServerSocket btss = null;
		int max = 10;
		// loop so we can try several times to register a service record
		for (int ii = 0; ii < max;) {
			try {
				Log
						.d(LOGTAG,
								"Listening for connection (establishing BluetoothServerSocket)");
				btss = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(
						"Serial Port", MY_UUID);
				this.btSocket = btss.accept();
				Log.d(LOGTAG, "Got BluetoothSocket (apparently)");
				btss.close();
				// if we got here, nothing bad happened. bail out of loop
				break;
			} catch (IOException e) {
				if (this.stop) {
					Log.d(LOGTAG, "IOException && Thread stop requested. Bye.");
					return;
				} else {
					e.printStackTrace();
					// this.sendBluetoothFailureMessage();
					Log.e(LOGTAG, "Error while listening for connection", e);
					Log.e(LOGTAG, "Error on trial: " + ii);
					Log.e(LOGTAG, "Sleeping for 1s, trying again");
					// sleep 1s
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					ii++;
				}
			}
		}
		if (this.btSocket != null) {
			this.sendBluetoothSocketObtainedMessage();
		} else {
			Log.e(LOGTAG, "BTSocket is null for mysterious reasons");
			this.sendBluetoothConnectionFailedMessage();
		}

	}

	/**
	 * Stops all workers fired off by this Service.
	 * Does not stop the service itself. 
	 */
	public void stopThread() {
		this.stop = true;
		if (this.talker != null) {
			Log.i(LOGTAG, "Stopping talker thread");
			this.talker.stopThread();
		} else {
			Log.i(LOGTAG, "Talker thread is null, not stopping.");
		}
		if (this.btrRunnable != null) {
			Log.i(LOGTAG, "Stopping btreader thread");

			this.btrRunnable.stopThread();
		} else {
			Log.i(LOGTAG, "Reader thread is null, not stopping.");

		}
		if (this.btServerSocket != null) {
			try {
				this.btServerSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (this.currentProvider != null) {
			Log.i(LOGTAG, "Stopping currentProvider thread");

			this.currentProvider.stopThread();
		} else {
			Log.i(LOGTAG, "Reader thread is null, not stopping.");
		}
				
		// if all else fails
		try {
			if (this.btSocket != null) {
				this.btSocket.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	/**
	 * Sends broadcast signaling that the toy has successfully connected
	 */
	private void sendBluetoothSocketObtainedMessage() {
		Message successMsg = Message.obtain();
		successMsg.what = Main.MESSAGE_BT_CONNECTION_SUCCESS;
		this.messageHandlerUI.sendMessage(successMsg);
	}

	/**
	 * Sends message signaling that connection to the toy has been lost or that
	 * it could not be successfully established.
	 */
	private void sendBluetoothConnectionFailedMessage() {
		Message failMsg = Message.obtain();
		failMsg.what = Main.MESSAGE_BT_CONNECTION_FAILURE;
		this.messageHandlerUI.sendMessage(failMsg);
	}

	/**
	 * 
	 * Listens for changes in the state of the Bluetooth adapter.
	 * We're interested in whether the Bluetooth adapter is
	 * discoverable or not.
	 * If it becomes discoverable, we start listening for a connection.
	 * 
	 * Note that this listener is not active all the time.
	 * 
	 * @author Some Guy
	 *
	 */
	private class BTBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(LOGTAG, "BTBroadcastReceiver invoked");
			if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(intent
					.getAction())) {
				// read state from extra
				int scanMode = intent.getIntExtra(
						BluetoothAdapter.EXTRA_SCAN_MODE, -1);
				if (scanMode == -1) {
					Log
							.e(LOGTAG,
									"Got ACTION_STATE_CHANGED intent, but has no extra field!");
				} else if (scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
					Log.d(LOGTAG, "Device is discoverable and connectable");
					// make sure we don't call this twice, although this should
					// never happen
					if (!obtainingSocket) {
						workerThread.start();
					}
				} else if (scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE) {
					// if we're currently waiting for a socket, it will never happen now
					//if (obtainingSocket) {
					//	sendBluetoothConnectionFailedMessage();
					//}
					Log.d(LOGTAG, "Device is connectable, not discoverable");
				} else {
					Log.d(LOGTAG, "Unknown scanMode: " + scanMode);
				}
			}

		}
	}

	/**
	 * 
	 * Set message handler for state propagation back to UI.
	 * 
	 * This method must be called before anything can be done.
	 * 
	 * @param handler
	 *            Message handler
	 */
	public void setUIMessageHandler(Handler handler) {
		this.messageHandlerUI = handler;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(LOGTAG, "onDestroy called");
		if (this.receiver != null) {
			this.unregisterReceiver(this.receiver);
		}
	}

	/**
	 * 
	 * Binder implementation. Used by the UI to obtain handle on WorkerService
	 * object.
	 * 
	 * Taken from {@link http
	 * ://developer.android.com/reference/android/app/Service
	 * .html#LocalServiceSample}
	 * 
	 * @author SomeGuy
	 * 
	 */
	public class LocalBinder extends Binder {
		WorkerService getService() {
			return WorkerService.this;
		}
	}
	
	/**
	 * Handler used to propagate state from threads
	 * back to WorkerService
	 * 
	 * @author Some Guy
	 *
	 */
	private class ServiceHandler extends Handler {
		public void handleMessage(Message message) {
			switch (message.what) {
			case WorkerService.MESSAGE_SERVICE_CONNECTED:
				sendBluetoothSocketObtainedMessage();
				break;
			case WorkerService.MESSAGE_SERVICE_DISCONNECTED:
				sendBluetoothConnectionFailedMessage();
				break;
			case WorkerService.MESSAGE_SERVICE_IO_FAIL:
				sendBluetoothConnectionFailedMessage();
				break;
			}
			
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

}
