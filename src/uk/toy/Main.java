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


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

/**
 * 
 * Main entry point into the application. This class
 * mostly holds the UI logic.
 * 
 * @author Some Guy
 *
 */
public class Main extends Activity {

	private static final String LOGTAG = "ToyUIMain";

	// Dialog identifiers
	private static final int DIALOG_SETTINGS_ID = 0;
	private static final int DIALOG_BTCONNECT_PROGRESS_ID = 1;
	private static final int DIALOG_INVALID_BT_NAME = 2;
	private static final int DIALOG_ABOUT = 3;
	public static final String PREF_MESSAGEPROVIDER_KEY = "MessageProvider";
	// used to retrieved SharedPreferences object
	private SharedPreferences pref;

	private Intent workerServiceIntent;

	private BluetoothAdapter btAdapter = null;

	public static final int MESSAGE_BT_CONNECTION_SUCCESS = 0;
	public static final int MESSAGE_BT_CONNECTION_FAILURE = 1;

	// Message Providers
	// also serves as index for the settings dialog list.
	// if you want to re-order the items, you will need to change the constants.
	// how many message providers we have
	private static final int SIZE_MESSAGEPROVIDER_DIALOG = 2;
	public static final int MESSAGEPROVIDER_READ_SMS = 0;
	public static final int MESSAGEPROVIDER_UNREAD_SMS = 1;

	// used for startActivityForResult
	public static final int REQUEST_BLUETOOTH_SETTINGS = 0;
	public static final int REQUEST_BLUETOOTH_ENABLE = 1;

	// this is fscked up
	private Dialog currentDialog = null; // can be null, set from

	/**
	 * WorkerService handle. Use this to interact with the worker service.
	 */
	private WorkerService boundService = null;

	/*
	 * Used to populate boundService field.
	 */
	private ServiceConnection workerServiceConnection = new WorkerServiceConnection();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		this.workerServiceIntent = new Intent();
		ComponentName cn = new ComponentName("uk.toy", "uk.toy.WorkerService");
		workerServiceIntent.setComponent(cn);
		pref = getPreferences(MODE_PRIVATE);
		// hook up buttons
		this.hookUpButtons();
		btAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	/**
	 * 
	 * Validates Bluetooth name and starts the WorkerService.
	 * 
	 * If the Bluetooth name is deemed invalid, the user is prompted to set a
	 * valid name. Once a valid name has been set, the WorkerService is started.
	 * Otherwise, it is started immediately. Bluetooth must be turned on before
	 * calling this method.
	 */
	private void launchService() {
		if (!this.btAdapter.isEnabled()) {
			throw new IllegalStateException(
					"Bluetooth Adapter must be enabled before calling launchService");
		}
		if (Main.validateBTName(this.getBTName())) {
			this.startWorkerService();
		} else {
			// startWorkerService() is called from resultHandler;
			// TODO: indicate errors to user
			showDialog(Main.DIALOG_INVALID_BT_NAME);
		}
	}

	/**
	 * Starts WorkerService which handles communication with the Toy.
	 * 
	 * Unlike launchService, this does not sanity-check the environment.
	 * 
	 * Sets up message provider and passes it to a new WorkerService instance.
	 */
	private void startWorkerService() {

		this.bindService(this.workerServiceIntent, workerServiceConnection,
				Context.BIND_AUTO_CREATE);
		// service is actually started in WorkerServiceConnection
		// callback so we have a chance to call setHandler.
		// dismissed by Message sent by WorkerService
		this.showDialog(DIALOG_BTCONNECT_PROGRESS_ID);
	}

	/**
	 * Stops WorkerService.
	 */
	private void stopWorkerService() {
		// TODO: check return value
		if (this.boundService != null) {
			this.boundService.stopThread();
			this.unbindService(workerServiceConnection);
		}
		this.boundService = null;
		this.stopService(workerServiceIntent);
	}



	
	/**
	 * Turns on the Bluetooth radio. This is needed so we can read the Bluetooth
	 * name successfully.
	 * 
	 */
	private void enableBluetooth() {

		if (! this.btAdapter.isEnabled()) {
		Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		Log.i(LOGTAG, "Enabling Bluetooth (launching intent");
		this
				.startActivityForResult(enableIntent,
						Main.REQUEST_BLUETOOTH_ENABLE);

		} else {
			// BT is already enabled, so let's fire off the service
			this.launchService();
		}
	}

	/**
	 * 
	 * Saves the chosen message provider to SharedPreferences.
	 * 
	 * @param provider
	 *            needs to be a MESSAGEPROVIDER constant
	 */
	private void saveMessageProvider(int provider) {
		SharedPreferences.Editor editor = this.pref.edit();
		editor.putInt(Main.PREF_MESSAGEPROVIDER_KEY, provider);
		editor.commit();
	}

