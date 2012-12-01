package edu.cmu.cs440.airhockey;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;

public class Utils {

  // Used for sending outgoing pucks
  public static byte[] toBytes(PuckRegion region, Puck ball, int outEdge) {
    // Client sends "X,puckId,edgeNum,args"
    // args: "xEntryPercent;yEntryPercent;angle;pps;radius"

    float minX = region.getLeft();
    float maxX = region.getRight();
    float minY = region.getTop();
    float maxY = region.getBottom();

    StringBuilder b = new StringBuilder();
    b = b.append("X,");
    b = b.append(ball.getId()).append(","); // puckId
    b = b.append(outEdge).append(",");
    b = b.append(ball.getX() / (maxX - minX)).append(";"); // xEntryPercent
    b = b.append(ball.getY() / (maxY - minY)).append(";"); // yEntryPercent
    b = b.append(ball.getAngle()).append(";");
    b = b.append(ball.getPixelsPerSecond()).append(";");
    b = b.append(ball.getRadiusPixels());

    return b.toString().getBytes();
  }

  public static Puck toBall(PuckRegion region, int puckId, int inEdge,
      float xEntryPercent, float yEntryPercent, double angle, float pps,
      float radius) {
    float minX = region.getLeft();
    float maxX = region.getRight();
    float minY = region.getTop();
    float maxY = region.getBottom();

    Puck.Builder builder = new Puck.Builder().setId(puckId)
        .setPixelsPerSecond(pps).setAngle(angle).setRadiusPixels(radius);

    switch (inEdge) {
      case PuckRegion.LEFT:
        builder = builder.setX(minX).setY(
            minY + (yEntryPercent * (maxY - minY)));
        break;
      case PuckRegion.TOP:
        builder = builder.setX(minX + (xEntryPercent * (maxX - minX))).setY(
            minY);
        break;
      case PuckRegion.BOTTOM:
        builder = builder.setX(minX + (xEntryPercent * (maxX - minX))).setY(
            maxY);
        break;
      case PuckRegion.RIGHT:
        builder = builder.setX(maxX).setY(
            minY + (yEntryPercent * (maxY - minY)));
        break;
    }

    return builder.setNow(SystemClock.elapsedRealtime()).create();
  }

  public static boolean isOnline(Context ctx) {
    ConnectivityManager cm = (ConnectivityManager) ctx
        .getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo ni = cm.getActiveNetworkInfo();
    return (ni != null && ni.isAvailable() && ni.isConnected());
  }
}
