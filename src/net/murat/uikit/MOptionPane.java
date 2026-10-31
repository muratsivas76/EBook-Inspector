package net.murat.uikit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 * MOptionPane is a lightweight JOptionPane alternative for Android.
 *
 * @author Murat
 */
public class MOptionPane {
  
  public static final int YES_OPTION = 1;
  public static final int NO_OPTION = 0;
  public static final int CANCEL_OPTION = -1;
  
  private final Activity hostActivity;
  private int selectedIndex = -1;
  private int confirmedOption = CANCEL_OPTION;
  private String inputResult = null;
  
  public interface OnConfirmListener {
    void onResult(int option);
  }
  
  public interface OnInputListener {
    void onResult(String input);
  }
  
  public interface OnChoiceListener {
    void onResult(int index);
  }
  
  public MOptionPane(Activity hostActivity) {
    if (hostActivity == null) {
      throw new IllegalArgumentException("Host Activity cannot be null");
    }
    this.hostActivity = hostActivity;
  }
  
  public Activity getActivity() {
    return this.hostActivity;
  }
  
  public void showMessageDialog(String message) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this.hostActivity);
    builder.setMessage(message != null ? message : "");
    builder.setCancelable(false);
    builder.setPositiveButton("OK", null);
    builder.create().show();
  }
  
  /**
   * Legacy input dialog support to maintain backwards compatibility across the project ecosystem.
   */
  public void showInputDialog(String message, final OnInputListener listener) {
    showInputDialog(message, null, listener);
  }
  
  /**
   * High-comfort input dialog that pre-populates the text field with a default configuration state.
   * Suppresses automatic keyboard popup on dialog open — keyboard appears only on explicit user tap.
   */
  public void showInputDialog(String message, String defaultText, final OnInputListener listener) {
    this.inputResult = null;
    
    final EditText inputField = new EditText(this.hostActivity);
    inputField.setPadding(32, 16, 32, 16);
    
    if (defaultText != null && !defaultText.isEmpty()) {
      inputField.setText(defaultText);
      
      // If the incoming text is a long report stream, keep caret at origin for top-aligned visibility.
      // Otherwise position caret at end for comfortable backspace editing.
      if (defaultText.length() > 32) {
        inputField.setSelection(0);
        } else {
        inputField.setSelection(defaultText.length());
      }
    }
    
    AlertDialog.Builder builder = new AlertDialog.Builder(this.hostActivity);
    builder.setMessage(message != null ? message : "");
    builder.setView(inputField);
    builder.setCancelable(false);
    
    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          MOptionPane.this.inputResult = inputField.getText().toString();
          hideKeyboardFromDialog(inputField);
          if (listener != null) {
            listener.onResult(MOptionPane.this.inputResult);
          }
        }
    });
    
    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          hideKeyboardFromDialog(inputField);
          if (listener != null) {
            listener.onResult(null);
          }
        }
    });
    
    AlertDialog dialog = builder.create();
    
    // Prevent the dialog's EditText from automatically stealing focus
    // and triggering the keyboard when the dialog first appears
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        @Override
        public void onShow(DialogInterface dialogInterface) {
          inputField.clearFocus();
          InputMethodManager imm = (InputMethodManager)
          hostActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
          if (imm != null && inputField.getWindowToken() != null) {
            imm.hideSoftInputFromWindow(inputField.getWindowToken(), 0);
          }
        }
    });
    
    dialog.show();
  }
  
  public void showConfirmDialog(String message, final OnConfirmListener listener) {
    this.confirmedOption = CANCEL_OPTION;
    
    AlertDialog.Builder builder = new AlertDialog.Builder(this.hostActivity);
    builder.setMessage(message != null ? message : "");
    builder.setCancelable(false);
    
    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          MOptionPane.this.confirmedOption = YES_OPTION;
          if (listener != null) {
            listener.onResult(YES_OPTION);
          }
        }
    });
    
    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          MOptionPane.this.confirmedOption = NO_OPTION;
          if (listener != null) {
            listener.onResult(NO_OPTION);
          }
        }
    });
    
    builder.create().show();
  }
  
  public void showSingleChoiceDialog(String message, final String[] options, final OnChoiceListener listener) {
    this.selectedIndex = -1;
    
    if (options == null || options.length == 0) {
      return;
    }
    
    AlertDialog.Builder builder = new AlertDialog.Builder(this.hostActivity);
    builder.setTitle(message != null ? message : "Select an Option");
    builder.setCancelable(false);
    
    builder.setItems(options, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          MOptionPane.this.selectedIndex = which;
          if (listener != null) {
            listener.onResult(which);
          }
        }
    });
    
    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          if (listener != null) {
            listener.onResult(-1);
          }
        }
    });
    
    builder.create().show();
  }
  
  public String getInputResult() {
    return this.inputResult;
  }
  
  public int getConfirmedOption() {
    return this.confirmedOption;
  }
  
  public int getSelectedIndex() {
    return this.selectedIndex;
  }
  
  /**
   * Hides the soft keyboard tied to the given view's window token.
   */
  private void hideKeyboardFromDialog(EditText view) {
    if (view == null) return;
    InputMethodManager imm = (InputMethodManager)
    hostActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null && view.getWindowToken() != null) {
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
  }
  
}
