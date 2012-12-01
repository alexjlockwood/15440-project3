package edu.cmu.cs440.airhockey;

/**
 * A shape has left, right, top and bottom dimensions.
 */
public abstract class Shape {

  public abstract float getLeft();

  public abstract float getRight();

  public abstract float getTop();

  public abstract float getBottom();

  /**
   * @param other
   *          Another 2d shape
   * @return Whether this shape is intersecting with the other.
   */
  public boolean isIntersecting(Shape other) {
    return getLeft() <= other.getRight() && getRight() >= other.getLeft()
        && getTop() <= other.getBottom() && getBottom() >= other.getTop();
  }

  /**
   * @param x
   *          An x coordinate
   * @param y
   *          A y coordinate
   * @return Whether the point is within this shape
   */
  public boolean isPointWithin(float x, float y) {
    return (x > getLeft() && x < getRight() && y > getTop() && y < getBottom());
  }

  public float getArea() {
    return getHeight() * getWidth();
  }

  public float getHeight() {
    return getBottom() - getTop();
  }

  public float getWidth() {
    return getRight() - getLeft();
  }
}
