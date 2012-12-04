package edu.cmu.cs440.airhockey;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class NetworkThread extends Thread {

  private static final String TAG = "15440_NetworkThread";
  private static final boolean DEBUG = true;

  public static final int IP = 0;
  public static final int XOK = 1;
  public static final int XNO = 2;
  public static final int POK = 3;
  public static final int PNO = 4;

  private PuckRegion mRegion;
  private Handler mHandler;
  private Socket mSocket;
  private BufferedReader mIn;
  private PrintWriter mOut;

  public NetworkThread(PuckRegion region, Handler handler, Socket socket, BufferedReader in,
      PrintWriter out) {
    mRegion = region;
    mHandler = handler;
    mSocket = socket;
    mIn = in;
    mOut = out;
  }

  @Override
  public void run() {
    if (DEBUG)
      Log.v(TAG, "Starting NetworkThread...");

    // Loop forever, read new messages from the network, and forward them to
    // the Activity.
    while (true) {
      try {
        if (DEBUG) {
          Log.v(TAG, "Waiting for server's next incoming message...");
        }

        String readMsg = mIn.readLine();

        if (DEBUG) {
          Log.v(TAG, "Message received in NetworkThread: " + readMsg);
        }

        if (readMsg == null) {
          // TODO: pause the game, and retry the connection?????????????????
          Log.e(TAG, "Message was null! THIS SHOULDNT HAPPEN I THINK?!");
          close();
          return;
        }

        // Data to pass to the handler
        Object msg;
        int arg1;

        // Split the message into fields
        String[] fields = readMsg.split(",");
        String msgType = fields[0];

        if (msgType.equals("IP")) {
          // fields: "IP,puckId,inEdge,args"
          // args: "xEntryPercent;yEntryPercent;angle;pps;radius"
          arg1 = IP;
          String[] args = fields[3].split(";");
          Log.v(TAG, Arrays.toString(fields));
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

          msg = Utils.toBall(mRegion, puckId, inEdge, xEntryPercent,
              yEntryPercent, angle, pps, radius, color, user);

        } else if (msgType.equals("XOK")) {
          // "XOK,puckId"
          if (DEBUG) {
            Log.v(TAG, "XOK received from server.");
          }
          arg1 = XOK;
          msg = Integer.parseInt(fields[1]);
          // Do stuff
        } else if (msgType.equals("XNO")) {
          // "XNO,puckId"
          if (DEBUG) {
            Log.v(TAG, "XNO received from server.");
          }
          arg1 = XNO;
          msg = Integer.parseInt(fields[1]);
        } else if (msgType.equals("POK")) {
          // "POK,puckId"
          if (DEBUG) {
            Log.v(TAG, "POK received from server.");
          }
          arg1 = POK;
          msg = Integer.parseInt(fields[1]);
        } else if (msgType.equals("PNO")) {
          // "PNO"
          if (DEBUG) {
            Log.v(TAG, "PNO received from server.");
          }
          arg1 = PNO;
          msg = null;

        } else {
          // Shouldn't happen
          arg1 = -1;
          msg = null;
        }

        mHandler.obtainMessage(AirHockeyActivity.MESSAGE_READ, arg1, -1, msg)
            .sendToTarget();

      } catch (IOException e) {
        // TODO: pause the game, and retry the connection!
        Log.e(TAG, "Client failed to read from server!");
        close();
        return;
      }
    }
  }

  public void write(String writeMsg) {
    if (DEBUG)
      Log.v(TAG, "Writing message to server: " + writeMsg);
    if (mOut != null) {
      mOut.println(writeMsg);
      if (mOut.checkError()) {
        Log.e(TAG,
            "Client failed to write message because PrintWriter threw an"
                + "IOException!");
      }
    } else {
      Log.e(TAG, "Client failed to write message because PrintWriter was null!");
    }
  }

  public void close() {
    if (DEBUG)
      Log.v(TAG, "Closing NetworkThread...");

    // TODO: send to front of queue?
    Message closeMsg = mHandler.obtainMessage(AirHockeyActivity.STATE_NONE);
    mHandler.sendMessage(closeMsg);
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
