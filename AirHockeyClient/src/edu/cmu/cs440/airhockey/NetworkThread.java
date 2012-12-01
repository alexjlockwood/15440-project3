package edu.cmu.cs440.airhockey;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import android.os.Handler;
import android.util.Log;

public class NetworkThread extends Thread {

  private static final String TAG = "AirHockeyTag";
  private static final boolean DEBUG = true;

  public static final int IP = 0;
  public static final int XOK = 1;
  public static final int XNO = 2;
  public static final int POK = 3;
  public static final int PNO = 4;

  private BallRegion mRegion;
  private Handler mHandler;
  private DatagramSocket mSocket;
  private InetAddress mServerAddr;
  private String mHostname;
  private int mPort;
  private String mUser;

  public NetworkThread(BallRegion region, Handler handler, String hostname,
      String port, String user) {
    mRegion = region;
    mHandler = handler;
    mHostname = hostname;
    mPort = Integer.parseInt(port);
    mUser = user;
  }

  @Override
  public void run() {
    if (DEBUG)
      Log.v(TAG, "Starting NetworkThread...");

    try {
      mServerAddr = InetAddress.getByName(mHostname);
      mSocket = new DatagramSocket(mPort);
    } catch (UnknownHostException e) {
      // Could not connect
      Log.e(TAG, "Could not resolve hostname: " + mHostname);
      close();
      return;
    } catch (SocketException e) {
      // Could not connect
      Log.e(TAG, "Socket exception during login");
      close();
      return;
    }

    // Notify the Activity that the client has connected
    mHandler.obtainMessage(AirHockeyActivity.STATE_CONNECTED).sendToTarget();

    if (DEBUG)
      Log.v(TAG, "Requesting a puck from the server...");

    // Request a puck from the server
    write(new String("P," + mUser).getBytes());

    // Loop forever, read new messages from the network, and forward them to
    // the Activity.
    byte[] buf = new byte[64];
    while (true) {
      DatagramPacket packet = new DatagramPacket(buf, buf.length);
      try {
        mSocket.receive(packet);
        byte[] bytes = packet.getData();
        int offset = packet.getOffset();
        int length = packet.getLength();
        String readMsg = new String(bytes, offset, length);

        if (DEBUG)
          Log.v(TAG, "Message received in NetworkThread: " + readMsg);

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
          int puckId = Integer.parseInt(fields[1]);
          int inEdge = Integer.parseInt(fields[2]);
          float xEntryPercent = Float.parseFloat(args[0]);
          float yEntryPercent = Float.parseFloat(args[1]);
          double angle = Double.parseDouble(args[2]);
          float pps = Float.parseFloat(args[3]);
          float radius = Float.parseFloat(args[4]);
          msg = IOUtils.toBall(mRegion, puckId, inEdge, xEntryPercent,
              yEntryPercent, angle, pps, radius);
        } else if (msgType.equals("XOK")) {
          // "XOK,puckId"
          arg1 = XOK;
          msg = Integer.parseInt(fields[1]);
          // Do stuff
        } else if (msgType.equals("XNO")) {
          // "XNO,puckId"
          arg1 = XNO;
          msg = Integer.parseInt(fields[1]);
          // TODO: do stuff!!!!!!!!!!!!!!!!!!!!!!!!!!
        } else if (msgType.equals("POK")) {
          // "POK,puckId"
          arg1 = POK;
          msg = Integer.parseInt(fields[1]);
          // Do stuff
        } else if (msgType.equals("PNO")) {
          // "PNO"
          arg1 = PNO;
          msg = null;
          // TODO: do stuff!!!!!!!!!!!!!!!!!!!!!!!!!!
        } else {
          // Shouldn't happen
          arg1 = -1;
          msg = null;
        }

        mHandler.obtainMessage(AirHockeyActivity.MESSAGE_READ, arg1, -1, msg)
            .sendToTarget();
      } catch (IOException e) {
        Log.e(TAG, "Client failed to read from server!");
        close();
        return;
      }
    }
  }

  public void write(byte[] buf) {
    try {
      if (DEBUG)
        Log.v(TAG, "Writing message to server: " + new String(buf));
      mSocket.send(new DatagramPacket(buf, buf.length, mServerAddr, mPort));
    } catch (IOException e) {
      Log.e(TAG, "Client failed to write message: "
          + new String(buf, 0, buf.length));
      close();
    }
  }

  public void close() {
    if (DEBUG)
      Log.v(TAG, "Closing NetworkThread...");
    mHandler.obtainMessage(AirHockeyActivity.STATE_NONE).sendToTarget();
    if (mSocket != null) {
      mSocket.close();
    }
  }
}
