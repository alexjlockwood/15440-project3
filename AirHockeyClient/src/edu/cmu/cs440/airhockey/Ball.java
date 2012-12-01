package edu.cmu.cs440.airhockey;

import java.util.Locale;

import android.util.Log;

/**
 * A ball has a current location, a trajectory angle, a speed in pixels per
 * second, and a last update time. It is capable of updating itself based on its
 * trajectory and speed.
 *
 * It also knows its boundaries, and will 'bounce' off them when it reaches
 * them.
 */
public class Ball extends Shape {

  private static final String TAG = "AirHockeyTag";
  private static final boolean DEBUG = true;

  public static final float MIN_SPEED = 100f;
  public static final float MAX_SPEED = 450f;

  private BallRegion mRegion;
  private int mId;
  private float mX;
  private float mY;
  private double mDirection;
  private float mRadiusPixels;
  private float mPixelsPerSecond;
  private boolean mPressed;
  private long mLastUpdate;
  private int mHasExited;

  private Ball(long now, int id, float pps, float x, float y, double angle,
      float radius) {
    mId = id;
    mX = x;
    mY = y;
    mDirection = angle;
    mRadiusPixels = radius;
    mPixelsPerSecond = pps;
    mPressed = false;
    mLastUpdate = now;
    mHasExited = -1;
  }

  public int getId() {
    return mId;
  }

  /**
   * Returns the x coordinate of the ball's origin.
   */
  public float getX() {
    return mX;
  }

  /**
   * Returns the y coordinate of the ball's origin.
   */
  public float getY() {
    return mY;
  }

  /**
   * Sets the ball's origin to point (x,y).
   */
  public void setCoords(float x, float y) {
    mX = x;
    mY = y;
  }

  /** {@inheritDoc} */
  @Override
  public float getLeft() {
    return mX - mRadiusPixels;
  }

  /** {@inheritDoc} */
  @Override
  public float getRight() {
    return mX + mRadiusPixels;
  }

  /** {@inheritDoc} */
  @Override
  public float getTop() {
    return mY - mRadiusPixels;
  }

  /** {@inheritDoc} */
  @Override
  public float getBottom() {
    return mY + mRadiusPixels;
  }

  public double getAngle() {
    return mDirection;
  }

  public void setDirection(double angle) {
    mDirection = angle;
  }

  public float getRadiusPixels() {
    return mRadiusPixels;
  }

  public void setRadiusPixels(float radiusPixels) {
    mRadiusPixels = radiusPixels;
  }

  public float getPixelsPerSecond() {
    return mPixelsPerSecond;
  }

  public void setPixelsPerSecond(float pps) {
    mPixelsPerSecond = pps;
  }

  public boolean isPressed() {
    return mPressed;
  }

  public void setPressed(boolean pressed) {
    mPressed = pressed;
  }

  public int hasExited() {
    return mHasExited;
  }

  public void setExited(int i) {
    mHasExited = i;
  }

  /**
   * Get the region that this ball is contained in.
   */
  public Shape getRegion() {
    return mRegion;
  }

  /**
   * Set the region that this ball is contained in.
   */
  public void setRegion(BallRegion region) {
    if (region.isGoal()) {
      if (mX < region.getLeft()) {
        mX = region.getLeft();
        // bounceOffRight();
        // bounceOffLeft();
      } else if (region.getRight() < mX) {
        mX = region.getRight();
        // bounceOffLeft();
        // bounceOffRight();
      }
      if (mY < region.getTop()) {
        mY = region.getTop();
        // bounceOffBottom();
        // bounceOffTop();
      } else if (region.getBottom() < mY) {
        mY = region.getBottom();
        // bounceOffTop();
        // bounceOffBottom();
      }
    }
    mRegion = region;
  }

  public void setNow(long now) {
    mLastUpdate = now;
  }

  public void update(long now) {
    if (now <= mLastUpdate) {
      // Don't update if 'now' is outdated.
      return;
    } else if (mPressed) {
      // Don't update if the ball is being held.
      return;
    }

    if (mRegion.isGoal()) {
      // bounce when at walls
      if (mX <= mRegion.getLeft() + mRadiusPixels) {
        // at left wall
        mX = mRegion.getLeft() + mRadiusPixels;
        bounceOffLeft();
      } else if (mY <= mRegion.getTop() + mRadiusPixels) {
        // at top wall
        mY = mRegion.getTop() + mRadiusPixels;
        bounceOffTop();
      } else if (mX >= mRegion.getRight() - mRadiusPixels) {
        // at right wall
        mX = mRegion.getRight() - mRadiusPixels;
        bounceOffRight();
      } else if (mY >= mRegion.getBottom() - mRadiusPixels) {
        // at bottom wall
        mY = mRegion.getBottom() - mRadiusPixels;
        bounceOffBottom();
      }
    } else {
      // fall out of the screen... don't bounce at walls
      if (mX <= -mRadiusPixels) {
        // at left wall
        mHasExited = BallRegion.LEFT;
      } else if (mY <= -mRadiusPixels) {
        // at top wall
        mHasExited = BallRegion.TOP;
      } else if (mX >= mRegion.getRight() + AirHockeyView.BORDER_WIDTH
          + mRadiusPixels) {
        // at right wall
        mHasExited = BallRegion.RIGHT;
      } else if (mY >= mRegion.getBottom() + AirHockeyView.BORDER_WIDTH
          + mRadiusPixels) {
        // at bottom wall
        mHasExited = BallRegion.BOTTOM;
      }
    }

    float delta = (now - mLastUpdate) * mPixelsPerSecond;
    delta = delta / 1000f;

    mX += (delta * Math.cos(mDirection));
    mY += (delta * Math.sin(mDirection));

    double newSpeed = 0.995 * mPixelsPerSecond;
    mPixelsPerSecond = (float) Math.max(newSpeed, MIN_SPEED);

    mLastUpdate = now;
  }

