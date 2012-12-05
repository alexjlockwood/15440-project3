package edu.cmu.cs440.airhockey;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Keeps track of the current state of pucks bouncing around within a a set of
 * regions.
 * 
 * Note: 'now' is the elapsed time in milliseconds since some consistent point
 * in time. As long as the reference point stays consistent, the engine will be
 * happy, though typically this is
 * {@link android.os.SystemClock#elapsedRealtime()}
 */
public class PuckEngine {

  /**
   * Directs callback events to the {@link AirHockeyView}.
   */
  private BallEventCallBack mCallBack;
  private PuckRegion mRegion;
  private PuckRegion mGoalRegion;

  private final float mMinX;
  private final float mMaxX;
  private final float mMinY;
  private final float mMaxY;

  private final float mGoalMinX;
  private final float mGoalMaxX;
  private final float mGoalMinY;
  private final float mGoalMaxY;

  public PuckEngine(float minX, float maxX, float minY, float maxY) {
    mMinX = minX;
    mMaxX = maxX;
    mMinY = minY;
    mMaxY = maxY;

    final float regionWidth = maxX - minX;
    final float regionHeight = maxY - minY;
    final float goalWidth = regionWidth / 6;
    final float goalHeight = regionHeight / 6;

    mGoalMinX = minX + (regionWidth / 2) - (goalWidth / 2);
    mGoalMaxX = maxX - (regionWidth / 2) + (goalWidth / 2);
    mGoalMinY = minY + (regionHeight / 2) - (goalHeight / 2);
    mGoalMaxY = maxY - (regionHeight / 2) + (goalHeight / 2);
  }

  /**
   * Set the callback that will be notified of ball events. Callback events are
   * received to the {@link AirHockeyView}.
   * 
   * Called by {@link AirHockeyView}.
   */
  public void setCallBack(BallEventCallBack callBack) {
    mCallBack = callBack;
  }

  /**
   * Returns the outer ball region (everything but the goal region).
   */
  public PuckRegion getRegion() {
    return mRegion;
  }

  /**
   * Returns the inner goal region (everything but the outer region).
   */
  public PuckRegion getGoalRegion() {
    return mGoalRegion;
  }

  /**
   * Update the notion of 'now' in milliseconds. Useful when returning from a
   * paused state.
   */
  public void setNow(long now) {
    mRegion.setNow(now);
    mGoalRegion.setNow(now);
  }

  /**
   * Update all regions and balls with the latest notion of 'now'. Useful when
   * the game is not coming back from a paused state.
   */
  public void update(long now) {
    // TODO: figure out how we might avoid allocating an iterator here...
    // Memory allocations can be kind of expensive given that we are constantly
    // redrawing and updating the screen's state.
    Iterator<Puck> it = mRegion.getBalls().iterator();
    while (it.hasNext()) {
      final Puck ball = it.next();
      if (!ball.isPressed()) {
        // Don't update the ball's timestamp if it is being held.
        if (mGoalRegion.isPointWithin(ball.getX(), ball.getY())) {
          // Then an outer region ball has entered the goal region.
          it.remove();
          mGoalRegion.addBall(ball);
          ball.setRegion(mGoalRegion);
          // Notify the AirHockeyView that a goal has been scored
          mCallBack.onGoalScored(now, ball);
        }
      }
    }
    mRegion.update(now);
    mGoalRegion.update(now);
  }

  public void reset() {
    PuckRegion region = new PuckRegion(mMinX, mMaxX, mMinY, mMaxY, new ArrayList<Puck>(10), false);
    region.setCallBack(mCallBack);
    mRegion = region;

    // Reset the goal region
    PuckRegion goalRegion = new PuckRegion(mGoalMinX, mGoalMaxX, mGoalMinY, mGoalMaxY,
        new ArrayList<Puck>(10), true);
    goalRegion.setCallBack(mCallBack);
    mGoalRegion = goalRegion;
  }

  public static interface BallEventCallBack {

    void onBallHitsBall(Puck b1, Puck b2);

    void onBallExitsRegion(long when, Puck ball, int exitEdge);

    void onGoalScored(long when, Puck ball);
  }

  public void addIncomingPuck(Puck inBall) {
    mRegion.getBalls().add(inBall);
  }
}
