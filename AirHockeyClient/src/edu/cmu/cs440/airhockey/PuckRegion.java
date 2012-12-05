package edu.cmu.cs440.airhockey;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A puck region is a rectangular region that contains bouncing pucks. In its
 * {@link #update(long)} method, it will update all of its pucks.
 */
public class PuckRegion extends Shape {

  public static final int LEFT = 0;
  public static final int TOP = 1;
  public static final int BOTTOM = 2;
  public static final int RIGHT = 3;

  private float mLeft;
  private float mRight;
  private float mTop;
  private float mBottom;
  protected List<Puck> mPucks;
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
  public PuckRegion(float left, float right, float top, float bottom,
      ArrayList<Puck> pucks, boolean isGoal) {
    mLeft = left;
    mRight = right;
    mTop = top;
    mBottom = bottom;
    mPucks = pucks;
    mIsGoal = isGoal;

    for (int i = 0; i < mPucks.size(); i++) {
      final Puck ball = mPucks.get(i);
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
    return mPucks;
  }

  /**
   * Adds a ball to the region.
   */
  public void addBall(Puck ball) {
    mPucks.add(ball);
  }

  /**
   * Update the notion of 'now' in milliseconds. Useful when returning from a
   * paused state. Called by the {@link PuckEngine}.
   */
  public void setNow(long now) {
    for (int i = 0; i < mPucks.size(); i++) {
      mPucks.get(i).setNow(now);
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
    Iterator<Puck> iter = mPucks.iterator();
    while (iter.hasNext()) {
      Puck ball = iter.next();
      ball.update(now);
      int exitEdge = ball.hasExited();
      if (exitEdge >= 0) {
        // The ball has exited the region. Remove it from the region's
        // list of balls.
        mCallback.get().onBallExitsRegion(now, ball, exitEdge);
        iter.remove();
      }
    }

    // Update ball to ball collisions
    for (int i = 0; i < mPucks.size(); i++) {
      final Puck ball = mPucks.get(i);
      for (int j = i + 1; j < mPucks.size(); j++) {
        Puck other = mPucks.get(j);
        if (ball.isCircleOverlapping(other)) {
          Puck.adjustForCollision(ball, other);
          // Notify the BallEngine that a collision has occurred
          // mCallback.get().onBallHitsBall(ball, other);
          break;
        }
      }
    }
  }
}
