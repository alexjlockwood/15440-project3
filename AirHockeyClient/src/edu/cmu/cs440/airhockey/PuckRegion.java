package edu.cmu.cs440.airhockey;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A ball region is a rectangular region that contains bouncing balls, and
 * possibly one animating line. In its {@link #update(long)} method, it will
 * update all of its balls, the moving line. It detects collisions between the
 * balls and the moving line, and when the line is complete, handles splitting
 * off a new region.
 */
public class PuckRegion extends Shape {

  private static final String TAG = "15440_BallRegion";
  private static final boolean DEBUG = true;

  public static final int LEFT = 0;
  public static final int TOP = 1;
  public static final int BOTTOM = 2;
  public static final int RIGHT = 3;

  private float mLeft;
  private float mRight;
  private float mTop;
  private float mBottom;
  protected List<Puck> mBalls;
  protected WeakReference<PuckEngine.BallEventCallBack> mCallback;
  private boolean mIsGoal;

  /**
   * Creates a BallRegion with the given bounds and balls.
   *
   * @param left
   *          The minimum x component
   * @param right
   *          The maximum x component
   * @param top
   *          The minimum y component
   * @param bottom
   *          The maximum y component
   * @param balls
   *          The balls of the region
   */
  public PuckRegion(float left, float right, float top,
      float bottom, ArrayList<Puck> balls, boolean isGoal) {
    mLeft = left;
    mRight = right;
    mTop = top;
    mBottom = bottom;
    mBalls = balls;
    mIsGoal = isGoal;

    for (int i = 0; i < mBalls.size(); i++) {
      final Puck ball = mBalls.get(i);
      ball.setRegion(this);
    }
  }

  public void setCallBack(PuckEngine.BallEventCallBack callBack) {
    mCallback = new WeakReference<PuckEngine.BallEventCallBack>(callBack);
  }

  public boolean isGoal() {
    return mIsGoal;
  }

  /** {@inheritDoc} */
  @Override
  public float getLeft() {
    return mLeft;
  }

  /** {@inheritDoc} */
  @Override
  public float getRight() {
    return mRight;
  }

  /** {@inheritDoc} */
  @Override
  public float getTop() {
    return mTop;
  }

  /** {@inheritDoc} */
  @Override
  public float getBottom() {
    return mBottom;
  }

  /**
   * Returns a list of all balls in this region.
   */
  public List<Puck> getBalls() {
    return mBalls;
  }

  /**
   * Adds a ball to the region.
   */
  public void addBall(Puck ball) {
    mBalls.add(ball);
  }

  /**
   * Update the notion of 'now' in milliseconds. Useful when returning from a
   * paused state. Called by the {@link PuckEngine}.
   */
  public void setNow(long now) {
    for (int i = 0; i < mBalls.size(); i++) {
      mBalls.get(i).setNow(now);
    }
  }

  /**
   * Update the balls in this region. Called by the {@link PuckEngine}.
   *
   * @param now
   *          in millis
   * @return A new region if a split has occurred because the animating line
   *         finished.
   */
  public void update(long now) {
    Iterator<Puck> iter = mBalls.iterator();
    while (iter.hasNext()) {
      Puck ball = iter.next();
      ball.update(now);
      int exitEdge = ball.hasExited();
      if (exitEdge >= 0) {
        //if (!movingAwayFromWall(ball, exitEdge)) {
          // The ball has exited the region. Remove it from the region's
          // list of balls.
          mCallback.get().onBallExitsRegion(now, ball, exitEdge);
          iter.remove();
        //}
      }
    }

    // Update ball to ball collisions
    for (int i = 0; i < mBalls.size(); i++) {
      final Puck ball = mBalls.get(i);
      for (int j = i + 1; j < mBalls.size(); j++) {
        Puck other = mBalls.get(j);
        if (ball.isCircleOverlapping(other)) {
          Puck.adjustForCollision(ball, other);
          // Notify the BallEngine that a collision has occurred
          mCallback.get().onBallHitsBall(ball, other);
          break;
        }
      }
    }
  }

  private static boolean movingAwayFromWall(Puck ball, int edge) {
    double angle = ball.getAngle();
    switch (edge) {
      case PuckRegion.BOTTOM:
        return 0 <= angle && angle < Math.PI;
      case PuckRegion.TOP:
        return Math.PI <= angle && angle < 2 * Math.PI;
      case PuckRegion.LEFT:
        return 0 <= angle && angle < 0.5 * Math.PI && 1.5 * Math.PI <= angle
            && angle < 2 * Math.PI;
      case PuckRegion.RIGHT:
        return 0.5 * Math.PI <= angle && angle < 1.5 * Math.PI;
      default:
        // Will never happen
        return false;
    }
  }
}
