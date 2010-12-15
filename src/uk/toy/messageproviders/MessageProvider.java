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

import android.content.Context;
import android.content.Intent;

import uk.toy.ToyMessage;


/**
 * 
 * A MessageProvider is a source of messages which will be delivered to the phone.
 * 
 * A Message is a simple String. However, it must not be longer than 160 characters as
 * The Toy was developed for use with SMS messages.
 * 
 * Messages are broadcast using an intent with the {@link #ACTION_NEW_MESSAGE} action.
 * The {@link #EXTRA_NEW_MESSAGE} field will contain the {@link uk.toy.ToyMessage}
 * instance.
 * 
 *  To obtain new messages from a MessageProvider instance, simply register
 *  an {@link android.content.BroadCastReceiver} with the given intent. 
 * 
 * @author Some Guy
 *
 */
public abstract class MessageProvider implements Runnable {
	
	public static final String ACTION_NEW_MESSAGE = "TOY_NEW_MESSAGE";
	public static final String EXTRA_NEW_MESSAGE = "uk.toy.TOY_NEW_MESSAGE";
	protected Context ctx = null;
	
	public MessageProvider(Context context) {
		this.ctx = context;
	}
	
	/**
	 * Signals arrival of new messages
	 */
	public void signalNewMessage(ToyMessage msg) {
		
		Intent i = new Intent(ACTION_NEW_MESSAGE);
		i.putExtra(EXTRA_NEW_MESSAGE, msg);
		this.ctx.sendBroadcast(i);
	}
	
	/**
	 * Sub-classes must stop all work once this method is called.
	 */
	abstract public void stopThread();

}
