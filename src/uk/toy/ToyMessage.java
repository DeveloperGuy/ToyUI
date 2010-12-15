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

import java.io.Serializable;

import org.marre.sms.SmsAddress;
import org.marre.sms.SmsException;
import org.marre.sms.SmsPduUtil;
import org.marre.util.StringUtil;
import android.util.Log;

/**
 * 
 * Storage class for messages to be sent to the toy.
 * 
 * The toy receives messages in PDU format, so this class
 * provides a converter from String to PDU.
 * 
 * @author Some Guy
 *
 */
public class ToyMessage implements Serializable {


	private static final long serialVersionUID = -642073213597342720L;

	// we need a phone number internally for PDU encoding purposes
	private static final String phoneNumber = "01234567889";
	// TODO: remove
	private static SmsAddress sender;
	
	private static final String LOGTAG = "ToyMessage";

	private String msg;

	/**
	 * 
	 * Default constructor.
	 * 
	 * @param message Message to be encapsulated.
	 */
	public ToyMessage(String message) {
		this.msg = message;
		try {
			sender = new SmsAddress(phoneNumber);
		} catch (SmsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Log.e(LOGTAG, "Error while instantiating SmsAddress", e);
		}
	}

	/**
	 * Gets septets from string message
	 * 
	 * @return
	 */
	private byte[] getPDU() {

		return SmsPduUtil.getSeptets(msg);
	}
	
	/**
	 * 
	 * Returns encapsulated message properly formatted for consumption
	 * by the toy.
	 * 
	 * Implementation note: we simply encode a fixed phone number
	 * into the String as they toy does not care about these details 
	 * 
	 * @return Septets encoded as String to be sent to the toy
	 */
	public String getPDUAsString() {

		byte[] septets = this.getPDU();
		StringBuffer res = new StringBuffer();
		
		// all stolen from http://www.dreamfabric.com/sms/deliver_fo.html
		// length of SMSC
		res.append("00");
		// first octet of SMS-deliver message
		res.append("04");
		// address length
		res.append("0B");
		// type-of-address of the sender number
		res.append("C8");
		// sender number
		res.append("7238880999F1");
		// TP-ID
		res.append("00");
		// TP-DCS (data coding scheme)
		res.append("00");
		// TP-SCTS (time stamp)
		res.append("99309251619580");
		// number of septets. Note: pay attention to encoding specified by TP-DCS
		String numOfSeptets =Integer.toHexString((int) (septets.length * (8.0/7.0)));
		if (numOfSeptets.length() < 2){
			res.append("0");
		}
		res.append(numOfSeptets);
		
		String tpUD = StringUtil.bytesToHexString(septets);
		res.append(tpUD);
		
		return res.toString();		
	}
}
