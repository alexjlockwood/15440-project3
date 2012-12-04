package edu.cmu.cs440.airhockey;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import edu.cmu.cs440.airhockey.AirHockeyView.Mode;
import edu.cmu.cs440.airhockey.LoginDialogFragment.NewGameCallback;
import edu.cmu.cs440.airhockey.LoginTask.LoginCallback;

/**
 * The activity for the game. Listens for callbacks from the game engine, and
 * responds appropriately.
 */
@SuppressLint("HandlerLeak")
public class AirHockeyActivity extends FragmentActivity implements
    AirHockeyView.BallEngineCallBack, NewGameCallback, LoginCallback {

  private static final String TAG = "15440_AirHockeyActivity";
  private static final boolean DEBUG = true;

  private static final int PREVIEW_NUM_BALLS = 0;
  private static final int NEW_GAME_NUM_BALLS = 0;
  private static final long UHOH_VIBRATE_MILLIS = 50;

  private AirHockeyView mBallsView;
  private LoginDialogFragment mDialog;
  private LoginTask mLoginTask;
  private NetworkThread mNetworkThread;
  private String mUser;
  private String mHost;
  private String mPort;
  private Puck.Color mPuckColor;

  private Vibrator mVibrator;

  public static final int STATE_NONE = 0;
  public static final int STATE_CONNECTED = 1;
  public static final int MESSAGE_READ = 2;
  public static final int MESSAGE_WRITE = 3;
  public static final int MESSAGE_TOAST = 4;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Turn off the title bar
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.main);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    mBallsView = (AirHockeyView) findViewById(R.id.ballsView);
    mBallsView.setCallback(this);
    mBallsView.setMode(Mode.Paused);
    mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    mBallsView.setVibrator(mVibrator);
  }

  /** {@inheritDoc} */
  @Override
  public void onEngineReady(PuckEngine ballEngine) {
    ballEngine.reset(SystemClock.elapsedRealtime(), PREVIEW_NUM_BALLS);
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
  public void onNewGame(String user, String host, String port, Puck.Color color) {
    if (Utils.isOnline(this)) {
      mUser = user;
      mHost = host;
      mPort = port;
      mPuckColor = color;
      mBallsView.setDefaultPuckColor(mPuckColor);
      mBallsView.setUser(mUser);
      mLoginTask = new LoginTask(this, this, user);
      mLoginTask.execute(mUser, mHost, mPort);
    } else {
      Toast.makeText(this, R.string.no_connection_found, Toast.LENGTH_SHORT)
          .show();
    }
  }

  @Override
  public void onLoginComplete(Socket socket, BufferedReader in,
      PrintWriter out, int puckId) {
    PuckRegion region = mBallsView.getEngine().getRegion();
    mNetworkThread = new NetworkThread(region, mHandler, socket, in, out);
    mNetworkThread.start();
    mDialog.dismiss();
    startGame();
    int enterEdge = (int) (Math.random() * 4);
    long now = SystemClock.elapsedRealtime();
    Puck newBall = mBallsView.randomIncomingPuck(now, puckId, enterEdge);
    newBall.setColor(mPuckColor);
    newBall.setRegion(mBallsView.getEngine().getRegion());
    mBallsView.getEngine().addIncomingPuck(newBall);
  }

  // TODO: handle server crashes! Don't reset until we know for sure we can't
  // reconnect with another server!

  private void startGame() {
    if (DEBUG)
      Log.v(TAG, "Client game starting...");
    mBallsView.getEngine().reset(SystemClock.elapsedRealtime(),
        NEW_GAME_NUM_BALLS);
    mBallsView.setMode(AirHockeyView.Mode.Bouncing);
    mDialog.dismiss();
  }

  private void resetGame() {
    if (DEBUG)
      Log.v(TAG, "Client game has ended...");
    mBallsView.setMode(AirHockeyView.Mode.Paused);
    showLoginDialog();
    if (mNetworkThread != null) {
      mNetworkThread.close();
      mNetworkThread = null;
    }
    mBallsView.getEngine().reset(SystemClock.elapsedRealtime(),
        PREVIEW_NUM_BALLS);
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
      final String writeMsg = Utils.toString(region, ball, exitEdge);
      mExecutor.execute(new WriteRunnable(writeMsg));
    }
  }

  // Use a single thread executor instead of creating a new thread for each
  // individual write message.
  private ExecutorService mExecutor = Executors.newSingleThreadExecutor();

  private class WriteRunnable implements Runnable {
    private String mMsg;

    public WriteRunnable(String msg) {
      mMsg = msg;
    }

    @Override
    public void run() {
      mNetworkThread.write(mMsg);
    }
  }

  @Override
  public void uhoh(String msg) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    if (msg.equals("Uh oh...")) {
      mVibrator.vibrate(new long[] { 0l, UHOH_VIBRATE_MILLIS, 50l,
          UHOH_VIBRATE_MILLIS, 50l, UHOH_VIBRATE_MILLIS }, -1);
      mExecutor.execute(new Runnable() {
        @Override
        public void run() {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
          }
          for (int i = 0; i < 10; i++) {
            mExecutor.execute(new WriteRunnable("P," + mUser));
          }
        }
      });
    }
  }

  private final Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case MESSAGE_READ:
          if (DEBUG)
            Log.v(TAG, "MESSAGE_READ received.");
          switch (msg.arg1) {
            case NetworkThread.IP:
              if (DEBUG) {
                Log.v(TAG, "IP type message read.");
              }
              Puck incomingBall = (Puck) msg.obj;
              incomingBall.setRegion(mBallsView.getEngine().getRegion());
              incomingBall.setNow(SystemClock.elapsedRealtime());
              mBallsView.getEngine().addIncomingPuck(incomingBall);
              break;
            case NetworkThread.POK:
              if (DEBUG) {
                Log.v(TAG, "POK type message read.");
              }
              int enterEdge = (int) (Math.random() * 4);
              long now = SystemClock.elapsedRealtime();
              Puck newBall = mBallsView.randomIncomingPuck(now,
                  (Integer) msg.obj, enterEdge);
              newBall.setColor(mPuckColor);
              newBall.setRegion(mBallsView.getEngine().getRegion());
              mBallsView.getEngine().addIncomingPuck(newBall);
              if (DEBUG) {
                Log.v(TAG, "Adding random incoming puck: " + newBall.toString());
              }
              break;
            case NetworkThread.PNO:
              if (DEBUG) {
                Log.v(TAG, "PNO type message read.");
              }
              break;
            case NetworkThread.XOK:
              if (DEBUG) {
                Log.v(TAG, "XOK type message read.");
              }
              break;
            case NetworkThread.XNO:
              if (DEBUG) {
                Log.v(TAG, "XNO type message read.");
              }
              break;
          }
          break;
        case MESSAGE_WRITE:
          break;
        case STATE_NONE:
          if (DEBUG)
            Log.v(TAG, "STATE_NONE received.");
          Toast.makeText(AirHockeyActivity.this, R.string.connection_lost,
              Toast.LENGTH_SHORT).show();
          resetGame();
          break;
        case STATE_CONNECTED:
          if (DEBUG)
            Log.v(TAG, "STATE_CONNECTED received.");
          break;
        case MESSAGE_TOAST:
          if (DEBUG)
            Log.v(TAG, "STATE_TOAST received.");
          break;
      }
    }
  };

  private static final int BACK_EXIT_WAIT = 2000;
  private long mLastBackPress = -1;

  @Override
  public void onBackPressed() {
    if (mBallsView.getMode() == Mode.Paused) {
      // If the dialog is showing
      super.onBackPressed();
    } else if (SystemClock.elapsedRealtime() - mLastBackPress < BACK_EXIT_WAIT) {
      resetGame();
    } else {
      mLastBackPress = SystemClock.elapsedRealtime();
      Toast.makeText(this, R.string.press_back_to_exit_warning,
          Toast.LENGTH_SHORT).show();
    }
  }
}
