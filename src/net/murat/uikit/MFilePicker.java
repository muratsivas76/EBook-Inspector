package net.murat.uikit;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * MFilePicker is a lightweight, customizable file picker for Android (API 29+).
 * It features multi-selection support, fluent MIME type filtering, and direct lifecycle bridging.
 *
 * @author Murat
 */
public class MFilePicker {
  
  public static final int MERGE_REQUEST_CODE    = 101;
  public static final int SPLIT_REQUEST_CODE    = 102;
  public static final int OPEN_REQUEST_CODE     = 103;
  public static final int SAVE_REQUEST_CODE     = 104;
  public static final int SAVE_ENC_REQUEST_CODE = 105;
  public static final int OPEN_ENC_REQUEST_CODE = 106;
  public static final int SAVE_SEGMENT_REQUEST_CODE = 107;
  
  private final Activity hostActivity;
  private final List<String> mimeTypes = new ArrayList<String>();
  private boolean multiSelectionEnabled = false;
  
  // Stores the primary selected Uri and the full list for multi-selection
  private Uri selectedUri = null;
  private List<Uri> selectedUris = new ArrayList<Uri>();
  
  /**
   * Constructor that firmly binds the library instance to your active Activity.
   *
   * @param hostActivity Your active Activity context.
   */
  public MFilePicker(Activity hostActivity) {
    if (hostActivity == null) {
      throw new IllegalArgumentException("Host Activity cannot be null");
    }
    this.hostActivity = hostActivity;
    // Default filter allows all file types
    this.mimeTypes.add("*/*");
  }
  
  /**
   * Overwrites the entire MIME type list with a new array of filters.
   *
   * @param newMimeTypes Array of MIME type strings (e.g., {"image/*", "application/pdf"}).
   * @return This instance for method chaining.
   */
  public MFilePicker setMimeTypes(String[] newMimeTypes) {
    this.mimeTypes.clear();
    if (newMimeTypes != null) {
      for (int i = 0; i < newMimeTypes.length; i++) {
        if (newMimeTypes[i] != null && !newMimeTypes[i].isEmpty()) {
          this.mimeTypes.add(newMimeTypes[i]);
        }
      }
    }
    if (this.mimeTypes.isEmpty()) {
      this.mimeTypes.add("*/*");
    }
    return this;
  }
  
  /**
   * Appends a single MIME type filter to the existing configuration list.
   * Handles wildcard overrides intelligently without clearing valid states.
   *
   * @param mimeType Single MIME type string (e.g., "text/plain").
   * @return This instance for method chaining.
   */
  public MFilePicker addMimeType(String mimeType) {
    if (mimeType == null || mimeType.isEmpty()) {
      return this;
    }
    
    // If the general wildcard is explicitly added, it supersedes all specific filters
    if (mimeType.equals("*/*")) {
      this.mimeTypes.clear();
      this.mimeTypes.add("*/*");
      return this;
    }
    
    // Adding a specific filter removes the general wildcard to avoid bypassing it
    this.mimeTypes.remove("*/*");
    
    // Avoid duplicate entries
    if (!this.mimeTypes.contains(mimeType)) {
      this.mimeTypes.add(mimeType);
    }
    
    // Safety fallback: ensure the list is never left empty
    if (this.mimeTypes.isEmpty()) {
      this.mimeTypes.add("*/*");
    }
    
    return this;
  }
  
  /**
   * Retrieves the currently configured list of MIME type filters as a standard array.
   *
   * @return Array of MIME type strings.
   */
  public String[] getMimeTypes() {
    return this.mimeTypes.toArray(new String[this.mimeTypes.size()]);
  }
  
  /**
   * Enables or disables multiple file selection.
   *
   * @param enabled True to allow selecting multiple files, false for single selection.
   * @return This instance for method chaining.
   */
  public MFilePicker setMultiSelectionEnabled(boolean enabled) {
    this.multiSelectionEnabled = enabled;
    return this;
  }
  
  /**
   * Checks if multiple file selection is currently enabled.
   *
   * @return True if multi-selection is active.
   */
  public boolean isMultiSelectionEnabled() {
    return this.multiSelectionEnabled;
  }
  
