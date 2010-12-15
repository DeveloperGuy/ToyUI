package uk.toy.bt;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import uk.toy.WorkerService;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class BluetoothWriterThread extends Thread {
	
	private static final String LOGTAG = "OOBMessager";
	
	private BluetoothSocket btSocket;	
	private Handler handler;
	private Handler sHandler;
	private Looper looper;
	
	public void sendMessageToDevice(String stringMsg) {
		if (this.handler == null) {
			throw new IllegalStateException("Thread not fully initialized");
		}
		Message message = Message.obtain();
		message.obj = stringMsg;
		this.handler.sendMessage(message);
	}
	
	public BluetoothWriterThread(Handler serviceHandler, BluetoothSocket socket) {
		this.sHandler = serviceHandler;
		this.btSocket = socket;
	}
	
	@Override
	public void run() {
		System.err.println("Starting BTWriterThread");
		Looper.prepare();
		this.handler = new OOBHandler();
		this.looper = Looper.myLooper();
		Looper.loop();
		System.err.println("Starting BTWriterThread: we should never get here");
	}
	
	public void stopThread() {
		Log.d(LOGTAG, "Stopping BTWriterThread");
		this.looper.quit();
	}
	
	private class OOBHandler extends Handler {
		private final static String LOGTAG = "BluetoothWriterThread";
		@Override
		public void handleMessage(Message msg) {
			if (msg == null) {
				Log.d(LOGTAG, "handleMessage: msg is null. BUG");
			}
			String actualMsg = (String) msg.obj;
			if (actualMsg == null) {
				Log.d(LOGTAG, "handleMessage: actualMsg is null. BUG");
			}
			try {
				Log.d(LOGTAG, "sending " + actualMsg);
				btSocket.getOutputStream().write(actualMsg.getBytes("ASCII"));
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				Message ioFailMessage = sHandler.obtainMessage();
				ioFailMessage.what = WorkerService.MESSAGE_SERVICE_IO_FAIL;
				sHandler.sendMessage(ioFailMessage);
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
	}
	
	
	

}
