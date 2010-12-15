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

import java.util.List;

/**
 * 
 * This classes provides a crude implementation of an AT stack
 * as found on a mobile phone.
 * 
 * It can only handle the bare minimum of commands necessary
 * to get the device up and running.
 * 
 * @author Some Guy
 *
 */
public class AT {

	// RESPONSES
	private static final String REPLY_OK = "\r\nOK\r\n";
	private static final String REPLY_ERROR = "\r\nERROR\r\n";
	private static final String REPLY_MANUFACTURER = "\r\n\"Sony Ericsson\"\r\n\r\nOK\r\n";
	private static final String REPLY_MODEL = "\r\nUnknown\r\nOK\r\n";
	private static final String REPLY_MESSAGE_STORE = "+CPMS:0,10\r\nOK\r\n";
	private static final String REPLY_GET_MESSAGE = "+CMGR:";
	// QUERIES
	private static final String ECHO_OFF = "ATE0";
	private static final String GET_MANUFACTURER = "AT+CGMI";
	private static final String GET_MODEL = "AT+CGMM";
	private static final String SET_MESSAGE_STORAGE = "AT+CPMS";
	private static final String GET_MESSAGE = "AT+CMGR=";
	
	//TODO: if echo is on, we'd need to echo commands
	// but I do not want to track state here
	
	List<ToyMessage> list;
	/**
	 * Default Constructor
	 * 
	 * @param messageQueue Queue of incoming messages to be sent to the toy
	 */
	public AT(List<ToyMessage> messageQueue) {
		this.list = messageQueue;
	}
	
	// NOTE: Toy asks AT+CGMI=2,1
	// the second parameter means: send notification of incoming SMS to terminal equipment
	// using +CMTI
	
	/**
	 * Handles AT commands by returning the correct answer.
	 * 
	 * The answer can then be sent to the device.
	 * 
	 * @param command command to be handled
	 */
	public String handleCommand(String command) {
	
		if (command.equals(ECHO_OFF)) {
			return REPLY_OK;
		} else if (command.equals(GET_MANUFACTURER)) {
			return REPLY_MANUFACTURER;
		} else if (command.equals(GET_MODEL)) {
			return REPLY_MODEL;
			// TODO: need to cache the regexen
		} else if (command.startsWith(SET_MESSAGE_STORAGE)) {
			return REPLY_MESSAGE_STORE;
		} else if (command.startsWith(GET_MESSAGE)) {
			// HACK: extract index
			int stringIndex = command.indexOf("=");
			if (stringIndex == -1) {
				return REPLY_ERROR;
			}
			// TODO: handle numberformatexception
			String n =  command.substring(stringIndex + 1);
			int index = Integer.parseInt(n);
			ToyMessage msg = this.list.get(index);
			StringBuffer sb = new StringBuffer();
			sb.append(REPLY_GET_MESSAGE);
			// message has been read
			sb.append(1);
			sb.append(",");
			// no data for second field. this would usually contain the name from the phone book
			// for the message sender
			sb.append(",");
			// number of octets
			// TODO: add correct value!
			// TODO: this probably breaks for very special character > 8bit?
			//sb.append(msg.getPDUAsString().getBytes().length);
			sb.append("\r\n");
			// TODO: is this SMS-Submit or SMS-Deliver?
			sb.append(msg.getPDUAsString());
			sb.append("\r\n");
			sb.append(REPLY_OK);
			return sb.toString();			
		}
		
		// base case
		return REPLY_OK;
	}
	
}