  /**
   * Triggers the system file manager to open/select existing files.
   *
   * @param code Request code to identify the result in onActivityResult.
   */
  public void showOpenDialog(int code) {
    resetSelectionState();
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    
    if (this.multiSelectionEnabled) {
      intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    }
    
    applyMimeTypesToIntent(intent);
    this.hostActivity.startActivityForResult(intent, code);
  }
  
  /**
   * Triggers the system file manager to create/save a new file.
   *
   * @param code Request code to identify the result in onActivityResult.
   */
  public void showSaveDialog(int code) {
    resetSelectionState();
    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    
    applyMimeTypesToIntent(intent);
    this.hostActivity.startActivityForResult(intent, code);
  }
  
  /**
   * Bridges the host Activity's onActivityResult data into the library state.
   * Handles both single selection (getData) and multi-selection (getClipData).
   *
   * @param requestCode The request code passed to startActivityForResult.
   * @param resultCode  The result code returned by the child activity.
   * @param data        The Intent carrying the result URI payload.
   * @return True if one or more valid Uris were successfully captured.
   */
  public boolean handleResult(int requestCode, int resultCode, Intent data) {
    if (resultCode != Activity.RESULT_OK || data == null) {
      return false;
    }
    
    resetSelectionState();
    
    // Case A: Multiple files selected — walk through the ClipData item list
    if (data.getClipData() != null) {
      ClipData clipData = data.getClipData();
      for (int i = 0; i < clipData.getItemCount(); i++) {
        Uri uri = clipData.getItemAt(i).getUri();
        if (uri != null) {
          this.selectedUris.add(uri);
        }
      }
    }
    // Case B: Single file selected — grab the direct Uri from the intent
    else if (data.getData() != null) {
      this.selectedUris.add(data.getData());
    }
    
    // Bind the primary Uri to the convenience accessor if anything was captured
    if (!this.selectedUris.isEmpty()) {
      this.selectedUri = this.selectedUris.get(0);
      return true;
    }
    
    return false;
  }
  
  /**
   * Returns the primary selected Uri (first item in multi-selection).
   *
   * @return The selected content Uri, or null if nothing was selected.
   */
  public Uri getSelectedUri() {
    return this.selectedUri;
  }
  
  /**
   * Returns all selected Uris during a multi-selection operation.
   *
   * @return List of selected content Uris, or an empty list if nothing was selected.
   */
  public List<Uri> getSelectedUris() {
    return this.selectedUris;
  }
  
  /**
   * Opens an InputStream for the primary selected file via ContentResolver.
   * This is the correct approach for content:// Uris on API 29+.
   *
   * @return InputStream for the selected file.
   * @throws FileNotFoundException if no file is selected or the Uri cannot be opened.
   */
  public InputStream getSelectedInputStream() throws FileNotFoundException {
    if (this.selectedUri == null) {
      throw new FileNotFoundException("No file has been selected yet.");
    }
    return this.hostActivity.getContentResolver().openInputStream(this.selectedUri);
  }
  
  /**
   * Opens an InputStream for any given Uri via ContentResolver.
   * Useful for iterating over multi-selection results manually.
   *
   * @param uri The content Uri to open.
   * @return InputStream for the given Uri.
   * @throws FileNotFoundException if the Uri cannot be opened.
   */
  public InputStream getInputStreamForUri(Uri uri) throws FileNotFoundException {
    if (uri == null) {
      throw new FileNotFoundException("Provided Uri is null.");
    }
    return this.hostActivity.getContentResolver().openInputStream(uri);
  }
  
  /**
   * Clears all previously captured selection state before a new dialog is launched.
   */
  private void resetSelectionState() {
    this.selectedUri = null;
    this.selectedUris.clear();
  }
  
  /**
   * Applies the configured MIME type filters to the given Intent.
   * Uses EXTRA_MIME_TYPES for multiple filters, setType for a single one.
   */
  private void applyMimeTypesToIntent(Intent intent) {
    if (this.mimeTypes.size() == 1) {
      intent.setType(this.mimeTypes.get(0));
      } else {
      // Umbrella type required when passing EXTRA_MIME_TYPES
      intent.setType("*/*");
      intent.putExtra(Intent.EXTRA_MIME_TYPES,
      this.mimeTypes.toArray(new String[this.mimeTypes.size()]));
    }
  }
  
}
