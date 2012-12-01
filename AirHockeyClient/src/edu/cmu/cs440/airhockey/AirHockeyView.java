package edu.cmu.cs440.airhockey;

import java.util.Iterator;
import java.util.List;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/**
 * Handles the visual display and touch input for the game.
 */
public class AirHockeyView extends View implements PuckEngine.BallEventCallBack {

  private static final String TAG = "15440_AirHockeyView";
  private static final boolean DEBUG = true;

  public static final int BORDER_WIDTH = 10;

  private static final float BALL_START_SPEED = 120f;

  /**
   * Directs callback events to the {@link AirHockeyActivity}.
   */
  private BallEngineCallBack mCallback;
  private final Paint mPaint;
  private PuckEngine mEngine;
  private Mode mMode = Mode.Paused;

  private Bitmap mBall48;
  private Bitmap mBall24;
  private float mBallRadius48;
  private float mBallRadius24;

  // The ball currently held by the user
  private Puck mPressedBall;
  // The pressed ball's last known x coordinate
  private float mLastPressedX;
  // The pressed ball's last known y coordinate'
  private float mLastPressedY;

  public AirHockeyView(Context context, AttributeSet attrs) {
    super(context, attrs);

    setBackgroundColor(Color.WHITE);

    mPaint = new Paint();
    mPaint.setAntiAlias(true);
    mPaint.setStrokeWidth(2);
    mPaint.setColor(Color.BLACK);

    // so we can see the back key
    setFocusableInTouchMode(true);

    // retrieve and decode bitmaps
    Resources res = context.getResources();
    mBall48 = BitmapFactory.decodeResource(res, R.drawable.blue_ball_48);
    mBall24 = BitmapFactory.decodeResource(res, R.drawable.blue_ball_24);
    mBallRadius48 = ((float) mBall48.getWidth()) / 2f;
    mBallRadius24 = ((float) mBall24.getWidth()) / 2f;
  }

  /**
   * Set the callback that will be notified of events related to the ball
   * engine. Callback events are received to the {@link AirHockeyActivity}.
   * Called by {@link AirHockeyActivity}.
   */
  public void setCallback(BallEngineCallBack callback) {
    mCallback = callback;
  }

  @Override
  protected void onSizeChanged(int i, int i1, int i2, int i3) {
    super.onSizeChanged(i, i1, i2, i3);

    int minX = BORDER_WIDTH;
    int maxX = getWidth() - BORDER_WIDTH;
    int minY = BORDER_WIDTH;
    int maxY = getHeight() - BORDER_WIDTH;

    mEngine = new PuckEngine(minX, maxX, minY, maxY, mBallRadius48);
    mEngine.setCallBack(this);

    // note: this should never be null
    if (mCallback != null) {
      mCallback.onEngineReady(mEngine);
    }
  }

  /**
   * Returns the {@link PuckEngine} associated with the game.
   */
  public PuckEngine getEngine() {
    return mEngine;
  }

  /**
   * Returns the current mode of operation.
   */
  public Mode getMode() {
    return mMode;
  }

