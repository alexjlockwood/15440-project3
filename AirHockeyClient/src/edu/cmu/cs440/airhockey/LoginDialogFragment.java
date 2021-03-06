package edu.cmu.cs440.airhockey;

import static edu.cmu.cs440.airhockey.Utils.LOGV;

import java.util.Locale;
import java.util.regex.Pattern;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class LoginDialogFragment extends DialogFragment implements View.OnClickListener {

  private static final String TAG = "15440_LoginDialogFragment";

  private Activity mActivity;
  private NewGameCallback mCallback;
  private EditText mUsername;
  private EditText mHostname;
  private EditText mPort;
  private Button mConnect;
  private Spinner mColorsSpinner;

  static LoginDialogFragment newInstance() {
    return new LoginDialogFragment();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    getDialog().setTitle("Login");
    getDialog().setCanceledOnTouchOutside(false);

    View v = inflater.inflate(R.layout.login_dialog, container, false);

    mConnect = (Button) v.findViewById(R.id.connect);
    mConnect.setOnClickListener(this);
    mUsername = (EditText) v.findViewById(R.id.username);
    mHostname = (EditText) v.findViewById(R.id.hostname);
    mPort = (EditText) v.findViewById(R.id.port);
    mColorsSpinner = (Spinner) v.findViewById(R.id.colors_spinner);

    // Set a random color... just to have some variation in puck colors
    String[] mColorsArray = mActivity.getResources().getStringArray(R.array.colors);
    int randomColor = (int) (Math.random() * mColorsArray.length);
    mColorsSpinner.setSelection(randomColor);

    // TODO: randomize these values for in-class presentation!
    mHostname.setText("unix11.andrew.cmu.edu");
    mPort.setText("18001");

    return v;
  }

  @Override
  public void onClick(View v) {
    if (v == mConnect) {
      Pattern p = Pattern.compile("[^A-Za-z0-9]");
      String user = mUsername.getText().toString().trim();
      String host = mHostname.getText().toString().trim();
      String port = mPort.getText().toString().trim();

      if (TextUtils.isEmpty(user)) {
        Toast.makeText(mActivity, R.string.login_user_non_empty, Toast.LENGTH_SHORT).show();
      } else if (p.matcher(user).find()) {
        Toast.makeText(mActivity, R.string.login_alpha_numeric_only, Toast.LENGTH_SHORT).show();
      } else if (TextUtils.isEmpty(host)) {
        Toast.makeText(mActivity, R.string.login_host_non_empty, Toast.LENGTH_SHORT).show();
      } else if (TextUtils.isEmpty(port)) {
        Toast.makeText(mActivity, R.string.login_port_non_empty, Toast.LENGTH_SHORT).show();
      } else {
        String colorText = mColorsSpinner.getSelectedItem().toString();
        colorText = colorText.toLowerCase(Locale.US);

        Puck.Color color;
        if (colorText.equals("blue")) {
          color = Puck.Color.Blue;
        } else if (colorText.equals("green")) {
          color = Puck.Color.Green;
        } else if (colorText.equals("light blue")) {
          color = Puck.Color.LightBlue;
        } else if (colorText.equals("orange")) {
          color = Puck.Color.Orange;
        } else if (colorText.equals("purple")) {
          color = Puck.Color.Purple;
        } else if (colorText.equals("red")) {
          color = Puck.Color.Red;
        } else { // if (colorText.equals("yellow")) {
          color = Puck.Color.Yellow;
        }

        mCallback.onNewGame(user, host, port, color);
      }
    }
  }

  @Override
  public void onCancel(DialogInterface dialog) {
    mActivity.finish();
  }

  @Override
  public void show(FragmentManager manager, String tag) {
    try {
      // Used to avoid a stupid annoying bug in the Android support package :(
      super.show(manager, tag);
    } catch (IllegalStateException e) {
      LOGV(TAG, "Caught IllegalStateException in LoginDialogFragment#show()!"
          + " This is most likely a bug in the Android Support Package!");
    }
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    mActivity = activity;
    try {
      mCallback = (NewGameCallback) activity;
    } catch (ClassCastException e) {
      throw new ClassCastException(activity.toString() + " must implement NewGameCallback");
    }
  }

  /**
   * Used by dialogs to tell the activity the user wants a new game.
   */
  public interface NewGameCallback {

    /**
     * The user wants to start a new game.
     */
    void onNewGame(String user, String host, String port, Puck.Color color);
  }
}
