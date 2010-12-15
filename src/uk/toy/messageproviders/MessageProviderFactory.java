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

import uk.toy.Main;
import android.content.Context;
import android.util.Log;

/**
 * 
 * Factory for MessageProvider objects.
 * 
 * @autor Some Guy
 */
public class MessageProviderFactory {

	private static final String LOGTAG = "ToyUIMessageProviderFactory";
	
	/**
	 * Returns MessageProvider for requested type.
	 * 
	 * See MESSAGEPROVIDER constants in Main class.
	 * 
	 * @param type
	 *            MESSAGEPROVIDER constant
	 * @return MessageProvider object (or null)
	 */
	public static MessageProvider getMessageProvider(int type, Context ctx) {
		MessageProvider mp = null;
		switch (type) {
		case Main.MESSAGEPROVIDER_READ_SMS:
			mp = new ReadSMSProvider(ctx);
			break;
		case Main.MESSAGEPROVIDER_UNREAD_SMS:
			mp = new UnreadSMSProvider(ctx);
			break;
		default:
			Log.e(LOGTAG, "Unknown MessageProvider ID requested: " + type);
			}
		return mp;
	}
}
