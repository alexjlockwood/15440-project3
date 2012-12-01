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
public class BallRegion extends Shape {

  private static final String TAG = "AirHockeyTag";
  private static final boolean DEBUG = true;

  public static final int LEFT = 0;
  public static final int TOP = 1;
  public static final int BOTTOM = 2;
  public static final int RIGHT = 3;

  private float mLeft;
  private float mRight;
  private float mTop;
  private float mBottom;
  protected List<Ball> mBalls;
  protected WeakReference<BallEngine.BallEventCallBack> mCallback;
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
  public BallRegion(float left, float right, float top,
      float bottom, ArrayList<Ball> balls, boolean isGoal) {
    mLeft = left;
    mRight = right;
    mTop = top;
    mBottom = bottom;
    mBalls = balls;
    mIsGoal = isGoal;

    for (int i = 0; i < mBalls.size(); i++) {
      final Ball ball = mBalls.get(i);
      ball.setRegion(this);
    }
  }

  public void setCallBack(BallEngine.BallEventCallBack callBack) {
    mCallback = new WeakReference<BallEngine.BallEventCallBack>(callBack);
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
  public List<Ball> getBalls() {
    return mBalls;
  }

  /**
   * Adds a ball to the region.
   */
  public void addBall(Ball ball) {
    mBalls.add(ball);
  }

  /**
   * Update the notion of 'now' in milliseconds. Useful when returning from a
   * paused state. Called by the {@link BallEngine}.
   */
  public void setNow(long now) {
    for (int i = 0; i < mBalls.size(); i++) {
      mBalls.get(i).setNow(now);
    }
  }

  /**
   * Update the balls in this region. Called by the {@link BallEngine}.
   *
   * @param now
   *          in millis
   * @return A new region if a split has occurred because the animating line
   *         finished.
   */
  public void update(long now) {
    Iterator<Ball> iter = mBalls.iterator();
    while (iter.hasNext()) {
      Ball ball = iter.next();
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
      final Ball ball = mBalls.get(i);
      for (int j = i + 1; j < mBalls.size(); j++) {
        Ball other = mBalls.get(j);
        if (ball.isCircleOverlapping(other)) {
          Ball.adjustForCollision(ball, other);
          // Notify the BallEngine that a collision has occurred
          mCallback.get().onBallHitsBall(ball, other);
          break;
        }
      }
    }
  }

  private static boolean movingAwayFromWall(Ball ball, int edge) {
    double angle = ball.getAngle();
    switch (edge) {
      case BallRegion.BOTTOM:
        return 0 <= angle && angle < Math.PI;
      case BallRegion.TOP:
        return Math.PI <= angle && angle < 2 * Math.PI;
      case BallRegion.LEFT:
        return 0 <= angle && angle < 0.5 * Math.PI && 1.5 * Math.PI <= angle
            && angle < 2 * Math.PI;
      case BallRegion.RIGHT:
        return 0.5 * Math.PI <= angle && angle < 1.5 * Math.PI;
      default:
        // Will never happen
        return false;
    }
  }
}
