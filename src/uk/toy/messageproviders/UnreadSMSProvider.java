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

import uk.toy.ToyMessage;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

/**
 * 
 * Listens for incoming new short messages.
 * 
 * @author Some Guy
 *
 */
public class UnreadSMSProvider extends MessageProvider {
	
	/**
	 * Intent for which we're listening.
	 */
	private final static String ACTION = "android.provider.Telephony.SMS_RECEIVED";
	private final static String LOGTAG = "UnreadSMSProvider";
	
	
	private BroadcastReceiver receiver;
	
	/**
	 * Constructor for UnreadSMSProvider class.
	 * 
	 * @param ctx Application context
	 */
	public UnreadSMSProvider(Context ctx) {
		super(ctx);
	}
	
	@Override
	public void run() {
		Log.d(LOGTAG, "UnreadSMSProvider started");
		receiver = new SMSBroadcastReceiver();
		// register BroadcastReceiver
		IntentFilter filter = new IntentFilter(ACTION);
		ctx.registerReceiver(receiver, filter);
	}
	
	private class SMSBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context arg0, Intent arg1) {
			if (! arg1.getAction().equals(ACTION)) {
				return;
			}
			Bundle bundle = arg1.getExtras();
			if (bundle == null) {
				return;
			}
			Object[] pdusObj = (Object[]) bundle.get("pdus");

			for (int ii = 0; ii < pdusObj.length; ii++) {
				byte[] curPDU = (byte[]) pdusObj[ii];
				SmsMessage sms = SmsMessage.createFromPdu(curPDU);
				ToyMessage curMSG = new ToyMessage(sms.getMessageBody());
				signalNewMessage(curMSG);
				}
			}
	}

	@Override
	public void stopThread() {
		if (this.receiver != null) {
			this.ctx.unregisterReceiver(receiver);
		} else {
			Log.d(LOGTAG, "Not unregistering receiver as receiver == null");
		}
		
		
	}

}
