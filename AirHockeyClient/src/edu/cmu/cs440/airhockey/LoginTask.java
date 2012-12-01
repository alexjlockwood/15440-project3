package edu.cmu.cs440.airhockey;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import android.os.AsyncTask;
import android.util.Log;

public class LoginTask extends AsyncTask<String, Void, String> {

  private static final String TAG = "15440_LoginTask";
  private static final boolean DEBUG = true;

  // Amount of time to wait for the server response
  private int WAIT_FOR_RESPONSE = 3000;
  // Retry the join 3 times before failing
  private static final int NUM_RETRY = 3;

  private LoginCallback mCallback;
  private DatagramSocket mSocket;
  private InetAddress mServerAddr;

  public LoginTask(LoginCallback callback) {
    mCallback = callback;
  }

  @Override
  protected String doInBackground(String... args) {
    String user = args[0];
    String host = args[1];
    int port = Integer.parseInt(args[2]);

    if (DEBUG) {
      Log.v(TAG, "Attempting to join! User: " + user + ", Host: " + host
          + ", Port: " + port);
    }

    try {
      // Sleep for a little bit just so it looks like we are doing
      // some work. :)
      Thread.sleep(500);
    } catch (InterruptedException e) {
    }

    try {
      mSocket = new DatagramSocket(port);
      mServerAddr = InetAddress.getByName(host);
    } catch (SocketException e) {
      Log.e(TAG, "Socket exception during login");
      close();
      return null;
    } catch (UnknownHostException e) {
      Log.e(TAG, "Could not resolve hostname: " + host);
      close();
      return null;
    }

    byte[] msg = ("J," + user).getBytes();
    DatagramPacket sendPacket = new DatagramPacket(msg, msg.length,
        mServerAddr, port);

    try {
      mSocket.send(sendPacket);
    } catch (IOException e) {
      Log.e(TAG, "Could not send join request to server.");
      close();
      return null;
    }

    DatagramPacket receivePacket;
    byte[] buf = new byte[64];

    int numAttempts = 0;
    while (numAttempts++ < NUM_RETRY) {

      try {
        mSocket.setSoTimeout(WAIT_FOR_RESPONSE);
      } catch (SocketException e) {
        Log.e(TAG, "Socket exception during login");
        break;
      }

      try {
        receivePacket = new DatagramPacket(buf, buf.length);

        if (isCancelled())
          break;

        mSocket.receive(receivePacket);

        if (isCancelled())
          break;

        close();
        byte[] data = receivePacket.getData();
        int offset = receivePacket.getOffset();
        int length = receivePacket.getLength();
        return new String(data, offset, length);

      } catch (IOException e) {
        if (isCancelled())
          break;

        try {
          // Resend the join request
          if (DEBUG)
            Log.v(TAG, "Resending join request...");
          mSocket.send(sendPacket);
        } catch (IOException ignore) {
          Log.e(TAG, "Could not send join request to server.");
          break;
        }
      }

      // Increase the time to wait by two seconds
      WAIT_FOR_RESPONSE += 3000;
    }

    close();
    return null;
  }

  private void close() {
    if (mSocket != null) {
      mSocket.close();
      mSocket = null;
    }
  }

  @Override
  protected void onPostExecute(String result) {
    if (mCallback != null) {
      if (DEBUG) {
        if (result != null)
          Log.v(TAG, "Received server response: " + result);
      }
      mCallback.onLoginComplete(result);
    }
  }

  @Override
  protected void onCancelled() {
    if (DEBUG) {
      Log.v(TAG, "The LoginTask was cancelled!");
    }
    if (mCallback != null) {
      mCallback.onLoginComplete(null);
    }
  }

  public interface LoginCallback {
    void onLoginComplete(String result);
  }
}
