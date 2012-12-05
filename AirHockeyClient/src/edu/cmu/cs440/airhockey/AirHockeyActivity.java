package edu.cmu.cs440.airhockey;

import static edu.cmu.cs440.airhockey.Utils.LOGV;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.v4.app.FragmentActivity;
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

  private AirHockeyView mBallsView;
  private LoginDialogFragment mDialog;
  private LoginTask mLoginTask;
  private NetworkThread mNetworkThread;
  private String mUser;
  private String mHost;
  private int mPort;
  private Puck.Color mPuckColor;

  private static final long UHOH_VIBRATE_MILLIS = 50;
  private Vibrator mVibrator;

  public static final int STATE_NONE = 0;
  public static final int STATE_CONNECTED = 1;
  public static final int STATE_RETRYING = 2;
  public static final int MESSAGE_READ = 3;
  public static final int RETRY_SUCCESS = 4;
  private int mState;

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
    mState = STATE_NONE;
  }

  /** {@inheritDoc} */
  @Override
  public void onEngineReady(PuckEngine ballEngine) {
    LOGV(TAG, "onEngineReady() called");
    ballEngine.reset();
    showLoginDialog();
  }

  private void showLoginDialog() {
    LOGV(TAG, "showLoginDialog() called");
    mDialog = LoginDialogFragment.newInstance();
    mDialog.show(getSupportFragmentManager(), "dialog");
  }

  /** {@inheritDoc} */
  @Override
  public void onNewGame(String user, String host, String port, Puck.Color color) {
    if (Utils.isOnline(this)) {
      mUser = user;
      mHost = host;
      mPort = Integer.parseInt(port);
      mPuckColor = color;
      mBallsView.setDefaultPuckColor(mPuckColor);
      mBallsView.setUser(mUser);
      mLoginTask = new LoginTask(this, this, user);
      mLoginTask.execute(mUser, mHost, port);
    } else {
      Toast.makeText(this, R.string.no_connection_found, Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onLoginComplete(Socket socket, BufferedReader in, PrintWriter out) {
    PuckRegion region = mBallsView.getEngine().getRegion();
    mNetworkThread = new NetworkThread(region, mHandler, socket, in, out, mUser, mHost, mPort);
    mNetworkThread.start();
    mDialog.dismiss();
    startGame();
  }

  private void startGame() {
    LOGV(TAG, "Client game has started...");
    mBallsView.getEngine().reset();
    mBallsView.setMode(AirHockeyView.Mode.Bouncing);
    mDialog.dismiss();
    mState = STATE_CONNECTED;
  }

  private void resetGame() {
    LOGV(TAG, "Client game has ended...");

    mState = STATE_NONE;

    mBallsView.setMode(AirHockeyView.Mode.Paused);
    mBallsView.getEngine().reset();
    mBallsView.invalidate();

    mHandler.removeMessages(MESSAGE_READ);
    mHandler.removeMessages(STATE_NONE);
    mHandler.removeMessages(STATE_RETRYING);

    if (mNetworkThread != null) {
      mNetworkThread.close();
      mNetworkThread = null;
    }

    if (mDialog != null) {
      mDialog.dismiss();
    }

    if (mRetryProgress != null) {
      mRetryProgress.dismiss();
    }

    LOGV(TAG, "calling showLoginDialog() from resetGame");
    showLoginDialog();

    Toast.makeText(AirHockeyActivity.this, R.string.connection_lost, Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onBallExitsRegion(long when, final Puck ball, final int exitEdge) {
    // if (DEBUG) {
    // final String edge;
    // if (exitEdge == PuckRegion.BOTTOM)
    // edge = "bottom";
    // else if (exitEdge == PuckRegion.TOP)
    // edge = "top";
    // else if (exitEdge == PuckRegion.LEFT)
    // edge = "left";
    // else if (exitEdge == PuckRegion.RIGHT)
    // edge = "right";
    // else
    // edge = "UNDEFINED!";
    // Log.v(TAG, "Ball (" + ball.toString() + " has left region (" + edge +
    // ")");
    // }
    if (mNetworkThread != null) {
      final PuckRegion region = mBallsView.getEngine().getRegion();
      final String writeMsg = Utils.toString(region, ball, exitEdge);
      mExecutor.execute(new WriteRunnable(writeMsg));
    }
  }

  /**
   * Use a single thread executor instead of creating a new thread for each
   * individual write message.
   */
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
  public void uhoh(String msg, final int numBalls) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    if (msg.equals("Uh oh...")) {
      mVibrator.vibrate(new long[] { 0l, UHOH_VIBRATE_MILLIS, 50l, UHOH_VIBRATE_MILLIS, 50l,
          UHOH_VIBRATE_MILLIS }, -1);
      mExecutor.execute(new Runnable() {
        @Override
        public void run() {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
          }
          for (int i = 0; i < numBalls; i++) {
            mExecutor.execute(new WriteRunnable("P," + mUser));
          }
        }
      });
    }
  }

  private ProgressDialog mRetryProgress;
  private final Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case MESSAGE_READ:
          LOGV(TAG, "MESSAGE_READ received.");
          switch (msg.arg1) {
            case NetworkThread.IP:
              LOGV(TAG, "IP type message read.");
              Puck incomingBall = (Puck) msg.obj;
              incomingBall.setRegion(mBallsView.getEngine().getRegion());
              incomingBall.setNow(SystemClock.elapsedRealtime());
              mBallsView.getEngine().addIncomingPuck(incomingBall);
              break;
            case NetworkThread.POK:
              LOGV(TAG, "POK type message read.");
              int enterEdge = (int) (Math.random() * 4);
              long now = SystemClock.elapsedRealtime();
              Puck newBall = mBallsView.randomIncomingPuck(now, (Integer) msg.obj, enterEdge);
              newBall.setColor(mPuckColor);
              newBall.setRegion(mBallsView.getEngine().getRegion());
              mBallsView.getEngine().addIncomingPuck(newBall);
              break;
          }
          break;
        case STATE_NONE:
          LOGV(TAG, "STATE_NONE received.");
          if (mState != STATE_NONE) {
            mState = STATE_NONE;
            Toast.makeText(AirHockeyActivity.this, R.string.connection_lost, Toast.LENGTH_SHORT)
                .show();
            resetGame();
          }
          break;
        case STATE_RETRYING:
          LOGV(TAG, "STATE_RETRYING received.");
          mBallsView.setMode(Mode.Paused);
          Context ctx = AirHockeyActivity.this;
          Resources res = ctx.getResources();
          mRetryProgress = ProgressDialog.show(ctx, "", res.getString(R.string.progress_retry),
              true);
          mRetryProgress.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
              mRetryProgress.dismiss();
              AirHockeyActivity.this.resetGame();
            }
          });
          mRetryProgress.setCanceledOnTouchOutside(false);
          mRetryProgress.setCancelable(true);
          break;
        case RETRY_SUCCESS:
          if (mRetryProgress != null) {
            mRetryProgress.dismiss();
          }
          mBallsView.setMode(Mode.Bouncing);
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
      // The game is still playing... user just wants to disconnect
      resetGame();
    } else {
      // Warn the user before disconnecting...
      mLastBackPress = SystemClock.elapsedRealtime();
      Toast.makeText(this, R.string.press_back_to_exit_warning, Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  protected void onDestroy() {
    if (mNetworkThread != null) {
      mNetworkThread.close();
      mNetworkThread = null;
    }
    if (mExecutor != null) {
      mExecutor.shutdown();
      mExecutor = null;
    }
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    // Remove the call to super(). Bug on API Level > 11.
  }
}
