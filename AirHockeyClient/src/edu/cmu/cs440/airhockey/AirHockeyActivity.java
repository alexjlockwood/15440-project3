package edu.cmu.cs440.airhockey;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;
import edu.cmu.cs440.airhockey.LoginDialogFragment.NewGameCallback;
import edu.cmu.cs440.airhockey.LoginTask.LoginCallback;

/**
 * The activity for the game. Listens for callbacks from the game engine, and
 * responds appropriately.
 */
public class AirHockeyActivity extends FragmentActivity implements
    AirHockeyView.BallEngineCallBack, NewGameCallback, LoginCallback {

  private static final String TAG = "15440_AirHockeyActivity";
  private static final boolean DEBUG = true;

  private static final int PREVIEW_NUM_BALLS = 0;
  private static final int NEW_GAME_NUM_BALLS = 0;
  private static final int COLLISION_VIBRATE_MILLIS = 50;

  private AirHockeyView mBallsView;

  private Vibrator mVibrator;

  private LoginDialogFragment mDialog;
  private ProgressDialog mProgress;
  private LoginTask mLoginTask;
  private NetworkThread mNetworkThread;

  private String mUser;
  private String mHost;
  private String mPort;

  public static final int STATE_NONE = 0;
  public static final int STATE_CONNECTED = 1;
  public static final int MESSAGE_READ = 2;
  public static final int MESSAGE_WRITE = 3;
  public static final int MESSAGE_TOAST = 4;

  // The client's current connection state
  private int mState = STATE_NONE;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Turn off the title bar
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.main);
    mBallsView = (AirHockeyView) findViewById(R.id.ballsView);
    mBallsView.setCallback(this);

    mState = STATE_NONE;
    mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
  }

  /** {@inheritDoc} */
  @Override
  public void onEngineReady(PuckEngine ballEngine) {
    // display 10 balls bouncing around for visual effect
    ballEngine.reset(SystemClock.elapsedRealtime(), PREVIEW_NUM_BALLS);
    mBallsView.setMode(AirHockeyView.Mode.Bouncing);
    showLoginDialog();
  }

  private void showLoginDialog() {
    // Create the fragment and show it as a dialog.
    mDialog = LoginDialogFragment.newInstance();
    mDialog.show(getSupportFragmentManager(), "dialog");
  }

  @Override
  protected void onPause() {
    super.onPause();
    // mBallsView.setMode(AirHockeyView.Mode.PausedByUser);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    // No call for super(). Bug on API Level > 11.
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (mNetworkThread != null) {
      mNetworkThread.close();
      mNetworkThread = null;
    }
    if (mExecutor != null) {
      mExecutor.shutdown();
      mExecutor = null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void onNewGame(String user, String host, String port) {
    if (Utils.isOnline(this)) {
      mUser = user;
      mHost = host;
      mPort = port;
      mProgress = ProgressDialog.show(this, "", "Connecting...", true);
      mProgress.setOnCancelListener(new DialogInterface.OnCancelListener() {
        @Override
        public void onCancel(DialogInterface dialog) {
          if (mLoginTask != null) {
            mLoginTask.cancel(true);
          }
        }
      });
      mProgress.setCanceledOnTouchOutside(false);
      mProgress.setCancelable(true);
      mLoginTask = new LoginTask(this);
      mLoginTask.execute(mUser, mHost, mPort);
    } else {
      Toast.makeText(this, "No internet connection found.", Toast.LENGTH_SHORT)
          .show();
    }
  }

  @Override
  public void onLoginComplete(String result) {
    if (result == null) {
      if (DEBUG)
        Log.v(TAG, "Client did not receive server response!");
      mProgress.dismiss();
      Toast.makeText(this, "Could not connect! Please try again.",
          Toast.LENGTH_SHORT).show();
      return;
    }

    int index = result.indexOf(",");
    if (result.equals("JNO")) {
      if (DEBUG)
        Log.v(TAG, "Client failed to join!");
      mProgress.dismiss();
      Toast.makeText(this, "Could not connect! Please try again.",
          Toast.LENGTH_SHORT).show();
    }

    else if (result.equals("DUP")) {
      mProgress.dismiss();
      if (DEBUG)
        Log.v(TAG, "Client failed to join!");
      Toast.makeText(this, "User already connected!", Toast.LENGTH_SHORT)
          .show();
    }

    else if (index >= 0 && result.substring(0, index).equals("JOK")) {
      if (DEBUG)
        Log.v(TAG, "Client joined successfully!");
      mProgress.setMessage("Setting up game environment...");
      mProgress.setCancelable(false);
      PuckRegion region = mBallsView.getEngine().getRegion();
      mNetworkThread = new NetworkThread(region, mHandler, mHost, mPort, mUser);
      mNetworkThread.start();
    }
  }

  private void startGame() {
    if (DEBUG)
      Log.v(TAG, "Client game starting...");
    mBallsView.getEngine().reset(SystemClock.elapsedRealtime(),
        NEW_GAME_NUM_BALLS);
    mBallsView.setMode(AirHockeyView.Mode.Bouncing);
    mProgress.dismiss();
    mDialog.dismiss();
  }

  private void resetGame() {
    if (DEBUG)
      Log.v(TAG, "Client game has ended...");
    mBallsView.getEngine().reset(SystemClock.elapsedRealtime(),
        PREVIEW_NUM_BALLS);
    mBallsView.setMode(AirHockeyView.Mode.Bouncing);
    if (mProgress != null) {
      mProgress.dismiss();
    }
    showLoginDialog();
  }

  @Override
  public void onBallExitsRegion(long when, final Puck ball, final int exitEdge) {
    if (DEBUG) {
      final String edge;
      if (exitEdge == PuckRegion.BOTTOM)
        edge = "bottom";
      else if (exitEdge == PuckRegion.TOP)
        edge = "top";
      else if (exitEdge == PuckRegion.LEFT)
        edge = "left";
      else if (exitEdge == PuckRegion.RIGHT)
        edge = "right";
      else
        edge = "UNDEFINED!";
      Log.v(TAG, "Ball (" + ball.toString() + " has left region (" + edge + ")");
    }
    if (mNetworkThread != null) {
      final PuckRegion region = mBallsView.getEngine().getRegion();
      final byte[] data = Utils.toBytes(region, ball, exitEdge);
      mExecutor.execute(new Runnable() {
        @Override
        public void run() {
          mNetworkThread.write(data);
        }
      });
    }
  }

  // Use a single thread executor instead of creating a new thread for each
  // individual write message.
  private ExecutorService mExecutor = Executors.newSingleThreadExecutor();

  // TODO: when a read/write fails, make sure that it is somehow brought to
  // the front of the handler's message queue so that the client doesn't do
  // a bunch of unnecessary work before realizing the connection has closed.
  private final Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case MESSAGE_READ:
          if (DEBUG)
            Log.v(TAG, "MESSAGE_READ received.");
          switch (msg.arg1) {
            case NetworkThread.IP:
              Puck incomingBall = (Puck) msg.obj;
              incomingBall.setRegion(mBallsView.getEngine().getRegion());
              incomingBall.setNow(SystemClock.elapsedRealtime());
              mBallsView.getEngine().addIncomingPuck(incomingBall);
              break;
            case NetworkThread.POK:
              Puck newBall = mBallsView.randomIncomingPuck((Integer) msg.obj);
              newBall.setRegion(mBallsView.getEngine().getRegion());
              mBallsView.getEngine().addIncomingPuck(newBall);
              if (DEBUG) {
                Log.v(TAG, "Adding random incoming puck: " + newBall.toString());
              }
              break;
          }
          break;
        case MESSAGE_WRITE:
          break;
        case STATE_NONE:
          if (DEBUG)
            Log.v(TAG, "STATE_NONE received.");
          mState = STATE_NONE;
          Toast.makeText(AirHockeyActivity.this, "Connection lost.",
              Toast.LENGTH_SHORT).show();
          resetGame();
          break;
        case STATE_CONNECTED:
          if (DEBUG)
            Log.v(TAG, "STATE_CONNECTED received.");
          mState = STATE_CONNECTED;
          startGame();
          break;
        case MESSAGE_TOAST:
          if (DEBUG)
            Log.v(TAG, "STATE_TOAST received.");
          // mVibrator.vibrate(50l);
          break;
      }
    }
  };

  private static final int BACK_EXIT_WAIT = 2000;
  private long mLastBackPress = -1;

  @Override
  public void onBackPressed() {
    if (SystemClock.elapsedRealtime() - mLastBackPress < BACK_EXIT_WAIT) {
      super.onBackPressed();
    } else {
      mLastBackPress = SystemClock.elapsedRealtime();
      Toast.makeText(this, R.string.press_back_to_exit_warning,
          Toast.LENGTH_SHORT).show();
    }
  }
}
