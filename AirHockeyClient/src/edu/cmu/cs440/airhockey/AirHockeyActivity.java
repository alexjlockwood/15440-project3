package edu.cmu.cs440.airhockey;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;
import edu.cmu.cs440.airhockey.AirHockeyView.Mode;
import edu.cmu.cs440.airhockey.LoginDialogFragment.NewGameCallback;
import edu.cmu.cs440.airhockey.LoginTask.LoginCallback;

/**
 * The activity for the game. Listens for callbacks from the game engine, and
 * responds appropriately.
 */
public class AirHockeyActivity extends FragmentActivity implements
    AirHockeyView.BallEngineCallBack, NewGameCallback, LoginCallback {

  private static final String TAG = "AirHockeyTag";
  private static final boolean DEBUG = true;

  private static final int PREVIEW_NUM_BALLS = 0;
  private static final int NEW_GAME_NUM_BALLS = 0;
  private AirHockeyView mBallsView;

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
  }

  /** {@inheritDoc} */
  @Override
  public void onEngineReady(BallEngine ballEngine) {
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
    mBallsView.setMode(AirHockeyView.Mode.PausedByUser);
  }

  @Override
  public void onBackPressed() {
    if (mBallsView.getMode() == Mode.PausedByUser) {
      super.onBackPressed();
    } else if (mBallsView.getMode() == Mode.Bouncing) {
      mBallsView.setMode(Mode.PausedByUser);
      Toast.makeText(this, R.string.press_back_to_exit_warning,
          Toast.LENGTH_SHORT).show();
    }
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
    mUser = user;
    mHost = host;
    mPort = port;
    mProgress = ProgressDialog.show(this, "", "Connecting...", true);
    mLoginTask = new LoginTask(this);
    mLoginTask.execute(mUser, mHost, mPort);
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
      BallRegion region = mBallsView.getEngine().getRegion();
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

  private void onConnectionLost() {
    Toast.makeText(this, "Connection lost.", Toast.LENGTH_SHORT).show();
    resetGame();
  }

  private void resetGame() {
    if (DEBUG)
      Log.v(TAG, "Client game has ended...");
    mBallsView.getEngine().reset(SystemClock.elapsedRealtime(),
        PREVIEW_NUM_BALLS);
    mBallsView.setMode(AirHockeyView.Mode.Bouncing);
    if (mProgress != null && mProgress.isShowing()) {
      mProgress.dismiss();
    }
    showLoginDialog();
  }

  @Override
  public void onBallExitsRegion(long when, final Ball ball, final int exitEdge) {
    if (DEBUG) {
      final String edge;
      if (exitEdge == BallRegion.BOTTOM)
        edge = "bottom";
      else if (exitEdge == BallRegion.TOP)
        edge = "top";
      else if (exitEdge == BallRegion.LEFT)
        edge = "left";
      else if (exitEdge == BallRegion.RIGHT)
        edge = "right";
      else
        edge = "UNDEFINED!";
      Log.v(TAG, "Ball (" + ball.toString() + " has left region (" + edge + ")");
    }
    if (mNetworkThread != null) {
      final BallRegion region = mBallsView.getEngine().getRegion();
      final byte[] data = IOUtils.toBytes(region, ball, exitEdge);
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
          switch (msg.arg1) {
            case NetworkThread.IP:
              Ball incomingBall = (Ball) msg.obj;
              incomingBall.setRegion(mBallsView.getEngine().getRegion());
              mBallsView.getEngine().addIncomingPuck(incomingBall);
              break;
            case NetworkThread.POK:
              Ball newBall = mBallsView.randomIncomingPuck((Integer) msg.obj);
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
          mState = STATE_NONE;
          onConnectionLost();
          break;
        case STATE_CONNECTED:
          mState = STATE_CONNECTED;
          startGame();
          break;
        case MESSAGE_TOAST:
          break;
      }
    }
  };
}
