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

  private static final String TAG = "AirHockeyTag";
  private static final boolean DEBUG = true;

  // Amount of time to wait for the server response
  private static final int WAIT_FOR_RESPONSE = 10000;
  // Retry the join 5 times before failing
  private static final int NUM_RETRY = 5;

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
      try {
        mSocket = new DatagramSocket(port);
        mServerAddr = InetAddress.getByName(host);
      } catch (SocketException e) {
        // Could not connect
        Log.e(TAG, "Socket exception during login");
        return null;
      } catch (UnknownHostException e) {
        // Could not connect
        Log.e(TAG, "Could not resolve hostname: " + host);
        return null;
      }

      byte[] msg = ("J," + user).getBytes();
      DatagramPacket sendPacket = new DatagramPacket(msg, msg.length,
          mServerAddr, port);

      try {
        mSocket.send(sendPacket);
      } catch (IOException e) {
        // Could not connect
        Log.e(TAG, "Could not send join request to server.");
        return null;
      }

      DatagramPacket receivePacket;
      byte[] buf = new byte[64];

      int numAttempts = 0;
      while (true) {
        try {
          mSocket.setSoTimeout(WAIT_FOR_RESPONSE);
          receivePacket = new DatagramPacket(buf, buf.length);
          mSocket.receive(receivePacket);
          byte[] data = receivePacket.getData();
          int offset = receivePacket.getOffset();
          int length = receivePacket.getLength();
          return new String(data, offset, length);
        } catch (SocketException e) {
          // Could not connect
          Log.e(TAG, "Socket exception during login");
          return null;
        } catch (IOException e) {
          // Resend the join request
          try {
            mSocket.send(sendPacket);
          } catch (IOException ignore) {
            Log.e(TAG, "Could not send join request to server.");
          }
          if (++numAttempts > NUM_RETRY) {
            // Timed out waiting on the server
            Log.v(TAG, "Client timed out waiting for the server's response.");
            return null;
          }
          if (DEBUG) {
            Log.v(TAG, "Server failed to respond. Resending join request...");
          }
        }
      }
    } finally {
      if (mSocket != null) {
        mSocket.close();
        mSocket = null;
      }
    }
  }

  @Override
  protected void onPostExecute(String result) {
    if (mCallback != null) {
      if (DEBUG) {
        Log.v(TAG, "Received server response: " + result);
      }
      mCallback.onLoginComplete(result);
    }
  }

  public interface LoginCallback {
    void onLoginComplete(String result);
  }
}
