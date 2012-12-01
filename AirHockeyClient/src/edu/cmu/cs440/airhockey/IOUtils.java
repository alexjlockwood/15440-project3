package edu.cmu.cs440.airhockey;

import android.os.SystemClock;

public class IOUtils {

  // Used for sending outgoing pucks
  public static byte[] toBytes(BallRegion region, Ball ball, int outEdge) {
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

  public static Ball toBall(BallRegion region, int puckId, int inEdge,
      float xEntryPercent, float yEntryPercent, double angle, float pps,
      float radius) {
    float minX = region.getLeft();
    float maxX = region.getRight();
    float minY = region.getTop();
    float maxY = region.getBottom();

    Ball.Builder builder = new Ball.Builder().setId(puckId)
        .setPixelsPerSecond(pps).setAngle(angle).setRadiusPixels(radius);

    switch (inEdge) {
      case BallRegion.LEFT:
        builder = builder.setX(minX).setY(
            minY + (yEntryPercent * (maxY - minY)));
        break;
      case BallRegion.TOP:
        builder = builder.setX(minX + (xEntryPercent * (maxX - minX))).setY(
            minY);
        break;
      case BallRegion.BOTTOM:
        builder = builder.setX(minX + (xEntryPercent * (maxX - minX))).setY(
            maxY);
        break;
      case BallRegion.RIGHT:
        builder = builder.setX(maxX).setY(
            minY + (yEntryPercent * (maxY - minY)));
        break;
    }

    return builder.setNow(SystemClock.elapsedRealtime()).create();
  }
}
