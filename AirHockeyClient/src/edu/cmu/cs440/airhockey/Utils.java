package edu.cmu.cs440.airhockey;

import java.util.Locale;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;

public class Utils {

  // Used for sending outgoing pucks
  public static String toString(PuckRegion region, Puck ball, int outEdge) {
    // Client sends "X,puckId,edgeNum,args"
    // args: "xEntryPercent;yEntryPercent;angle;pps;radius;color"

    float minX = region.getLeft();
    float maxX = region.getRight();
    float minY = region.getTop();
    float maxY = region.getBottom();
    String colorText = Utils.colorToStr(ball.getColor());

    StringBuilder b = new StringBuilder();
    b = b.append("X,");
    b = b.append(ball.getId()).append(","); // puckId
    b = b.append(outEdge).append(",");
    b = b.append(ball.getX() / (maxX - minX)).append(";"); // xEntryPercent
    b = b.append(ball.getY() / (maxY - minY)).append(";"); // yEntryPercent
    b = b.append(ball.getAngle()).append(";");
    b = b.append(ball.getPixelsPerSecond()).append(";");
    b = b.append(ball.getRadiusPixels()).append(";");
    b = b.append(colorText).append(";");
    b = b.append(ball.getLastUser());
    b = b.append("\n");

    return b.toString();
  }

  public static Puck toBall(PuckRegion region, int puckId, int inEdge,
      float xEntryPercent, float yEntryPercent, double angle, float pps,
      float radius, Puck.Color color, String user) {
    float minX = region.getLeft();
    float maxX = region.getRight();
    float minY = region.getTop();
    float maxY = region.getBottom();

    Puck.Builder builder = new Puck.Builder().setId(puckId)
        .setPixelsPerSecond(pps).setAngle(angle).setRadiusPixels(radius)
        .setColor(color).setLastUser(user);

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

  public static Puck.Color strToColor(String colorText) {
    colorText = colorText.toLowerCase(Locale.US);
    if (colorText.equals("blue")) {
      return Puck.Color.Blue;
    } else if (colorText.equals("green")) {
      return Puck.Color.Green;
    } else if (colorText.equals("orange")) {
      return Puck.Color.Orange;
    } else if (colorText.equals("purple")) {
      return Puck.Color.Purple;
    } else if (colorText.equals("red")) {
      return Puck.Color.Red;
    } else { // if (colorText.equals("yellow")) {
      return Puck.Color.Yellow;
    }
  }

  public static String colorToStr(Puck.Color color) {
    if (color == Puck.Color.Blue) {
      return "blue";
    } else if (color == Puck.Color.Green) {
      return "green";
    } else if (color == Puck.Color.Orange) {
      return "orange";
    } else if (color == Puck.Color.Purple) {
      return "purple";
    } else if (color == Puck.Color.Red) {
      return "red";
    } else { // if (ballColor == Puck.Color.Yellow) {
      return "yellow";
    }
  }

  public static boolean isOnline(Context ctx) {
    ConnectivityManager cm = (ConnectivityManager) ctx
        .getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo ni = cm.getActiveNetworkInfo();
    return (ni != null && ni.isAvailable() && ni.isConnected());
  }

  public static String exitEdgeToStr(int exitEdge) {
    switch (exitEdge) {
      case PuckRegion.LEFT:
        return "left";
      case PuckRegion.RIGHT:
        return "right";
      case PuckRegion.TOP:
        return "top";
      case PuckRegion.BOTTOM:
        return "bottom";
      default:
        return "n/a";
    }
  }
}
