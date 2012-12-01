package edu.cmu.cs440.airhockey;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class LoginDialogFragment extends DialogFragment implements
    View.OnClickListener {

  private static final String TAG = "15440_LoginDialogFragment";
  private static final boolean DEBUG = true;

  private Activity mActivity;
  private NewGameCallback mCallback;
  private EditText mUsername;
  private EditText mHostname;
  private EditText mPort;
  private Button mConnect;

  static LoginDialogFragment newInstance() {
    return new LoginDialogFragment();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    getDialog().setTitle("Login");
    getDialog().setCanceledOnTouchOutside(false);

    View v = inflater.inflate(R.layout.login_dialog, container, false);
    mConnect = (Button) v.findViewById(R.id.connect);
    mConnect.setOnClickListener(this);
    mUsername = (EditText) v.findViewById(R.id.username);
    mHostname = (EditText) v.findViewById(R.id.hostname);
    mPort = (EditText) v.findViewById(R.id.port);

    mHostname.setText("unix11.andrew.cmu.edu");
    mPort.setText("18001");

    return v;
  }

  @Override
  public void onClick(View v) {
    if (v == mConnect) {
      String user = mUsername.getText().toString();
      String host = mHostname.getText().toString();
      String port = mPort.getText().toString();

      if (TextUtils.isEmpty(user)) {
        Toast.makeText(mActivity, "User name must not be empty!",
            Toast.LENGTH_SHORT).show();
        return;
      } else if (TextUtils.isEmpty(host)) {
        Toast.makeText(mActivity, "Host name must not be empty!",
            Toast.LENGTH_SHORT).show();
        return;
      } else if (TextUtils.isEmpty(port)) {
        Toast
            .makeText(mActivity, "Port must not be empty!", Toast.LENGTH_SHORT)
            .show();
        return;
      }

      mCallback.onNewGame(user, host, port);
    }
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    mActivity = activity;
    try {
      mCallback = (NewGameCallback) activity;
    } catch (ClassCastException e) {
      throw new ClassCastException(activity.toString()
          + " must implement NewGameCallback");
    }
  }

  @Override
  public void onCancel(DialogInterface dialog) {
    mActivity.finish();
  }

  @Override
  public void show(FragmentManager manager, String tag) {
    try {
      super.show(manager, tag);
    } catch (IllegalStateException e) {
      if (DEBUG)
        Log.e(TAG,
            "Caught IllegalStateException in LoginDialogFragment#show()! "
                + "This is most likely a bug in the Android Support Package!");
    }
  }

  /**
   * Used by dialogs to tell the activity the user wants a new game.
   */
  public interface NewGameCallback {
    /**
     * The user wants to start a new game.
     */
    void onNewGame(String user, String host, String port);
  }

}
