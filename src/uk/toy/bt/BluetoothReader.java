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
package uk.toy.bt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import uk.toy.AT;
import uk.toy.ToyMessage;
import uk.toy.WorkerService;
import uk.toy.messageproviders.MessageProvider;

import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * 
 * Handles actual communication with the toy.
 * This class only listens to what the toy has to say. Any
 * answers go through an instance of {@link BluetoothWriterThread}.
 * 
 * @author Some Guy
 * 
 */
public class BluetoothReader implements Runnable {

	private List<ToyMessage> list = new ArrayList<ToyMessage>();

	private final static String LOGTAG = "BluetoothTalker";
	private BluetoothSocket socket;
	private BluetoothWriterThread writer;
	private Context ctx;
	private BroadcastReceiver rec;
	private AT at;
	private Handler sHandler;
	private boolean stop = false;

	public BluetoothReader(BluetoothSocket btSocket, BluetoothWriterThread btTalker, Context context,
			Handler serviceHandler) {
		this.socket = btSocket;
		this.writer = btTalker;
		this.ctx = context;
		this.at = new AT(this.list);
		this.sHandler = serviceHandler;
	}

	private void indicateNewMessage() {
		String toToy = "\r\n+CMTI:\"SM\"," + (list.size() - 1) + "\r\n";
		Log.d(LOGTAG, "Indicating new message");
		Log.d(LOGTAG, "to toy:" + toToy);
		this.writer.sendMessageToDevice(toToy);

	}

	@Override
	public void run() {
		// register broadcast listener for new messages
		Log.d(LOGTAG, "Registering Broadcast Receiver for new messages");
		this.rec = new NewMessageReceiver();
		IntentFilter filter = new IntentFilter(
				MessageProvider.ACTION_NEW_MESSAGE);
		this.ctx.registerReceiver(this.rec, filter);

		while (true) {
			try {
				// OutputStreamWriter outbw = new OutputStreamWriter(out);
				// BufferedWriter outbw = new BufferedWriter(outw);

				InputStream in = this.socket.getInputStream();
				InputStreamReader inr = new InputStreamReader(in);
				BufferedReader bf = new BufferedReader(inr);
				if (in == null) {
					Log.d(LOGTAG, "InputStream is null!");
					this.indicateIOFailureToService();
				}
				StringBuffer inputBuffer = new StringBuffer();
				while (true) {
					int cur = in.read();
					if (cur == -1) {
						Log
								.d(LOGTAG,
										"Got -1 from InputStream. Lost connection to Toy?");
						throw new NullPointerException();
					}
					// carriage return
					else if (cur == 13) {
						if (in.markSupported()) {
							Log.d(LOGTAG, "Inputstream supports mark!");
							in.mark(1);
							if (bf.ready()) {
								int afterCR = in.read();
								// is linefeed?
								if (afterCR == 10) {
									break;
								} else {
									// for some reason, we're getting other data
									// after a linefeed
									Log
											.e(LOGTAG,
													"Got character other than <LF> after >CR>!");
									in.reset();
									continue;
								}
							}
						}
						break;
					}
					// line feed
					else if (cur == 10) {
						break;
					} else {
						inputBuffer.appendCodePoint(cur);
						continue;
					}
				}

				String currentLine = inputBuffer.toString();
				Log.d(LOGTAG, "From Device:" + currentLine);
				String response = at.handleCommand(currentLine);
				Log.d(LOGTAG, "To Device: " + response);
				this.writer.sendMessageToDevice(response);
			} catch (IOException e) {
				// calling stopThread triggers an IOException here
				if (!this.stop) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					this.indicateIOFailureToService();
					Log.d(LOGTAG, "IOException: all aboard the failboat!", e);
				} else {
					// TODO: call successcallback?
					Log
							.d(LOGTAG,
									"Caught controlled IOException. stop == true. Ending.");
				}
				// it's over.
				return;

			}
		}
	}

	/**
	 * Stops thread. Stops listening for messages and ceases communication with
	 * Toy.
	 * 
	 * Unregisters BroadcastReceiver. Closes BluetoothSocket which will likely
	 * trigger an IOException. Sets stop flag.
	 * 
	 */
	public void stopThread() {
		this.stop = true;
		this.ctx.unregisterReceiver(this.rec);
		try {
			this.socket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void indicateIOFailureToService() {
		Message ioFailMsg = sHandler.obtainMessage();
		ioFailMsg.what = WorkerService.MESSAGE_SERVICE_IO_FAIL;
		this.sHandler.sendMessage(ioFailMsg);
	}
	
	/**
	 * Listens for new SMS broadcasts, usually coming from instances of
	 * MessageProvider.
	 * 
	 * @author SomeGuy
	 * 
	 */
	private class NewMessageReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO Auto-generated method stub

			if (intent.getAction().equals(MessageProvider.ACTION_NEW_MESSAGE)) {
				// TODO: add constant for extra key
				list
						.add((ToyMessage) intent
								.getSerializableExtra(MessageProvider.EXTRA_NEW_MESSAGE));
				indicateNewMessage();
			}
		}
	}

}
