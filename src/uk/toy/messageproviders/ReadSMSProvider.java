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
package uk.toy.messageproviders;

import java.util.HashSet;
import uk.toy.ToyMessage;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * 
 * This classes provides read SMS messages from the Inbox.
 * 
 * This is a bit of a hack as the API we are using is not exactly public.
 * As the Android OS changes, this might stop working.
 * 
 * For proprietary or third-party SMS applications, this might not work at all
 * if they user a different data scheme or URI for message storage.
 * 
 * See {@link UnreadSMSProvider} for a class that uses public APIs.
 * 
 * @author Some Guy
 *
 */
public class ReadSMSProvider extends MessageProvider {

	private final static String CONTENT_OBSERVER_URI_STRING = "content://mms-sms/";
	private final static Uri CONTENT_OBSERVER_URI = Uri
			.parse(CONTENT_OBSERVER_URI_STRING);
	private final static String CONTENT_URI_STRING = "content://sms/";
	private final static Uri CONTENT_URI = Uri.parse(CONTENT_URI_STRING);

	private Looper looper;
	private ContentObserver obs;

	private final static String LOGTAG = "ReadSMSProvider";
	
	public ReadSMSProvider(Context context) {
		super(context);
	}

	@Override
	public void run() {
		Looper.prepare();
		this.looper = Looper.myLooper();
		this.registerObserver();
		Looper.loop();
	}

	private void registerObserver() {
		// TODO: I am not entirely sure why I need to pass in a Handler instance
		// According to Javadoc, just calling the Handler constructor will
		// associate
		// the Handler with the MessageQueue of the current thread.
		// I assume that's what we want.
		ContentResolver cr = this.ctx.getContentResolver();
		obs = new ReadSMSObserver(new Handler(), cr);
		cr.registerContentObserver(CONTENT_OBSERVER_URI, false, obs);
	}

	private class ReadSMSObserver extends ContentObserver {
		private int maxID = -1;
		private ContentResolver cr = null;
		// we need to be smart when choosing which messages we want to consider
		// strategy is as follows:
		// do not look at messages which were received before the app/service
		// was started
		// this is done by looking at maxIDs
		// we have no great way to see which messages, be it read or unread, we
		// have already seen
		// so let's save read messages which we have enqueued in the seenIDs
		// hashset
		// so we can easily (and cheaply) check whether we may enqueue a message
		// Once we're up and running, we need to consider all messages between
		// maxID and the newest messages as they might get read at different
		// times.
		// to sum this up:
		// onChange will only enqueue messages
		// * if their ID is over maxID
		// AND
		// * if their ID is not in seenIDs
		private HashSet<Integer> seenIDs = new HashSet<Integer>();
		String LOGTAG = "ReadSMSObserver";

		public ReadSMSObserver(Handler handler, ContentResolver contentResolver) {
			super(handler);
			this.cr = contentResolver;
			this.maxID = this.getCurrentMaxID();
			Log.d(LOGTAG, "Initial maxID: " + maxID);
		}

		@Override
		public void onChange(boolean selfChange) {
			Log.d(LOGTAG, "Got change!");
			// load new read sms into queue
			this.signalNewMessages();
		}

		private void signalNewMessages() {
			String[] projection = new String[] { "_id", "read", "body" };
			Cursor res = cr.query(CONTENT_URI, projection, null, null, null);
			// FIXME: needs to signal to consumer if breakage occurs
			int columnOfID = res.getColumnIndexOrThrow("_id");
			int columnOfRead = res.getColumnIndexOrThrow("read");
			int columnOfBody = res.getColumnIndexOrThrow("body");

			while (res.moveToNext()) {
				// TODO: use where clause to filter out stuff <= maxID
				int id = res.getInt(columnOfID);
				if (id <= this.maxID) {
					Log.d(LOGTAG, "Skipping message as id <= maxID");
					continue;
				}
				if (this.seenIDs.contains(id)) {
					continue;
				}
				int read = res.getInt(columnOfRead);
				String body = res.getString(columnOfBody);
				if (read == 1) {
					Log.d(LOGTAG, "Got new read message!");
					Log.d(LOGTAG, "Message is " + body);
					ToyMessage curMsg = new ToyMessage(body);
					signalNewMessage(curMsg);
					this.seenIDs.add(id);
				}
			}
		}

		/**
		 * 
		 * Finds out highest message index. We need to do this once when we're
		 * starting up.
		 * 
		 * 
		 * 
		 * @return highest ID (ID of last message)
		 */
		private int getCurrentMaxID() {
			String[] projection = new String[] { "_id" };
			Cursor res = cr.query(CONTENT_URI, projection, null, null,
					"_id DESC");
			if (res.moveToFirst()) {
				int index = res.getColumnIndexOrThrow("_id");
				return res.getInt(index);
			} else {
				return -1;
			}
		}
	}

	@Override
	public void stopThread() {
		if (this.looper != null) {
			this.looper.quit();
		} else {
			Log.d(LOGTAG, "Not quitting looper as looper == null");
		}
		if (this.obs != null) {
			this.ctx.getContentResolver().unregisterContentObserver(obs);
		} else {
			Log.d(LOGTAG, "Not unregistering content observer as obs == null");
		}
	}

}
