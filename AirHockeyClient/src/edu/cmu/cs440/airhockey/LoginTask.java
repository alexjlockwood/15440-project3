package edu.cmu.cs440.airhockey;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Locale;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

public class LoginTask extends AsyncTask<String, Void, String> {

  private static final String TAG = "15440_LoginTask";
  private static final boolean DEBUG = true;

  private Context mCtx;
  private LoginCallback mCallback;
  private Socket mSocket;
  private PrintWriter out;
  private BufferedReader in;
  private InetAddress mServerAddr;
  private ProgressDialog mProgress;
  private String mUser;

  public LoginTask(Context ctx, LoginCallback callback, String user) {
    mCtx = ctx;
    mCallback = callback;
    mUser = user;
  }

  @Override
  protected void onPreExecute() {
    mProgress = ProgressDialog.show(mCtx, "", "Connecting...", true);
    mProgress.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        LoginTask.this.cancel(true);
        mProgress.dismiss();
      }
    });
    mProgress.setCanceledOnTouchOutside(false);
    mProgress.setCancelable(true);
  }

  @Override
  protected String doInBackground(String... args) {
    String user = args[0];
    String host = args[1];
    int port = Integer.parseInt(args[2]);

    try {
      // Sleep for a little bit just so it looks like we are doing
      // some work. :)
      Thread.sleep(500);
    } catch (InterruptedException e) {
    }

    if (isCancelled()) {
      return null;
    }

    try {
      mServerAddr = InetAddress.getByName(host);
      mSocket = new Socket(mServerAddr, port);
      out = new PrintWriter(mSocket.getOutputStream(), true);
      in = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
    } catch (SocketException e) {
      Log.e(TAG, "SocketException during login");
      close();
      return null;
    } catch (UnknownHostException e) {
      Log.e(TAG, "Could not resolve hostname: " + host);
      close();
      return null;
    } catch (IOException e) {
      Log.e(TAG, "IOException during login");
      close();
      return null;
    }

    if (DEBUG) {
      Log.v(TAG, String.format(Locale.US,
          "Attempting to join! User: %s, Host: %s, Port: %d", user, host, port));
    }

    byte[] joinMsg = String.format(Locale.US, "J,%s", user).getBytes();
    out.println(joinMsg);

    if (out.checkError()) {
      Log.e(TAG, "Client failed to write message because PrintWriter threw an"
          + "IOException!");
      close();
      return null;
    }

    if (DEBUG) {
      Log.v(TAG, "Waiting for server's 'join response'.");
    }

    if (isCancelled()) {
      close();
      return null;
    }

    // Blocks until the server responds or the client times out waiting
    // for the server to respond.
    String result = null;
    try {
      result = in.readLine();
    } catch (IOException e) {
      Log.e(TAG, "IOException while waiting for server's join response.");
      close();
      return null;
    }

    if (isCancelled() || result == null) {
      close();
      return null;
    } else if (result.equals("DUP\n")) {
      close();
      return "DUP";
    } else if (result.equals("JNO\n")) {
      close();
      return "JNO";
    }

    mProgress.setMessage("Setting up game environment...");
    mProgress.setCancelable(false);

    if (DEBUG) {
      Log.v(TAG, "Requesting a puck from the server...");
    }

    // Request a puck from the server
    String puckMsg = String.format(Locale.US, "P,%s\n", mUser);
    out.println(puckMsg);

    if (out.checkError()) {
      Log.e(TAG, "Client failed to write message because PrintWriter threw an"
          + "IOException!");
      close();
      return null;
    }

    // Blocks until the server responds or the client times out waiting
    // for the server to respond.
    String puckResult = null;
    try {
      puckResult = in.readLine();
    } catch (IOException e) {
      Log.e(TAG, "IOException while waiting for server's join response.");
      close();
      return null;
    }

    if (puckResult == null) {
      close();
      return null;
    }

    //try {
    //  in.close();
    //} catch (IOException e) {
    //  // Ignore exception
    //}
    //out.close();

    try {
      // Sleep for a little bit just so it looks like we are doing
      // some work. :)
      Thread.sleep(500);
    } catch (InterruptedException e) {
    }

    // Return the socket and the result minus the newline character
    if (puckResult.charAt(puckResult.length() - 1) != '\n') {
      Log.e(TAG, "WARNING!! RESULT IS NON-NULL AND DOES NOT END WITH NEW LINE!");
    }

    String trimmedResult = puckResult.substring(puckResult.length() - 1);
    return trimmedResult;
  }

  private void close() {
    if (mSocket != null) {
      try {
        mSocket.close();
      } catch (IOException e) {
        // Ignore exception
      }
      mSocket = null;
    }
    if (in != null) {
      try {
        in.close();
      } catch (IOException e) {
        // Ignore exception
      }
      in = null;
    }
    if (out != null) {
      out.close();
      out = null;
    }
  }

  @Override
  protected void onPostExecute(String result) {
    if (DEBUG) {
      Log.v(TAG, "onPostExecute() called!");
    }
    mProgress.dismiss();

    if (result == null) {
      if (DEBUG) {
        Log.v(TAG, "Client socket connection lost!");
      }
      Toast.makeText(mCtx, "Could not connect! Please try again.",
          Toast.LENGTH_SHORT).show();
      return;
    }


    if (result.equals("JNO")) {
      if (DEBUG) {
        Log.v(TAG, "Client failed to join!");
      }
      Toast.makeText(mCtx, "Could not connect! Please try again.",
          Toast.LENGTH_SHORT).show();
      return;
    }

    if (result.equals("DUP")) {
      if (DEBUG) {
        Log.v(TAG, "Client failed to join!");
      }
      Toast.makeText(mCtx, "User already connected!", Toast.LENGTH_SHORT)
          .show();
      return;
    }

    int index = result.indexOf(",");
    if (index >= 0 && result.substring(0, index).equals("JOK")) {
      if (DEBUG) {
        Log.v(TAG, "Client joined successfully!");
      }
    }

    if (mCallback != null) {
      if (DEBUG) {
        if (result != null)
          Log.v(TAG, "Received server response: " + result);
      }
      mCallback.onLoginComplete(mSocket, in, out);
    }
  }

  @Override
  protected void onCancelled() {
    if (DEBUG) {
      Log.v(TAG, "onCancelled() called!");
    }
    mProgress.dismiss();
    if (mCallback != null) {
      if (DEBUG) {
        Log.v(TAG, "The LoginTask was cancelled!");
      }
    }
  }

  public interface LoginCallback {
    void onLoginComplete(Socket socket, BufferedReader in, PrintWriter out);
  }
}
