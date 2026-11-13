package net.murat.uikit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 * MOptionPane is a lightweight JOptionPane alternative for Android.
 * Fully compatible with Android API 29 and legacy Java 6-8 without lambda expressions.
 * All internal source code comments follow strict English standards.
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

    /**
     * Constructs the MOptionPane with a verified Activity context.
     * 
     * @param hostActivity The parent activity hosting the dialogs
     */
    public MOptionPane(Activity hostActivity) {
        if (hostActivity == null) {
            throw new IllegalArgumentException("Host Activity cannot be null");
        }
        this.hostActivity = hostActivity;
    }

    public Activity getActivity() {
        return this.hostActivity;
    }

    /**
     * Displays a basic notification message dialog to the user.
     * 
     * @param message The string content to display
     */
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
     * Bypasses the keyboard ghosting issue by introducing a micro-delay handler.
     * This allows the OS to finish the keyboard hide animation before downstream heavy UI tasks execute.
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
                
                // 1. Force hide the keyboard immediately using the window layer
                if (dialog instanceof AlertDialog) {
                    hideKeyboardDirectly(((AlertDialog) dialog).getWindow());
                }
                
                // 2. Dismiss the dialog window right away
                dialog.dismiss();
                
                // 3. Postpone the heavy text manipulation callback by 150ms.
                // This gives the OS breathing room to drop the keyboard smoothly.
                if (listener != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            listener.onResult(MOptionPane.this.inputResult);
                        }
                    }, 150);
                }
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (dialog instanceof AlertDialog) {
                    hideKeyboardDirectly(((AlertDialog) dialog).getWindow());
                }
                dialog.dismiss();
                
                if (listener != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            listener.onResult(null);
                        }
                    }, 150);
                }
            }
        });

        final AlertDialog dialog = builder.create();

        // Prevent the dialog's EditText from automatically stealing focus
        // and triggering the keyboard when the dialog first appears
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                inputField.clearFocus();
                hideKeyboardDirectly(dialog.getWindow());
            }
        });

        dialog.show();
    }

    /**
     * Displays a two-option confirmation layout (Yes / No).
     */
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

    /**
     * Displays a single-choice item selection array list.
     */
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
     * High-reliability keyboard suppression engine.
     * Extracts the core window token from the specific Dialog Window layer instead of an active View reference.
     * This bypasses lifecycle race-conditions during immediate view component dismissals.
     *
     * @param window The target dialog window reference containing the active input focus
     */
    private void hideKeyboardDirectly(Window window) {
        if (window != null && window.getDecorView() != null) {
            InputMethodManager imm = (InputMethodManager) 
                    this.hostActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(window.getDecorView().getWindowToken(), 0);
            }
        }
    }
    
}
