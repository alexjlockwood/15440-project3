package edu.cmu.cs440.airhockey;

import static edu.cmu.cs440.airhockey.Utils.LOGE;
import static edu.cmu.cs440.airhockey.Utils.LOGV;
import static edu.cmu.cs440.airhockey.Utils.LOGW;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import android.os.Handler;
import android.os.Message;

public class NetworkThread extends Thread {

  private static final String TAG = "15440_NetworkThread";

  public static final int IP = 0;
  public static final int XOK = 1;
  public static final int XNO = 2;
  public static final int POK = 3;
  public static final int PNO = 4;

  private static final int RETRY_ATTEMPTS = 6;
  private static final int RETRY_ATTEMPTS_WAIT_MILLIS = 5000;

  private PuckRegion mRegion;
  private Handler mHandler;
  private Socket mSocket;
  private BufferedReader mIn;
  private PrintWriter mOut;
  private String mUser;
  private String mHost;
  private int mPort;

  private boolean mForceClosed = false;

  public NetworkThread(PuckRegion region, Handler handler, Socket socket, BufferedReader in,
      PrintWriter out, String user, String host, int port) {
    mRegion = region;
    mHandler = handler;
    mSocket = socket;
    mIn = in;
    mOut = out;
    mUser = user;
    mHost = host;
    mPort = port;
  }

  @Override
  public void run() {
    LOGV(TAG, "Starting NetworkThread...");

    // Loop forever, read new messages from the network, and forward them to
    // the Activity.
    while (true) {
      try {
        LOGV(TAG, "Waiting for server's next incoming message...");
        String readMsg = mIn.readLine();
        LOGV(TAG, "Message received in NetworkThread: " + readMsg);

        if (readMsg == null) {
          if (retryConnection()) {
            continue;
          } else {
            close();
            return;
          }
        }

        // Split the message into fields
        String[] fields = readMsg.split(",");
        String msgType = fields[0];

        if (msgType.equals("IP")) {
          // fields: "IP,puckId,inEdge,args"
          // args: "xEntryPercent;yEntryPercent;angle;pps;radius"
          LOGV(TAG, "IP received from server.");

          String[] args = fields[3].split(";");
          int puckId = Integer.parseInt(fields[1]);
          int inEdge = Integer.parseInt(fields[2]);
          float xEntryPercent = Float.parseFloat(args[0]);
          float yEntryPercent = Float.parseFloat(args[1]);
          double angle = Double.parseDouble(args[2]);
          float pps = Float.parseFloat(args[3]);
          float radius = Float.parseFloat(args[4]);
          String colorText = args[5];
          String user = args[6];

          Puck.Color color;
          if (colorText.equals("blue")) {
            color = Puck.Color.Blue;
          } else if (colorText.equals("green")) {
            color = Puck.Color.Green;
          } else if (colorText.equals("lightblue")) {
            color = Puck.Color.LightBlue;
          } else if (colorText.equals("orange")) {
            color = Puck.Color.Orange;
          } else if (colorText.equals("purple")) {
            color = Puck.Color.Purple;
          } else if (colorText.equals("red")) {
            color = Puck.Color.Red;
          } else { // if (colorText.equals("yellow")) {
            color = Puck.Color.Yellow;
          }

          Object msg = Utils.toBall(mRegion, puckId, inEdge, xEntryPercent, yEntryPercent, angle,
              pps, radius, color, user);
          mHandler.obtainMessage(AirHockeyActivity.MESSAGE_READ, IP, -1, msg).sendToTarget();

        } else if (msgType.equals("POK")) {
          // "POK,puckId"
          LOGV(TAG, "POK received from server.");
          Object msg = Integer.parseInt(fields[1]);
          mHandler.obtainMessage(AirHockeyActivity.MESSAGE_READ, POK, -1, msg).sendToTarget();
        }

        // else if (msgType.equals("XOK")) {
        // "XOK,puckId"
        // if (DEBUG) {
        // Log.v(TAG, "XOK received from server.");
        // }
        // arg1 = XOK;
        // msg = Integer.parseInt(fields[1]);
        // } else if (msgType.equals("XNO")) {
        // "XNO,puckId"
        // if (DEBUG) {
        // Log.v(TAG, "XNO received from server.");
        // }
        // arg1 = XNO;
        // msg = Integer.parseInt(fields[1]);
        // } else if (msgType.equals("PNO")) {
        // "PNO"
        // if (DEBUG) {
        // Log.v(TAG, "PNO received from server.");
        // }
        // arg1 = PNO;
        // msg = null;
        // }

      } catch (IOException e) {
        LOGE(TAG, "Client failed to read from server!");
        LOGV(TAG, "Attempting to re-establish the connection...");
        if (retryConnection()) {
          continue;
        } else {
          close();
          return;
        }
      }
    }
  }

