package edu.cmu.cs440.airhockey;

import static edu.cmu.cs440.airhockey.Utils.LOGE;
import static edu.cmu.cs440.airhockey.Utils.LOGV;

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
import android.widget.Toast;

public class LoginTask extends AsyncTask<String, String, String> {

  private static final String TAG = "15440_LoginTask";

  private Context mCtx;
  private LoginCallback mCallback;
  private Socket mSocket;
  private BufferedReader mIn;
  private PrintWriter mOut;
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
    String connecting = mCtx.getString(R.string.progress_connecting);
    mProgress = ProgressDialog.show(mCtx, "", connecting, true);
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
  protected void onProgressUpdate(String... progress) {
    mProgress.setMessage(progress[0]);
  }

  @Override
  protected String doInBackground(String... args) {
    String user = args[0];
    String host = args[1];
    int port = Integer.parseInt(args[2]);

    // Sleep for a little bit just so it looks like we are doing
    // some work. :)
    sleep(500);

    if (isCancelled()) {
      return null;
    }

    try {
      mServerAddr = InetAddress.getByName(host);
      mSocket = new Socket(mServerAddr, port);
      mOut = new PrintWriter(mSocket.getOutputStream(), true);
      mIn = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
    } catch (SocketException e) {
      LOGE(TAG, "SocketException during login");
      return null;
    } catch (UnknownHostException e) {
      LOGE(TAG, "Could not resolve hostname: " + host);
      return null;
    } catch (IOException e) {
      LOGE(TAG, "IOException during login");
      return null;
    }

    LOGV(TAG, String.format(Locale.US, "Attempting to join! User: %s, Host: %s, Port: %d", user,
        host, port));

    LOGV(TAG, "Sending join request: J," + user);
    mOut.println("J," + user);

    if (mOut.checkError()) {
      LOGE(TAG, "Client failed to write message because PrintWriter threw an IOException!");
      return null;
    }

    if (isCancelled()) {
      return null;
    }

    LOGV(TAG, "Waiting for server's 'join response'.");

    // Blocks until the server responds or the client times out waiting
    // for the server to respond.
    String result = null;
    try {
      result = mIn.readLine();
    } catch (IOException e) {
      LOGE(TAG, "IOException while waiting for server's join response.");
      return null;
    }

    if (isCancelled()) {
      return null;
    }

    if (result == null || result.equals("DUP") || result.equals("JNO")) {
      return result;
    }

    // Publish the updated progress...
    publishProgress(new String[] { "Setting up game environment..." });

    // Yeah, I know... 'Setting up game environment...' makes it sound like
    // there's a lot of background processing going on doesn't it? :D
    sleep(500);

    if (isCancelled()) {
      return null;
    }

    // OK, I'll stop giving you guys meaningless progress dialog updates now...

    // Request a puck from the server
    String puckMsg = new String("P," + mUser);
    mOut.println(puckMsg);

    if (mOut.checkError()) {
      LOGE(TAG, "Client failed to write message because PrintWriter threw an IOException!");
      return null;
    }

    // The result should be a "JOK" response.
    return result;
  }

  @Override
  protected void onPostExecute(String result) {
    mProgress.dismiss();
    if (result == null) {
      LOGV(TAG, "Client socket connection lost!");
      Toast.makeText(mCtx, "Could not connect! Please try again.", Toast.LENGTH_SHORT).show();
      close();
      return;
    } else if (result.equals("JNO")) {
      LOGV(TAG, "Client failed to join!");
      Toast.makeText(mCtx, R.string.could_not_connect, Toast.LENGTH_SHORT).show();
      close();
      return;
    } else if (result.equals("DUP")) {
      LOGV(TAG, "Client failed to join!");
      Toast.makeText(mCtx, R.string.user_already_connected, Toast.LENGTH_SHORT).show();
      close();
      return;
    } else {
      LOGV(TAG, "Received server response: " + result);
      mCallback.onLoginComplete(mSocket, mIn, mOut);
    }
  }

  @Override
  protected void onCancelled() {
    LOGV(TAG, "The LoginTask was cancelled!");
    mProgress.dismiss();
    close();
  }

  private void close() {
    if (mSocket != null) {
      try {
        mSocket.close();
      } catch (IOException ignore) {
      }
      mSocket = null;
    }
    if (mIn != null) {
      try {
        mIn.close();
      } catch (IOException ignore) {
      }
      mIn = null;
    }
    if (mOut != null) {
      mOut.close();
      mOut = null;
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ignore) {
    }
  }

  public interface LoginCallback {
    void onLoginComplete(Socket socket, BufferedReader in, PrintWriter out);
  }
}