	/**
	 * Gets message provider from SharedPreferences.
	 * 
	 * Returns MESSAGEPROVIDER_READ_SMS on first run as this is default legacy
	 * behavior.
	 * 
	 * @return saved message provider or default
	 * 
	 */
	private int getMessageProviderID() {
		return this.pref.getInt(PREF_MESSAGEPROVIDER_KEY,
				MESSAGEPROVIDER_READ_SMS);
	}

	/**
	 * Connect actions to UI buttons.
	 */
	private void hookUpButtons() {
		Button settings = (Button) findViewById(R.id.settings);
		View.OnClickListener settingsListener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showDialog(DIALOG_SETTINGS_ID);
			}
		};
		settings.setOnClickListener(settingsListener);

		Button start = (Button) findViewById(R.id.start);
		View.OnClickListener startListener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				enableBluetooth();
			}
		};
		start.setOnClickListener(startListener);

		Button exit = (Button) findViewById(R.id.exit);
		View.OnClickListener exitListener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// TODO: handle these in default activity lifecycle callbacks
				finish();
			}
		};
		exit.setOnClickListener(exitListener);
	}

	private void switchUIToConnectedMode() {
		if (this.currentDialog != null) {
			this.currentDialog.dismiss();
			this.currentDialog = null;
		}
		Button start = (Button) findViewById(R.id.start);
		start.setEnabled(false);
		start.setVisibility(android.widget.Button.INVISIBLE);
		Button settings = (Button) findViewById(R.id.settings);
		settings.setEnabled(false);
		settings.setVisibility(android.widget.Button.INVISIBLE);
	}

	private void switchUIToDisconnectedMode() {
		Button start = (Button) findViewById(R.id.start);
		start.setEnabled(true);
		start.setVisibility(android.widget.Button.VISIBLE);
		Button settings = (Button) findViewById(R.id.settings);
		settings.setEnabled(true);
		settings.setVisibility(android.widget.Button.VISIBLE);
	}

	private void showBTSettings() {
		Intent intent = new Intent();
		intent.setAction(Settings.ACTION_BLUETOOTH_SETTINGS);
		this.startActivityForResult(intent, REQUEST_BLUETOOTH_SETTINGS);
	}

	private String getBTName() {
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter
				.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
			Log.e(LOGTAG, "Device does not support bluetooth!");
		}
		return mBluetoothAdapter.getName();
	}

	private boolean isBTEnabled() {
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter
				.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
			Log.e(LOGTAG, "Device does not support bluetooth!");
		}
		return mBluetoothAdapter.isEnabled();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (REQUEST_BLUETOOTH_SETTINGS == requestCode) {
			Log.d(LOGTAG, "For REQUEST_BLUETOOTH_SETTINGS: all went well");
			String btName = this.getBTName();
			Log.d(LOGTAG, "New BT device name is: " + btName);
			if (!Main.validateBTName(btName)) {
				showDialog(Main.DIALOG_INVALID_BT_NAME);
			} else {
				this.startWorkerService();
			}
		} else if (REQUEST_BLUETOOTH_ENABLE == requestCode) {
			Log.d(LOGTAG, "For REQUEST_BLUETOOTH_ENABLE: got callback");
			if (resultCode == Main.RESULT_OK) {
				// TODO: is bluetooth enabled?
				Log.d(LOGTAG, "User enabled Bluetooth");
				// TODO: this might break on the 845 due to raciness
				// however, since we use two steps to enable BT and
				// to enable discoverability, this might actually make
				// things work
				if (this.isBTEnabled()) {
					Log.d(LOGTAG, "Bluetooth is enabled");
					this.launchService();
				} else {
					Log
							.d(
									LOGTAG,
									"Blutooth is still disabled. We need to listen for STATE_CHANGE broadcasts then");
					this.switchUIToDisconnectedMode();
				}
			}
			else {
				// user does not want to enable BT.
				//this.disconnect();
				this.switchUIToDisconnectedMode();
			}
		}

	}

	/**
	 * Disconnects the phone from the Toy.
	 */
	private void disconnect() {
		this.stopWorkerService();
		this.switchUIToDisconnectedMode();
	}

	// DIALOG CREATION

	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		switch (id) {
		case DIALOG_SETTINGS_ID:
			dialog = this.createSettingsDialog();
			break;
		case DIALOG_BTCONNECT_PROGRESS_ID:
			// TODO: implement listener for bt-not-discoverable
			dialog = this.createBTConnectProgressDialog();
			this.currentDialog = dialog;
			break;
		case DIALOG_INVALID_BT_NAME:
			dialog = this.createBTInvalidNameDialog();
			break;
		case DIALOG_ABOUT:
			dialog = this.createAboutDialog();
			break;
		default:
			Log.e(LOGTAG, "Unknown Dialog ID requested: " + id);
			dialog = null;
		}
		return dialog;
	}
	
	/**
	 * Creates settings dialog. Does not show dialog.
	 * 
	 * @return settings dialog
	 */
	private Dialog createSettingsDialog() {
		// build list content
		String[] list = new String[SIZE_MESSAGEPROVIDER_DIALOG];
		list[MESSAGEPROVIDER_UNREAD_SMS] = this.getString(R.string.unreadSMS);
		list[MESSAGEPROVIDER_READ_SMS] = this.getString(R.string.readSMS);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setCancelable(false);
		// saves new MessageProvider and closes dialog
		DialogInterface.OnClickListener listener = (new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				// set preference
				Log.i("Dialog", "Got item " + item);
				saveMessageProvider(item);
				dialog.dismiss();
			}
		});
		int currentProvider = this.getMessageProviderID();
		builder.setSingleChoiceItems(list, currentProvider, listener);
		AlertDialog settings = builder.create();
		return settings;
	}

	/**
	 * Creates connection progress dialog. Does not show dialog.
	 * 
	 * This dialog is to be shown while establishing a connection with the toy.
	 * 
	 * The dialog has a 'cancel' button which will - dismiss the dialog - stop
	 * the connection attempt (that is, stop the service) -
	 * 
	 * @return a ProgressDialog instance
	 */
	private Dialog createBTConnectProgressDialog() {
		ProgressDialog.Builder builder = new ProgressDialog.Builder(this);
		builder.setCancelable(false);
		builder.setMessage("Push play button now");
		OnClickListener l = new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// TODO Auto-generated method stub
				disconnect();
				dialog.dismiss();
			}
		};
		builder.setNegativeButton("Cancel", l);
		return builder.create();
	}

	/**
	 * Creates 'invalid bluetooth name' dialog. Does not show dialog.
	 * 
	 * This dialog is to be shown if the Bluetooth name is invalid. Once 'OK'
	 * has been clicked, the Bluetooth Settings dialog is invoked so the user
	 * can correct the wrong name.
	 * 
	 * @return a Dialog instance
	 */
	private Dialog createBTInvalidNameDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Invalid Bluetooth name");
		builder
				.setMessage("Bluetooth Name is invalid. Please set it to 'Thetoy'");
		OnClickListener l = new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// TODO: this is an endless loop. Needs 'abort' option.
				showBTSettings();
				dialog.dismiss();
			}
		};
		builder.setPositiveButton("OK", l);
		return builder.create();
	}
	private Dialog createAboutDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setCancelable(true);
		// saves new MessageProvider and closes dialog
		DialogInterface.OnClickListener listener = (new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				dialog.dismiss();
			}
		});
		builder.setMessage(R.string.licenseText);

		builder.setPositiveButton("OK", listener);
		AlertDialog about = builder.create();
		return about;
	}

	/**
	 * Verifies whether a BT name will allow the toy to connect.
	 * 
	 * @param name
	 *            Name to be validated
	 * @return Whether name is valid or not
	 */
	private static boolean validateBTName(String name) {
		if (name == null) {
			return false;
		}
		if (name.equals("Thetoy")) {
			return true;
		}
		return false;
	}

	/**
	 * 
	 * Handler for messages coming from WorkerService.
	 * 
	 * Two messages are handled here: - MESSAGE_BT_CONNECTION_SUCCESS -> UI is
	 * updated to reflect established connection - MESSAGE_BT_CONNECTION_FAILURE
	 * -> UI is updated to reflect that connection could not be established or
	 * failed unexpectedly.
	 * 
	 * @author Someguy
	 * 
	 */
	private class ServiceHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case Main.MESSAGE_BT_CONNECTION_SUCCESS:
				switchUIToConnectedMode();
				break;
			case Main.MESSAGE_BT_CONNECTION_FAILURE:
				switchUIToDisconnectedMode();
				break;
			}
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		this.stopWorkerService();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.main, menu);
	    return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	    case R.id.about:
	        this.showDialog(DIALOG_ABOUT);
	        return true;
	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}

	/**
	 * Used to bind to the WorkerService.
	 */
	private class WorkerServiceConnection implements ServiceConnection {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service.
			Log.d(LOGTAG, "WorkerServiceConnection::onServiceConnected called");
			boundService = ((WorkerService.LocalBinder) service).getService();
			Handler handler = new ServiceHandler();
			boundService.setUIMessageHandler(handler);
			// TODO: can we set this multiple times w/o breaking?
			workerServiceIntent.putExtra(PREF_MESSAGEPROVIDER_KEY,
					getMessageProviderID());
			startService(workerServiceIntent);

		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			Log.d(LOGTAG,
					"WorkerServiceConnection::onServiceDisconnected called");
			switchUIToDisconnectedMode();
			boundService = null;
		}
	};

}