  /**
   * Set the mode of operation.
   */
  public void setMode(Mode mode) {
    mMode = mode;
    if (mMode == Mode.Bouncing && mEngine != null) {
      // when starting up again, the engine needs to know what 'now' is.
      final long now = SystemClock.elapsedRealtime();
      mEngine.setNow(now);
      invalidate();
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent motionEvent) {
    if (mMode == Mode.PausedByUser) {
      // touching unpauses when the game was paused by the user.
      setMode(Mode.Bouncing);
      return true;
    } else if (mMode == Mode.Paused) {
      return false;
    }

    long now = SystemClock.elapsedRealtime();
    float x = motionEvent.getX();
    float y = motionEvent.getY();

    switch (motionEvent.getAction()) {
      case MotionEvent.ACTION_DOWN:
        Puck ball = removePuckInBounds(x, y);
        if (ball != null) {
          mPressedBall = ball;
          mPressedBall.setCoords(x, y);
          mLastPressedX = x;
          mLastPressedY = y;
          mPressedBall.setNow(now);
          ball.setPressed(true);
          invalidate();
        }
        return true;
      case MotionEvent.ACTION_MOVE:
        if (mPressedBall != null) {
          mLastPressedX = mPressedBall.getX();
          mLastPressedY = mPressedBall.getY();
          mPressedBall.setCoords(x, y);
          mPressedBall.setNow(now);
          invalidate();
        }
        return true;
      case MotionEvent.ACTION_UP:
        if (mPressedBall != null) {
          double dx = x - mLastPressedX;
          double dy = y - mLastPressedY;
          mPressedBall.setDirection(Math.atan2(dy, dx));
          mPressedBall.setCoords(x, y);
          PuckRegion region = mEngine.getRegion();
          PuckRegion goalRegion = mEngine.getGoalRegion();
          if (goalRegion.isPointWithin(x, y)) {
            goalRegion.addBall(mPressedBall);
            mPressedBall.setRegion(goalRegion);
            mPressedBall.setRadiusPixels(mBallRadius24);

            // TODO: notify that a goal was scored? Can a player manipulate this
            // and simply drag all balls into their goal to prevent others from
            // scoring??
            onGoalScored(now, mPressedBall);
          } else {
            region.addBall(mPressedBall);
            mPressedBall.setRegion(region);
            mPressedBall.setRadiusPixels(mBallRadius48);
          }
          mPressedBall.setPressed(false);
          mPressedBall.setPixelsPerSecond(Puck.MAX_SPEED);
          mPressedBall.setNow(now);
          mPressedBall = null;
          invalidate();
        }
        return true;
    }
    return super.onTouchEvent(motionEvent);
  }

  private Puck removePuckInBounds(float x, float y) {
    // Make it a little easier for the user to catch the ball.
    final double radiusBounds48 = 2.0 * mBallRadius48;

    List<Puck> regionBalls = mEngine.getRegion().getBalls();
    Iterator<Puck> regionIter = regionBalls.iterator();
    while (regionIter.hasNext()) {
      // Calculate the distance from (x,y) to the puck's origin
      final Puck puck = regionIter.next();
      double distance = getDistance(puck.getX(), puck.getY(), x, y);
      if (distance <= radiusBounds48) {
        // Then the ball is in bounds
        regionIter.remove();
        return puck;
      }
    }

    final double radiusBounds24 = 2.5 * mBallRadius24;
    List<Puck> goalBalls = mEngine.getGoalRegion().getBalls();
    Iterator<Puck> goalIter = goalBalls.iterator();
    while (goalIter.hasNext()) {
      // Calculate the distance from (x,y) to the puck's origin
      final Puck puck = goalIter.next();
      double distance = getDistance(puck.getX(), puck.getY(), x, y);
      if (distance <= radiusBounds24) {
        // Then the puck is in bounds
        goalIter.remove();
        return puck;
      }
    }

    // No puck in the given bounds
    return null;
  }

  /**
   * Returns the distance between points (x1,y1) and (x2,y2).
   */
  private static double getDistance(float x1, float y1, float x2, float y2) {
    return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (mMode == Mode.Bouncing) {
      // Update all regions/balls with the latest notion of 'now'
      long now = SystemClock.elapsedRealtime();
      mEngine.update(now);
    }

    final PuckRegion region = mEngine.getRegion();
    final PuckRegion goal = mEngine.getGoalRegion();

    drawRegion(canvas, region);
    drawGoal(canvas, goal);
    drawBalls(canvas, region, goal);
    if (mPressedBall != null) {
      canvas.drawBitmap(mBall48, mPressedBall.getX() - mBallRadius48,
          mPressedBall.getY() - mBallRadius48, mPaint);
    }

    if (mMode == Mode.PausedByUser) {
      drawPausedText(canvas);
    } else if (mMode == Mode.Bouncing) {
      // Re-draw the screen (results in a subsequent call to 'onDraw(Canvas)')
      invalidate();
    }
  }

  /**
   * Pain the text instructing the user how to unpause the game.
   */
  private void drawPausedText(Canvas canvas) {
    mPaint.setColor(Color.BLACK);
    mPaint.setAntiAlias(true);
    mPaint.setTextAlign(Paint.Align.CENTER);
    mPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
        20, getResources().getDisplayMetrics()));
    String instructions = getContext().getString(R.string.unpause_instructions);
    canvas.drawText(instructions, getWidth() / 2, getHeight() / 2, mPaint);
    mPaint.setAntiAlias(false);
  }

  private RectF mRectF = new RectF();

  /**
   * Draw the outer region.
   */
  private void drawRegion(Canvas canvas, PuckRegion region) {
    // Draw the region
    mPaint.setColor(Color.LTGRAY);
    mRectF.set(region.getLeft(), region.getTop(), region.getRight(),
        region.getBottom());
    canvas.drawRect(mRectF, mPaint);

    // Draw an outline
    mPaint.setStyle(Paint.Style.STROKE);
    mPaint.setColor(Color.WHITE);
    mPaint.setStrokeWidth(0);
    canvas.drawRect(mRectF, mPaint);
    mPaint.setStyle(Paint.Style.FILL);
  }

  /**
   * Draw the goal region.
   */
  private void drawGoal(Canvas canvas, PuckRegion region) {
    // draw fill rect to offset against background
    mPaint.setColor(Color.GRAY);
    mRectF.set(region.getLeft(), region.getTop(), region.getRight(),
        region.getBottom());
    canvas.drawRect(mRectF, mPaint);

    // draw an outline
    mPaint.setStyle(Paint.Style.STROKE);
    mPaint.setColor(Color.WHITE);
    mPaint.setStrokeWidth(10);
    canvas.drawRect(mRectF, mPaint);
    mPaint.setStyle(Paint.Style.FILL);
  }

  /**
   * Draw all of the balls to the screen.
   */
  private void drawBalls(Canvas canvas, PuckRegion region, PuckRegion goal) {
    mPaint.setAntiAlias(true);
    List<Puck> regionBalls = region.getBalls();
    for (int i = 0; i < regionBalls.size(); i++) {
      final Puck ball = regionBalls.get(i);
      canvas.drawBitmap(mBall48, ball.getX() - mBallRadius48, ball.getY()
          - mBallRadius48, mPaint);
    }

    List<Puck> goalBalls = goal.getBalls();
    for (int i = 0; i < goalBalls.size(); i++) {
      final Puck ball = goalBalls.get(i);
      canvas.drawBitmap(mBall24, ball.getX() - mBallRadius24, ball.getY()
          - mBallRadius24, mPaint);
    }
    mPaint.setAntiAlias(true);
  }

  /** {@inheritDoc} */
  @Override
  public void onBallHitsBall(Puck b1, Puck b2) {
    // Don't need this after all :)
  }

  /** {@inheritDoc} */
  @Override
  public void onBallExitsRegion(long when, Puck ball, int exitEdge) {
    mCallback.onBallExitsRegion(when, ball, exitEdge);
  }

  /** {@inheritDoc} */
  @Override
  public void onGoalScored(long when, Puck ball) {
    if (DEBUG)
      Log.v(TAG, "A ball has entered the goal region: " + ball.toString());
    ball.setRadiusPixels(mBallRadius24);
  }

  /**
   * Callback notifying of events related to the ball engine.
   */
  public static interface BallEngineCallBack {

    /**
     * The engine has its dimensions and is ready to go.
     */
    void onEngineReady(PuckEngine ballEngine);

    void onBallExitsRegion(long when, Puck ball, int exitEdge);
  }

  /**
   * Keeps track of the mode of this view.
   */
  enum Mode {

    /**
     * The balls are bouncing around.
     */
    Bouncing,

    /**
     * The animation has stopped and the balls won't move around. The user may
     * not unpause it; this is used to temporarily stop games between levels, or
     * when the game is over and the activity places a dialog up.
     */
    Paused,

    /**
     * Same as {@link #Paused}, but paints the word 'touch to unpause' on the
     * screen, so the user knows he/she can unpause the game.
     */
    PausedByUser,

    Preview,
  }

  public Puck randomIncomingPuck(int puckId) {
    float x, y;
    double angle;
    float pps = BALL_START_SPEED;
    float radius = mBallRadius48;

    int edge = (int) (Math.random() * 4);
    switch (edge) {
      case PuckRegion.LEFT: // 0
        x = -mBallRadius48 + 1;
        y = (float) (getHeight() / 2);
        angle = 0;
        break;
      case PuckRegion.TOP: // 1
        x = (float) (getWidth() / 2);
        y = -mBallRadius48 + 1;
        angle = 1.5 * Math.PI;
        break;
      case PuckRegion.BOTTOM: // 2
        x = (float) (getWidth() / 2);
        y = getHeight() + mBallRadius48 - 1;
        angle = 0.5 * Math.PI;
        break;
      default: // 3
        x = getWidth() + mBallRadius48 - 1;
        y = (float) (getHeight() / 2);
        angle = Math.PI;
        break;
    }

    return new Puck.Builder().setX(x).setY(y).setAngle(angle)
        .setRadiusPixels(radius).setPixelsPerSecond(pps).setId(puckId)
        .setNow(SystemClock.elapsedRealtime()).create();
  }
}