  private void bounceOffBottom() {
    if (DEBUG) {
      Log.v(TAG, "bounceOffBottom() called! (mDirection -> " + mDirection + ")");
    }
    if (mDirection < 0.5 * Math.PI) {
      mDirection = -mDirection; // going right
    } else {
      mDirection += (Math.PI - mDirection) * 2; // going left
    }
  }

  private void bounceOffRight() {
    if (DEBUG) {
      Log.v(TAG, "bounceOffRight() called! (mDirection -> " + mDirection + ")");
    }
    if (mDirection > 1.5 * Math.PI) {
      mDirection -= (mDirection - 1.5 * Math.PI) * 2; // going up
    } else {
      mDirection += (.5 * Math.PI - mDirection) * 2; // going down
    }
  }

  private void bounceOffTop() {
    if (DEBUG) {
      Log.v(TAG, "bounceOffTop() called! (mDirection -> " + mDirection + ")");
    }
    if (mDirection < 1.5 * Math.PI) {
      mDirection -= (mDirection - Math.PI) * 2; // going left
    } else {
      mDirection += (2 * Math.PI - mDirection) * 2; // going right
      mDirection -= 2 * Math.PI;
    }
  }

  private void bounceOffLeft() {
    if (mDirection < Math.PI) {
      mDirection -= ((mDirection - (Math.PI / 2)) * 2); // going down
    } else {
      mDirection += (((1.5 * Math.PI) - mDirection) * 2); // going up
    }
    if (DEBUG) {
      Log.v(TAG, "bounceOffLeft() called! (mDirection -> " + mDirection + ")");
    }
  }

  public boolean isCircleOverlapping(Ball otherBall) {
    float dy = otherBall.mY - mY;
    float dx = otherBall.mX - mX;
    float distance = dy * dy + dx * dx;
    return (distance < ((2 * mRadiusPixels) * (2 * mRadiusPixels)))
        && !movingAwayFromEachother(this, otherBall); // avoid messy collisions
  }

  private boolean movingAwayFromEachother(Ball b1, Ball b2) {
    double collA = Math.atan2(b2.mY - b1.mY, b2.mX - b1.mX);
    double collB = Math.atan2(b1.mY - b2.mY, b1.mX - b2.mX);
    double ax = Math.cos(b1.mDirection - collA);
    double bx = Math.cos(b2.mDirection - collB);
    return ax + bx < 0;
  }

  /**
   * Given that ball a and b have collided, adjust their angles to reflect their
   * state after the collision.
   *
   * This method works based on the conservation of energy and momentum in an
   * elastic collision. Because the balls have equal mass and speed, it ends up
   * being that they simply swap velocities along the axis of the collision,
   * keeping the velocities tangent to the collision constant.
   *
   * @param b1
   *          The first ball in a collision
   * @param b2
   *          The second ball in a collision
   */
  public static void adjustForCollision(Ball b1, Ball b2) {
    double collA = Math.atan2(b2.mY - b1.mY, b2.mX - b1.mX);
    double collB = Math.atan2(b1.mY - b2.mY, b1.mX - b2.mX);
    double ax = Math.cos(b1.mDirection - collA);
    double ay = Math.sin(b1.mDirection - collA);
    double bx = Math.cos(b2.mDirection - collB);
    double by = Math.cos(b2.mDirection - collB);
    double diffA = Math.atan2(ay, -bx);
    double diffB = Math.atan2(by, -ax);
    b1.mDirection = collA + diffA;
    b2.mDirection = collB + diffB;
  }

  @Override
  public String toString() {
    return String.format(Locale.US,
        "Ball(id=%d, x=%f, y=%f, angle=%f, exitEdge=%d)", mId, mX, mY,
        Math.toDegrees(mDirection), mHasExited);
  }

  /**
   * A more readable way to create balls than using a 5 param constructor of all
   * numbers.
   */
  public static class Builder {
    private long mNow = -1;
    // TODO: figure out what to do with the ids!
    private int mId = -1;
    private float mX = -1;
    private float mY = -1;
    private double mAngle = -1;
    private float mRadiusPixels = -1;
    private float mPixelsPerSecond = 120f;

    // TODO: fix weird scenario in which the angle is negative??

    public Ball create() {
      if (mNow < 0) {
        throw new IllegalStateException("must set 'now'");
      }
      if (mId < 0) {
        throw new IllegalStateException("id must be set");
      }
      if (mX < 0) {
        // throw new IllegalStateException("X must be set");
      }
      if (mY < 0) {
        // throw new IllegalStateException("Y must be stet");
      }
      if (mAngle < 0) {
        throw new IllegalStateException("angle must be set");
      }
      if (mAngle > 2 * Math.PI) {
        throw new IllegalStateException("angle must be less that 2Pi");
      }
      if (mRadiusPixels <= 0) {
        throw new IllegalStateException("radius must be set");
      }
      return new Ball(mNow, mId, mPixelsPerSecond, mX, mY, mAngle,
          mRadiusPixels);
    }

    public Builder setId(int id) {
      mId = id;
      return this;
    }

    public Builder setNow(long now) {
      mNow = now;
      return this;
    }

    public Builder setX(float x) {
      mX = x;
      return this;
    }

    public Builder setY(float y) {
      mY = y;
      return this;
    }

    public Builder setAngle(double angle) {
      mAngle = angle;
      return this;
    }

    public Builder setRadiusPixels(float pixels) {
      mRadiusPixels = pixels;
      return this;
    }

    public Builder setPixelsPerSecond(float pixelsPerSecond) {
      mPixelsPerSecond = pixelsPerSecond;
      return this;
    }
  }
}