  private boolean retryConnection() {
    if (mForceClosed) {
      return false;
    }

    LOGV(TAG, "Retrying to establish connection...");
    mHandler.obtainMessage(AirHockeyActivity.STATE_RETRYING).sendToTarget();

    try {
      // close the old input/output streams
      if (mOut != null) {
        mOut.close();
      }
      if (mIn != null) {
        mIn.close();
      }
    } catch (IOException ignore) {
    }

    LOGV(TAG, "Closed old input/output streams.");

    int numAttempts = 0;
    while (++numAttempts < RETRY_ATTEMPTS) {
      try {
        Thread.sleep(RETRY_ATTEMPTS_WAIT_MILLIS);
      } catch (InterruptedException ex) {
      }

      try {
        InetAddress serverAddr = InetAddress.getByName(mHost);
        mSocket = new Socket(serverAddr, mPort);
        mOut = new PrintWriter(mSocket.getOutputStream(), true);
        mIn = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));

        // If we've gotten this far, then a connection has been established!

        String joinMsg = new String("J," + mUser);
        mOut.println(joinMsg);
        LOGV(TAG, "Sending join request: " + joinMsg);

        if (mOut.checkError()) {
          LOGE(TAG, "Client failed to write message because PrintWriter threw an" + "IOException!");
          return false;
        }

        LOGV(TAG, "Waiting for server's 'join response'.");

        // Blocks until the server responds or the client times out waiting
        // for the server to respond.
        String result = null;
        try {
          result = mIn.readLine();
        } catch (IOException ex) {
          LOGE(TAG, "IOException while waiting for server's join response.");
          return false;
        }

        LOGV(TAG, "Client received message from server: " + result);

        if (result != null && !result.equals("DUP") && !result.equals("JNO")) {
          Message retrySuccess = mHandler.obtainMessage(AirHockeyActivity.RETRY_SUCCESS);
          mHandler.sendMessageAtFrontOfQueue(retrySuccess);
          return true;
        }

      } catch (SocketException ignore) {
        LOGW(TAG, "SocketException during retry attempt...");
      } catch (UnknownHostException ignore) {
        LOGW(TAG, "Could not resolve hostname during retry attmept...");
      } catch (IOException ignore) {
        LOGW(TAG, "IOException during retry attempt...");
      }
    }

    return false;
  }

  public void write(String writeMsg) {
    LOGV(TAG, "Writing message to server: " + writeMsg);
    if (mOut != null) {
      mOut.println(writeMsg);
      if (mOut.checkError()) {
        LOGE(TAG, "Client failed to write message because PrintWriter threw an" + "IOException!");
      }
    } else {
      LOGE(TAG, "Client failed to write message because PrintWriter was null!");
    }
  }

  public void close() {
    LOGV(TAG, "Closing NetworkThread...");

    mForceClosed = true;
    Message closeMsg = mHandler.obtainMessage(AirHockeyActivity.STATE_NONE);
    mHandler.sendMessageAtFrontOfQueue(closeMsg);

    if (mSocket != null) {
      try {
        mSocket.close();
      } catch (IOException e) {
        // Ignore exception
      }
      mSocket = null;
    }
    if (mIn != null) {
      try {
        mIn.close();
      } catch (IOException e) {
        // Ignore exception
      }
      mIn = null;
    }
    if (mOut != null) {
      mOut.close();
      mOut = null;
    }
  }
}
