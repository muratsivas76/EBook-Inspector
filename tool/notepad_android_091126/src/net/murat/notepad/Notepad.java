package net.murat.notepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.Layout;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import net.murat.uikit.*;
import net.murat.util.*;

public class Notepad extends Activity {

  private final Locale mylocale = Locale.getDefault();

  /** Hard upper bound (in UTF-8 bytes) for encode/decode payloads. */
  private static final int MAX_ENCODE_INPUT = 16 * 1024; // 16 KB

  private MEditor editor;
  private EditText et_chunk_jump;

  private MFilePicker filePicker;
  private MOptionPane optionPane;
  private MColorPicker colorPicker;

  // Layout navigation containers and view hierarchies
  private LinearLayout chunk_navigation_bar;
  private Button btn_chunk_prev;
  private Button btn_chunk_next;
  private TextView tv_chunk_status;

  private InputMethodManager imm = null;
  
  private String findTarget = "";
  private String replaceWith = "";
  // Case-magic mode applied to the replacement payload (none | upper | lower | firstUpper |
  // sentence)
  private String replaceMode = "none";

  // Class variables for session tracking
  private String currentFileAbsolutePath = "";
  private String currentFileName = "";
  private int currentFileCaretPosition = 0;

  // Font face: empty string means Typeface.DEFAULT, otherwise a TTF file name resolved inside
  // TTFDIR
  private String currentFontFace = "";
  private int currentFontStyle = Typeface.NORMAL;

  private final String UTF8 = "UTF-8";
  private String currentEncoding = UTF8;

  // Global counter variable caching the absolute total page segments for large assets
  private int totalChunksCount = 1;

  private final double CIEN = 100.0;
  private final int ONEKBI = 1024;
  private final long ONEKBL = 1024L;
  private final double ONEKBD = 1024.0;
  private final double CEROD = 0.0;

  private final long TAMPON = 24L;
  private final int TAMPON_NUMBER = 16;
  private long MAX_CHUNK_SIZE = (96L * ONEKBL) + ((long) TAMPON);

  private int DAY_VALUE = 21;
  private int MONTH_VALUE = 5;

  // Tracks current active position inside the large file bytes sequence
  private long currentFilePointer = 0;

  // Caches the absolute file size metrics in bytes
  private long totalFileSize = 0;

  // Stores reference to the loaded active file descriptor
  private File currentActiveFile = null;

  // Tracks current visual page index for the UI status text components
  private int currentChunkIndex = 1;

  private int pendingRestoreScrollY = -1;

  private final String[] options = {
    "4 KB", "8 KB", "16 KB", "32 KB", "64 KB", "128 KB", "256 KB", "512 KB", "1024 KB"
  };
  private final long[] sizesInBytes = {
    4L * ONEKBL,
    8L * ONEKBL,
    16L * ONEKBL,
    32L * ONEKBL,
    64L * ONEKBL,
    128L * ONEKBL,
    256L * ONEKBL,
    512L * ONEKBL,
    ONEKBL * ONEKBL
  };
  private final String[] styles = {"Normal", "Bold", "Italic", "Bold Italic"};
  private final int[] styleValues = {
    Typeface.NORMAL, Typeface.BOLD, Typeface.ITALIC, Typeface.BOLD_ITALIC
  };

  private String PASSWORD = "5EmrE4325EmrE432";
  private final String PREPASS = "5EmrE4325Emr";

  private final String[] MONTHS =
      new String[] {
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
      };

  // Comprehensive MIME type filter mapped 1:1 with mimeExtensions array
  private final String[] mimeTypes = MFilePicker.MIME_TYPES;

  private final String[] encMimeTypes = {"application/octet-stream"};

  private File nameCaretInfoFile = null;

  private Menu mMenu; // Global menu reference

  private final Map<String, java.util.List<String>> languageMap =
      new HashMap<String, java.util.List<String>>(12000);

  private final Map<Integer, File> dirtyChunksFileMap = new HashMap<Integer, File>();
  private final Map<Integer, Long> chunkByteOffsets = new HashMap<Integer, Long>();

  // Keep a reference for emergency/undo if needed
  private String lastBackupText = "";

  private final int BUFSIZE = 8192 * 2;
  private final int MAXSELECT = 32 * ONEKBI;

  // Shared single-threaded executor — reused across all file operations to prevent thread
  // proliferation
  private final ExecutorService fileIOExecutor = Executors.newSingleThreadExecutor();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  private boolean isReadOnlyMode = false;

  private File ROOTDIR = null;
  private String PATH = "";
  private File TTFDIR = null;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    final String PATHX =
        getMainPathName(); // Because getPackageName can be called after super.onCreate
    PATH = PATHX;

    String ROOTPATH = PATH.replace("/Download", "");
    ROOTDIR = new File(ROOTPATH);

    File folder = new File(PATH + "/carets");
    if (folder.exists() == false) {
      folder.mkdir();
    }

    // Resolve the TTF directory as a File object so font faces can be looked up later
    TTFDIR = new File(PATH + "/carets/ttf");
    if (TTFDIR.exists() == false) {
      TTFDIR.mkdir();
    }

    folder = new File(PATH + "/carets/notepad");
    if (folder.exists() == false) {
      folder.mkdir();
    }

    folder = new File(PATH + "/carets/notepad/splits");
    if (folder.exists() == false) {
      folder.mkdir();
    }

    folder = new File(PATH + "/carets/notepad/merges");
    if (folder.exists() == false) {
      folder.mkdir();
    }

    File arFile = new File(PATH + "/carets/notepad/arabicCharsGuide.txt");
    if (arFile.exists() == false) {
      FileOutputStream fos = null;
      OutputStreamWriter writer = null;
      try {
        // Ensure that all parent directories exist safely under our primary PATH structures
        File parentDir = arFile.getParentFile();
        if (parentDir != null && parentDir.exists() == false) {
          parentDir.mkdirs();
        }

        // Create the brand new distinct guide file natively on disk storage
        arFile.createNewFile();

        fos = new FileOutputStream(arFile);
        writer = new OutputStreamWriter(fos, "UTF-8");

        char[] arabicArray = ArabicTool.getArabicchr();
        char[] latinArray = ArabicTool.getLatinchr();

        // Match minimum layout size boundaries to prevent index out of bounds exceptions
        int totalMappingsCount = latinArray.length;
        if (arabicArray.length < totalMappingsCount) {
          totalMappingsCount = arabicArray.length;
        }

        // Traditional fast loop mapping Latin assets 1:1 with Arabic characters directly on main
        // thread
        for (int i = 0; i < totalMappingsCount; i++) {
          char latinKey = latinArray[i];
          char arabicVal = arabicArray[i];

          // Skip layout character tokens or control elements that do not represent true keys
          if (latinKey == '\n' || latinKey == '\r') {
            continue;
          }

          // Synthesize human readable structural documentation maps
          writer.write("Latin: [" + latinKey + "] = Arabic: [" + arabicVal + "]\n");
        }

        writer.flush();

      } catch (final Exception e) {
        // Silent: guide file generation is not critical for app startup
      } finally {
        // Secure zero-leak resource cleanup management across all layout channels
        try {
          if (writer != null) writer.close();
        } catch (Exception ignored) {
        }
        try {
          if (fos != null) fos.close();
        } catch (Exception ignored) {
        }
      }
    }

    Calendar calendar = Calendar.getInstance();

    // Extract the day of the month (1 - 31)
    DAY_VALUE = calendar.get(Calendar.DAY_OF_MONTH);
    // Extract the month (0 - 11), adding 1 to match human-readable format (1 - 12)
    MONTH_VALUE = calendar.get(Calendar.MONTH) + 1;

    File file = new File(PATH + "/carets/notepad/dict.txt");
    initializeMap(file);

    nameCaretInfoFile = new File(PATH + "/carets/notepad/nameCaretInfo.txt");

    // Define the file structure for the first time
    if (!nameCaretInfoFile.exists()) {
      try {
        PrintStream ps = new PrintStream(new FileOutputStream(nameCaretInfoFile));

        // Line 1: Last opened file absolute path (Empty for now)
        ps.println("");

        // Line 2: Chunk tracking and Caret position mapped as
        // (pointer:chunkIndex:selection:maxChunkSize)
        ps.println("0:1:0:" + MAX_CHUNK_SIZE);

        // Line 3: Font Size (Float - Default 24.0f)
        ps.println("24.0");

        // Line 4: Font Style (Integer - Typeface.NORMAL = 0)
        ps.println("0");

        // Line 5: Read Only Mode (Boolean as String - "false")
        ps.println("false");

        // Line 6: Vertical scroll position (Integer - Default 0)
        ps.println("0");

        // Line 7: Line spacing multiplier (Float - Default 1.0f)
        ps.println("1.0");

        // Line 8: Font face (Empty string means Typeface.DEFAULT)
        ps.println("");

        // Line 9: Background color (ARGB int - Default white)
        ps.println(Color.WHITE);

        // Line 10: Text color (ARGB int - Default black)
        ps.println(Color.BLACK);

        ps.flush();
        ps.close();
      } catch (IOException e) {
        // Standard Java error tracking
      }
    }

    editor = (MEditor) findViewById(R.id.editor);
    editor.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

    et_chunk_jump = (EditText) findViewById(R.id.et_chunk_jump);

    // Locate and bind chunk viewport management components directly from the XML layout
    chunk_navigation_bar = (LinearLayout) findViewById(R.id.chunk_navigation_bar);
    btn_chunk_prev = (Button) findViewById(R.id.btn_chunk_prev);
    btn_chunk_next = (Button) findViewById(R.id.btn_chunk_next);
    tv_chunk_status = (TextView) findViewById(R.id.tv_chunk_status);

    // Click listener configuration for Next Chunk button with dynamic input jump validations
    btn_chunk_next.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            if (currentActiveFile == null) return;

            String jumpText = et_chunk_jump.getText().toString().trim();
            int targetPage = 0;
            try {
              targetPage = Integer.parseInt(jumpText);
            } catch (Exception e) {
              targetPage = 0;
            }

            if (targetPage <= 0 || targetPage > totalChunksCount) {
              targetPage = currentChunkIndex + 1;
            }

            if (targetPage > totalChunksCount) return;

            final int finalTargetPage = targetPage;
            final String currentViewportContent = editor.getFullText();
            final int outgoingChunkIndex = currentChunkIndex;

            showLoadingProgress();

            fileIOExecutor.execute(
                new Runnable() {
                  @Override
                  public void run() {
                    FileInputStream fis = null;
                    FileChannel channel = null;
                    FileOutputStream fosTmp = null;
                    try {
                      // STEP 1: Flush outgoing viewport content into dynamic virtual session memory
                      File currentTmpSlot =
                          new File(getCacheDir(), "dirty_chunk_" + outgoingChunkIndex + ".tmp");
                      fosTmp = new FileOutputStream(currentTmpSlot);

                      if (currentViewportContent != null && !currentViewportContent.isEmpty()) {
                        fosTmp.write(currentViewportContent.getBytes(currentEncoding));
                      } else {
                        fosTmp.write("\n".getBytes(currentEncoding));
                      }
                      fosTmp.flush();
                      fosTmp.close();
                      fosTmp = null;
                      dirtyChunksFileMap.put(outgoingChunkIndex, currentTmpSlot);

                      // STEP 2: Evaluate and load targeted destination viewport configurations
                      final String nextChunkText;

                      if (dirtyChunksFileMap.containsKey(finalTargetPage)) {
                        File targetTmpFile = dirtyChunksFileMap.get(finalTargetPage);
                        if (targetTmpFile != null && targetTmpFile.exists()) {
                          long tmpLength = targetTmpFile.length();
                          if (tmpLength > 0) {
                            fis = new FileInputStream(targetTmpFile);
                            channel = fis.getChannel();
                            MappedByteBuffer buffer =
                                channel.map(FileChannel.MapMode.READ_ONLY, 0, tmpLength);
                            Charset charset = Charset.forName(currentEncoding);
                            CharBuffer charBuffer = charset.decode(buffer);
                            // nextChunkText = charBuffer.toString();
                            nextChunkText =
                                readChunkSafe(targetTmpFile, 0, tmpLength, currentEncoding);
                          } else {
                            nextChunkText = "\n";
                          }
                        } else {
                          nextChunkText = "\n";
                        }
                      } else {
                        // Calculate safe start offset using stored byte offsets map
                        long readStartOffset;
                        if (chunkByteOffsets.containsKey(finalTargetPage - 1)) {
                          readStartOffset = chunkByteOffsets.get(finalTargetPage - 1);
                        } else {
                          readStartOffset = (long) (finalTargetPage - 1) * MAX_CHUNK_SIZE;
                        }

                        long remainingBytes = totalFileSize - readStartOffset;
                        long bytesToRead =
                            (remainingBytes > MAX_CHUNK_SIZE) ? MAX_CHUNK_SIZE : remainingBytes;

                        if (bytesToRead > 0 && readStartOffset < totalFileSize) {
                          nextChunkText =
                              readChunkSafe(
                                  currentActiveFile, readStartOffset, bytesToRead, currentEncoding);
                          // Store the actual end offset of this chunk for the next one
                          chunkByteOffsets.put(
                              finalTargetPage,
                              readStartOffset + nextChunkText.getBytes(currentEncoding).length);
                        } else {
                          nextChunkText = "\n";
                        }
                      }

                      currentChunkIndex = finalTargetPage;

                      mainHandler.post(
                          new Runnable() {
                            @Override
                            public void run() {
                              editor.setEditorText(nextChunkText, true);
                              editor.setSelection(0);
                              applyReadOnlyState();
                              btn_chunk_prev.setEnabled(currentChunkIndex > 1);
                              btn_chunk_next.setEnabled(currentChunkIndex < totalChunksCount);
                              tv_chunk_status.setText(
                                  "Chunk: " + currentChunkIndex + " / " + totalChunksCount);
                              et_chunk_jump.setText("0");
                              hideLoadingProgress();
                            }
                          });
                    } catch (Exception e) {
                      mainHandler.post(
                          new Runnable() {
                            @Override
                            public void run() {
                              hideLoadingProgress();
                            }
                          });
                    } finally {
                      try {
                        if (fosTmp != null) fosTmp.close();
                      } catch (Exception ignored) {
                      }
                      try {
                        if (channel != null) channel.close();
                      } catch (Exception ignored) {
                      }
                      try {
                        if (fis != null) fis.close();
                      } catch (Exception ignored) {
                      }
                    }
                  }
                });
          }
        });

    // Click listener configuration for Previous Chunk button with dynamic input jump validations
    btn_chunk_prev.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            if (currentActiveFile == null) return;

            String jumpText = et_chunk_jump.getText().toString().trim();
            int targetPage = 0;
            try {
              targetPage = Integer.parseInt(jumpText);
            } catch (Exception e) {
              targetPage = 0;
            }

            if (targetPage <= 0 || targetPage > totalChunksCount) {
              targetPage = currentChunkIndex - 1;
            }

            if (targetPage < 1) return;

            final int finalTargetPage = targetPage;
            final String currentViewportContent = editor.getFullText();
            final int outgoingChunkIndex = currentChunkIndex;

            showLoadingProgress();

            fileIOExecutor.execute(
                new Runnable() {
                  @Override
                  public void run() {
                    FileInputStream fis = null;
                    FileChannel channel = null;
                    FileOutputStream fosTmp = null;
                    try {
                      // STEP 1: Flush and cache outgoing viewport content into dynamic virtual
                      // session memory
                      File currentTmpSlot =
                          new File(getCacheDir(), "dirty_chunk_" + outgoingChunkIndex + ".tmp");
                      fosTmp = new FileOutputStream(currentTmpSlot);

                      if (currentViewportContent != null && !currentViewportContent.isEmpty()) {
                        fosTmp.write(currentViewportContent.getBytes(currentEncoding));
                      } else {
                        fosTmp.write("\n".getBytes(currentEncoding));
                      }
                      fosTmp.flush();
                      fosTmp.close();
                      fosTmp = null;
                      dirtyChunksFileMap.put(outgoingChunkIndex, currentTmpSlot);

                      // STEP 2: Evaluate and intercept read targets based on localized session
                      // dirty map state
                      final String prevChunkText;

                      if (dirtyChunksFileMap.containsKey(finalTargetPage)) {
                        File targetTmpFile = dirtyChunksFileMap.get(finalTargetPage);
                        if (targetTmpFile != null && targetTmpFile.exists()) {
                          long tmpLength = targetTmpFile.length();
                          if (tmpLength > 0) {
                            fis = new FileInputStream(targetTmpFile);
                            channel = fis.getChannel();
                            MappedByteBuffer buffer =
                                channel.map(FileChannel.MapMode.READ_ONLY, 0, tmpLength);
                            Charset charset = Charset.forName(currentEncoding);
                            CharBuffer charBuffer = charset.decode(buffer);
                            // prevChunkText = charBuffer.toString();
                            prevChunkText =
                                readChunkSafe(targetTmpFile, 0, tmpLength, currentEncoding);
                          } else {
                            prevChunkText = "\n";
                          }
                        } else {
                          prevChunkText = "\n";
                        }
                      } else {
                        // Calculate safe start offset using stored byte offsets map
                        long readStartOffset;
                        if (chunkByteOffsets.containsKey(finalTargetPage - 1)) {
                          readStartOffset = chunkByteOffsets.get(finalTargetPage - 1);
                        } else {
                          readStartOffset = (long) (finalTargetPage - 1) * MAX_CHUNK_SIZE;
                        }

                        long remainingBytes = totalFileSize - readStartOffset;
                        long bytesToRead =
                            (remainingBytes > MAX_CHUNK_SIZE) ? MAX_CHUNK_SIZE : remainingBytes;

                        if (bytesToRead > 0 && readStartOffset < totalFileSize) {
                          prevChunkText =
                              readChunkSafe(
                                  currentActiveFile, readStartOffset, bytesToRead, currentEncoding);
                          // Store the actual end offset of this chunk for the next one
                          chunkByteOffsets.put(
                              finalTargetPage,
                              readStartOffset + prevChunkText.getBytes(currentEncoding).length);
                        } else {
                          prevChunkText = "\n";
                        }
                      }

                      currentChunkIndex = finalTargetPage;

                      mainHandler.post(
                          new Runnable() {
                            @Override
                            public void run() {
                              editor.setEditorText(prevChunkText, true);
                              editor.setSelection(0);
                              applyReadOnlyState();
                              btn_chunk_prev.setEnabled(currentChunkIndex > 1);
                              btn_chunk_next.setEnabled(currentChunkIndex < totalChunksCount);
                              tv_chunk_status.setText(
                                  "Chunk: " + currentChunkIndex + " / " + totalChunksCount);
                              et_chunk_jump.setText("0");
                              hideLoadingProgress();
                            }
                          });
                    } catch (Exception e) {
                      mainHandler.post(
                          new Runnable() {
                            @Override
                            public void run() {
                              hideLoadingProgress();
                            }
                          });
                    } finally {
                      try {
                        if (fosTmp != null) fosTmp.close();
                      } catch (Exception ignored) {
                      }
                      try {
                        if (channel != null) channel.close();
                      } catch (Exception ignored) {
                      }
                      try {
                        if (fis != null) fis.close();
                      } catch (Exception ignored) {
                      }
                    }
                  }
                });
          }
        });

    filePicker = new MFilePicker(this);
    optionPane = new MOptionPane(this);
    colorPicker = new MColorPicker(this);

    initSearchToolbar();

    // Define the callback for text selection
    editor.setCustomSelectionActionModeCallback(
        new ActionMode.Callback() {
          @Override
          public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Add a custom menu item as a "placeholder" or action
            menu.add(0, 1001, 0, "Dictionary");
            return true;
          }

          @Override
          public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            int start = editor.getSelectionStart();
            int end = editor.getSelectionEnd();

            if (start >= 0 && end > start) {
              // 1. Get and clean selected text
              String fullText = editor.getText().toString();
              String selectedText = fullText.substring(start, end).trim().toLowerCase(mylocale);

              // 1.5 Apply boundary length safety pass to ensure system responsiveness during
              // massive text blocks selection
              if (selectedText.length() > TAMPON_NUMBER) {
                selectedText = selectedText.substring(0, TAMPON_NUMBER).trim();
              }

              // 2. Search for meanings (using the List-based logic)
              java.util.List<String> meanings = findMeaning(selectedText);

              MenuItem item = menu.findItem(1001);
              if (item != null) {
                if (meanings != null && !meanings.isEmpty()) {
                  // If multiple meanings exist, join them with a comma
                  StringBuilder sb = new StringBuilder();
                  for (int i = 0; i < meanings.size(); i++) {
                    sb.append(meanings.get(i));
                    if (i < meanings.size() - 1) sb.append(", ");
                  }
                  // Display: "okul: school, escuela"
                  item.setTitle(selectedText + ": " + sb.toString());
                } else {
                  // No match found
                  item.setTitle("?");
                }
              }
            }
            return true;
          }

          @Override
          public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int itemId = item.getItemId();

            // Check if the clicked item is "Select All"
            if (itemId == android.R.id.selectAll) {
              if (editor.getText().length() > MAXSELECT) {
                Toast.makeText(
                        getApplicationContext(), "Text too large to select all", Toast.LENGTH_SHORT)
                    .show();
                mode.finish();
                return true; // Stop the system from selecting everything
              }
            }

            // Check if the clicked item is "Copy" or "Cut"
            if (itemId == android.R.id.copy || itemId == android.R.id.cut) {
              int start = editor.getSelectionStart();
              int end = editor.getSelectionEnd();
              int selectionLength = Math.abs(end - start);

              if (selectionLength > MAXSELECT) {
                Toast.makeText(
                        getApplicationContext(),
                        "Selected text too large to copy or cut",
                        Toast.LENGTH_SHORT)
                    .show();
                mode.finish();
                return true; // Strictly block the operating system clipboard interaction pipeline
              }

              return false;
            }

            if (itemId == 1001) {
              // Your dictionary logic here
              return true;
            }
            return false;
          }

          @Override
          public void onDestroyActionMode(ActionMode mode) {}
        });

    try {
      imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    } catch (Exception e) {
      imm = null;
    }

    // Check if the app was started by "Open With"
    Intent intent = getIntent();
    String action = intent.getAction();
    Uri data = intent.getData();

    if (Intent.ACTION_VIEW.equals(action) && data != null) {
      openExternalFile(data);
    } else {
      // App opened normally, check for last session
      if (nameCaretInfoFile != null && nameCaretInfoFile.exists()) {
        try {
          BufferedReader br =
              new BufferedReader(
                  new InputStreamReader(new FileInputStream(nameCaretInfoFile), "UTF-8"));

          String lastPath = br.readLine();
          String lastPosStr = br.readLine();
          String lastFontSizeStr = br.readLine();
          String lastFontStyleStr = br.readLine();
          String lastReadOnlyStr = br.readLine();
          String lastScrollStr = br.readLine();
          String lastLineSpacingStr = br.readLine();
          String lastFontFaceStr = br.readLine();
          String lastBgColorStr = br.readLine();
          String lastFgColorStr = br.readLine();

          br.close();

          if (lastPath != null && !lastPath.trim().isEmpty()) {

            // Initialize target variables for deep chunk restoring passes
            long restoredPointer = 0;
            int restoredChunkIndex = 1;
            int lastPos = 0;

            // 1. Parse Caret Position and Composite Chunk Tracking Parameters
            if (lastPosStr != null) {
              try {
                String cleanPosStr = lastPosStr.trim();
                if (cleanPosStr.contains(":")) {
                  String[] tokenizedParts = cleanPosStr.split(":");
                  if (tokenizedParts.length >= 3) {
                    restoredPointer = Long.parseLong(tokenizedParts[0]);
                    restoredChunkIndex = Integer.parseInt(tokenizedParts[1]);
                    lastPos = Integer.parseInt(tokenizedParts[2]);
                  }
                  if (tokenizedParts.length >= 4) {
                    MAX_CHUNK_SIZE = Long.parseLong(tokenizedParts[3]);
                  }
                } else {
                  lastPos = Integer.parseInt(cleanPosStr);
                }
              } catch (NumberFormatException e) {
                restoredPointer = 0;
                restoredChunkIndex = 1;
                lastPos = 0;
              }
            }

            // 2. Parse and Apply Font Size (if exists)
            if (lastFontSizeStr != null) {
              try {
                float fSize = Float.parseFloat(lastFontSizeStr.trim());
                editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, fSize);
              } catch (Exception e) {
                /* keep default */
              }
            }

            // 2.5. Parse and Apply Line Spacing (if exists)
            if (lastLineSpacingStr != null) {
              try {
                float spacing = Float.parseFloat(lastLineSpacingStr.trim());
                if (spacing > 0f) {
                  editor.setLineSpacing(0f, spacing);
                }
              } catch (Exception e) {
                /* keep default */
              }
            }

            // 2.7. Parse and Apply Font Face (if exists)
            // Must run before the font style block below, because applyTypeface()
            // reads both currentFontFace and currentFontStyle together.
            if (lastFontFaceStr != null) {
              try {
                String ff = lastFontFaceStr.trim();
                currentFontFace = ff.isEmpty() ? "" : ff;
              } catch (Exception e) {
                /* keep default */
              }
            }

            // 3. Parse and Apply Font Style (if exists)
            if (lastFontStyleStr != null) {
              try {
                int fStyle = Integer.parseInt(lastFontStyleStr.trim());
                currentFontStyle = fStyle;
                applyTypeface();
              } catch (Exception e) {
                /* keep default */
              }
            }

            // 3.5. Parse and Apply Colors (if exists)
            if (lastBgColorStr != null) {
              try {
                editor.setBackgroundColor(Integer.parseInt(lastBgColorStr.trim()));
              } catch (Exception e) {
                /* keep default */
              }
            }
            if (lastFgColorStr != null) {
              try {
                editor.setTextColor(Integer.parseInt(lastFgColorStr.trim()));
              } catch (Exception e) {
                /* keep default */
              }
            }

            // 4. Parse and Apply Read Only Mode (if exists)
            if (lastReadOnlyStr != null) {
              boolean isReadOnly = Boolean.parseBoolean(lastReadOnlyStr.trim());
              setEditorReadOnlyFromSession(isReadOnly);
            }

            // 5. Finally, trigger the special session loader with historical metadata
            if ((lastPath.trim().toLowerCase().endsWith(".enc")) == false) {
              if (restoredPointer > 0) {
                currentFilePointer = restoredPointer;
                currentChunkIndex = restoredChunkIndex;
              }
              if (lastScrollStr != null) {
                try {
                  pendingRestoreScrollY = Integer.parseInt(lastScrollStr.trim());
                } catch (Exception ignored) {
                  pendingRestoreScrollY = -1;
                }
              }
              openFileWithName(lastPath, lastPos);
            }

            editor.requestFocus();
            hideKeyboard();
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      } // end of if namecaretinfo exists non-null and non-enc
    }
  } // End of onCreate

  private final String getMainPathName() {
    File path = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
    String px = path.getAbsolutePath();

    String exclude = "Android/data/" + (getPackageName()) + "/files/";
    px = px.replace(exclude, "");

    return px;
  }

  // Menu handling
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    this.mMenu = menu; // Store it globally here
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.menu, menu);

    // Apply the saved read-only check status from the previous session state immediately during
    // menu creation
    if (this.mMenu != null) {
      MenuItem readOnlyItem = this.mMenu.findItem(R.id.menu_read_only);
      if (readOnlyItem != null) {
        readOnlyItem.setChecked(isReadOnlyMode);
      }
    }

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();

    // --- Search & Replace Group ---
    if (id == R.id.menu_change_find_word) {
      showChangeFindWordDialog();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_find) {
      // 1. Make visible the search bar in XML
      View searchBar = findViewById(R.id.search_toolbar);
      if (searchBar != null) {
        searchBar.setVisibility(View.VISIBLE);
      }
      // 2. First searching
      executeFindNext();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_replace) {
      executeReplaceAll();
      hideKeyboard();
      return true;
    }

    // --- File Group ---
    else if (id == R.id.menu_new) {
      showNewFileConfirmation(true);
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_open) {
      // Configure file picker with our comprehensive developer mimeTypes array
      filePicker.setMimeTypes(mimeTypes);
      filePicker.setMultiSelectionEnabled(false);
      // Trigger the parametric open dialog using consolidated library code constants
      filePicker.showOpenDialog(MFilePicker.OPEN_REQUEST_CODE);
      return true;
    } else if (id == R.id.menu_open_dec) {
      // Encrypted file picking utilizing dedicated encryption filter array
      filePicker.setMimeTypes(encMimeTypes);
      filePicker.setMultiSelectionEnabled(false);
      filePicker.showOpenDialog(MFilePicker.OPEN_ENC_REQUEST_CODE);
      return true;
    } else if (id == R.id.menu_save) {
      // Configure picker for safe text document creation via system SAF framework
      filePicker.setMimeTypes(mimeTypes);
      filePicker.showSaveDialog(MFilePicker.SAVE_REQUEST_CODE);
      return true;
    } else if (id == R.id.menu_save_segment) {
      // Configure picker for safe single segment document creation via system SAF framework
      if (editor.getFullText().isEmpty()) {
        Toast.makeText(
                getApplicationContext(), "Cannot export an empty segment.", Toast.LENGTH_SHORT)
            .show();
        return true;
      }
      filePicker.setMimeTypes(mimeTypes);
      filePicker.showSaveDialog(MFilePicker.SAVE_SEGMENT_REQUEST_CODE);
      return true;
    } else if (id == R.id.menu_save_enc) {
      // Encrypted document creation forcing strict binary stream filters
      filePicker.setMimeTypes(encMimeTypes);
      filePicker.showSaveDialog(MFilePicker.SAVE_ENC_REQUEST_CODE);
      return true;
    } else if (id == R.id.menu_change_maxlen) {
      showMaxLenDialog();
      return true;
    }

    // --- Tools Group ---
    else if (id == R.id.menu_insert_date) {
      insertSystemDateTime();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_clean_spaces) {
      // Regex logic to replace multiple spaces with single space
      executeCleanExtraSpaces();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_clear_junk) {
      // Filter non-printable or specific junk characters
      executeClearJunk();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_clear_text) {
      showNewFileConfirmation(false);
      return true;
    } else if (id == R.id.menu_clear_tags) {
      excuteClearTags();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_clean_larger) {
      executeCleanLargerCharacters();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_specific_info) {
      executeSpecificInfo();
      return true;
    } else if (id == R.id.menu_char_info) {
      hideKeyboard();
      showCharacterDetails();
      return true;
    } else if (id == R.id.menu_editor_info) {
      hideKeyboard();
      showEditorDetails();
      return true;
    } else if (id == R.id.menu_append_char) {
      // Show a dialog with special character grid to append
      showAppendCharDialog();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_add_diyez) {
      addDiyez();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_to_brackets) {
      toBrackets();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_append_meanings) {
      appendMeanings();
      hideKeyboard();
      return true;
    }

    // Encode tools
    else if (id == R.id.menu_sha_generate) {
      executeGenerateTextHash();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_base64_encode) {
      executeBase64Encode();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_base64_decode) {
      executeBase64Decode();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_url_encode) {
      executeUrlEncode();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_url_decode) {
      executeUrlDecode();
      hideKeyboard();
      return true;
    }

    // --- Line Tools ---
    else if (id == R.id.menu_goto_line) {
      gotoLine();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_trim_lines) {
      executeTrimLines();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_remove_starts) {
      executeRemoveStarts();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_remove_contains) {
      executeRemoveContains();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_remove_notcontains) {
      executeRemoveNotContains();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_remove_pagenums) {
      executeRemovePageNumbers();
      return true;
    } else if (id == R.id.menu_sort_lines) {
      executeSortLines();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_remove_empty_lines) {
      executeRemoveEmptyLines();
      return true;
    } else if (id == R.id.menu_remove_numlines) {
      executeRemoveNumberedLines();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_select_numlines) {
      executeSelectNumberedLines();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_remove_foreign) {
      executeRemoveForeignAlphabets();
      hideKeyboard();
      return true;
    }

    // Beautify
    else if (id == R.id.menu_code_beautify) {
      executeCodeBeautify();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_beautify) {
      executeBeautify();
      hideKeyboard();
      return true;
    }

    // --- Split - Merge Group ---
    else if (id == R.id.menu_calculate) {
      executeCalculate();
      return true;
    } else if (id == R.id.menu_find_filename) {
      executeFindFilename();
      return true;
    } else if (id == R.id.menu_compress_image) {
      // Configured for advanced source code files using the comprehensive developer types array
      filePicker.setMimeTypes(MFilePicker.IMAGE_MIME_TYPES);
      filePicker.setMultiSelectionEnabled(false);
      // Trigger the parametric open dialog using the consolidated split code constant
      filePicker.showOpenDialog(MFilePicker.COMPRESS_IMAGE_REQUEST_CODE);
      return true;
    } else if (id == R.id.menu_searchin_files) {
      // Configured for advanced source code files using the comprehensive developer types array
      filePicker.setMimeTypes(MFilePicker.MIME_TYPES);
      filePicker.setMultiSelectionEnabled(false);
      // Trigger the parametric open dialog using the consolidated split code constant
      filePicker.showOpenDialog(MFilePicker.SEARCH_CONTENT_REQUEST_CODE);
      return true;
    } else if (id == R.id.menu_split) {
      // Configured for advanced source code files using the comprehensive developer types array
      filePicker.setMimeTypes(mimeTypes);
      filePicker.setMultiSelectionEnabled(false);
      // Trigger the parametric open dialog using the consolidated split code constant
      filePicker.showOpenDialog(MFilePicker.SPLIT_REQUEST_CODE);
      return true;
    } else if (id == R.id.menu_merge) {
      // Configured to process any file type globally with multi-selection active for bulk
      // operations
      filePicker.setMimeTypes(mimeTypes);
      filePicker.setMultiSelectionEnabled(true);
      // Trigger the parametric open dialog using the consolidated merge code constant
      filePicker.showOpenDialog(MFilePicker.MERGE_REQUEST_CODE);
      return true;
    } else if (id == R.id.menu_find_islamic_festival) {
      findIslamicFestivals();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_karbala_calendar) {
      executeKarbalaCalendar();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_convert_to_arabic) {
      executeTransliteration(true);
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_convert_from_arabic) {
      executeTransliteration(false);
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_sel_convert_to_arabic) {
      executeSelectedTransliteration(true);
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_sel_convert_from_arabic) {
      executeSelectedTransliteration(false);
      hideKeyboard();
      return true;
    }

    // --- Appearance: Font Settings ---
    else if (id == R.id.menu_line_spacing) {
      executeLineSpacing();
      return true;
    } else if (id == R.id.menu_font_family) {
      // Show input dialog for a TTF name or "Default"
      showFontFaceDialog();
      return true;
    } else if (id == R.id.menu_font_style) {
      // Show single choice dialog (Normal, Bold, Italic, Bold-Italic)
      showFontStyleDialog();
      return true;
    } else if (id == R.id.menu_font_size) {
      // Show numeric input dialog with 10-50 limit check
      showFontSizeDialog();
      return true;
    }

    // --- Appearance: Color Settings ---
    else if (id == R.id.menu_read_only) {
      setEditorReadOnly();
      hideKeyboard();
      return true;
    } else if (id == R.id.menu_color_bg) {
      showColorPickerDialog("Background");
      return true;
    } else if (id == R.id.menu_color_fg) {
      showColorPickerDialog("Text");
      return true;
    } else if (id == R.id.menu_color_selection) {
      showColorPickerDialog("Selection");
      return true;
    } else if (id == R.id.menu_reset) {
      executeReset();
      return true;
    }

    // --- Encoding Group ---
    else if (id == R.id.enc_utf8) {
      currentEncoding = "UTF-8";
      Toast.makeText(this, "Encoding set to: " + currentEncoding, Toast.LENGTH_SHORT).show();
      return true;
    } else if (id == R.id.enc_tr_win) {
      currentEncoding = "Windows-1254";
      Toast.makeText(this, "Encoding set to: " + currentEncoding, Toast.LENGTH_SHORT).show();
      return true;
    } else if (id == R.id.enc_tr_iso) {
      currentEncoding = "ISO-8859-9";
      Toast.makeText(this, "Encoding set to: " + currentEncoding, Toast.LENGTH_SHORT).show();
      return true;
    } else if (id == R.id.enc_latin1) {
      currentEncoding = "ISO-8859-1";
      Toast.makeText(this, "Encoding set to: " + currentEncoding, Toast.LENGTH_SHORT).show();
      return true;
    } else if (id == R.id.enc_utf16) {
      currentEncoding = "UTF-16";
      Toast.makeText(this, "Encoding set to: " + currentEncoding, Toast.LENGTH_SHORT).show();
      return true;
    } else if (id == R.id.enc_ascii) {
      currentEncoding = "US-ASCII";
      Toast.makeText(this, "Encoding set to: " + currentEncoding, Toast.LENGTH_SHORT).show();
      return true;
    } else if (id == R.id.enc_custom) {
      showCustomEncodingDialog();
      return true;
    }

    // Orientation
    else if (id == R.id.menu_ltr) {
      applyTextDirectionSafely(View.TEXT_DIRECTION_LTR, Gravity.START | Gravity.LEFT);
      return true;
    } else if (id == R.id.menu_rtl) {
      applyTextDirectionSafely(View.TEXT_DIRECTION_RTL, Gravity.END | Gravity.RIGHT);
      return true;
    }

    // Align submenu (inside Orientation)
    else if (id == R.id.menu_align_left) {
      applyAlignment(Gravity.START | Gravity.LEFT);
      return true;
    } else if (id == R.id.menu_align_right) {
      applyAlignment(Gravity.END | Gravity.RIGHT);
      return true;
    } else if (id == R.id.menu_align_center) {
      applyAlignment(Gravity.CENTER_HORIZONTAL);
      return true;
    } else if (id == R.id.menu_align_justify) {
      applyJustification();
      return true;
    }

    // --- Direct Actions ---
    else if (id == R.id.menu_share) {
      // Implement ACTION_SEND Intent to share text
      executeShareText();
      return true;
    } else if (id == R.id.menu_exit) {
      showExitConfirmation();
      return true;
    }

    return super.onOptionsItemSelected(item);
  } // End of onOptionsItemSelected

  /**
   * Applies horizontal gravity only. Unlike applyTextDirectionSafely this does not touch text
   * direction, so the current LTR/RTL orientation is preserved.
   */
  private void applyAlignment(final int gravity) {
    if (editor == null) return;
    // Clear any inter-word justification set earlier
    try {
      editor.setJustificationMode(Layout.JUSTIFICATION_MODE_NONE);
    } catch (Exception ignored) {
    }
    editor.setGravity(gravity | Gravity.TOP);
    editor.requestLayout();
    editor.invalidate();
    Toast.makeText(this, "Align applied", Toast.LENGTH_SHORT).show();
  }

  /** Applies inter-word justification with the current text direction preserved. */
  private void applyJustification() {
    if (editor == null) return;
    try {
      editor.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD);
    } catch (Exception ignored) {
    }
    editor.setGravity(Gravity.START | Gravity.TOP);
    editor.requestLayout();
    editor.invalidate();
    Toast.makeText(this, "Align: Justify", Toast.LENGTH_SHORT).show();
  }

  private void initializeMap(File file) {
    if (file == null) return;

    try {
      // 1. If the dictionary file doesn't exist, create a default sample file
      if (!file.exists()) {
        BufferedWriter writer =
            new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
        writer.write(
            "okul: school\nkalem: pencil\nyol: way\nkitap: book\nokul: escuela\nkitap: libro\n");
        writer.close();
      }

      // 2. Read the file and populate the bidirectional map
      FileInputStream fis = new FileInputStream(file);
      BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"), 32768);

      String line;
      while ((line = reader.readLine()) != null) {
        int sep = line.indexOf(':');
        if (sep > 0) {
          // Extract key and value based on the separator
          String key = line.substring(0, sep).trim().toLowerCase(mylocale);
          String value = line.substring(sep + 1).trim().toLowerCase(mylocale);

          if (!key.isEmpty() && !value.isEmpty()) {
            // Bidirectional mapping with multi-meaning support
            addWordToMap(key, value);
            addWordToMap(value, key);
          }
        }
      }
      reader.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Adds a word to the languageMap using a List to support multiple meanings. Ensures no duplicates
   * are added to the list.
   */
  private void addWordToMap(String key, String value) {
    if (!languageMap.containsKey(key)) {
      languageMap.put(key, new ArrayList<String>());
    }
    java.util.List<String> list = languageMap.get(key);
    if (!list.contains(value)) {
      list.add(value);
    }
  }

  // --- Core Logic ---
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == MFilePicker.MERGE_REQUEST_CODE) {
      mergeFiles(data);
      hideKeyboard();
      return;
    }

    Uri uri = null;

    // Delegate the raw intent payloads straight into our custom file picker state management
    if (filePicker.handleResult(requestCode, resultCode, data)) {
      uri = filePicker.getSelectedUri();
    }

    if (uri == null) {
      Toast.makeText(getApplicationContext(), "Not Selected or Cancelled!", Toast.LENGTH_LONG)
          .show();
      return;
    }

    if (requestCode == MFilePicker.OPEN_REQUEST_CODE) {
      readTextFromFile(uri);
    } else if (requestCode == MFilePicker.SPLIT_REQUEST_CODE) {
      splitFile(uri);
    } else if (requestCode == MFilePicker.SAVE_REQUEST_CODE) {
      // Show the charset dialog before saving; the active charset field is not modified
      promptSaveEncoding(uri, false);
    } else if (requestCode == MFilePicker.SAVE_ENC_REQUEST_CODE) {
      showPasswordDialog(uri, true);
    } else if (requestCode == MFilePicker.OPEN_ENC_REQUEST_CODE) {
      showPasswordDialog(uri, false);
    } else if (requestCode == MFilePicker.SAVE_SEGMENT_REQUEST_CODE) {
      // Same encoding dialog for the segment exporter
      promptSaveEncoding(uri, true);
    } else if (requestCode == MFilePicker.SEARCH_CONTENT_REQUEST_CODE) {
      searchInsideFilesWorkflow(uri);
    } else if (requestCode == MFilePicker.COMPRESS_IMAGE_REQUEST_CODE) {
      compressImageWorkflow(uri);
    } else {
    }
  }

  /**
   * Prompts for the charset to use for this single write operation. Pre-filled with the active
   * charset. On OK the save runs with the chosen charset; the active charset field is NOT modified.
   */
  private void promptSaveEncoding(final Uri uri, final boolean isSegment) {
    String prefill =
        (currentEncoding == null || currentEncoding.trim().isEmpty()) ? "UTF-8" : currentEncoding;
    optionPane.showInputDialog(
        "Save - Select Encoding:",
        prefill,
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            if (input == null || input.trim().isEmpty()) return;
            final Charset saveCharset;
            try {
              saveCharset = Charset.forName(input.trim());
            } catch (Exception e) {
              Toast.makeText(
                      getApplicationContext(),
                      "Invalid charset: " + input.trim(),
                      Toast.LENGTH_SHORT)
                  .show();
              return;
            }
            String content = editor.getText().toString();
            if (isSegment) {
              saveCurrentSegmentToFile(uri, content, saveCharset);
            } else {
              saveTextToFile(uri, content, saveCharset);
            }
          }
        });
  }

  /**
   * Helper method to extract the display name from a Uri. Works for both Content Providers and File
   * Uris.
   */
  private String getFileName(Uri uri) {
    String result = null;
    if (uri.getScheme().equals("content")) {
      Cursor cursor = getContentResolver().query(uri, null, null, null, null);
      try {
        if (cursor != null && cursor.moveToFirst()) {
          int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
          if (index != -1) result = cursor.getString(index);
        }
      } finally {
        if (cursor != null) cursor.close();
      }
    }
    if (result == null) {
      result = uri.getPath();
      int cut = result.lastIndexOf('/');
      if (cut != -1) result = result.substring(cut + 1);
    }
    return result;
  }

  /**
   * Prompts the user for a TTF name inside TTFDIR. "Default" (or empty) restores Typeface.DEFAULT.
   * Any other value is resolved against TTFDIR (with ".ttf" appended when missing). Loaded fonts
   * are applied via Typeface.createFromFile; on any failure the editor falls back to
   * Typeface.DEFAULT.
   */
  private void showFontFaceDialog() {
    hideKeyboard();
    String hint =
        (currentFontFace == null || currentFontFace.isEmpty()) ? "Default" : currentFontFace;
    optionPane.showInputDialog(
        "Font face (type \"Default\" or a TTF name from carets/ttf):",
        hint,
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            if (input == null) return;
            String requested = input.trim();

            // "Default" (case-insensitive) or empty resets the face
            if (requested.isEmpty() || "default".equalsIgnoreCase(requested)) {
              currentFontFace = "";
              applyTypeface();
              Toast.makeText(getApplicationContext(), "Font Face: Default", Toast.LENGTH_SHORT)
                  .show();
              return;
            }

            String fileName =
                requested.toLowerCase(Locale.US).endsWith(".ttf") ? requested : requested + ".ttf";

            File f = new File(TTFDIR, fileName);
            if (!f.exists() || !f.isFile()) {
              // Case-insensitive fallback scan across the TTF directory
              File[] all = TTFDIR.listFiles();
              if (all != null) {
                for (int i = 0; i < all.length; i++) {
                  if (all[i].isFile() && all[i].getName().equalsIgnoreCase(fileName)) {
                    f = all[i];
                    break;
                  }
                }
              }
            }

            if (f == null || !f.exists() || !f.isFile()) {
              currentFontFace = "";
              applyTypeface();
              Toast.makeText(
                      getApplicationContext(),
                      "Font not found: " + fileName + " (Default applied)",
                      Toast.LENGTH_SHORT)
                  .show();
              return;
            }

            try {
              Typeface tf = Typeface.createFromFile(f);
              if (tf == null) throw new IOException("Typeface load returned null");
              currentFontFace = f.getName();
              applyTypeface();
              Toast.makeText(
                      getApplicationContext(), "Font Face: " + currentFontFace, Toast.LENGTH_SHORT)
                  .show();
            } catch (Exception e) {
              currentFontFace = "";
              applyTypeface();
              Toast.makeText(
                      getApplicationContext(),
                      "Failed to load font: " + e.getMessage() + " (Default applied)",
                      Toast.LENGTH_SHORT)
                  .show();
            }
          }
        });
  }

  private void showFontStyleDialog() {
    hideKeyboard();

    // Transitioned to our lightweight MOptionPane single choice engine
    optionPane.showSingleChoiceDialog(
        "Select Font Style",
        styles,
        new MOptionPane.OnChoiceListener() {
          @Override
          public void onResult(int which) {
            // Circuit breaker if user cancelled the selection layout (-1)
            if (which == -1) {
              return;
            }
            currentFontStyle = styleValues[which];
            // Apply changes directly to the full buffer without complex view mutations
            applyTypeface();
          }
        });
  }

  private void showFontSizeDialog() {
    hideKeyboard();

    // Convert the current pixel text size into SP so we can display it as a hint.
    // deriveDimension() is the modern replacement for the deprecated scaledDensity path.
    float rawSize =
        android.util.TypedValue.deriveDimension(
            TypedValue.COMPLEX_UNIT_SP, editor.getTextSize(), getResources().getDisplayMetrics());

    // Round to one decimal place so the hint shows e.g. "16.0" instead of "10.35463".
    float currentSize = Math.round(rawSize * 10f) / 10f;

    // Use the existing minimalist MOptionPane input method without changing its signature
    optionPane.showInputDialog(
        "Set Font Size (Current size is " + currentSize + "):",
        Float.toString(currentSize),
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            // Circuit breaker if the transaction was aborted or cancelled by the user
            if (input == null) {
              return;
            }

            try {
              String rawInput = input.trim().replace(',', '.');
              if (rawInput.isEmpty()) return;

              float size = Float.parseFloat(rawInput);

              // Enforce active typography scale limits (10sp to 50sp inclusive)
              if (size < 10f) size = 10f;
              if (size > 50f) size = 50f;

              // Apply text size directly; safe under the MAX_CHUNK_SIZEKB payload limit
              editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
            } catch (Exception e) {
              // Gracefully capture alpha characters or bad formats entered by the user
              Toast.makeText(getApplicationContext(), "Invalid format", Toast.LENGTH_SHORT).show();
            }
          }
        });
  }

  /**
   * Builds the final Typeface from the current font face (custom TTF or DEFAULT) and font style
   * (normal/bold/italic/bold-italic), then applies it to the editor.
   */
  private void applyTypeface() {
    if (editor == null) return;

    Typeface baseTf = Typeface.DEFAULT;
    if (currentFontFace != null && !currentFontFace.isEmpty() && TTFDIR != null) {
      File f = new File(TTFDIR, currentFontFace);
      if (f.exists() && f.isFile()) {
        try {
          Typeface loaded = Typeface.createFromFile(f);
          if (loaded != null) baseTf = loaded;
        } catch (Exception ignored) {
          baseTf = Typeface.DEFAULT;
        }
      }
    }

    Typeface finalTf = Typeface.create(baseTf, currentFontStyle);
    editor.setTypeface(finalTf);
  }

  private void executeLineSpacing() {
    hideKeyboard();

    // Fetch the current line spacing multiplier to display it as a hint.
    // The multiplier is stored on the editor's line spacing extra (0 by default).
    float currentMultiplier = editor.getLineSpacingMultiplier();

    // Round to one decimal place so the hint shows e.g. "1.2" instead of "1.200001".
    float rounded = Math.round(currentMultiplier * 10f) / 10f;

    optionPane.showInputDialog(
        "Line spacing multiplier (1.0 = normal, 1.2 = wider, 0.8 = tighter):",
        Float.toString(rounded),
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            if (input == null) return;
            try {
              String rawInput = input.trim().replace(',', '.');
              if (rawInput.isEmpty()) return;

              float multiplier = Float.parseFloat(rawInput);

              // Enforce sane limits (0.5x to 3.0x inclusive).
              if (multiplier < 0.5f) multiplier = 0.5f;
              if (multiplier > 3.0f) multiplier = 3.0f;

              editor.setLineSpacing(0f, multiplier);
              Toast.makeText(
                      getApplicationContext(), "Line spacing: " + multiplier, Toast.LENGTH_SHORT)
                  .show();
            } catch (Exception e) {
              Toast.makeText(getApplicationContext(), "Invalid format", Toast.LENGTH_SHORT).show();
            }
          }
        });
  }

  /** Escape sequences for regex pattern. Keeps regex tokens like \b, \w, \d intact. */
  private String escapeSequencesForRegex(String input) {
    if (input == null) return null;

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);

      if (c == '\\' && i + 1 < input.length()) {
        char next = input.charAt(i + 1);

        switch (next) {
          case 'n':
            sb.append('\n');
            i++;
            break;
          case 't':
            sb.append('\t');
            i++;
            break;
          case 'r':
            sb.append('\r');
            i++;
            break;
          case '\\':
            sb.append('\\');
            i++;
            break;
          default:
            sb.append('\\').append(next);
            i++;
            break;
        }
      } else {
        sb.append(c);
      }
    }

    return sb.toString();
  }

  /**
   * Applies the case-magic mode to a single replacement payload. Only the text produced by the
   * replacement template is transformed; the surrounding text is untouched.
   */
  private String applyReplaceMode(String text) {
    if (text == null || text.length() == 0) return "";
    String m = (replaceMode == null) ? "none" : replaceMode.toLowerCase(mylocale);
    if ("upper".equals(m)) return text.toUpperCase(mylocale);
    if ("lower".equals(m)) return text.toLowerCase(mylocale);
    if ("firstupper".equals(m)) return firstUpperEveryWord(text);
    if ("sentence".equals(m)) return sentenceCase(text);
    return text;
  }

  private String firstUpperEveryWord(String text) {
    StringBuilder sb = new StringBuilder(text.length());
    boolean startOfWord = true;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isLetter(c)) {
        if (startOfWord) {
          sb.append(Character.toUpperCase(c));
          startOfWord = false;
        } else {
          sb.append(c);
        }
      } else {
        sb.append(c);
        startOfWord = !Character.isLetterOrDigit(c);
        if (Character.isDigit(c)) startOfWord = false;
      }
    }
    return sb.toString();
  }

  private String sentenceCase(String text) {
    StringBuilder sb = new StringBuilder(text.length());
    boolean startOfSentence = true;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (startOfSentence && Character.isLetter(c)) {
        sb.append(Character.toUpperCase(c));
        startOfSentence = false;
      } else {
        sb.append(c);
        if (c == '.' || c == '!' || c == '?' || c == '\u2026') {
          startOfSentence = true;
        }
      }
    }
    return sb.toString();
  }

  private void showChangeFindWordDialog() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(40, 20, 40, 20);

    final EditText inputFrom = new EditText(this);
    inputFrom.setHint("From (Find)");
    inputFrom.setText(findTarget);

    final EditText inputTo = new EditText(this);
    inputTo.setHint("To (Replace)");
    inputTo.setText(replaceWith);

    final EditText inputMode = new EditText(this);
    inputMode.setHint("mode: none | upper | lower | firstUpper | sentence");
    inputMode.setText(replaceMode);

    layout.addView(inputFrom);
    layout.addView(inputTo);
    layout.addView(inputMode);

    new AlertDialog.Builder(this)
        .setTitle("Search-Replace Terms Settings")
        .setView(layout)
        .setPositiveButton(
            "Set",
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                // Read all three fields at once and apply them together.
                findTarget = inputFrom.getText().toString();
                replaceWith = escapeSequencesForRegex(inputTo.getText().toString());

                String raw = inputMode.getText().toString().trim().toLowerCase(mylocale);
                if (raw.equals("upper")
                    || raw.equals("lower")
                    || raw.equals("firstupper")
                    || raw.equals("sentence")) {
                  replaceMode = raw;
                } else {
                  replaceMode = "none";
                }

                Toast.makeText(
                        Notepad.this, "Terms set (mode=" + replaceMode + ")", Toast.LENGTH_SHORT)
                    .show();
                hideKeyboard();
              }
            })
        .setNegativeButton(
            "Close",
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                hideKeyboard();
              }
            })
        .show();
  }

  /**
   * Processes the entire text in the editor and appends dictionary meanings. Enforces predictable
   * RAM footprints and unified thread dispatching.
   */
  private void appendMeanings() {
    if (editor == null) return;

    // Use unified text retriever pass to ensure cross-layout stability
    final String content = editor.getFullText();
    if (content == null || content.isEmpty()) return;

    Toast.makeText(this, "Processing text...", Toast.LENGTH_SHORT).show();

    // Standardized background execution utilizing the shared file IO executor channel
    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            // PERFORMANCE: Pre-allocate StringBuilder capacity to minimize memory re-allocations
            final StringBuilder sb = new StringBuilder(content.length() * 2);

            // Regex to identify words (\w+) and preserve non-words (\W+) like spaces/newlines
            Pattern pattern = Pattern.compile("(\\w+)|(\\W+)");
            Matcher matcher = pattern.matcher(content);

            while (matcher.find()) {
              String segment = matcher.group();

              // If it's a word, look up its meanings via native data provider
              if (segment != null && segment.matches("\\w+")) {
                sb.append(segment);

                java.util.List<String> meanings = findMeaning(segment);
                if (meanings != null && !meanings.isEmpty()) {
                  sb.append(" [");
                  int totalMeanings = meanings.size();
                  for (int i = 0; i < totalMeanings; i++) {
                    sb.append(meanings.get(i));
                    if (i < totalMeanings - 1) {
                      sb.append(", ");
                    }
                  }
                  sb.append("]");
                }
              } else if (segment != null) {
                // Preserve punctuation, spaces, and newlines exactly
                sb.append(segment);
              }
            }

            final String finalResult = sb.toString();

            // Dispatch compiled dictionary tokens safely back to the primary main UI thread
            mainHandler.post(
                new Runnable() {
                  @Override
                  public void run() {
                    // Deliver the updated text block seamlessly via our consolidated bridge method
                    editor.setEditorText(finalResult, true);
                    editor.requestFocus();
                  }
                });
          }
        });
  }

  /**
   * Triggers the comprehensive Karbala Calendar system matrix by requesting input from the user,
   * calculating Ehli Beyt milestones on a background channel, and returning copyable outputs.
   */
  private void executeKarbalaCalendar() {
    if (optionPane == null) return;

    // Dynamically capture today's exact system date fields to offer a friendly default value
    Calendar todayCal = Calendar.getInstance();
    SimpleDateFormat defaultFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
    String todayString = defaultFormat.format(todayCal.getTime());

    // STEP 1: Request target date selection matrix from user using our lightweight dialog channel
    optionPane.showInputDialog(
        "Enter Target Date (dd.MM.yyyy):",
        todayString,
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(final String input) {
            // Circuit breaker if user aborted or dismissed the input view
            if (input == null || input.trim().isEmpty()) {
              return;
            }

            showLoadingProgress();

            // STEP 2: Offload chronological calculation matrix safely into our background
            // fileIOExecutor channel
            fileIOExecutor.execute(
                new Runnable() {
                  @Override
                  public void run() {
                    // Execute calendar metrics calculation against our static KarbalaCalendar data
                    // class
                    final String finalReport = KarbalaCalendar.getCalendarResults(input.trim());

                    // STEP 3: Return back to core main UI view layer to present results inside a
                    // copyable dialog
                    mainHandler.post(
                        new Runnable() {
                          @Override
                          public void run() {
                            hideLoadingProgress();

                            // Display formatted report inside inputDialog so the user can easily
                            // long-press and copy text
                            optionPane.showMessageDialog(
                                "Karbala Calendar Milestones Report\n\n" + finalReport);
                          }
                        });
                  }
                });
          }
        });
  }

  /**
   * Spawns a custom dual-picker frame using old-school layouts, then processes the historical data
   * lookup via the active custom dialog engine infrastructure.
   */
  private void findIslamicFestivals() {
    LinearLayout linearLayout = new LinearLayout(this);
    linearLayout.setOrientation(LinearLayout.HORIZONTAL);
    linearLayout.setGravity(android.view.Gravity.CENTER);

    // Static layout margin boundaries mapping padding parameters without density scale helper
    // artifacts
    linearLayout.setPadding(32, 16, 32, 16);

    // Configure Day Picker Column Unit
    final NumberPicker dayPicker = new NumberPicker(this);
    dayPicker.setMinValue(1);
    dayPicker.setMaxValue(31);
    dayPicker.setValue(DAY_VALUE); // Default benchmark tracking threshold fallback

    // Configure Month Picker Column Unit
    final NumberPicker monthPicker = new NumberPicker(this);
    monthPicker.setMinValue(1);
    monthPicker.setMaxValue(12);
    monthPicker.setValue(MONTH_VALUE); // Default reference index mapping fallback (May)
    monthPicker.setDisplayedValues(MONTHS);

    // Pack structural elements tightly into the main linear container bounds
    linearLayout.addView(dayPicker);
    linearLayout.addView(monthPicker);

    // Harness the core platform native alert frame layout builder to drop structural dependencies
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Select Day & Month");
    builder.setView(linearLayout);
    builder.setCancelable(false);

    builder.setPositiveButton(
        "Search",
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            int selectedDay = dayPicker.getValue();
            int selectedMonth = monthPicker.getValue();

            DAY_VALUE = selectedDay;
            MONTH_VALUE = selectedMonth;

            // Invoke our underlying data pattern scanner pipeline
            FestivalSearcher searcher = new FestivalSearcher();
            java.util.List<String> matches = searcher.findYearsForDate(selectedDay, selectedMonth);

            // Format title marker metadata safely before delegating payload tasks to our custom
            // pane
            String titleMarker =
                "Matches for "
                    + (selectedDay < 10 ? "0" : "")
                    + selectedDay
                    + "/"
                    + (selectedMonth < 10 ? "0" : "")
                    + selectedMonth;

            MOptionPane pane = new MOptionPane(Notepad.this);

            if (matches == null || matches.isEmpty()) {
              pane.showMessageDialog(
                  "No historical Islamic festivals matched this specific date between 1900 and"
                      + " 2100.");
            } else {
              // Extract payload buffers into pure primitive arrays compatible with the legacy
              // option panes
              String[] resultsArray = new String[matches.size()];
              resultsArray = matches.toArray(resultsArray);

              pane.showSingleChoiceDialog(
                  titleMarker,
                  resultsArray,
                  new MOptionPane.OnChoiceListener() {
                    @Override
                    public void onResult(int index) {
                      // No-op block context: Pure visual telemetry selection, simply absorb
                      // tracking signals
                    }
                  });
            }
          }
        });

    builder.setNegativeButton("Cancel", null);
    builder.create().show();
  }

  /**
   * Android version of our core search logic with suffix stripping. Returns a list of strings if
   * found, otherwise returns null.
   */
  private java.util.List<String> findMeaning(String word) {
    if (word == null || word.length() < 3) return null;
    word = word.toLowerCase(mylocale);

    // A. Exact match check
    if (languageMap.containsKey(word)) {
      return languageMap.get(word);
    }

    // B. Suffix stripping (e.g., "okula" -> "okul")
    String temp = word;
    while (temp.length() > 3) {
      temp = temp.substring(0, temp.length() - 1);
      if (languageMap.containsKey(temp)) {
        return languageMap.get(temp);
      }
    }
    return null;
  }

  private void gotoLine() {
    // Extract the active document text buffer safely from our editor layer
    final String content = editor.getFullText();

    // Leverages our optimized layout-free line counter directly to completely eliminate memory
    // pressure
    final int totalLines = editor.getLineCount();

    // Use your existing minimalist MOptionPane input method with a clear contextual message
    String dialogMessage =
        "Enter line number or 'E' for end (Total lines: "
            + totalLines
            + ")"
            + " (CaretLine: "
            + editor.getCaretLine()
            + "):";

    optionPane.showInputDialog(
        dialogMessage,
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            // Circuit breaker if the transaction was aborted or cancelled by the user (returns
            // null)
            if (input == null) {
              return;
            }

            String value = input.trim();

            // Safe evaluation to route the cursor straight to the EOF boundary
            if (value.equalsIgnoreCase("E")) {
              editor.setSelection(content.length());
              editor.requestFocus();
              return;
            }

            try {
              int targetLine = Integer.parseInt(value);
              if (targetLine <= 0 || targetLine > totalLines) return;

              // Compute the exact absolute character index offset to position the editor selection
              // cursor
              int pos = 0;
              int currentLineIndex = 1;

              while (currentLineIndex < targetLine) {
                int nextNewlineIndex = content.indexOf('\n', pos);
                if (nextNewlineIndex == -1) {
                  break; // Safeguard boundary pass if document metadata behaves unexpectedly
                }
                pos = nextNewlineIndex + 1; // Step over the newline character block boundary
                currentLineIndex++;
              }

              editor.setSelection(pos);
              editor.requestFocus();

            } catch (NumberFormatException e) {
              // Gracefully capture and ignore invalid alphanumeric payloads entered by the user
            }
          }
        });
  }

  /**
   * Executes textual transliteration between Latin and Arabic script architectures safely
   * offloading the processing metrics onto our shared single-thread background worker.
   */
  private void executeTransliteration(final boolean toArabicDirection) {
    if (editor == null) return;

    showLoadingProgress();
    // Fetch the absolute active editor snapshot data safely from memory layout blocks
    final String sourceText = editor.getFullText();

    if (sourceText.isEmpty()) {
      mainHandler.post(
          new Runnable() {
            @Override
            public void run() {
              hideLoadingProgress();
              Toast.makeText(getApplicationContext(), "Editor is empty!", Toast.LENGTH_SHORT)
                  .show();
            }
          });
      return;
    }

    // Standardized background execution utilizing the shared file IO executor channel
    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              final String convertedResult;

              // Phase mapping: decide direction algorithm matching user selection matrix
              if (toArabicDirection) {
                convertedResult = ArabicTool.toArabic(sourceText);
              } else {
                convertedResult = ArabicTool.fromArabic(sourceText);
              }

              // Return back to primary layout user interface thread to push our modified payload
              // data
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      editor.setEditorText(convertedResult, true);
                      Toast.makeText(
                              getApplicationContext(),
                              "Text converted successfully",
                              Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });

            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Conversion Error: " + e.getMessage(),
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            }
          }
        });
  }

  /**
   * Executes textual transliteration on the selected text range between Latin and Arabic script
   * architectures safely offloading the processing metrics onto our shared single-thread background
   * worker.
   */
  private void executeSelectedTransliteration(final boolean toArabicDirection) {
    if (editor == null || editor.getText() == null) return;

    // Get current text selection boundaries directly from the UI context
    final int start = editor.getSelectionStart();
    final int end = editor.getSelectionEnd();

    // If there is no active text selection, exit the operation silently
    if (start == end) return;

    // Handle reverse selection safety matrix (min/max calculations)
    final int min = Math.min(start, end);
    final int max = Math.max(start, end);

    // Extract only the isolated targeted text selection block
    final String selectedText = editor.getText().subSequence(min, max).toString();

    // Exit if the extracted text sequence carries no length data
    if (selectedText.isEmpty()) return;

    showLoadingProgress();

    // Standardized background execution utilizing the shared file IO executor channel
    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              final String convertedResult;

              // Phase mapping: process selected string matching user selection matrix
              if (toArabicDirection) {
                convertedResult = ArabicTool.toArabic(selectedText);
              } else {
                convertedResult = ArabicTool.fromArabic(selectedText);
              }

              // Return back to primary layout user interface thread to push our modified subset
              // payload
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      // Replace the exact selected layout tokens inside the text buffer
                      editor.getText().replace(min, max, convertedResult);

                      // Keep cursor viewport context healthy precisely at the end of the modified
                      // text payload
                      editor.setSelection(min + convertedResult.length());

                      Toast.makeText(
                              getApplicationContext(),
                              "Selection converted successfully",
                              Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });

            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Selection Conversion Error: " + e.getMessage(),
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            }
          }
        });
  }

  /**
   * Converts Android Storage Access Framework content URIs into absolute file system paths.
   * Natively supports 'primary' storage partitions under API 29 environments.
   */
  private String getRealPathFromUri(Uri uri) {
    if (uri == null) return "";

    // If it is already a direct file scheme, return the path immediately
    if ("file".equals(uri.getScheme())) {
      return uri.getPath();
    }

    // Check if the URI belongs to the native external storage document provider
    if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
      String docId = "";
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        // Document URI formats require token extraction via the official documents contract layer
        docId = DocumentsContract.getDocumentId(uri);
      } else {
        docId = uri.getPath();
      }

      if (docId != null && docId.contains(":")) {
        String[] parts = docId.split(":");
        String type = parts[0];
        String relativePath = parts[1];

        // 'primary' token maps directly onto the root folder of internal user storage
        if ("primary".equalsIgnoreCase(type)) {
          return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + relativePath;
        } else {
          // Secondary SD Card mapping configuration layer fallback
          return "/storage/" + type + "/" + relativePath;
        }
      }
    }

    // Fallback pass if the scheme configuration is too complex or unparseable
    return uri.getPath() != null ? uri.getPath() : uri.toString();
  }

  // OPEN-1: Modern localized file loader with deterministic chunk jump maps logic.
  // Calculates total page metrics (e.g., 1 / 5) and allows safe layout memory segment loading.
  private void openFileWithName(final String path, final int pos) {
    final File file = new File(path);
    if (!file.exists()) return;

    currentFileAbsolutePath = path;
    currentFileName = getOnlyFileName(currentFileAbsolutePath);
    currentActiveFile = file;

    // Evaluate if this specific invocation pipeline runs under a valid active session map restore
    final boolean isRestoringSession = (currentFilePointer > 0 && currentChunkIndex > 1);

    // If it's a fresh launch sequence, safely initialize the file tracking parameters back to
    // baseline
    if (!isRestoringSession) {
      currentFilePointer = 0;
      currentChunkIndex = 1;
    }

    // Clear all dirty chunk slots from any previous session before loading a new file
    Iterator<File> dirtyIt = dirtyChunksFileMap.values().iterator();
    while (dirtyIt.hasNext()) {
      File f = dirtyIt.next();
      if (f != null && f.exists()) f.delete();
    }
    dirtyChunksFileMap.clear();

    showLoadingProgress();

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            FileInputStream fis = null;
            FileChannel channel = null;
            try {
              fis = new FileInputStream(file);
              channel = fis.getChannel();
              totalFileSize = channel.size();

              // DECISION PIPELINE: Check if the file thickness requires specialized chunk layout
              // processing
              if (totalFileSize >= MAX_CHUNK_SIZE) {
                // Compute absolute total structural page partitions layout boundaries safely
                totalChunksCount = (int) ((totalFileSize + MAX_CHUNK_SIZE - 1) / MAX_CHUNK_SIZE);
                if (totalChunksCount < 1) totalChunksCount = 1;

                long readStartOffset = 0;
                long bytesToFetch = MAX_CHUNK_SIZE;

                if (isRestoringSession) {
                  // Validate bounds to prevent session restore overflow parameters mismatch
                  if (currentChunkIndex > totalChunksCount) currentChunkIndex = totalChunksCount;

                  // Math calibration block: deduce start byte address purely from historical index
                  // integers
                  readStartOffset = (long) (currentChunkIndex - 1) * MAX_CHUNK_SIZE;
                  if (readStartOffset < 0) readStartOffset = 0;

                  long remainingData = totalFileSize - readStartOffset;
                  bytesToFetch = (remainingData > MAX_CHUNK_SIZE) ? MAX_CHUNK_SIZE : remainingData;
                  currentFilePointer = readStartOffset + bytesToFetch;
                } else {
                  // Standard initial load baseline setup
                  readStartOffset = 0;
                  bytesToFetch = MAX_CHUNK_SIZE;
                  currentFilePointer = MAX_CHUNK_SIZE;
                  currentChunkIndex = 1;
                }

                MappedByteBuffer buffer =
                    channel.map(FileChannel.MapMode.READ_ONLY, readStartOffset, bytesToFetch);

                Charset charset = Charset.forName(currentEncoding);
                final CharBuffer charBuffer = charset.decode(buffer);
                // final String initialChunkText = charBuffer.toString();

                final String initialChunkText =
                    readChunkSafe(file, readStartOffset, bytesToFetch, currentEncoding);

                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        chunk_navigation_bar.setVisibility(View.VISIBLE);
                        editor.setEditorText(initialChunkText, true);

                        btn_chunk_prev.setEnabled(currentChunkIndex > 1);
                        btn_chunk_next.setEnabled(currentChunkIndex < totalChunksCount);

                        tv_chunk_status.setText(
                            "Chunk: " + currentChunkIndex + " / " + totalChunksCount);
                        et_chunk_jump.setText("0");

                        // Restore caret position when the target index is inside the loaded chunk
                        if ((pos >= 0) && (pos <= initialChunkText.length())) {
                          editor.setSelection(pos);
                        }
                        if (pendingRestoreScrollY >= 0) {
                          final int restoreY = pendingRestoreScrollY;
                          pendingRestoreScrollY = -1;
                          editor.post(
                              new Runnable() {
                                @Override
                                public void run() {
                                  editor.post(
                                      new Runnable() {
                                        @Override
                                        public void run() {
                                          editor.scrollTo(0, restoreY);
                                        }
                                      });
                                }
                              });
                        }
                        hideLoadingProgress();
                        hideKeyboard();
                      }
                    });

              } else {
                // LEGACY FILE PIPELINE: Small file delivery pass
                totalChunksCount = 1;
                currentChunkIndex = 1;

                MappedByteBuffer buffer =
                    channel.map(FileChannel.MapMode.READ_ONLY, 0, totalFileSize);
                Charset charset = Charset.forName(currentEncoding);
                final CharBuffer charBuffer = charset.decode(buffer);
                final String legacyFullText = charBuffer.toString();

                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        chunk_navigation_bar.setVisibility(View.GONE);
                        editor.setEditorText(legacyFullText, true);

                        // Restore caret position when the target index is inside the loaded chunk
                        if ((pos >= 0) && (pos <= legacyFullText.length())) {
                          editor.setSelection(pos);
                        }
                        if (pendingRestoreScrollY >= 0) {
                          final int restoreY = pendingRestoreScrollY;
                          pendingRestoreScrollY = -1;
                          editor.post(
                              new Runnable() {
                                @Override
                                public void run() {
                                  editor.post(
                                      new Runnable() {
                                        @Override
                                        public void run() {
                                          editor.scrollTo(0, restoreY);
                                        }
                                      });
                                }
                              });
                        }
                        hideLoadingProgress();
                        hideKeyboard();
                      }
                    });
              }

            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Load Error: " + e.getMessage(),
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            } finally {
              // Close the channel safely first and null it immediately to prevent clashes
              try {
                if (channel != null) {
                  channel.close();
                  channel = null;
                }
              } catch (Exception ignored) {
              }

              // Then close the stream safely and null it
              try {
                if (fis != null) {
                  fis.close();
                  fis = null;
                }
              } catch (Exception ignored) {
              }
            }
          }
        });
  }

  // OPEN-2: Modern Uri document handler utilizing zero-copy FileChannel processing to avoid heap
  // leaks
  private void readTextFromFile(final Uri uri) {
    if (uri == null) return;

    final long fileSize = getFileSize(uri);
    final String flName = getFileName(uri);

    // Clear all dirty chunk slots from any previous session before loading a new file
    Iterator<File> dirtyIt = dirtyChunksFileMap.values().iterator();
    while (dirtyIt.hasNext()) {
      File f = dirtyIt.next();
      if (f != null && f.exists()) f.delete();
    }
    dirtyChunksFileMap.clear();

    showLoadingProgress();

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            ParcelFileDescriptor pfd = null;
            FileInputStream fis = null;
            FileChannel channel = null;
            FileOutputStream fos = null;
            FileChannel targetChannel = null;
            FileInputStream cacheFis = null;
            FileChannel cacheChannel = null;

            try {
              // Open hardware-mapped descriptor via modern system layout resolver structures
              pfd = getContentResolver().openFileDescriptor(uri, "r");
              if (pfd == null) {
                throw new IOException("Unable to acquire atomic file descriptor reference.");
              }

              fis = new FileInputStream(pfd.getFileDescriptor());
              channel = fis.getChannel();
              totalFileSize = channel.size();

              if (totalFileSize == 0) {
                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        Toast.makeText(getApplicationContext(), "File is empty", Toast.LENGTH_SHORT)
                            .show();
                        hideLoadingProgress();
                      }
                    });
                // Clean exit: null out refs so the finally block does not double-close
                channel = null;
                fis = null;
                pfd = null;
                return;
              }

              // Resolve and cache path metadata from the incoming Uri reference
              currentFileAbsolutePath = getRealPathFromUri(uri);
              currentFileName = getOnlyFileName(currentFileAbsolutePath);

              // Reset chunk pointers back to the beginning of the document
              currentFilePointer = 0;
              currentChunkIndex = 1;

              // DECISION PIPELINE: Check if the file size exceeds our chunk viewport layout limits
              if (totalFileSize >= MAX_CHUNK_SIZE) {
                // Compute absolute total structural page partitions layout boundaries safely
                totalChunksCount = (int) ((totalFileSize + MAX_CHUNK_SIZE - 1) / MAX_CHUNK_SIZE);
                if (totalChunksCount < 1) totalChunksCount = 1;

                // Create a secure temporary cache file to enable seamless forward/backward random
                // access
                currentActiveFile = new File(getCacheDir(), "large_uri_temp_cache.txt");
                if (currentActiveFile.exists()) {
                  currentActiveFile.delete();
                }

                // Duplicate the Uri input stream into our local file channel safely
                fos = new FileOutputStream(currentActiveFile);
                targetChannel = fos.getChannel();
                channel.transferTo(0, totalFileSize, targetChannel);
                targetChannel.close();
                fos.close();
                targetChannel = null;
                fos = null;

                // Recalculate totalFileSize from the actual cache file to guarantee
                // Next/Prev offset calculations match the real cached content on disk
                totalFileSize = currentActiveFile.length();
                totalChunksCount = (int) ((totalFileSize + MAX_CHUNK_SIZE - 1) / MAX_CHUNK_SIZE);
                if (totalChunksCount < 1) totalChunksCount = 1;

                // Map only the initial chunk portion via safe background memory blocks
                cacheFis = new FileInputStream(currentActiveFile);
                cacheChannel = cacheFis.getChannel();
                MappedByteBuffer byteBuffer =
                    cacheChannel.map(FileChannel.MapMode.READ_ONLY, 0, MAX_CHUNK_SIZE);
                cacheChannel.close();
                cacheFis.close();
                cacheChannel = null;
                cacheFis = null;

                Charset charset = Charset.forName(currentEncoding);
                final CharBuffer charBuffer = charset.decode(byteBuffer);
                // final String initialChunkText = charBuffer.toString();
                final String initialChunkText =
                    readChunkSafe(currentActiveFile, 0, MAX_CHUNK_SIZE, currentEncoding);

                // Advance pointer position safely
                currentFilePointer = MAX_CHUNK_SIZE;

                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        chunk_navigation_bar.setVisibility(View.VISIBLE);
                        editor.setEditorText(initialChunkText, true);

                        btn_chunk_prev.setEnabled(false);
                        btn_chunk_next.setEnabled(currentChunkIndex < totalChunksCount);
                        tv_chunk_status.setText(
                            "Chunk: " + currentChunkIndex + " / " + totalChunksCount);
                        et_chunk_jump.setText("0");

                        Toast.makeText(
                                getApplicationContext(),
                                "Opened " + (flName != null ? flName : "") + " (Large File Mode)",
                                Toast.LENGTH_SHORT)
                            .show();

                        hideLoadingProgress();
                        hideKeyboard();
                      }
                    });

              } else {
                // LEGACY FILE PIPELINE: Standard native delivery route for ordinary configuration
                // files
                totalChunksCount = 1;
                currentChunkIndex = 1;
                currentActiveFile = null;

                MappedByteBuffer byteBuffer =
                    channel.map(FileChannel.MapMode.READ_ONLY, 0, totalFileSize);

                Charset charset = Charset.forName(currentEncoding);
                final CharBuffer charBuffer = charset.decode(byteBuffer);
                final String legacyFullText = charBuffer.toString();

                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        chunk_navigation_bar.setVisibility(View.GONE);
                        editor.setEditorText(legacyFullText, true);

                        Toast.makeText(
                                getApplicationContext(),
                                "Opened "
                                    + (flName != null ? flName : "")
                                    + " — "
                                    + (charBuffer.length() / ONEKBI)
                                    + " KB",
                                Toast.LENGTH_SHORT)
                            .show();

                        hideLoadingProgress();
                        hideKeyboard();
                      }
                    });
              }

            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Error: " + e.getMessage(),
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            } finally {
              // Hierarchical and independent safe close order
              try {
                if (targetChannel != null) {
                  targetChannel.close();
                  targetChannel = null;
                }
              } catch (Exception ignored) {
              }
              try {
                if (fos != null) {
                  fos.close();
                  fos = null;
                }
              } catch (Exception ignored) {
              }
              try {
                if (cacheChannel != null) {
                  cacheChannel.close();
                  cacheChannel = null;
                }
              } catch (Exception ignored) {
              }
              try {
                if (cacheFis != null) {
                  cacheFis.close();
                  cacheFis = null;
                }
              } catch (Exception ignored) {
              }
              try {
                if (channel != null) {
                  channel.close();
                  channel = null;
                }
              } catch (Exception ignored) {
              }
              try {
                if (fis != null) {
                  fis.close();
                  fis = null;
                }
              } catch (Exception ignored) {
              }
              try {
                if (pfd != null) {
                  pfd.close();
                  pfd = null;
                }
              } catch (Exception ignored) {
              }
            }
          }
        });
  }

  // OPEN-3: Specialized external document handler utilizing high-performance asynchronous zero-copy
  // buffer piping
  private void openExternalFile(final Uri uri) {
    if (uri == null) return;

    final long fileSize = getFileSize(uri);

    // Clear all dirty chunk slots from any previous session before loading a new file
    Iterator<File> dirtyIt = dirtyChunksFileMap.values().iterator();
    while (dirtyIt.hasNext()) {
      File f = dirtyIt.next();
      if (f != null && f.exists()) f.delete();
    }
    dirtyChunksFileMap.clear();

    showLoadingProgress();

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            ParcelFileDescriptor pfd = null;
            FileInputStream fis = null;
            FileChannel channel = null;
            FileOutputStream fos = null;
            FileChannel targetChannel = null;
            FileInputStream cacheFis = null;
            FileChannel cacheChannel = null;

            try {
              // Open hardware-mapped descriptor via modern system layout resolver structures
              pfd = getContentResolver().openFileDescriptor(uri, "r");
              if (pfd == null) {
                throw new IOException("Unable to acquire atomic file descriptor reference.");
              }

              fis = new FileInputStream(pfd.getFileDescriptor());
              channel = fis.getChannel();
              totalFileSize = channel.size();

              if (totalFileSize == 0) {
                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        Toast.makeText(getApplicationContext(), "File is empty", Toast.LENGTH_SHORT)
                            .show();
                        hideLoadingProgress();
                      }
                    });
                // Clean exit: null out refs so the finally block does not double-close
                channel = null;
                fis = null;
                pfd = null;
                return;
              }

              currentFileAbsolutePath = getRealPathFromUri(uri);
              currentFileName = getOnlyFileName(currentFileAbsolutePath);

              // Reset chunk pointers back to the beginning of the document
              currentFilePointer = 0;
              currentChunkIndex = 1;

              // DECISION PIPELINE: Check if the file size exceeds our chunk viewport layout limits
              if (totalFileSize >= MAX_CHUNK_SIZE) {
                // Compute absolute total structural page partitions layout boundaries safely
                totalChunksCount = (int) ((totalFileSize + MAX_CHUNK_SIZE - 1) / MAX_CHUNK_SIZE);
                if (totalChunksCount < 1) totalChunksCount = 1;

                // Create a secure temporary cache file to enable seamless forward/backward random
                // access
                currentActiveFile = new File(getCacheDir(), "external_uri_temp_cache.txt");
                if (currentActiveFile.exists()) {
                  currentActiveFile.delete();
                }

                // Duplicate the Uri input stream into our local file channel safely
                fos = new FileOutputStream(currentActiveFile);
                targetChannel = fos.getChannel();
                channel.transferTo(0, totalFileSize, targetChannel);
                targetChannel.close();
                fos.close();
                targetChannel = null;
                fos = null;

                // Recalculate totalFileSize from the actual cache file to guarantee
                // Next/Prev offset calculations match the real cached content on disk
                totalFileSize = currentActiveFile.length();
                totalChunksCount = (int) ((totalFileSize + MAX_CHUNK_SIZE - 1) / MAX_CHUNK_SIZE);
                if (totalChunksCount < 1) totalChunksCount = 1;

                // Map only the initial chunk portion via safe background memory blocks
                cacheFis = new FileInputStream(currentActiveFile);
                cacheChannel = cacheFis.getChannel();
                MappedByteBuffer byteBuffer =
                    cacheChannel.map(FileChannel.MapMode.READ_ONLY, 0, MAX_CHUNK_SIZE);
                cacheChannel.close();
                cacheFis.close();
                cacheChannel = null;
                cacheFis = null;

                Charset charset = Charset.forName(currentEncoding);
                final CharBuffer charBuffer = charset.decode(byteBuffer);
                // final String initialChunkText = charBuffer.toString();
                final String initialChunkText =
                    readChunkSafe(currentActiveFile, 0, MAX_CHUNK_SIZE, currentEncoding);

                // Advance pointer position safely
                currentFilePointer = MAX_CHUNK_SIZE;

                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        chunk_navigation_bar.setVisibility(View.VISIBLE);
                        editor.setEditorText(initialChunkText, true);

                        btn_chunk_prev.setEnabled(false);
                        btn_chunk_next.setEnabled(currentChunkIndex < totalChunksCount);
                        tv_chunk_status.setText(
                            "Chunk: " + currentChunkIndex + " / " + totalChunksCount);
                        et_chunk_jump.setText("0");

                        hideLoadingProgress();
                        hideKeyboard();
                      }
                    });

              } else {
                // LEGACY FILE PIPELINE: Standard native delivery route for ordinary configuration
                // files
                totalChunksCount = 1;
                currentChunkIndex = 1;
                currentActiveFile = null;

                MappedByteBuffer byteBuffer =
                    channel.map(FileChannel.MapMode.READ_ONLY, 0, totalFileSize);

                Charset charset = Charset.forName(currentEncoding);
                final CharBuffer charBuffer = charset.decode(byteBuffer);
                final String legacyFullText = charBuffer.toString();

                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        chunk_navigation_bar.setVisibility(View.GONE);
                        editor.setEditorText(legacyFullText, true);
                        hideLoadingProgress();
                        hideKeyboard();
                      }
                    });
              }

            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Error: Could not read the external file.",
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            } finally {
              // Hierarchical and independent safe close order
              try {
                if (targetChannel != null) {
                  targetChannel.close();
                  targetChannel = null;
                }
              } catch (Exception ignored) {
              }
              try {
                if (fos != null) {
                  fos.close();
                  fos = null;
                }
              } catch (Exception ignored) {
              }
              try {
                if (cacheChannel != null) {
                  cacheChannel.close();
                  cacheChannel = null;
                }
              } catch (Exception ignored) {
              }
              try {
                if (cacheFis != null) {
                  cacheFis.close();
                  cacheFis = null;
                }
              } catch (Exception ignored) {
              }
              try {
                if (channel != null) {
                  channel.close();
                  channel = null;
                }
              } catch (Exception ignored) {
              }
              try {
                if (fis != null) {
                  fis.close();
                  fis = null;
                }
              } catch (Exception ignored) {
              }
              try {
                if (pfd != null) {
                  pfd.close();
                  pfd = null;
                }
              } catch (Exception ignored) {
              }
            }
          }
        });
  }

  // OPEN-4: Secure asynchronous decryption handler utilizing AES-128 FileChannel processing and
  // size checks
  private void decryptFileAndShow(final Uri uri, final String userFourChars) {
    if (uri == null || userFourChars == null) return;

    String name = getFileName(uri);
    if (name == null || !name.toLowerCase().endsWith(".enc")) {
      Toast.makeText(this, "This is not an encrypted file.", Toast.LENGTH_LONG).show();
      return;
    }

    final String cleanInput = userFourChars.trim();
    if (cleanInput.length() != 4) {
      Toast.makeText(this, "Error: You must enter exactly 4 characters!", Toast.LENGTH_LONG).show();
      return;
    }

    // Clear all dirty chunk slots from any previous session before loading a new file
    Iterator<File> dirtyIt = dirtyChunksFileMap.values().iterator();
    while (dirtyIt.hasNext()) {
      File f = dirtyIt.next();
      if (f != null && f.exists()) f.delete();
    }
    dirtyChunksFileMap.clear();

    showLoadingProgress();

    // Dynamic key reconstruction pipeline: PREPASS (12 chars) + User Input (4 chars) = 16 bytes key
    // matrix
    this.PASSWORD = this.PREPASS + cleanInput;

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            ParcelFileDescriptor pfd = null;
            FileInputStream fis = null;
            FileChannel channel = null;
            FileOutputStream fos = null;

            // Declare data arrays at method scope so finally can clear them safely
            byte[] fileBytes = null;
            byte[] decryptedBytes = null;

            try {
              // 1. Prepare Key Configuration derived from our reconstructed 16 character password
              // asset
              byte[] keyBytes = PASSWORD.getBytes("UTF-8");
              final SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

              // 2. Read Encrypted Data using optimized native OS file channel buffer allocation
              pfd = getContentResolver().openFileDescriptor(uri, "r");
              if (pfd == null) throw new IOException("InputStream is null");

              fis = new FileInputStream(pfd.getFileDescriptor());
              channel = fis.getChannel();

              long channelSize = channel.size();
              ByteBuffer byteBuffer = ByteBuffer.allocate((int) channelSize);
              channel.read(byteBuffer);
              fileBytes = byteBuffer.array();

              // Deallocate stream descriptors immediately to release operating system handles
              channel.close();
              fis.close();
              pfd.close();
              channel = null;
              fis = null;
              pfd = null;

              // 3. Decrypt Payload Sequence
              Cipher cipher = Cipher.getInstance("AES");
              cipher.init(Cipher.DECRYPT_MODE, secretKey);
              decryptedBytes = cipher.doFinal(fileBytes);
              fileBytes = null; // Release sensitive bytes immediately

              // Cache absolute path details for subsequent application layers
              currentFileAbsolutePath = getRealPathFromUri(uri);
              currentFileName = getOnlyFileName(currentFileAbsolutePath);

              // Reset chunk pointers back to the beginning of the document
              currentFilePointer = 0;
              currentChunkIndex = 1;
              totalFileSize = decryptedBytes.length;

              // DECISION PIPELINE: Check if the decrypted size exceeds our chunk layout limits
              if (totalFileSize >= MAX_CHUNK_SIZE) {
                // Compute absolute total structural page partitions layout boundaries safely
                totalChunksCount = (int) ((totalFileSize + MAX_CHUNK_SIZE - 1) / MAX_CHUNK_SIZE);
                if (totalChunksCount < 1) totalChunksCount = 1;

                // Create a secure temporary cache file for seamless random access navigation
                currentActiveFile = new File(getCacheDir(), "decrypted_temp_cache.txt");
                if (currentActiveFile.exists()) currentActiveFile.delete();

                // Dump entire decrypted stream to persistent cache storage
                fos = new FileOutputStream(currentActiveFile);
                fos.write(decryptedBytes);
                fos.flush();
                fos.close();
                fos = null;
                decryptedBytes = null; // Release memory

                // Recalculate totalFileSize from the actual cache file to guarantee
                // Next/Prev offset calculations match the real cached content on disk
                totalFileSize = currentActiveFile.length();
                totalChunksCount = (int) ((totalFileSize + MAX_CHUNK_SIZE - 1) / MAX_CHUNK_SIZE);
                if (totalChunksCount < 1) totalChunksCount = 1;

                // Open channel to cache file to read the very first chunk fragment safely
                fis = new FileInputStream(currentActiveFile);
                channel = fis.getChannel();
                MappedByteBuffer chunkBuffer =
                    channel.map(FileChannel.MapMode.READ_ONLY, 0, MAX_CHUNK_SIZE);
                channel.close();
                fis.close();
                channel = null;
                fis = null;

                Charset charset = Charset.forName("UTF-8");
                final CharBuffer charBuffer = charset.decode(chunkBuffer);
                // final String initialChunkText = charBuffer.toString();
                final String initialChunkText =
                    readChunkSafe(currentActiveFile, 0, MAX_CHUNK_SIZE, "UTF-8");

                // Advance pointer position safely
                currentFilePointer = MAX_CHUNK_SIZE;

                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        chunk_navigation_bar.setVisibility(View.VISIBLE);
                        editor.setEditorText(initialChunkText, true);

                        btn_chunk_prev.setEnabled(false);
                        btn_chunk_next.setEnabled(currentChunkIndex < totalChunksCount);
                        tv_chunk_status.setText(
                            "Chunk: " + currentChunkIndex + " / " + totalChunksCount);

                        Toast.makeText(
                                getApplicationContext(),
                                "File decrypted successfully! (Large File Mode)",
                                Toast.LENGTH_SHORT)
                            .show();
                        hideLoadingProgress();
                        hideKeyboard();
                      }
                    });

              } else {
                // LEGACY FILE PIPELINE: Standard delivery route for small decrypted content
                totalChunksCount = 1;
                currentChunkIndex = 1;
                currentActiveFile = null;

                ByteBuffer decryptedByteBuffer = ByteBuffer.wrap(decryptedBytes);
                Charset charset = Charset.forName("UTF-8");
                final CharBuffer charBuffer = charset.decode(decryptedByteBuffer);
                final String legacyFullText = charBuffer.toString();
                decryptedBytes = null; // Release memory

                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        chunk_navigation_bar.setVisibility(View.GONE);
                        editor.setEditorText(legacyFullText, true);
                        hideKeyboard();

                        Toast.makeText(
                                getApplicationContext(),
                                "File decrypted successfully!",
                                Toast.LENGTH_SHORT)
                            .show();
                        hideLoadingProgress();
                      }
                    });
              }

            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Error: Wrong password or corrupted file.",
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            } finally {
              // Clear big byte arrays to release heap memory eagerly
              fileBytes = null;
              decryptedBytes = null;

              // Hierarchical and independent safe close order
              try {
                if (fos != null) {
                  fos.close();
                  fos = null;
                }
              } catch (Exception ignored) {
              }
              try {
                if (channel != null) {
                  channel.close();
                  channel = null;
                }
              } catch (Exception ignored) {
              }
              try {
                if (fis != null) {
                  fis.close();
                  fis = null;
                }
              } catch (Exception ignored) {
              }
              try {
                if (pfd != null) {
                  pfd.close();
                  pfd = null;
                }
              } catch (Exception ignored) {
              }
            }
          }
        });
  }

  private long getFileSize(Uri uri) {
    try {
      Cursor cursor = getContentResolver().query(uri, null, null, null, null);
      if (cursor != null) {
        int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
        cursor.moveToFirst();
        long size = cursor.getLong(sizeIndex);
        cursor.close();
        return size;
      }
    } catch (Exception e) {
      // Fallback
      try {
        InputStream is = getContentResolver().openInputStream(uri);
        if (is != null) {
          long size = is.available(); // Non-secure but fallback
          is.close();
          return size;
        }
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    }
    return -1;
  }

  // SAVE-1
  /**
   * Merges dirty chunks into the destination using the caller-supplied charset. The active charset
   * field is not modified.
   */
  private void saveTextToFile(final Uri uri, final String content, final Charset saveCharset) {
    if (uri == null) return;

    showLoadingProgress();
    final String flName = getFileName(uri);

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            ParcelFileDescriptor pfd = null;
            FileOutputStream fos = null;
            FileChannel targetChannel = null;

            FileInputStream originalFis = null;
            FileChannel originalChannel = null;

            FileOutputStream tempFos = null;
            FileChannel tempChannel = null;

            File tempSaveFile = null;

            try {
              if (totalFileSize > MAX_CHUNK_SIZE
                  && totalChunksCount > 1
                  && currentActiveFile != null
                  && currentActiveFile.exists()) {

                // Build a temporary merged file from dirty map + unedited original chunks
                tempSaveFile = new File(getCacheDir(), "temp_save_file.txt");
                if (tempSaveFile.exists()) tempSaveFile.delete();

                tempFos = new FileOutputStream(tempSaveFile);
                tempChannel = tempFos.getChannel();

                // Flush the currently visible editor viewport into its dirty slot before merging
                File currentTmpSlot =
                    new File(getCacheDir(), "dirty_chunk_" + currentChunkIndex + ".tmp");
                FileOutputStream currentTmpFos = new FileOutputStream(currentTmpSlot);
                if (content != null && !content.isEmpty()) {
                  currentTmpFos.write(content.getBytes(saveCharset));
                } else {
                  // Write a single newline so the slot is never zero-length
                  currentTmpFos.write("\n".getBytes(saveCharset));
                }
                currentTmpFos.flush();
                currentTmpFos.close();
                dirtyChunksFileMap.put(currentChunkIndex, currentTmpSlot);

                // Merge all chunks in order: dirty slots override original file regions
                for (int i = 1; i <= totalChunksCount; i++) {
                  if (dirtyChunksFileMap.containsKey(i)) {
                    File tmpFile = dirtyChunksFileMap.get(i);
                    if (tmpFile != null && tmpFile.exists() && tmpFile.length() > 0) {
                      FileInputStream fisTmp = new FileInputStream(tmpFile);
                      FileChannel chanTmp = fisTmp.getChannel();
                      chanTmp.transferTo(0, chanTmp.size(), tempChannel);
                      chanTmp.close();
                      fisTmp.close();
                    }
                    // If the dirty slot is empty (user cleared that chunk),
                    // we write nothing — the chunk is intentionally removed from output
                  } else {
                    // Read the untouched region directly from the original cache file
                    long chunkStartOffset = (long) (i - 1) * MAX_CHUNK_SIZE;
                    long remaining = totalFileSize - chunkStartOffset;
                    long bytesToStream = (remaining > MAX_CHUNK_SIZE) ? MAX_CHUNK_SIZE : remaining;

                    if (bytesToStream > 0) {
                      originalFis = new FileInputStream(currentActiveFile);
                      originalChannel = originalFis.getChannel();
                      originalChannel.transferTo(chunkStartOffset, bytesToStream, tempChannel);
                      originalChannel.close();
                      originalFis.close();
                      originalChannel = null;
                      originalFis = null;
                    }
                  }
                }

                tempChannel.close();
                tempFos.close();
                tempChannel = null;
                tempFos = null;

                // Write the merged temp file to the destination URI
                pfd = getContentResolver().openFileDescriptor(uri, "wt");
                if (pfd == null) throw new IOException("Unable to open write descriptor.");

                fos = new FileOutputStream(pfd.getFileDescriptor());
                targetChannel = fos.getChannel();

                FileInputStream tempSrcFis = new FileInputStream(tempSaveFile);
                FileChannel tempSrcChannel = tempSrcFis.getChannel();
                tempSrcChannel.transferTo(0, tempSrcChannel.size(), targetChannel);
                tempSrcChannel.close();
                tempSrcFis.close();

                targetChannel.close();
                fos.close();

                try {
                  pfd.getFileDescriptor().sync();
                } catch (Exception ignored) {
                }

                pfd.close();
                targetChannel = null;
                fos = null;
                pfd = null;

                // Clear all dirty slots — they are now baked into the merged file
                Iterator<File> dirtyIterator = dirtyChunksFileMap.values().iterator();
                while (dirtyIterator.hasNext()) {
                  File f = dirtyIterator.next();
                  if (f != null && f.exists()) f.delete();
                }
                dirtyChunksFileMap.clear();

                // Promote the merged temp file as the new active cache file
                File newActiveFile = new File(getCacheDir(), "large_uri_temp_cache_saved.txt");
                if (newActiveFile.exists()) newActiveFile.delete();
                tempSaveFile.renameTo(newActiveFile);
                tempSaveFile = null; // Ownership transferred — skip finally-block deletion

                currentActiveFile = newActiveFile;

                // Update path metadata to match the save destination
                currentFileAbsolutePath = getRealPathFromUri(uri);
                currentFileName = getOnlyFileName(currentFileAbsolutePath);

                // Capture the new active file reference for the re-open call on the main thread
                final File reopenTarget = currentActiveFile;
                final String savedFileName = flName;
                final String savedCharsetName = saveCharset.name();

                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        Toast.makeText(
                                getApplicationContext(),
                                "Saved: "
                                    + (savedFileName != null ? savedFileName : "")
                                    + " ("
                                    + savedCharsetName
                                    + ")",
                                Toast.LENGTH_SHORT)
                            .show();

                        // Reset chunk tracking so openFileWithName treats this
                        // as a fresh load and recalculates all offsets correctly
                        currentFilePointer = 0;
                        currentChunkIndex = 1;

                        // Re-open the merged cache file to rebuild chunk offsets
                        // against the real post-save file size on disk
                        openFileWithName(reopenTarget.getAbsolutePath(), 0);
                      }
                    });

              } else {
                // SINGLE CHUNK / SMALL FILE PATH
                pfd = getContentResolver().openFileDescriptor(uri, "wt");
                if (pfd == null)
                  throw new IOException("Save Error: ContentResolver returned null descriptor");

                fos = new FileOutputStream(pfd.getFileDescriptor());
                targetChannel = fos.getChannel();

                if (content != null) {
                  byte[] contentBytes = content.getBytes(saveCharset);
                  targetChannel.write(ByteBuffer.wrap(contentBytes));
                }

                targetChannel.close();
                fos.close();

                try {
                  pfd.getFileDescriptor().sync();
                } catch (Exception ignored) {
                }

                pfd.close();
                targetChannel = null;
                fos = null;
                pfd = null;

                totalFileSize = (content != null) ? content.getBytes(saveCharset).length : 0;
                totalChunksCount = 1;
                currentChunkIndex = 1;
                currentActiveFile = null;

                currentFileAbsolutePath = getRealPathFromUri(uri);
                currentFileName = getOnlyFileName(currentFileAbsolutePath);

                final String finalContent = content;
                final String savedFileName = flName;
                final String savedCharsetName = saveCharset.name();

                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        editor.setEditorText((finalContent != null ? finalContent : ""), true);
                        chunk_navigation_bar.setVisibility(View.GONE);
                        btn_chunk_prev.setEnabled(false);
                        btn_chunk_next.setEnabled(false);
                        tv_chunk_status.setText("Chunk: 1 / 1");

                        Toast.makeText(
                                getApplicationContext(),
                                "Saved: "
                                    + (savedFileName != null ? savedFileName : "")
                                    + " ("
                                    + savedCharsetName
                                    + ")",
                                Toast.LENGTH_SHORT)
                            .show();
                        hideLoadingProgress();
                      }
                    });
              }

            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Save Error: " + e.getMessage(),
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            } finally {
              // Safeguard cleanup logic by validating reference state before invoking deallocation
              try {
                if (targetChannel != null) targetChannel.close();
              } catch (Exception ignored) {
              }
              try {
                if (fos != null) fos.close();
              } catch (Exception ignored) {
              }
              try {
                if (pfd != null) pfd.close();
              } catch (Exception ignored) {
              }
              try {
                if (tempChannel != null) tempChannel.close();
              } catch (Exception ignored) {
              }
              try {
                if (tempFos != null) tempFos.close();
              } catch (Exception ignored) {
              }
              try {
                if (originalChannel != null) originalChannel.close();
              } catch (Exception ignored) {
              }
              try {
                if (originalFis != null) originalFis.close();
              } catch (Exception ignored) {
              }

              // Only delete tempSaveFile if ownership was NOT transferred via rename
              if (tempSaveFile != null && tempSaveFile.exists()) tempSaveFile.delete();

              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      hideKeyboard();
                    }
                  });
            }
          }
        });
  }

  // SAVE-2
  private void saveEncryptedToFile(
      final Uri uri, final String content, final String userFourChars) {
    if (uri == null || content == null || userFourChars == null) return;

    final String cleanInput = userFourChars.trim();
    if (cleanInput.length() != 4) {
      Toast.makeText(this, "Error: You must enter exactly 4 characters!", Toast.LENGTH_LONG).show();
      return;
    }

    showLoadingProgress();
    final String flName = getFileName(uri);
    this.PASSWORD = this.PREPASS + cleanInput;

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            ParcelFileDescriptor pfd = null;
            FileOutputStream fos = null;

            FileInputStream originalFis = null;
            FileChannel originalChannel = null;

            FileOutputStream tempFos = null;
            FileChannel tempChannel = null;

            File tempPlainFile = null;

            try {
              byte[] keyBytes = PASSWORD.getBytes("UTF-8");
              SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
              byte[] finalEncryptedData = null;

              if (totalFileSize > MAX_CHUNK_SIZE
                  && totalChunksCount > 1
                  && currentActiveFile != null
                  && currentActiveFile.exists()) {

                // Build a merged plaintext file from dirty slots + unedited original chunks
                tempPlainFile = new File(getCacheDir(), "temp_plain_merge.txt");
                if (tempPlainFile.exists()) tempPlainFile.delete();

                tempFos = new FileOutputStream(tempPlainFile);
                tempChannel = tempFos.getChannel();

                // Flush the current editor viewport into its dirty slot
                File currentTmpSlot =
                    new File(getCacheDir(), "dirty_chunk_" + currentChunkIndex + ".tmp");
                FileOutputStream currentTmpFos = new FileOutputStream(currentTmpSlot);
                if (content != null && !content.isEmpty()) {
                  currentTmpFos.write(content.getBytes("UTF-8"));
                } else {
                  currentTmpFos.write("\n".getBytes("UTF-8"));
                }
                currentTmpFos.flush();
                currentTmpFos.close();
                dirtyChunksFileMap.put(currentChunkIndex, currentTmpSlot);

                // Merge all chunks: dirty slots override original file regions
                for (int i = 1; i <= totalChunksCount; i++) {
                  if (dirtyChunksFileMap.containsKey(i)) {
                    File tmpFile = dirtyChunksFileMap.get(i);
                    if (tmpFile != null && tmpFile.exists() && tmpFile.length() > 0) {
                      FileInputStream fisTmp = new FileInputStream(tmpFile);
                      FileChannel chanTmp = fisTmp.getChannel();
                      chanTmp.transferTo(0, chanTmp.size(), tempChannel);
                      chanTmp.close();
                      fisTmp.close();
                    }
                    // Empty dirty slot = user cleared that chunk intentionally
                  } else {
                    long chunkStartOffset = (long) (i - 1) * MAX_CHUNK_SIZE;
                    long remaining = totalFileSize - chunkStartOffset;
                    long bytesToStream = (remaining > MAX_CHUNK_SIZE) ? MAX_CHUNK_SIZE : remaining;

                    if (bytesToStream > 0) {
                      originalFis = new FileInputStream(currentActiveFile);
                      originalChannel = originalFis.getChannel();
                      originalChannel.transferTo(chunkStartOffset, bytesToStream, tempChannel);
                      originalChannel.close();
                      originalFis.close();
                      originalChannel = null;
                      originalFis = null;
                    }
                  }
                }

                tempChannel.close();
                tempFos.close();
                tempChannel = null;
                tempFos = null;

                // Read merged plaintext and encrypt it
                long plainFileSize = tempPlainFile.length();
                FileInputStream plainFis = new FileInputStream(tempPlainFile);
                FileChannel plainChannel = plainFis.getChannel();
                ByteBuffer plainBuffer = ByteBuffer.allocate((int) plainFileSize);
                plainChannel.read(plainBuffer);
                byte[] rawPlainBytes = plainBuffer.array();
                plainChannel.close();
                plainFis.close();

                Cipher encCipher = Cipher.getInstance("AES");
                encCipher.init(Cipher.ENCRYPT_MODE, secretKey);
                finalEncryptedData = encCipher.doFinal(rawPlainBytes);
                rawPlainBytes = null;

                // Write encrypted output to the destination URI
                pfd = getContentResolver().openFileDescriptor(uri, "wt");
                if (pfd == null) throw new IOException("Encryption failed: null descriptor");
                fos = new FileOutputStream(pfd.getFileDescriptor());
                fos.write(finalEncryptedData);
                fos.flush();

                try {
                  pfd.getFileDescriptor().sync();
                } catch (Exception ignored) {
                }

                fos.close();
                pfd.close();
                fos = null;
                pfd = null;
                finalEncryptedData = null;

                // Clear all dirty slots — baked into the merged plaintext file
                Iterator<File> dirtyIterator = dirtyChunksFileMap.values().iterator();
                while (dirtyIterator.hasNext()) {
                  File f = dirtyIterator.next();
                  if (f != null && f.exists()) f.delete();
                }
                dirtyChunksFileMap.clear();

                // Promote the merged plaintext file as the new active cache
                File newActiveFile = new File(getCacheDir(), "decrypted_temp_cache_saved.txt");
                if (newActiveFile.exists()) newActiveFile.delete();
                tempPlainFile.renameTo(newActiveFile);
                tempPlainFile = null; // Ownership transferred — skip finally-block deletion

                currentActiveFile = newActiveFile;

                currentFileAbsolutePath = getRealPathFromUri(uri);
                currentFileName = getOnlyFileName(currentFileAbsolutePath);

                final File reopenTarget = currentActiveFile;
                final String savedFileName = flName;

                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        Toast.makeText(
                                getApplicationContext(),
                                "Encrypted file saved: "
                                    + (savedFileName != null ? savedFileName : ""),
                                Toast.LENGTH_SHORT)
                            .show();

                        // Reset chunk tracking so openFileWithName treats this
                        // as a fresh load and recalculates all offsets correctly
                        currentFilePointer = 0;
                        currentChunkIndex = 1;

                        // Re-open the merged cache file to rebuild chunk offsets
                        // against the real post-save file size on disk
                        openFileWithName(reopenTarget.getAbsolutePath(), 0);
                      }
                    });

              } else {
                // SINGLE CHUNK / SMALL FILE PATH
                byte[] contentBytes = content.getBytes("UTF-8");
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                finalEncryptedData = cipher.doFinal(contentBytes);

                pfd = getContentResolver().openFileDescriptor(uri, "wt");
                if (pfd == null) throw new IOException("Encryption failed: null descriptor");
                fos = new FileOutputStream(pfd.getFileDescriptor());
                fos.write(finalEncryptedData);
                fos.flush();

                try {
                  pfd.getFileDescriptor().sync();
                } catch (Exception ignored) {
                }

                fos.close();
                pfd.close();
                fos = null;
                pfd = null;

                totalFileSize = contentBytes.length;
                totalChunksCount = 1;
                currentChunkIndex = 1;
                currentActiveFile = null;

                currentFileAbsolutePath = getRealPathFromUri(uri);
                currentFileName = getOnlyFileName(currentFileAbsolutePath);

                final String finalContent = content;
                final String savedFileName = flName;

                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        editor.setEditorText((finalContent != null ? finalContent : ""), true);
                        chunk_navigation_bar.setVisibility(View.GONE);
                        btn_chunk_prev.setEnabled(false);
                        btn_chunk_next.setEnabled(false);
                        tv_chunk_status.setText("Chunk: 1 / 1");

                        Toast.makeText(
                                getApplicationContext(),
                                "Encrypted file saved: "
                                    + (savedFileName != null ? savedFileName : ""),
                                Toast.LENGTH_SHORT)
                            .show();
                        hideLoadingProgress();
                      }
                    });
              }

            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Encryption failed: " + e.getMessage(),
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            } finally {
              // Safeguard cleanup logic by validating reference state before invoking deallocation
              try {
                if (tempChannel != null) tempChannel.close();
              } catch (Exception ignored) {
              }
              try {
                if (tempFos != null) tempFos.close();
              } catch (Exception ignored) {
              }
              try {
                if (originalChannel != null) originalChannel.close();
              } catch (Exception ignored) {
              }
              try {
                if (originalFis != null) originalFis.close();
              } catch (Exception ignored) {
              }
              try {
                if (fos != null) fos.close();
              } catch (Exception ignored) {
              }
              try {
                if (pfd != null) pfd.close();
              } catch (Exception ignored) {
              }

              // Only delete tempPlainFile if ownership was NOT transferred via rename
              if (tempPlainFile != null && tempPlainFile.exists()) tempPlainFile.delete();

              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      hideKeyboard();
                    }
                  });
            }
          }
        });
  }

  /**
   * Writes the current editor text as a standalone segment using the supplied charset. The active
   * charset field is not modified.
   */
  private void saveCurrentSegmentToFile(
      final Uri destinationUri, final String content, final Charset saveCharset) {
    if (destinationUri == null) return;

    showLoadingProgress();
    final String targetFileName = getFileName(destinationUri);

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            // Try-with-resources handles close automatically
            try (ParcelFileDescriptor pfd =
                    getContentResolver().openFileDescriptor(destinationUri, "wt");
                FileOutputStream fos =
                    (pfd != null) ? new FileOutputStream(pfd.getFileDescriptor()) : null;
                FileChannel targetChannel = (fos != null) ? fos.getChannel() : null) {

              if (pfd == null || fos == null || targetChannel == null) {
                throw new IOException(
                    "Save Segment Error: System layout architecture returned a null descriptor"
                        + " reference.");
              }

              if (content != null && !content.isEmpty()) {
                byte[] segmentBytes = content.getBytes(saveCharset);
                ByteBuffer buffer = ByteBuffer.wrap(segmentBytes);
                targetChannel.write(buffer);
              }

              final String savedCharsetName = saveCharset.name();

              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Segment exported successfully! Saved as: "
                                  + (targetFileName != null ? targetFileName : "")
                                  + " ("
                                  + savedCharsetName
                                  + ")",
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });

            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Export Segment Failure: " + e.getMessage(),
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            }
          }
        });
  }

  private void executeCleanLargerCharacters() {
    if (editor == null || optionPane == null) return;

    // Request the threshold from the user via our unified lightweight input engine
    optionPane.showInputDialog(
        "Clean characters larger than(decimal or 0xNUM):",
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            // Circuit breaker if user cancelled the input operation (returns null)
            if (input == null) {
              return;
            }

            final String cleanInput = input.trim();
            if (cleanInput.isEmpty()) return;

            // Parse the input supporting both standard decimal and 0x hex patterns
            final int threshold;
            try {
              if (cleanInput.toLowerCase(mylocale).startsWith("0x")) {
                // Extract the hex part after 0x and parse using base 16 radix
                String hexSubstring = cleanInput.substring(2);
                threshold = Integer.parseInt(hexSubstring, 16);
              } else {
                // Parse as standard decimal radix 10
                threshold = Integer.parseInt(cleanInput);
              }
            } catch (NumberFormatException e) {
              // Securely warn user about invalid syntax on the main thread channel
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      optionPane.showMessageDialog(
                          "Invalid Input: Please enter a valid decimal or 0x hex value.");
                    }
                  });
              return;
            }

            showLoadingProgress();
            final String sourceText = editor.getFullText();

            // Standardized background execution utilizing the shared file IO executor channel
            fileIOExecutor.execute(
                new Runnable() {
                  @Override
                  public void run() {
                    int length = sourceText.length();
                    StringBuilder result = new StringBuilder(length);

                    // Scan every single character using older Java specifications loops
                    for (int i = 0; i < length; i++) {
                      char currentChar = sourceText.charAt(i);

                      // Rule: Wipe out the character if its decimal representation is equal or
                      // larger
                      if ((int) currentChar >= threshold) {
                        continue;
                      }
                      result.append(currentChar);
                    }

                    final String finalResult = result.toString();

                    // Dispatch filtered content safely back to the primary main UI thread
                    mainHandler.post(
                        new Runnable() {
                          @Override
                          public void run() {
                            editor.setEditorText(finalResult, true);
                            hideLoadingProgress();
                          }
                        });
                  }
                });
          }
        });
  }

  private void executeRemoveStarts() {
    if (editor == null) return;

    // Transitioned to our lightweight MOptionPane input engine for a unified UI language
    optionPane.showInputDialog(
        "Remove lines starting with:",
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            // Circuit breaker if user cancelled the input operation (returns null)
            if (input == null) {
              return;
            }

            final String filterText = input;
            if (filterText.isEmpty()) return;

            showLoadingProgress();
            final String sourceText = editor.getFullText();

            // Standardized background execution utilizing the shared file IO executor channel
            fileIOExecutor.execute(
                new Runnable() {
                  @Override
                  public void run() {
                    String[] lines = sourceText.split("\n", -1);
                    StringBuilder result = new StringBuilder();

                    for (int i = 0; i < lines.length; i++) {
                      String currentLine = lines[i];
                      // Rule: Check if the trimmed line starts with the target expression
                      if (currentLine.trim().startsWith(filterText)) {
                        continue;
                      }
                      if (result.length() > 0) {
                        result.append("\n");
                      }
                      result.append(currentLine);
                    }

                    final String finalResult = result.toString();

                    // Dispatch filtered content safely back to the primary main UI thread
                    mainHandler.post(
                        new Runnable() {
                          @Override
                          public void run() {
                            editor.setEditorText(finalResult, true);
                            hideLoadingProgress();
                          }
                        });
                  }
                });
          }
        });
  }

  private void executeRemoveContains() {
    if (editor == null) return;

    // Transitioned to our lightweight MOptionPane input engine for a unified UI language
    optionPane.showInputDialog(
        "Remove lines containing:",
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            // Circuit breaker if user cancelled the input operation (returns null)
            if (input == null) {
              return;
            }

            final String filterText = input;
            if (filterText.isEmpty()) return;

            showLoadingProgress();
            final String sourceText = editor.getFullText();

            // Standardized background execution utilizing the shared file IO executor channel
            fileIOExecutor.execute(
                new Runnable() {
                  @Override
                  public void run() {
                    String[] lines = sourceText.split("\n", -1);
                    StringBuilder result = new StringBuilder();

                    for (int i = 0; i < lines.length; i++) {
                      String currentLine = lines[i];
                      // Rule: Check if the line contains the target expression
                      if (currentLine.contains(filterText)) {
                        continue;
                      }
                      if (result.length() > 0) {
                        result.append("\n");
                      }
                      result.append(currentLine);
                    }

                    final String finalResult = result.toString();

                    // Dispatch filtered content safely back to the primary main UI thread
                    mainHandler.post(
                        new Runnable() {
                          @Override
                          public void run() {
                            editor.setEditorText(finalResult, true);
                            hideLoadingProgress();
                          }
                        });
                  }
                });
          }
        });
  }

  private void executeRemoveNotContains() {
    if (editor == null) return;

    // Transitioned to our lightweight MOptionPane input engine for a unified UI language
    optionPane.showInputDialog(
        "Remove lines NOT containing:",
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            // Circuit breaker if user cancelled the input operation (returns null)
            if (input == null) {
              return;
            }

            final String filterText = input;
            if (filterText.isEmpty()) return;

            showLoadingProgress();
            final String sourceText = editor.getFullText();

            // Standardized background execution utilizing the shared file IO executor channel
            fileIOExecutor.execute(
                new Runnable() {
                  @Override
                  public void run() {
                    String[] lines = sourceText.split("\n", -1);
                    StringBuilder result = new StringBuilder();

                    for (int i = 0; i < lines.length; i++) {
                      String currentLine = lines[i];
                      // Rule: Check if the line does NOT contain the target expression
                      if (!currentLine.contains(filterText)) {
                        continue;
                      }
                      if (result.length() > 0) {
                        result.append("\n");
                      }
                      result.append(currentLine);
                    }

                    final String finalResult = result.toString();

                    // Dispatch filtered content safely back to the primary main UI thread
                    mainHandler.post(
                        new Runnable() {
                          @Override
                          public void run() {
                            editor.setEditorText(finalResult, true);
                            hideLoadingProgress();
                          }
                        });
                  }
                });
          }
        });
  }

  private void executeRemovePageNumbers() {
    if (editor == null) return;

    showLoadingProgress();
    final String sourceText = editor.getText().toString();

    // Standardized background execution utilizing the shared file IO executor channel
    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            String[] lines = sourceText.split("\n", -1);
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < lines.length; i++) {
              String currentLine = lines[i];

              // Rule: Trim line and replace all inner spaces/tabs with empty string
              String cleanedLine = currentLine.trim().replace(" ", "").replace("\t", "");

              // Check if the resulting cleaned string consists strictly of digits
              if (!cleanedLine.isEmpty() && isOnlyDigits(cleanedLine)) {
                result.append("\n");
                continue;
              }

              if (result.length() > 0) {
                result.append("\n");
              }
              result.append(currentLine);
            }

            final String finalResult = result.toString();

            // Dispatch filtered content safely back to the primary main UI thread
            mainHandler.post(
                new Runnable() {
                  @Override
                  public void run() {
                    editor.setEditorText(finalResult, true);
                    hideLoadingProgress();
                  }
                });
          }

          // Helper method to natively verify integer sequences without relying on regex overhead
          private boolean isOnlyDigits(String str) {
            for (int i = 0; i < str.length(); i++) {
              if (!Character.isDigit(str.charAt(i))) {
                return false;
              }
            }
            return true;
          }
        });
  }

  private void executeCalculate() {
    // Ensure optionPane is initialized properly using the host activity context
    if (optionPane == null) {
      optionPane = new MOptionPane(this);
    }

    // Comprehensive instruction message to guide the user on supported operators and layout format
    String message =
        "Enter a mathematical expression to evaluate.\n"
            + "Supported operators: +, -, *, /, x, ÷\n"
            + "Example: 5.5 + 4.5 or 10÷3";

    // Launch the high-comfort input dialog with zero-populated default text
    optionPane.showInputDialog(
        message,
        "",
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            // If user clicked 'Cancel' or closed the dialog, input will be null
            if (input == null) {
              return;
            }

            // Execute processing using our static stateless Calculator utility
            String result = Calculator.calculate(input);

            // Display the formatted evaluation outcome back to the user via a clean message dialog
            optionPane.showMessageDialog("Result: " + input + "=" + result);
          }
        });
  }

  // =====================================================================
  // ENCODE / DECODE SHARED HELPERS
  // =====================================================================

  /**
   * Returns the text that the encode/decode tools should operate on: the current selection if there
   * is one, otherwise the whole editor content.
   *
   * <p>Returns null (after showing a Toast) when:
   *
   * <ul>
   *   <li>the editor is null,
   *   <li>the target text is empty,
   *   <li>the target text exceeds {@link #MAX_ENCODE_INPUT} UTF-8 bytes.
   * </ul>
   *
   * <p>The check order is deliberate: the cheap char-length test runs first so that gigantic
   * documents are rejected without allocating a byte array for them (a UTF-16 char is at least 1
   * UTF-8 byte, so a length above the limit in chars is always above the limit in bytes).
   */
  private String getEncodeTargetOrWarn() {
    if (editor == null) return null;

    final int selStart = editor.getSelectionStart();
    final int selEnd = editor.getSelectionEnd();
    final boolean hasSelection = (selStart >= 0 && selEnd >= 0 && selStart != selEnd);

    final String target;
    if (hasSelection) {
      target =
          editor
              .getText()
              .subSequence(Math.min(selStart, selEnd), Math.max(selStart, selEnd))
              .toString();
    } else {
      target = editor.getFullText();
    }

    if (target.isEmpty()) {
      Toast.makeText(this, "Text is empty!", Toast.LENGTH_SHORT).show();
      return null;
    }

    // Fast pre-check: char count can never exceed byte count for UTF-8.
    // Reject huge inputs before materializing a byte array.
    if (target.length() > MAX_ENCODE_INPUT) {
      Toast.makeText(
              this,
              "Text too long, max 16 KB (current: at least " + (target.length() / 1024) + " KB)",
              Toast.LENGTH_LONG)
          .show();
      return null;
    }

    // Exact byte-length check for inputs that passed the fast pre-check.
    final int byteLen;
    try {
      byteLen = target.getBytes("UTF-8").length;
    } catch (Exception e) {
      Toast.makeText(this, "Encoding error", Toast.LENGTH_SHORT).show();
      return null;
    }

    if (byteLen > MAX_ENCODE_INPUT) {
      Toast.makeText(
              this,
              "Text too long, max 16 KB (current: " + (byteLen / 1024) + " KB)",
              Toast.LENGTH_LONG)
          .show();
      return null;
    }

    return target;
  }

  /**
   * Writes the encode/decode result back into the editor.
   *
   * <p>When {@code hasSelection} is true, only the selected range is replaced and the caret is
   * moved right after the inserted text. When it is false, the entire editor content is replaced
   * (and the caret goes to the start, matching {@code setEditorText}).
   *
   * <p>Must be called on the main thread.
   */
  private void applyEncodeResult(final String newContent, final boolean hasSelection) {
    if (editor == null) return;
    if (hasSelection) {
      // Selection path already preserves the caret by construction:
      // we replace only the selected range and re-anchor right after it.
      final int start = editor.getSelectionStart();
      final int end = editor.getSelectionEnd();
      final int min = Math.min(start, end);
      final int max = Math.max(start, end);
      editor.getText().replace(min, max, newContent);
      editor.setSelection(min + newContent.length());
    } else {
      // Whole-document path: preserve the caret near its previous position.
      // Previously this was editor.setEditorText(newContent) which always
      // jumped to 0 — that is the bug this change fixes.
      editor.setEditorText(newContent, true);
    }
  }

  // =====================================================================
  // BASE64 ENCODE / DECODE
  // =====================================================================

  private void executeBase64Encode() {
    if (editor == null) return;

    // Capture selection boundaries on the main thread before going async.
    final int selStart = editor.getSelectionStart();
    final int selEnd = editor.getSelectionEnd();
    final boolean hasSelection = (selStart >= 0 && selEnd >= 0 && selStart != selEnd);

    // Enforces the "selection first, else whole text" rule and the 16 KB cap.
    final String target = getEncodeTargetOrWarn();
    if (target == null) return;

    showLoadingProgress();

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              byte[] contentBytes = target.getBytes("UTF-8");
              final String encoded =
                  android.util.Base64.encodeToString(contentBytes, android.util.Base64.NO_WRAP);

              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      applyEncodeResult(encoded, hasSelection);
                      Toast.makeText(
                              getApplicationContext(),
                              "Base64 encoded successfully",
                              Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Encode Error: " + e.getMessage(),
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            }
          }
        });
  }

  private void executeBase64Decode() {
    if (editor == null) return;

    // Capture selection boundaries on the main thread before going async.
    final int selStart = editor.getSelectionStart();
    final int selEnd = editor.getSelectionEnd();
    final boolean hasSelection = (selStart >= 0 && selEnd >= 0 && selStart != selEnd);

    // Enforces the "selection first, else whole text" rule and the 16 KB cap.
    final String target = getEncodeTargetOrWarn();
    if (target == null) return;

    showLoadingProgress();

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              // Strip all whitespace and newlines before decoding to be safe.
              String cleaned = target.trim().replaceAll("\\s", "");
              byte[] decodedBytes =
                  android.util.Base64.decode(cleaned, android.util.Base64.NO_WRAP);
              final String decoded = new String(decodedBytes, "UTF-8");

              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      applyEncodeResult(decoded, hasSelection);
                      Toast.makeText(
                              getApplicationContext(),
                              "Base64 decoded successfully",
                              Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Decode Error: Invalid Base64 input.",
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            }
          }
        });
  }

  // =====================================================================
  // URL ENCODE / DECODE
  // =====================================================================

  private void executeUrlEncode() {
    if (editor == null) return;

    // Capture selection boundaries on the main thread before going async.
    final int selStart = editor.getSelectionStart();
    final int selEnd = editor.getSelectionEnd();
    final boolean hasSelection = (selStart >= 0 && selEnd >= 0 && selStart != selEnd);

    // Enforces the "selection first, else whole text" rule and the 16 KB cap.
    final String target = getEncodeTargetOrWarn();
    if (target == null) return;

    showLoadingProgress();

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              final String encoded =
                  java.net.URLEncoder.encode(target, "UTF-8")
                      .replace("+", "%20"); // Prefer %20 over + for space encoding

              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      applyEncodeResult(encoded, hasSelection);
                      Toast.makeText(
                              getApplicationContext(),
                              "URL encoded successfully",
                              Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "URL Encode Error: " + e.getMessage(),
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            }
          }
        });
  }

  private void executeUrlDecode() {
    if (editor == null) return;

    // Capture selection boundaries on the main thread before going async.
    final int selStart = editor.getSelectionStart();
    final int selEnd = editor.getSelectionEnd();
    final boolean hasSelection = (selStart >= 0 && selEnd >= 0 && selStart != selEnd);

    // Enforces the "selection first, else whole text" rule and the 16 KB cap.
    final String target = getEncodeTargetOrWarn();
    if (target == null) return;

    showLoadingProgress();

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              final String decoded = java.net.URLDecoder.decode(target, "UTF-8");

              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      applyEncodeResult(decoded, hasSelection);
                      Toast.makeText(
                              getApplicationContext(),
                              "URL decoded successfully",
                              Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "URL Decode Error: Invalid input.",
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            }
          }
        });
  }

  // =====================================================================
  // SORT LINES
  // =====================================================================

  private void executeSortLines() {
    if (editor == null || optionPane == null) return;

    final String[] sortOptions = {"A → Z (Ascending)", "Z → A (Descending)"};

    optionPane.showSingleChoiceDialog(
        "Sort Lines",
        sortOptions,
        new MOptionPane.OnChoiceListener() {
          @Override
          public void onResult(int which) {
            if (which == -1) return;

            final boolean ascending = (which == 0);
            final String content = editor.getFullText();

            if (content.isEmpty()) {
              Toast.makeText(getApplicationContext(), "Editor is empty!", Toast.LENGTH_SHORT)
                  .show();
              return;
            }

            showLoadingProgress();

            fileIOExecutor.execute(
                new Runnable() {
                  @Override
                  public void run() {
                    try {
                      // Split preserving empty lines (-1 limit keeps trailing empty entries)
                      String[] lines = content.split("\n", -1);

                      ArrayList<String> lineList = new ArrayList<String>(Arrays.asList(lines));

                      final Collator trCollator = Collator.getInstance(Locale.of("tr", "TR"));
                      trCollator.setStrength(java.text.Collator.SECONDARY);

                      Collections.sort(
                          lineList,
                          new Comparator<String>() {
                            @Override
                            public int compare(String a, String b) {
                              if (ascending) {
                                return trCollator.compare(a, b);
                              } else {
                                return trCollator.compare(b, a);
                              }
                            }
                          });

                      // Rebuild the text without using TextUtils to stay Java 6-8 clean
                      StringBuilder sb = new StringBuilder(content.length());
                      for (int i = 0; i < lineList.size(); i++) {
                        if (i > 0) sb.append('\n');
                        sb.append(lineList.get(i));
                      }

                      final String finalResult = sb.toString();

                      mainHandler.post(
                          new Runnable() {
                            @Override
                            public void run() {
                              editor.setEditorText(finalResult, true);
                              Toast.makeText(
                                      getApplicationContext(),
                                      "Lines sorted successfully",
                                      Toast.LENGTH_SHORT)
                                  .show();
                              hideLoadingProgress();
                            }
                          });
                    } catch (final Exception e) {
                      mainHandler.post(
                          new Runnable() {
                            @Override
                            public void run() {
                              Toast.makeText(
                                      getApplicationContext(),
                                      "Sort Error: " + e.getMessage(),
                                      Toast.LENGTH_LONG)
                                  .show();
                              hideLoadingProgress();
                            }
                          });
                    }
                  }
                });
          }
        });
  }

  private void executeTrimLines() {
    if (editor == null) return;

    // 1. Fetch text structure safely without breaking main scheduler loop
    final String content = editor.getText().toString();
    if (content.isEmpty()) {
      Toast.makeText(this, "Nothing to trim", Toast.LENGTH_SHORT).show();
      return;
    }

    showLoadingProgress();

    // Standardized background execution utilizing the shared file IO executor channel
    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              // PERFORMANCE: Pre-allocate StringBuilder capacity to minimize memory re-allocations
              final StringBuilder sb = new StringBuilder(content.length());
              int len = content.length();
              int start = 0;

              // Index-based line parser instead of heavy regex split() array allocations
              while (start < len) {
                int nextLineBreak = content.indexOf('\n', start);
                int end = (nextLineBreak == -1) ? len : nextLineBreak;

                // Manual trim optimization: compute non-whitespace start and end bounds
                int lineStart = start;
                int lineEnd = end;

                while (lineStart < lineEnd && content.charAt(lineStart) <= ' ') {
                  lineStart++;
                }
                while (lineEnd > lineStart && content.charAt(lineEnd - 1) <= ' ') {
                  lineEnd--;
                }

                sb.append(content, lineStart, lineEnd);

                if (nextLineBreak != -1) {
                  sb.append('\n');
                }
                start = end + 1;
              }

              final String finalResult = sb.toString();

              // Dispatch trimmed content safely back to the primary main UI thread
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      editor.setEditorText(finalResult, true);
                      Toast.makeText(
                              getApplicationContext(), "All lines trimmed", Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            } catch (Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(), "Error trimming lines", Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            }
          }
        });
  }

  // =====================================================================
  // REMOVE EMPTY LINES
  // =====================================================================

  private void executeRemoveEmptyLines() {
    if (editor == null) return;
    final String content = editor.getFullText();

    if (content.isEmpty()) {
      Toast.makeText(this, "Editor is empty!", Toast.LENGTH_SHORT).show();
      return;
    }

    showLoadingProgress();

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              String[] lines = content.split("\n", -1);
              StringBuilder sb = new StringBuilder(content.length());
              boolean firstWritten = false;

              for (int i = 0; i < lines.length; i++) {
                // A line is considered empty if it contains only whitespace
                if (lines[i].trim().isEmpty()) {
                  continue;
                }
                if (firstWritten) {
                  sb.append('\n');
                }
                sb.append(lines[i]);
                firstWritten = true;
              }

              final String finalResult = sb.toString();

              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      editor.setEditorText(finalResult, true);
                      Toast.makeText(
                              getApplicationContext(),
                              "Empty lines removed successfully",
                              Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Error: " + e.getMessage(),
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            }
          }
        });
  }

  private void executeRemoveNumberedLines() {
    if (editor == null) return;

    // Use a unified dialogue description to guide input constraints inside MOptionPane
    String messagePrompt =
        "Enter a single line number (e.g., 25) or a range (e.g., 10-20) (Lines: "
            + editor.getLineCount()
            + " | CaretLine: "
            + editor.getCaretLine()
            + "):";

    // Transitioned to our lightweight MOptionPane input engine for a unified UI language
    optionPane.showInputDialog(
        messagePrompt,
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            // Circuit breaker if user cancelled the input operation (returns null)
            if (input == null) {
              return;
            }

            // Rule: Trim and eliminate all whitespace characters natively
            String rawInput = input.trim().replace(" ", "").replace("\t", "");
            if (rawInput.isEmpty()) return;

            final String sourceText = editor.getFullText();
            final String[] lines = sourceText.split("\n", -1);
            final int totalLines = lines.length;

            int startLine = -1;
            int endLine = -1;

            try {
              if (rawInput.contains("-")) {
                // Range processing block (e.g., 10-20)
                String[] parts = rawInput.split("-", -1);
                if (parts.length == 2) {
                  startLine = Integer.parseInt(parts[0]);
                  endLine = Integer.parseInt(parts[1]);
                } else {
                  throw new NumberFormatException();
                }
              } else {
                // Single line processing block (e.g., 25)
                startLine = Integer.parseInt(rawInput);
                endLine = startLine;
              }
            } catch (NumberFormatException e) {
              Toast.makeText(getApplicationContext(), "Invalid input format!", Toast.LENGTH_SHORT)
                  .show();
              return;
            }

            // Check if the requested lines actually exist in the document (1-based index
            // conversion)
            if (startLine < 1
                || endLine < 1
                || startLine > totalLines
                || endLine > totalLines
                || startLine > endLine) {
              Toast.makeText(
                      getApplicationContext(),
                      "Requested lines do not exist in the document!",
                      Toast.LENGTH_SHORT)
                  .show();
              return;
            }

            // Final effectively final values for background thread access mapping
            final int finalStart = startLine;
            final int finalEnd = endLine;

            showLoadingProgress();

            // Standardized background execution utilizing the shared file IO executor channel
            fileIOExecutor.execute(
                new Runnable() {
                  @Override
                  public void run() {
                    StringBuilder result = new StringBuilder();

                    for (int i = 0; i < totalLines; i++) {
                      int currentLineNum =
                          i + 1; // Convert 0-based array loop index to 1-based line number

                      // Rule: Continue if the current line falls inside the requested removal scope
                      if (currentLineNum >= finalStart && currentLineNum <= finalEnd) {
                        continue;
                      }

                      if (result.length() > 0) {
                        result.append("\n");
                      }
                      result.append(lines[i]);
                    }

                    final String finalResult = result.toString();

                    // Dispatch filtered content safely back to the primary main UI thread
                    mainHandler.post(
                        new Runnable() {
                          @Override
                          public void run() {
                            editor.setEditorText(finalResult, true);
                            hideLoadingProgress();
                            Toast.makeText(
                                    getApplicationContext(),
                                    "Lines removed successfully!",
                                    Toast.LENGTH_SHORT)
                                .show();
                          }
                        });
                  }
                });
          }
        });
  }

  private String readChunkSafe(File file, long startOffset, long maxBytes, String encoding)
      throws IOException {
    FileInputStream fis = null;

    try {
      fis = new FileInputStream(file);
      // If startOffset points into the middle of a UTF-8 multi-byte character,
      // walk backwards to the start of that character so the chunk begins on a
      // valid UTF-8 boundary. This handles the "ı" split-across-chunks case.
      if ("UTF-8".equalsIgnoreCase(encoding) && startOffset > 0) {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
          long probe = startOffset - 1;
          int back = 0;
          while (probe >= 0 && back < 4) {
            raf.seek(probe);
            int b = raf.read();
            if (b == -1) break;
            if ((b & 0xC0) != 0x80) {
              int len = 1;
              if ((b & 0xE0) == 0xC0) len = 2;
              else if ((b & 0xF0) == 0xE0) len = 3;
              else if ((b & 0xF8) == 0xF0) len = 4;
              if (probe + len > startOffset) {
                startOffset = probe;
              }
              break;
            }
            probe--;
            back++;
          }
        } finally {
          raf.close();
        }
      }

      // skip() may return fewer bytes than requested; loop until the offset is reached.
      long skipped = 0;
      while (skipped < startOffset) {
        long s = fis.skip(startOffset - skipped);
        if (s <= 0) break;
        skipped += s;
      }

      long fileSize = file.length();
      long available = fileSize - startOffset;
      int toRead = (int) Math.min(maxBytes, available);
      boolean isLastChunk = (startOffset + toRead) >= fileSize;

      byte[] bytes = new byte[toRead];
      int totalRead = 0;
      while (totalRead < toRead) {
        int r = fis.read(bytes, totalRead, toRead - totalRead);
        if (r <= 0) break;
        totalRead += r;
      }

      // For UTF-8, trim a partial multi-byte character at the chunk end.
      if (!isLastChunk && "UTF-8".equalsIgnoreCase(encoding)) {
        int cut = totalRead;
        for (int i = totalRead - 1; i >= 0; i--) {
          if ((bytes[i] & 0xC0) != 0x80) {
            int charLen = 1;
            if ((bytes[i] & 0xE0) == 0xC0) charLen = 2;
            else if ((bytes[i] & 0xF0) == 0xE0) charLen = 3;
            else if ((bytes[i] & 0xF8) == 0xF0) charLen = 4;

            if (i + charLen <= totalRead) {
              cut = i + charLen;
            } else {
              cut = i;
            }
            break;
          }
        }
        totalRead = cut;
      }

      return new String(bytes, 0, totalRead, encoding);

    } finally {
      try {
        if (fis != null) fis.close();
      } catch (Exception ignored) {
      }
    }
  }

  private void executeRemoveForeignAlphabets() {
    if (editor == null) return;

    final String text = editor.getText().toString();
    if (text.isEmpty()) {
      Toast.makeText(this, "Text is empty!", Toast.LENGTH_SHORT).show();
      return;
    }

    final MOptionPane pane = new MOptionPane(this);

    // STEP 1: Query the user for the Starting character boundary with Arabic block as default
    // priority
    pane.showInputDialog(
        "Enter Start (e.g. 97 or 0x0600):",
        "0x0600",
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String startInput) {
            if (startInput == null || startInput.trim().isEmpty()) {
              Toast.makeText(getApplicationContext(), "Operation cancelled", Toast.LENGTH_SHORT)
                  .show();
              return;
            }

            final String finalStartInput = startInput.trim();

            // STEP 2: Query the user for the Ending character boundary with Arabic block as default
            // priority
            pane.showInputDialog(
                "Enter End (e.g. 122 or 0x06FF):",
                "0x06FF",
                new MOptionPane.OnInputListener() {
                  @Override
                  public void onResult(String endInput) {
                    if (endInput == null || endInput.trim().isEmpty()) {
                      Toast.makeText(
                              getApplicationContext(), "Operation cancelled", Toast.LENGTH_SHORT)
                          .show();
                      return;
                    }

                    final String finalEndInput = endInput.trim();

                    showLoadingProgress();

                    // STEP 3: Dispatch parsing and filtering tasks directly onto the background
                    // thread
                    fileIOExecutor.execute(
                        new Runnable() {
                          @Override
                          public void run() {
                            try {
                              // Resolve parameters adaptively using your existing mix-radix parser
                              // mechanism
                              int startCode = parseMixedRadixInput(finalStartInput);
                              int endCode = parseMixedRadixInput(finalEndInput);

                              // Validate boundary alignment thresholds matching traditional Unicode
                              // bounds
                              if (startCode < 0
                                  || endCode < 0
                                  || startCode > 65535
                                  || endCode > 65535) {
                                throw new IllegalArgumentException(
                                    "Character code out of UTF-16 bounds");
                              }

                              // Synthesize the localized regex string natively matching target
                              // interval criteria
                              char startChar = (char) startCode;
                              char endChar = (char) endCode;
                              String dynamicRegex = "[" + startChar + "-" + endChar + "]";

                              // Fire background string sweeping mechanism
                              final String finalResult = text.replaceAll(dynamicRegex, "");

                              // Deliver payload results safely back to the core framework main
                              // layout view threads
                              mainHandler.post(
                                  new Runnable() {
                                    @Override
                                    public void run() {
                                      editor.setEditorText(finalResult, true);
                                      Toast.makeText(
                                              getApplicationContext(),
                                              "Range metrics processed successfully",
                                              Toast.LENGTH_SHORT)
                                          .show();
                                      hideLoadingProgress();
                                    }
                                  });

                            } catch (final Exception e) {
                              // Catch corrupted inputs gracefully to defend primary app context
                              // from fatal exceptions
                              mainHandler.post(
                                  new Runnable() {
                                    @Override
                                    public void run() {
                                      Toast.makeText(
                                              getApplicationContext(),
                                              "Error: Invalid character code or interval boundary!",
                                              Toast.LENGTH_SHORT)
                                          .show();
                                      hideLoadingProgress();
                                    }
                                  });
                            }
                          }
                        });
                  }
                });
          }
        });
  }

  /**
   * Helper method that intelligently translates mixed numeric inputs (Decimal or Hexadecimal) into
   * a native base-10 primitive integer value safely inside old school parameters.
   */
  private int parseMixedRadixInput(String input) {
    String clean = input.trim();

    // Strip standard prefix indicators if typed explicitly by power-users
    if (clean.toLowerCase().startsWith("0x")) {
      return Integer.parseInt(clean.substring(2), 16);
    }
    if (clean.startsWith("\\u")) {
      return Integer.parseInt(clean.substring(2), 16);
    }

    // Check if input is a pure base-10 numeric string
    boolean isDecimal = true;
    for (int i = 0; i < clean.length(); i++) {
      char ch = clean.charAt(i);
      if (ch < '0' || ch > '9') {
        isDecimal = false;
        break;
      }
    }

    // Parse with base-10 if pure digits, else default lookup into traditional hexadecimal base-16
    if (isDecimal) {
      return Integer.parseInt(clean, 10);
    } else {
      return Integer.parseInt(clean, 16);
    }
  }

  private void executeSelectNumberedLines() {
    if (editor == null) return;

    // Extract the active document text buffer safely from our editor layer
    final String sourceText = editor.getFullText();

    // Leverages our optimized layout-free line counter directly to completely eliminate memory
    // pressure
    final int totalLines = editor.getLineCount();

    String messagePrompt =
        "Enter a single line number to select (e.g., 5) or a range (e.g., 7-21) (Lines: "
            + editor.getLineCount()
            + " | CaretLine: "
            + editor.getCaretLine()
            + "):";

    // Transitioned to our lightweight MOptionPane input engine for a unified UI language
    optionPane.showInputDialog(
        messagePrompt,
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            // Circuit breaker if user cancelled the input operation (returns null)
            if (input == null) {
              return;
            }

            // Rule: Trim and eliminate all whitespace characters natively
            String rawInput = input.trim().replace(" ", "").replace("\t", "");
            if (rawInput.isEmpty()) return;

            int startLine = -1;
            int endLine = -1;

            try {
              if (rawInput.contains("-")) {
                // Range processing block (e.g., 7-21)
                String[] parts = rawInput.split("-", -1);
                if (parts.length == 2) {
                  startLine = Integer.parseInt(parts[0]);
                  endLine = Integer.parseInt(parts[1]);
                } else {
                  throw new NumberFormatException();
                }
              } else {
                // Single line processing block (e.g., 5)
                startLine = Integer.parseInt(rawInput);
                endLine = startLine;
              }
            } catch (NumberFormatException e) {
              Toast.makeText(getApplicationContext(), "Invalid input format!", Toast.LENGTH_SHORT)
                  .show();
              return;
            }

            // Check if the requested lines actually exist in the document (1-based index
            // conversion)
            if (startLine < 1
                || endLine < 1
                || startLine > totalLines
                || endLine > totalLines
                || startLine > endLine) {
              Toast.makeText(
                      getApplicationContext(),
                      "Requested lines do not exist in the document!",
                      Toast.LENGTH_SHORT)
                  .show();
              return;
            }

            // 1. Calculate selection indexes and character count to protect device memory matrix
            int selectionStartCharIndex = 0;
            int selectionEndCharIndex = 0;
            int currentLineIndex = 1;
            int currentPos = 0;
            int sourceTextLength = sourceText.length();

            while (currentPos <= sourceTextLength && currentLineIndex <= endLine) {
              int nextNewlineIndex = sourceText.indexOf('\n', currentPos);
              int lineEndOffset = (nextNewlineIndex == -1) ? sourceTextLength : nextNewlineIndex;

              if (currentLineIndex == startLine) {
                selectionStartCharIndex = currentPos;
              }

              if (currentLineIndex == endLine) {
                selectionEndCharIndex = lineEndOffset;
                break;
              }

              if (nextNewlineIndex == -1) {
                // SAFE FALLBACK: If we reached the EOF but couldn't fulfill the exact endLine
                // block, force boundary
                if (currentLineIndex == endLine) {
                  selectionEndCharIndex = sourceTextLength;
                }
                break;
              }

              currentPos = nextNewlineIndex + 1; // Step over the newline character block boundary
              currentLineIndex++;
            }

            // 2. Perform memory utilization analysis boundary check pass
            int totalSelectionLength = selectionEndCharIndex - selectionStartCharIndex;
            if (totalSelectionLength > MAXSELECT) {
              Toast.makeText(
                      getApplicationContext(),
                      "Selected range text size too large for memory protection!",
                      Toast.LENGTH_SHORT)
                  .show();
              return;
            }

            // Final effectively final values for assignment back to UI layer variables safely
            final int finalStartSelection = selectionStartCharIndex;
            final int finalEndSelection = selectionEndCharIndex;

            // Dispatch selection indicators configuration parameters straight to main execution
            // frame layout
            mainHandler.post(
                new Runnable() {
                  @Override
                  public void run() {
                    // Delay focus execution slightly using a post queue runner
                    // to prevent the MOptionPane dismiss cycle from stealing focus back.
                    editor.post(
                        new Runnable() {
                          @Override
                          public void run() {
                            editor.requestFocus();
                            editor.setSelection(finalStartSelection, finalEndSelection);
                            Toast.makeText(
                                    getApplicationContext(),
                                    "Lines selected successfully!",
                                    Toast.LENGTH_SHORT)
                                .show();
                          }
                        });
                  }
                });
          }
        });
  }

  private void setEditorReadOnly() {
    if (mMenu != null) {
      MenuItem item = mMenu.findItem(R.id.menu_read_only);
      isReadOnlyMode = !item.isChecked();
      item.setChecked(isReadOnlyMode);
      applyReadOnlyState();
    }
  }

  private void applyReadOnlyState() {
    editor.setExternalReadOnly(isReadOnlyMode);
  }

  /**
   * Prompts the user for a search query (character or word) and displays the total number of
   * occurrences found within the active editor text.
   */
  private void executeSpecificInfo() {
    optionPane.showInputDialog(
        "Enter a character or word to count:",
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String query) {
            if (query == null || query.isEmpty()) {
              Toast.makeText(Notepad.this, "Search cancelled. Empty query.", Toast.LENGTH_SHORT)
                  .show();
              return;
            }

            if (editor.getText() == null) {
              optionPane.showMessageDialog("Occurrences found: 0");
              return;
            }

            String content = editor.getText().toString();
            int count = 0;
            int index = 0;

            // Loop through the text content using traditional indexOf pointer shifting
            while ((index = content.indexOf(query, index)) != -1) {
              count++;
              index +=
                  query.length(); // Move pointer past the current match to avoid infinite loops
            }

            optionPane.showMessageDialog("The text '" + query + "' appears " + count + " time(s).");
          }
        });
  }

  private void setEditorReadOnlyFromSession(boolean isReadOnly) {
    isReadOnlyMode = isReadOnly;

    // Notify the editor widget that the read-only mode is being restored
    editor.setExternalReadOnly(isReadOnly);

    applyReadOnlyState();
    if (mMenu != null) {
      MenuItem readOnlyItem = mMenu.findItem(R.id.menu_read_only);
      if (readOnlyItem != null) {
        readOnlyItem.setChecked(isReadOnly);
      }
    }
  }

  /** Shows the loading spinner and blocks user interaction. */
  private void showLoadingProgress() {
    runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            View overlay = findViewById(R.id.loading_overlay);
            if (overlay != null) {
              overlay.setVisibility(View.VISIBLE);
            }
          }
        });
  }

  /** Hides the loading spinner. */
  private void hideLoadingProgress() {
    runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            View overlay = findViewById(R.id.loading_overlay);
            if (overlay != null) {
              overlay.setVisibility(View.GONE);
            }
          }
        });
  }

  private void showPasswordDialog(final Uri uri, final boolean isSave) {
    String title =
        isSave
            ? ("Enter your password to encrypt (PREPASS is: " + PREPASS + ")")
            : ("Enter the password to open this file (PREPASS is: " + PREPASS + ")");

    // Transitioned to our lightweight MOptionPane input engine
    optionPane.showInputDialog(
        title,
        "1234",
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            // Circuit breaker if the password input transaction was cancelled
            if (input == null) {
              return;
            }

            final String pass = input;
            if (pass.isEmpty()) {
              Toast.makeText(Notepad.this, "Password cannot be empty!", Toast.LENGTH_SHORT).show();
              return;
            }

            if (isSave) {
              saveEncryptedToFile(uri, editor.getFullText(), pass);
            } else {
              decryptFileAndShow(uri, pass);
            }
          }
        });
  }

  private void showExitConfirmation() {
    String exitMessage =
        "Are you sure you want to close the application? Unsaved changes will be lost.";

    // Transitioned standard exit dialog straight to our custom MOptionPane confirm framework
    optionPane.showConfirmDialog(
        exitMessage,
        new MOptionPane.OnConfirmListener() {
          @Override
          public void onResult(int option) {
            // Evaluate the confirmation feedback selection bounds
            if (option == MOptionPane.YES_OPTION) {
              if (languageMap != null) {
                languageMap.clear();
              }

              boolean isNotRequired = currentFileAbsolutePath.trim().toLowerCase().endsWith(".enc");

              // Save session info before finishing
              if (nameCaretInfoFile != null && !isNotRequired) {
                try {
                  PrintStream ps = new PrintStream(new FileOutputStream(nameCaretInfoFile));

                  // Line 1: Absolute Path (Keep original)
                  ps.println(currentFileAbsolutePath != null ? currentFileAbsolutePath : "");

                  // Line 2: Composite Chunk Tracking Parameters ->
                  // pointer:chunkIndex:caretSelection:maxChunkSize
                  String chunkAndSelectionData =
                      currentFilePointer
                          + ":"
                          + currentChunkIndex
                          + ":"
                          + editor.getSelectionStart()
                          + ":"
                          + MAX_CHUNK_SIZE;
                  ps.println(chunkAndSelectionData);

                  // Line 3: Font Size
                  // ps.println(editor.getTextSize() / 2f);
                  ps.println(
                      TypedValue.deriveDimension(
                          TypedValue.COMPLEX_UNIT_SP,
                          editor.getTextSize(),
                          getResources().getDisplayMetrics()));

                  // Line 4: Font Style
                  if (editor.getTypeface() != null) {
                    ps.println(editor.getTypeface().getStyle());
                  } else {
                    ps.println("0"); // Default Normal
                  }

                  // Line 5: Read Only Mode Status
                  ps.println(!editor.isFocusable());

                  // Line 6: Vertical scroll position
                  ps.println(editor.getScrollY());

                  // Line 7: Line spacing multiplier
                  ps.println(editor.getLineSpacingMultiplier());

                  // Line 8: Font face (empty string means Typeface.DEFAULT)
                  ps.println(currentFontFace != null ? currentFontFace : "");

                  // Line 9: Background color (ARGB int)
                  ps.println(editor.getPersistedBackgroundColor());

                  // Line 10: Text color (ARGB int)
                  ps.println(editor.getPersistedTextColor());

                  ps.flush();
                  ps.close();
                } catch (FileNotFoundException e) {
                  e.printStackTrace();
                }
              }

              // Execute original closure framework
              finishAndRemoveTask();

              //  new Handler(Looper.getMainLooper())
              //      .postDelayed(
              //          new Runnable() {
              //            @Override
              //            public void run() {
              //              android.os.Process.killProcess(android.os.Process.myPid());
              //              System.exit(0);
              //            }
              //          },
              //          250);
            }
          }
        });
  }

  private void showAppendCharDialog() {
    // Transitioned to our lightweight MOptionPane input engine for a unified UI language
    optionPane.showInputDialog(
        "Enter a character, Unicode (\\uXXXX), or Hex (0xXXXX) eg, 0x1f34b:",
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            // Circuit breaker if the input window transaction was cancelled by the user
            if (input == null) {
              return;
            }

            String raw = input.trim();
            if (raw.isEmpty()) return;

            String toAppend = "";
            try {
              if (raw.startsWith("\\u") && raw.length() >= 6) {
                // Handle Unicode Escape sequences natively (e.g., \u015F)
                int hexVal = Integer.parseInt(raw.substring(2, 6), 16);
                toAppend = String.valueOf((char) hexVal);
              } else if (raw.startsWith("0x")) {
                // Handle Hex values (Including full multi-byte Emojis like 0x1F34B)
                int hexVal = Integer.parseInt(raw.substring(2), 16);
                if (hexVal > 0x10FFFF) {
                  throw new Exception("Out of Unicode range");
                }
                toAppend = new String(Character.toChars(hexVal));
              } else {
                // Standard raw character layout input fallback
                toAppend = raw;
              }

              // Fetch active buffer editable reference to securely perform index updates
              Editable editableText = editor.getText();
              if (editableText != null) {
                int start = Math.max(editor.getSelectionStart(), 0);
                int end = Math.max(editor.getSelectionEnd(), 0);

                // Insert exactly at the current active cursor bounds
                editableText.replace(
                    Math.min(start, end), Math.max(start, end), toAppend, 0, toAppend.length());

                // Advance the cursor safely right after the newly appended character sequence
                editor.setSelection(Math.min(start, end) + toAppend.length());
              }

            } catch (Exception e) {
              Toast.makeText(Notepad.this, "Invalid input format", Toast.LENGTH_SHORT).show();
            }
          }
        });
  }

  private void showColorPickerDialog(final String mode) {
    // Leverage our custom listener-supported MColorPicker component as requested
    colorPicker.showColorPickerDialog(
        new MColorPicker.OnColorSelectedListener() {
          @Override
          public void onColorSelected(int color) {
            // Direct property delegation passing down selected color spectrum tokens
            applyColor(mode, color);
          }
        });
  }

  /**
   * Resets all appearance settings back to their defaults: white background, black text, line
   * spacing 1.0, default font family, plain (normal) font style, and 24.0sp font size.
   */
  private void executeReset() {
    if (editor == null) return;

    // Reset the background and text colors to plain white / black.
    editor.setBackgroundColor(Color.WHITE);
    editor.setTextColor(Color.BLACK);

    // Reset line spacing: 0f add, 1.0f multiplier = normal spacing.
    editor.setLineSpacing(0f, 1.0f);

    // Reset the font family: empty string means Typeface.DEFAULT in applyTypeface().
    currentFontFace = "";

    // Reset the font style to plain (normal).
    currentFontStyle = Typeface.NORMAL;

    // Apply the font family and style together, then set the size to 24sp.
    applyTypeface();
    editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24.0f);

    // Make sure the layout is refreshed.
    editor.requestLayout();
    editor.invalidate();

    Toast.makeText(this, "Appearance reset to defaults", Toast.LENGTH_SHORT).show();
  }

  private void applyColor(String mode, int color) {
    if (editor == null) return;

    // Direct layout property updates are highly performant O(1) operations
    if ("Background".equals(mode)) {
      editor.setBackgroundColor(color);
    } else if ("Text".equals(mode)) {
      editor.setTextColor(color);
    } else if ("Selection".equals(mode)) {
      // High visibility highlight targets for better display readability
      editor.setHighlightColor(color);
    }

    // Emit runtime notification confirming layout changes to the user interface
    Toast.makeText(this, mode + " color updated", Toast.LENGTH_SHORT).show();
  }

  private void showCustomEncodingDialog() {
    String messagePrompt =
        "Please enter the charset name manually (Current: " + currentEncoding + "):";

    // Invoking our newly overloaded input framework pass with comfort-prepopulated parameters
    optionPane.showInputDialog(
        messagePrompt,
        currentEncoding,
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            // Circuit breaker if user aborted or dismissed the dialogue interface
            if (input == null) {
              return;
            }

            String customEnc = input.trim();
            if (!customEnc.isEmpty()) {
              try {
                // Validate if the requested charset layout is explicitly supported by the target VM
                // configuration
                if (Charset.isSupported(customEnc)) {
                  currentEncoding = customEnc;
                  Toast.makeText(
                          Notepad.this, "Encoding set to: " + currentEncoding, Toast.LENGTH_SHORT)
                      .show();
                } else {
                  Toast.makeText(Notepad.this, "Unsupported charset!", Toast.LENGTH_SHORT).show();
                }
              } catch (Exception e) {
                Toast.makeText(Notepad.this, "Invalid charset name", Toast.LENGTH_SHORT).show();
              }
            }
          }
        });
  }

  private void executeShareText() {
    String textToShare = editor.getText().toString();

    if (textToShare.isEmpty()) {
      Toast.makeText(this, "Nothing to share", Toast.LENGTH_SHORT).show();
      return;
    }

    try {
      Intent shareIntent = new Intent(Intent.ACTION_SEND);
      shareIntent.setType("text/plain");

      // Subject line (Useful for Email apps)
      shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Shared Document from Notepad");

      // The actual content
      shareIntent.putExtra(Intent.EXTRA_TEXT, textToShare);

      // Create a chooser to let the user select their preferred app
      startActivity(Intent.createChooser(shareIntent, "Share text via:"));

    } catch (Exception e) {
      Toast.makeText(this, "Error during sharing process", Toast.LENGTH_SHORT).show();
    }
  }

  private void searchInsideFilesWorkflow(Uri uri) {
    if (optionPane == null) {
      optionPane = new MOptionPane(this);
    }

    final String realPath = getRealPathFromUri(uri);
    String pickedFileName = getFileName(uri);

    if (realPath == null || realPath.isEmpty()) {
      Toast.makeText(getApplicationContext(), "Failed to resolve file path", Toast.LENGTH_LONG)
          .show();
      return;
    }

    // Build the clean instructional prompt window
    String promptMessage =
        "Target File: "
            + pickedFileName
            + "\n\n"
            + "Enter search term and mode separated by '///'.\n"
            + "Mode 0: Scan this single file.\n"
            + "Mode 1: Scan entire parent directory recursively.\n\n"
            + "Example: java///0 or yourTerm///1";

    optionPane.showInputDialog(
        promptMessage,
        "",
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(final String rawInput) {
            if (rawInput == null || rawInput.trim().isEmpty()) {
              return;
            }

            // Display loading ui component before launching the heavy content stream check
            showLoadingProgress();

            // Standardized background execution utilizing the shared file IO executor channel
            fileIOExecutor.execute(
                new Runnable() {
                  @Override
                  public void run() {
                    try {
                      String searchTerm = "";
                      String mode = "0";

                      // Parse text inputs and tokens safely inside the thread pass
                      if (rawInput.contains("///")) {
                        String[] inputParts = rawInput.split("///");
                        if (inputParts.length > 0) {
                          searchTerm = inputParts[0].trim();
                        }
                        if (inputParts.length > 1) {
                          mode = inputParts[1].trim();
                        }
                      } else {
                        searchTerm = rawInput.trim();
                      }

                      if (searchTerm.isEmpty()) {
                        mainHandler.post(
                            new Runnable() {
                              @Override
                              public void run() {
                                hideLoadingProgress();
                                optionPane.showMessageDialog("Search term cannot be empty.");
                              }
                            });
                        return;
                      }

                      File targetFile = new File(realPath);
                      final String finalResults;

                      // Delegate to ContentSearcher matching the desired operation mode
                      if ("1".equals(mode)) {
                        File parentDir = targetFile.getParentFile();
                        if (parentDir != null && parentDir.isDirectory()) {
                          finalResults = ContentSearcher.result(parentDir, searchTerm);
                        } else {
                          finalResults = ContentSearcher.result(targetFile, searchTerm);
                        }
                      } else {
                        finalResults = ContentSearcher.result(targetFile, searchTerm);
                      }

                      // Dispatch deeply matched text lines back to the primary main UI thread
                      mainHandler.post(
                          new Runnable() {
                            @Override
                            public void run() {
                              hideLoadingProgress();
                              // Present the matched contents to the user safely
                              if (finalResults == null || finalResults.isEmpty()) {
                                optionPane.showMessageDialog("No content matches discovered.");
                              } else {
                                optionPane.showMessageDialog("Search Matches:\n\n" + finalResults);
                              }
                            }
                          });
                    } catch (Exception e) {
                      mainHandler.post(
                          new Runnable() {
                            @Override
                            public void run() {
                              hideLoadingProgress();
                              Toast.makeText(
                                      getApplicationContext(),
                                      "Error searching content inside files",
                                      Toast.LENGTH_SHORT)
                                  .show();
                            }
                          });
                    }
                  }
                });
          }
        });
  }

  private void executeClearJunk() {
    if (editor == null) return;

    // 1. BOM HEADER STRIPPER (Pre-processing Phase)
    // Strips the invisible Byte Order Mark signatures from the raw stream beforehand
    String textx = editor.getText().toString();
    if (textx.startsWith("\uFEFF")) {
      textx = textx.substring(1);
    }
    if (textx.startsWith("\uEFBB\u00BF")) {
      textx = textx.substring(3);
    }

    if (textx.isEmpty()) {
      Toast.makeText(this, "Text is too short or empty!", Toast.LENGTH_SHORT).show();
      return;
    }

    final String text = textx;
    showLoadingProgress();

    // Standardized background execution utilizing the shared file IO executor channel
    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              final StringBuilder sb = new StringBuilder(text.length());
              int length = text.length();

              // Character parsing optimized under background payload architecture
              for (int i = 0; i < length; i++) {
                char c = text.charAt(i);
                int n = (int) c;

                // 2. MIXED LINE ENDINGS FIXER (Unix/LF Standart)
                // Intercepts Windows Carriage Returns (\r) to enforce uniform Unix LF (\n) streams
                if (c == '\r') {
                  // If followed by LF, let the loop handle the LF natively on the next step
                  if (i + 1 < length && text.charAt(i + 1) == '\n') {
                    continue;
                  }
                  // If it's a standalone CR, normalize it immediately into a standard Unix LF
                  sb.append('\n');
                  continue;
                }

                // 3. ZERO-WIDTH CHAR HUNTER
                // Detects hidden, disruptive zero-width characters and replaces them with a visible
                // marker
                if (c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\uFEFF') {
                  sb.append("•"); // Visible safe red/neutral dot indicator replacement target
                  continue;
                }

                if (n < 32 && n != 9 && n != 10 && n != 13) {
                  continue;
                }

                switch (c) {
                  case '`':
                    sb.append('\'');
                    break;
                  case (char) 95:
                    sb.append('-');
                    break;
                  case (char) 127:
                    sb.append(' ');
                    break;
                  case (char) 160:
                    sb.append(' ');
                    break;
                  case (char) 166:
                    sb.append(':');
                    break;
                  case (char) 168:
                    sb.append('~');
                    break;
                  case (char) 170:
                    sb.append('a');
                    break;
                  case (char) 171:
                    sb.append('"');
                    break;
                  case (char) 173:
                    sb.append('-');
                    break;
                  case (char) 175:
                    sb.append('~');
                    break;
                  case (char) 176:
                    sb.append("o:");
                    break;
                  case (char) 180:
                    sb.append('\'');
                    break;
                  case (char) 182:
                    sb.append('~');
                    break;
                  case (char) 183:
                    sb.append(' ');
                    break;
                  case (char) 184:
                    sb.append(' ');
                    break;
                  case (char) 186:
                    sb.append("o:");
                    break;
                  case (char) 187:
                    sb.append('"');
                    break;
                  case (char) 247:
                    sb.append('/');
                    break;
                  case (char) 8210:
                  case (char) 8211:
                  case (char) 8212:
                    sb.append('-');
                    break;
                  case (char) 8213:
                    sb.append("--");
                    break;
                  case (char) 8214:
                    break;
                  case (char) 8215:
                    sb.append('=');
                    break;
                  case (char) 8216:
                  case (char) 8217:
                    sb.append('\'');
                    break;
                  case (char) 8218:
                    sb.append(',');
                    break;
                  case (char) 8219:
                    sb.append('/');
                    break;
                  case (char) 8220:
                  case (char) 8221:
                  case (char) 8223:
                    sb.append('"');
                    break;
                  case (char) 8222:
                    sb.append(',');
                    break;
                  case (char) 8224:
                  case (char) 8225:
                    sb.append('+');
                    break;
                  case (char) 8226:
                    sb.append("--");
                    break;
                  case (char) 8230:
                    sb.append("...");
                    break;
                  case (char) 8252:
                    sb.append("!!");
                    break;
                  case (char) 8592:
                    sb.append("<--");
                    break;
                  case (char) 8594:
                    sb.append("-->");
                    break;
                  case (char) 9474:
                    sb.append('-');
                    break;
                  case (char) 61694:
                    sb.append('~');
                    break;
                  case (char) 65533:
                    sb.append(' ');
                    break;
                  case (char) 128:
                    sb.append("EUR");
                    break;
                  case (char) 153:
                    sb.append("(TM)");
                    break;
                  case (char) 169:
                    sb.append("(c)");
                    break;
                  case (char) 174:
                    sb.append("(r)");
                    break;
                  default:
                    sb.append(c);
                    break;
                }
              }

              final String finalResult = sb.toString();

              // Dispatch cleaned content safely back to the primary main UI thread
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      editor.setEditorText(finalResult, true);
                      Toast.makeText(
                              getApplicationContext(),
                              "Text cleaned successfully",
                              Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            } catch (Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Error during cleaning process!",
                              Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            }
          }
        });
  }

  private void excuteClearTags() {
    if (editor == null) return;

    final String content = editor.getText().toString();
    if (content.isEmpty()) {
      Toast.makeText(this, "Text is empty!", Toast.LENGTH_SHORT).show();
      return;
    }

    showLoadingProgress();

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              String cleaned = content;

              // 1. Remove HTML comments, DOCTYPE and XML processing instructions.
              cleaned = cleaned.replaceAll("(?is)<!--.*?-->", "");
              cleaned = cleaned.replaceAll("(?is)<!DOCTYPE[^>]*>", "");
              cleaned = cleaned.replaceAll("(?is)<\\?xml[^>]*\\?>", "");

              // 2. Remove script and style blocks together with their contents.
              cleaned = cleaned.replaceAll("(?is)<script[^>]*>.*?</script>", "");
              cleaned = cleaned.replaceAll("(?is)<style[^>]*>.*?</style>", "");

              // 3. Remove opening / self-closing tags for a fixed set of common HTML tags.
              //    The pattern tolerates any attributes (quoted or unquoted), e.g.
              //    <font color="red">, <font color='red' size=4>, <p class="x">, <br/>, <hr />.
              String openTags =
                  "font|p|b|i|u|tt|sub|sup|big|small|ul|ol|li|br|hr|div|span|center|strike|s|em|strong|h[1-6]|blockquote|pre|code|table|tr|td|th|tbody|thead|a|img";
              cleaned = cleaned.replaceAll("(?is)<\\s*(" + openTags + ")\\b[^>]*?/?\\s*>", "");

              // 4. Remove the matching closing tags, e.g. </font>, </p >, </ b>.
              String closeTags =
                  "font|p|b|i|u|tt|sub|sup|big|small|ul|ol|li|br|hr|div|span|center|strike|s|em|strong|h[1-6]|blockquote|pre|code|table|tr|td|th|tbody|thead|a|img";
              cleaned = cleaned.replaceAll("(?is)<\\s*/\\s*(" + closeTags + ")\\s*>", "");

              // 5. Decode a small set of common HTML entities so the visible text is clean.
              cleaned = cleaned.replace("&nbsp;", " ");
              cleaned = cleaned.replace("&amp;", "&");
              cleaned = cleaned.replace("&lt;", "<");
              cleaned = cleaned.replace("&gt;", ">");
              cleaned = cleaned.replace("&quot;", "\"");
              cleaned = cleaned.replace("&#39;", "'");

              // 6. Collapse runs of three or more newlines into two.
              while (cleaned.contains("\n\n\n")) {
                cleaned = cleaned.replace("\n\n\n", "\n\n");
              }

              final String finalResult = cleaned;

              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      editor.setEditorText(finalResult, true);
                      Toast.makeText(getApplicationContext(), "Tags cleared", Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });

            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Clear Tags failed: " + e.getMessage(),
                              Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            }
          }
        });
  }

  private void executeCleanExtraSpaces() {
    if (editor == null) return;

    final String content = editor.getText().toString();
    if (content.isEmpty()) {
      Toast.makeText(this, "Document is empty", Toast.LENGTH_SHORT).show();
      return;
    }

    showLoadingProgress();

    // Standardized background execution utilizing the shared file IO executor channel
    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              // PERFORMANCE: Linear iteration for whitespace pruning instead of slow regex engine
              // patterns
              final StringBuilder sb = new StringBuilder(content.length());
              int len = content.length();
              boolean inSpaceSequence = false;

              for (int i = 0; i < len; i++) {
                char c = content.charAt(i);

                if (c == ' ' || c == '\t') {
                  if (!inSpaceSequence) {
                    sb.append(' '); // Keep only the first single space representation
                    inSpaceSequence = true;
                  }
                } else {
                  sb.append(c);
                  inSpaceSequence = false; // Break flag since token context changed
                }
              }

              final String finalResult = sb.toString();

              // Dispatch cleaned content safely back to the primary main UI thread
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      editor.setEditorText(finalResult, true);
                      Toast.makeText(
                              getApplicationContext(),
                              "Extra spaces cleaned (lines preserved)",
                              Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });

            } catch (Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(), "Error processing text", Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            }
          }
        });
  }

  private String getOnlyFileName(String curPath) {
    if (curPath == null) return "";
    if (curPath.isEmpty()) return "";

    int zindex = curPath.lastIndexOf("/");
    if (zindex < 0) return curPath;

    String result = curPath.substring(zindex + 1);

    return result;
  }

  private void compressImageWorkflow(Uri uri) {
    if (optionPane == null) {
      optionPane = new MOptionPane(this);
    }

    // Convert the target Uri into a physical absolute path layout string
    final String realPath = getRealPathFromUri(uri);
    String pickedFileName = getFileName(uri);

    if (realPath == null || realPath.isEmpty()) {
      Toast.makeText(getApplicationContext(), "Failed to resolve image path", Toast.LENGTH_LONG)
          .show();
      return;
    }

    // Instruction prompt matching your exact high-comfort UI kit layout structure
    String promptMessage =
        "Target Image: "
            + pickedFileName
            + "\n\n"
            + "Enter compression ratio between 0.0 and 1.0:\n"
            + "Example: 0.5 means 50% quality (Recommended)";

    // Launch input dialog with default 0.5 ratio text pre-populated
    optionPane.showInputDialog(
        promptMessage,
        "0.5",
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(final String inputRatio) {
            // Cancel or escape action safeguard
            if (inputRatio == null || inputRatio.trim().isEmpty()) {
              return;
            }

            // Display loading ui indicator before running the native bitmap stream compression
            showLoadingProgress();

            // Offload the heavy image re-encoding pass onto the background worker thread channel
            fileIOExecutor.execute(
                new Runnable() {
                  @Override
                  public void run() {
                    try {
                      // Parse float string securely into memory bounds
                      float ratio = 0.5f;
                      try {
                        ratio = Float.parseFloat(inputRatio.trim());
                      } catch (NumberFormatException nfe) {
                        // Fallback to default index state if parsing encounters junk tokens
                      }

                      final File sourceFile = new File(realPath);

                      // Trigger the static native compressor tool on the worker channel
                      final String compressedPath = ImageCompressor.compress(sourceFile, ratio);

                      // Safe dispatch execution context back to the native primary Main UI thread
                      // layer
                      mainHandler.post(
                          new Runnable() {
                            @Override
                            public void run() {
                              hideLoadingProgress();

                              if (compressedPath != null && !compressedPath.isEmpty()) {
                                File compFile = new File(compressedPath);

                                // Calculate exact file sizes in KB using explicit double cast
                                double originalKb = ((double) sourceFile.length()) / ONEKBD;
                                double compressedKb = ((double) compFile.length()) / ONEKBD;

                                // Calculate final percentage savings ratio safely to avoid division
                                // by zero
                                double savingRatio = CEROD;
                                if (originalKb > CEROD) {
                                  savingRatio = ((originalKb - compressedKb) / originalKb) * CIEN;
                                }

                                // Format message payload containing comparative metadata details
                                String successMessage =
                                    "Compression Success!\n\n"
                                        + "Saved as: "
                                        + compFile.getName()
                                        + "\n"
                                        + "Original Size: "
                                        + originalKb
                                        + " KB\n"
                                        + "Compressed Size: "
                                        + compressedKb
                                        + " KB\n"
                                        + "Reduction: "
                                        + String.format("%.1f", savingRatio)
                                        + "%";

                                // Feedback message passing the clean filename metadata to user
                                // window frame
                                optionPane.showMessageDialog(successMessage);
                              } else {
                                optionPane.showMessageDialog(
                                    "Compression failed. Unrecognized image format.");
                              }
                            }
                          });
                    } catch (Exception e) {
                      mainHandler.post(
                          new Runnable() {
                            @Override
                            public void run() {
                              hideLoadingProgress();
                              Toast.makeText(
                                      getApplicationContext(),
                                      "Error processing image structure",
                                      Toast.LENGTH_SHORT)
                                  .show();
                            }
                          });
                    }
                  }
                });
          }
        });
  }

  private void insertSystemDateTime() {
    try {
      // Define the date format (Day Month Year - Hour:Minute)
      SimpleDateFormat formatter = new SimpleDateFormat("dd MMM yyyy - HH:mm", Locale.ENGLISH);
      String currentDateTime = formatter.format(new Date());

      // Get the current cursor position
      int start = Math.max(editor.getSelectionStart(), 0);
      int end = Math.max(editor.getSelectionEnd(), 0);

      // Replace the selection (or insert at cursor) with the formatted string
      editor
          .getText()
          .replace(
              Math.min(start, end),
              Math.max(start, end),
              currentDateTime,
              0,
              currentDateTime.length());

      // Move the cursor to the end of the newly inserted text
      editor.setSelection(start + currentDateTime.length());

    } catch (Exception e) {
      Toast.makeText(this, "Error inserting date", Toast.LENGTH_SHORT).show();
    }
  }

  private void showNewFileConfirmation(boolean isAll) {
    String messagePrompt =
        "All unsaved changes will be lost. Do you want to create a new document/page?";

    // Transitioned standard alert dialog straight to our custom MOptionPane confirm framework
    optionPane.showConfirmDialog(
        messagePrompt,
        new MOptionPane.OnConfirmListener() {
          @Override
          public void onResult(int option) {
            // Evaluate the confirmation feedback selection bounds
            if (option == MOptionPane.YES_OPTION) {
              // Instantly wipes the layout buffer and resets the native cursor track
              editor.setEditorText("", false);

              if (isAll) {
                totalChunksCount = 1;
                currentChunkIndex = 1;
                chunk_navigation_bar.setVisibility(View.GONE);
              }

              Toast.makeText(Notepad.this, "New document/page initialized", Toast.LENGTH_SHORT)
                  .show();
            }
          }
        });
  }

  private void executeFindFilename() {
    if (optionPane == null) {
      optionPane = new MOptionPane(this);
    }

    String message = "Enter a file name or part of it to search for (case-sensitive):";

    optionPane.showInputDialog(
        message,
        "",
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(final String searchTerm) {
            // Cancel or empty input safeguard
            if (searchTerm == null || searchTerm.trim().isEmpty()) {
              return;
            }

            // Display loading ui component before launching the background work
            showLoadingProgress();

            // Standardized background execution utilizing the shared file IO executor channel
            fileIOExecutor.execute(
                new Runnable() {
                  @Override
                  public void run() {
                    try {
                      // Using the predefined ROOTDIR file object as the starting point
                      File startDirectory = ROOTDIR;

                      // Trigger our static file searcher engine in background thread
                      final String searchResults = FileSearcher.result(startDirectory, searchTerm);

                      // Dispatch search results safely back to the primary main UI thread
                      mainHandler.post(
                          new Runnable() {
                            @Override
                            public void run() {
                              hideLoadingProgress();
                              // Present the list of found elements to the user using UI framework
                              if (searchResults == null || searchResults.isEmpty()) {
                                optionPane.showMessageDialog(
                                    "No matching files found under this directory.");
                              } else {
                                optionPane.showMessageDialog("Search Results:\n\n" + searchResults);
                              }
                            }
                          });
                    } catch (Exception e) {
                      mainHandler.post(
                          new Runnable() {
                            @Override
                            public void run() {
                              hideLoadingProgress();
                              Toast.makeText(
                                      getApplicationContext(),
                                      "Error searching files",
                                      Toast.LENGTH_SHORT)
                                  .show();
                            }
                          });
                    }
                  }
                });
          }
        });
  }

  private void executeFindNext() {
    if (findTarget == null || findTarget.isEmpty()) return;

    if (editor == null) return;
    final String content = editor.getText().toString();
    if (content.isEmpty()) return;

    try {
      // Step 1: Process escape sequences (like \w, \d) to make them real regex tokens
      String processedPattern = escapeSequencesForRegex(findTarget);

      // Step 2: Compile pattern with essential flags using exact package declarations
      Pattern pattern = Pattern.compile(processedPattern, Pattern.MULTILINE | Pattern.UNICODE_CASE);

      Matcher matcher = pattern.matcher(content);

      // Step 3: Determine starting position from current selection end
      int selectionEnd = editor.getSelectionEnd();
      int start = (selectionEnd >= 0) ? selectionEnd : 0;

      // Reset to start if we are at the end of the text
      if (start >= content.length()) {
        start = 0;
      }

      // Step 4: Try to find the pattern starting from 'start'
      if (matcher.find(start)) {
        // Found: Focus, select and scroll into view
        editor.requestFocus();
        editor.setSelection(matcher.start(), matcher.end());

        final int endPos = matcher.end();
        editor.post(
            new Runnable() {
              @Override
              public void run() {
                editor.bringPointIntoView(endPos);
              }
            });
      } else {
        // Step 5: Wrap around logic if no more matches found
        if (start > 0) {
          new AlertDialog.Builder(this)
              .setTitle("Search")
              .setMessage("No more matches. Search from beginning?")
              .setPositiveButton(
                  "Yes",
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                      // Reset cursor to start and trigger the search again
                      editor.setSelection(0);
                      executeFindNext();
                    }
                  })
              .setNegativeButton("No", null)
              .show();
        } else {
          Toast.makeText(this, "Pattern not found!", Toast.LENGTH_SHORT).show();
        }
      }
    } catch (Exception e) {
      // Handle regex syntax or other processing errors
      Toast.makeText(this, "Regex error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  // Logic for backward search (The < button)
  private void executeFindPrevious() {
    if (findTarget == null || findTarget.isEmpty()) return;

    if (editor == null) return;
    final String content = editor.getText().toString();
    if (content.isEmpty()) return;

    try {
      String processedPattern = escapeSequencesForRegex(findTarget);
      Pattern pattern =
          Pattern.compile(
              processedPattern,
              Pattern.MULTILINE | Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);
      Matcher matcher = pattern.matcher(content);

      int start = editor.getSelectionStart();
      int lastMatchStart = -1;
      int lastMatchEnd = -1;

      // Find the last match that ends before the current selection start
      while (matcher.find()) {
        if (matcher.end() <= start) {
          lastMatchStart = matcher.start();
          lastMatchEnd = matcher.end();
        } else {
          break;
        }
      }

      if (lastMatchStart != -1) {
        editor.requestFocus();
        editor.setSelection(lastMatchStart, lastMatchEnd);

        final int endPos = lastMatchEnd;
        editor.post(
            new Runnable() {
              @Override
              public void run() {
                editor.bringPointIntoView(endPos);
              }
            });
      } else {
        Toast.makeText(this, "No previous matches", Toast.LENGTH_SHORT).show();
      }
    } catch (Exception e) {
      Toast.makeText(this, "Regex error", Toast.LENGTH_SHORT).show();
    }
  }

  private void initSearchToolbar() {
    final View searchBar = findViewById(R.id.search_toolbar);
    Button btnNext = (Button) findViewById(R.id.btn_next);
    Button btnPrev = (Button) findViewById(R.id.btn_prev);
    Button btnClose = (Button) findViewById(R.id.btn_close_search);

    btnNext.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            executeFindNext();
            hideKeyboard();
          }
        });

    btnPrev.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            executeFindPrevious();
            hideKeyboard();
          }
        });

    btnClose.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            searchBar.setVisibility(View.GONE);
          }
        });
  }

  private void executeGenerateTextHash() {
    if (editor == null || optionPane == null) return;

    showLoadingProgress();
    // Fetch current text content safely from our custom editor component
    final String sourceText = editor.getFullText();

    if (sourceText.isEmpty()) {
      mainHandler.post(
          new Runnable() {
            @Override
            public void run() {
              hideLoadingProgress();
              optionPane.showMessageDialog(
                  "Information: Editor is empty. Cannot generate hash code.");
            }
          });
      return;
    }

    // Execute heavy cryptographic checksum generation on our shared background thread channel
    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              // Compute secure SHA-256 fingerprint array using universal bytes mapping
              MessageDigest digest = MessageDigest.getInstance("SHA-256");
              byte[] hashBytes = digest.digest(sourceText.getBytes("UTF-8"));

              // Convert raw crypto bytes matrix into a human-readable hexadecimal representation
              StringBuilder hexString = new StringBuilder();
              for (int i = 0; i < hashBytes.length; i++) {
                String hex = Integer.toHexString(0xff & hashBytes[i]);
                if (hex.length() == 1) {
                  hexString.append('0');
                }
                hexString.append(hex);
              }

              // Convert entire hash digest directly into an uppercase standard string
              final String finalHashResult = hexString.toString().toUpperCase(mylocale);

              // Return back to primary layout user interface thread to trigger dialog interactions
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      hideLoadingProgress();
                      // Utilizing showInputDialog so the user can easily select, highlight, and
                      // copy the hash value
                      optionPane.showInputDialog(
                          "SHA-256 Unique Fingerprint:",
                          finalHashResult,
                          new MOptionPane.OnInputListener() {
                            @Override
                            public void onResult(String modifiedInput) {
                              // No internal actions required if user closes or presses ok on the
                              // read-only display dialog
                            }
                          });
                    }
                  });

            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      hideLoadingProgress();
                      Toast.makeText(
                              getApplicationContext(),
                              "Hash Error: " + e.getMessage(),
                              Toast.LENGTH_LONG)
                          .show();
                    }
                  });
            }
          }
        });
  }

  private void addDiyez() {
    if (editor == null) return;
    final String content = editor.getText().toString();

    if (content == null || content.length() < 1) {
      Toast.makeText(this, "Text is empty!", Toast.LENGTH_SHORT).show();
      return;
    }

    // Save backup snapshot before initiating mutations
    lastBackupText = content;

    showLoadingProgress();

    // Standardized background execution utilizing the shared file IO executor channel
    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              // Fire the heavy background engine conversion algorithm
              final String processedText = V2Utils.getDiyezString(content);

              if (processedText != null) {
                // Dispatch processed content safely back to the primary main UI thread
                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        editor.setEditorText(processedText, true);
                        editor.requestFocus();
                        Toast.makeText(
                                getApplicationContext(),
                                "Diyez Added Successfully!",
                                Toast.LENGTH_SHORT)
                            .show();
                        hideLoadingProgress();
                      }
                    });
              } else {
                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        hideLoadingProgress();
                      }
                    });
              }
            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Add Diyez operation failed",
                              Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            }
          }
        });
  }

  private void toBrackets() {
    if (editor == null) return;
    final String content = editor.getText().toString();

    if (content == null || content.length() < 1) {
      Toast.makeText(this, "Text is empty!", Toast.LENGTH_SHORT).show();
      return;
    }

    lastBackupText = content;
    showLoadingProgress();

    // Standardized background execution utilizing the shared file IO executor channel
    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              // Execute the heavy background block processing algorithm
              final String processedText = V2Utils.processBlocksSeparately(content);

              if (processedText != null) {
                // Dispatch processed content safely back to the primary main UI thread
                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        editor.setEditorText(processedText, true);
                        editor.requestFocus();
                        Toast.makeText(
                                getApplicationContext(),
                                "Footnotes Converted to Brackets!",
                                Toast.LENGTH_SHORT)
                            .show();
                        hideLoadingProgress();
                      }
                    });
              } else {
                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        hideLoadingProgress();
                      }
                    });
              }
            } catch (final Exception e) {
              // Catch the exact software exception and extract the absolute stack trace
              StringWriter sw = new StringWriter();
              PrintWriter pw = new PrintWriter(sw);
              e.printStackTrace(pw);
              final String fullStackTrace = sw.toString();

              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      // Print the raw exception report directly into the editor layout
                      editor.setEditorText("ACTUAL ERROR REPORT:\n" + fullStackTrace, true);
                      hideLoadingProgress();
                    }
                  });
            }
          }
        });
  }

  private void executeBeautify() {
    if (editor == null) return;
    final String content = editor.getText().toString();

    if (content == null || content.length() < 1) {
      Toast.makeText(this, "Text is empty!", Toast.LENGTH_SHORT).show();
      return;
    }

    // Save backup snapshot before initiating mutations
    lastBackupText = content;

    // Config for V2Utils (keeping your exact desktop parity flags)
    V2Utils.setBorrowNums(false);

    showLoadingProgress();

    // Standardized background execution utilizing the shared file IO executor channel
    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              // 1. Fire the heavy desktop engine conversion algorithm inside the background thread
              final String processedText = V2UTFToISO.cnt(content);

              if (processedText != null) {
                // Dispatch processed content safely back to the primary main UI thread
                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        editor.setEditorText(processedText, true);
                        editor.requestFocus();
                        Toast.makeText(
                                getApplicationContext(), "Text Beautified!", Toast.LENGTH_SHORT)
                            .show();
                        hideLoadingProgress();
                      }
                    });
              } else {
                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        hideLoadingProgress();
                      }
                    });
              }
            } catch (final IOException e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Error in V2UTFToISO engine!",
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Beautify operation failed",
                              Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            }
          }
        });
  }

  private void executeCodeBeautify() {
    if (editor == null) return;
    final String content = editor.getText().toString();

    if (content == null || content.isEmpty()) {
      Toast.makeText(this, "Text is empty!", Toast.LENGTH_SHORT).show();
      return;
    }

    // Save backup snapshot before initiating layout mutations
    lastBackupText = content;
    showLoadingProgress();

    // Standardized background execution utilizing the shared file IO executor channel
    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              // Initialize clean lexer states maintained across the entire text stream
              boolean inString = false;
              boolean inChar = false;
              boolean inMultiComment = false;

              final int spaces = 2; // Fixed spacing configuration as requested
              int level = 0;

              // Pre-allocate output buffer capacity to match raw text payload structures
              final StringBuilder sb = new StringBuilder(content.length());

              // Fast text line-splitter loop bypassing heavy regex split arrays
              int len = content.length();
              int start = 0;
              boolean first = true;

              while (start < len) {
                int nextLineBreak = content.indexOf('\n', start);
                int end = (nextLineBreak == -1) ? len : nextLineBreak;

                String rawLine = content.substring(start, end);
                String trimmed = rawLine.trim();

                // Preserve completely vacant empty code lines safely
                if (trimmed.isEmpty()) {
                  if (!first) sb.append('\n');
                  first = false;
                  start = end + 1;
                  continue;
                }

                int open = 0;
                int close = 0;
                boolean startsWithClose = false;
                boolean foundFirstMeaningful = false;

                // Snapshot structural indicators prior to traversing this line's tokens
                boolean initialInComment = inMultiComment;
                boolean initialInLiteral = inString || inChar;

                for (int i = 0; i < trimmed.length(); i++) {
                  char c = trimmed.charAt(i);
                  char next = (i + 1 < trimmed.length()) ? trimmed.charAt(i + 1) : '\0';

                  // 1. Monitor Multi-line comment state blocks
                  if (inMultiComment) {
                    if (c == '*' && next == '/') {
                      inMultiComment = false;
                      i++; // Consume trailing token
                    }
                    continue;
                  }

                  // 2. Short-circuit execution loops upon hitting single-line triggers
                  if (!inString && !inChar && c == '/' && next == '/') {
                    break;
                  }

                  if (!inString && !inChar && c == '/' && next == '*') {
                    inMultiComment = true;
                    i++; // Consume trailing token
                    continue;
                  }

                  // 3. Resolve active character string backslash escape flags
                  boolean isEscaped = false;
                  if (inString || inChar) {
                    int backslashCount = 0;
                    int checkIndex = i - 1;
                    while (checkIndex >= 0 && trimmed.charAt(checkIndex) == '\\') {
                      backslashCount++;
                      checkIndex--;
                    }
                    if (backslashCount % 2 != 0) {
                      isEscaped = true;
                    }
                  }

                  // 4. Toggle text token boundaries
                  if (!inChar && c == '"' && !isEscaped) {
                    inString = !inString;
                    continue;
                  }

                  if (!inString && c == '\'' && !isEscaped) {
                    inChar = !inChar;
                    continue;
                  }

                  // 5. Evaluate brace indicators outside comments or string literals
                  if (!inString && !inChar) {
                    if (c == '{') {
                      open++;
                      if (!foundFirstMeaningful) foundFirstMeaningful = true;
                    } else if (c == '}') {
                      close++;
                      if (!foundFirstMeaningful) {
                        startsWithClose = true;
                        foundFirstMeaningful = true;
                      }
                    } else if (!Character.isWhitespace(c) && !foundFirstMeaningful) {
                      foundFirstMeaningful = true;
                    }
                  }
                }

                // Adjust line level dynamically for lines starting with closing blocks
                int lineLevel = level;
                if (startsWithClose && !initialInComment && !initialInLiteral) {
                  lineLevel = Math.max(0, level - 1);
                }

                // Append newline delimiters correctly between lines
                if (!first) sb.append('\n');

                // Generate structural tabs indentation prefix
                if (lineLevel > 0) {
                  int totalSpaces = lineLevel * spaces;
                  for (int k = 0; k < totalSpaces; k++) {
                    sb.append(' ');
                  }
                }
                sb.append(trimmed);

                // Re-calculate depth hierarchy for downstream blocks
                level = level + open - close;
                if (level < 0) level = 0;

                first = false;
                start = end + 1;
              }
              sb.append("\n");

              final String finalResult = sb.toString();

              // Dispatch delivery safely back to the primary main UI thread
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      editor.setEditorText(finalResult, true);
                      editor.requestFocus();
                      Toast.makeText(
                              getApplicationContext(),
                              "Code Beautified! (2 Spaces)",
                              Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });

            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Beautify operation failed",
                              Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            }
          }
        });
  }

  private void applyTextDirectionSafely(final int direction, final int gravity) {
    if (editor == null) return;
    editor.setTextDirection(direction);
    editor.setGravity(gravity | Gravity.TOP);
    editor.requestLayout();
    editor.invalidate();
  }

  private void showMaxLenDialog() {
    optionPane.showInputDialog(
        "Enter a value between 1 and 1024:",
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            if (input == null) return;
            String inputStr = input.trim();
            if (inputStr.isEmpty()) {
              Toast.makeText(getApplicationContext(), "Input cannot be empty!", Toast.LENGTH_SHORT)
                  .show();
              return;
            }
            try {
              long userValue = Long.parseLong(inputStr);
              if (userValue >= 1L && userValue <= 1024L) {
                long returnedKB = userValue * ONEKBL;
                MAX_CHUNK_SIZE = (returnedKB + TAMPON);
                Toast.makeText(
                        getApplicationContext(),
                        "Limits updated: " + Long.toString(MAX_CHUNK_SIZE),
                        Toast.LENGTH_SHORT)
                    .show();
              } else {
                Toast.makeText(
                        getApplicationContext(),
                        "Out of bounds! EnterKB 1 - 1024.",
                        Toast.LENGTH_LONG)
                    .show();
              }
            } catch (NumberFormatException e) {
              Toast.makeText(getApplicationContext(), "Invalid number!", Toast.LENGTH_SHORT).show();
            }
          }
        });
  }

  private void executeReplaceAll() {
    if (findTarget == null || findTarget.isEmpty()) {
      Toast.makeText(this, "Search term is empty", Toast.LENGTH_SHORT).show();
      return;
    }
    if (editor == null) return;

    final String fullText = editor.getText().toString();
    final int selStart = editor.getSelectionStart();
    final int selEnd = editor.getSelectionEnd();
    final boolean hasSelection = (selStart >= 0 && selEnd >= 0 && selStart != selEnd);
    final int rangeStart = hasSelection ? Math.min(selStart, selEnd) : 0;
    final int rangeEnd = hasSelection ? Math.max(selStart, selEnd) : fullText.length();

    showLoadingProgress();

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              String processedTarget = escapeSequencesForRegex(findTarget);
              // Note: replaceWith has already been run through escapeSequencesForRegex when it
              // was set, so \\n, \\t and \\\\ are already expanded. Only $N group references
              // remain to be resolved per-match below.
              String processedReplacement = (replaceWith != null) ? replaceWith : "";

              Pattern pattern =
                  Pattern.compile(processedTarget, Pattern.MULTILINE | Pattern.UNICODE_CASE);
              Matcher matcher = pattern.matcher(fullText);

              StringBuffer sb = new StringBuffer(fullText.length());
              boolean matched = false;
              while (matcher.find()) {
                matched = true;
                if (matcher.start() >= rangeStart && matcher.end() <= rangeEnd) {
                  // 1) Resolve $1, $2 ... against THIS match so group refs become real text.
                  String expanded = expandGroupReferences(processedReplacement, matcher);
                  // 2) Apply the case mode to the resolved text (not to the template).
                  String applied = applyReplaceMode(expanded);
                  // 3) Write the result literally: $ and \ are now ordinary characters.
                  matcher.appendReplacement(sb, Matcher.quoteReplacement(applied));
                } else {
                  // Outside the selected range: put the matched text back verbatim.
                  matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
                }
              }
              matcher.appendTail(sb);

              if (!matched) {
                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        Toast.makeText(
                                getApplicationContext(),
                                "No matches found to replace",
                                Toast.LENGTH_SHORT)
                            .show();
                        hideLoadingProgress();
                      }
                    });
                return;
              }

              final String result = sb.toString();
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      editor.setEditorText(result, true);
                      Toast.makeText(
                              getApplicationContext(), "Replace applied.", Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });

            } catch (final PatternSyntaxException e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Invalid Regex: " + e.getDescription(),
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Error during replacement",
                              Toast.LENGTH_SHORT)
                          .show();
                      hideLoadingProgress();
                    }
                  });
            }
          }
        });
  }

  /**
   * Expands $0..$9 group references in the replacement template against the current match.
   *
   * <p>Details:
   *
   * <ul>
   *   <li>$N (N = single digit 0..9) is replaced by the text of group N.
   *   <li>If group N does not exist in the pattern, the reference is emitted literally as "$N"
   *       instead of throwing.
   *   <li>$$ is treated as a literal single '$'.
   *   <li>A trailing '$' with no digit after it is emitted literally.
   *   <li>Any other character is copied verbatim.
   * </ul>
   *
   * <p>Escape sequences (\n, \t, \\ etc.) were already expanded by escapeSequencesForRegex when
   * replaceWith was captured, so this method only deals with $ group references.
   */
  private String expandGroupReferences(String template, Matcher m) {
    if (template == null || template.isEmpty()) return "";
    StringBuilder out = new StringBuilder(template.length() + 16);
    int i = 0;
    while (i < template.length()) {
      char c = template.charAt(i);
      if (c == '$' && i + 1 < template.length()) {
        char next = template.charAt(i + 1);
        if (next == '$') {
          // $$ -> literal $
          out.append('$');
          i += 2;
          continue;
        }
        if (next >= '0' && next <= '9') {
          int groupNum = next - '0';
          try {
            String groupText = m.group(groupNum);
            out.append(groupText != null ? groupText : "");
          } catch (Exception e) {
            // Invalid group number: emit the reference literally.
            out.append('$').append(next);
          }
          i += 2;
          continue;
        }
        // '$' not followed by '$' or a digit: emit literally.
        out.append(c);
        i++;
      } else {
        out.append(c);
        i++;
      }
    }
    return out.toString();
  }

  private void splitFile(final Uri uri) {
    if (uri == null) return;

    String sourceFileName = getFileName(uri);
    String cleanBaseName = "split_document";
    String detectedExt = ".txt";

    if (sourceFileName != null) {
      int mindex = sourceFileName.lastIndexOf(".");
      if (mindex > 0) {
        cleanBaseName = sourceFileName.substring(0, mindex);
        detectedExt = sourceFileName.substring(mindex);
        if (detectedExt.trim().length() < 1) {
          detectedExt = ".txt";
        }
      } else {
        cleanBaseName = sourceFileName;
      }
    }

    final String finalExtension = detectedExt;
    final String finalBaseName = cleanBaseName;

    optionPane.showSingleChoiceDialog(
        "Select Split Size",
        options,
        new MOptionPane.OnChoiceListener() {
          @Override
          public void onResult(int which) {
            if (which == -1) return;

            final long chunkSizeBytes = sizesInBytes[which];
            showLoadingProgress();

            fileIOExecutor.execute(
                new Runnable() {
                  @Override
                  public void run() {
                    InputStream inputStream = null;
                    OutputStream outputStream = null;
                    ParcelFileDescriptor pfd = null;
                    ByteArrayOutputStream chunkBuffer =
                        new ByteArrayOutputStream((int) chunkSizeBytes + BUFSIZE);

                    try {
                      inputStream = getContentResolver().openInputStream(uri);
                      if (inputStream == null) throw new IOException("Unable to open URI stream");

                      int fileIndex = 1;
                      byte[] readBuffer = new byte[BUFSIZE];
                      int bytesRead;

                      while ((bytesRead = inputStream.read(readBuffer)) != -1) {
                        chunkBuffer.write(readBuffer, 0, bytesRead);

                        if (chunkBuffer.size() >= chunkSizeBytes) {
                          byte[] accumulatedBytes = chunkBuffer.toByteArray();

                          int splitPoint = -1;
                          for (int i = accumulatedBytes.length - 1; i >= 0; i--) {
                            char c = (char) (accumulatedBytes[i] & 0xFF);
                            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                              splitPoint = i + 1;
                              break;
                            }
                          }

                          if (splitPoint == -1) {
                            splitPoint = (int) chunkSizeBytes;
                          }

                          String formattedPartitionName =
                              String.format(
                                  Locale.US, finalBaseName + "_%03d" + finalExtension, fileIndex);

                          ContentValues values = new ContentValues();
                          values.put(MediaStore.MediaColumns.DISPLAY_NAME, formattedPartitionName);
                          values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                          values.put(
                              MediaStore.MediaColumns.RELATIVE_PATH,
                              Environment.DIRECTORY_DOWNLOADS + "/carets/notepad/splits");

                          Uri outputUri =
                              getContentResolver()
                                  .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                          if (outputUri == null)
                            throw new IOException(
                                "Failed to generate MediaStore record framework context.");

                          pfd = getContentResolver().openFileDescriptor(outputUri, "wt");
                          if (pfd == null)
                            throw new IOException(
                                "Failed to acquire atomic partition descriptors descriptor.");

                          outputStream = new FileOutputStream(pfd.getFileDescriptor());

                          outputStream.write(accumulatedBytes, 0, splitPoint);
                          outputStream.flush();

                          try {
                            pfd.getFileDescriptor().sync();
                          } catch (Exception ignored) {
                          }

                          outputStream.close();
                          pfd.close();
                          outputStream = null;
                          pfd = null;

                          chunkBuffer.reset();
                          int leftoverLength = accumulatedBytes.length - splitPoint;
                          if (leftoverLength > 0) {
                            chunkBuffer.write(accumulatedBytes, splitPoint, leftoverLength);
                          }

                          fileIndex++;
                        }
                      }
                      if (chunkBuffer.size() > 0) {
                        String formattedPartitionName =
                            String.format(
                                Locale.US, finalBaseName + "_%03d" + finalExtension, fileIndex);

                        ContentValues values = new ContentValues();
                        values.put(MediaStore.MediaColumns.DISPLAY_NAME, formattedPartitionName);
                        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                        values.put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/carets/notepad/splits");

                        Uri outputUri =
                            getContentResolver()
                                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (outputUri == null)
                          throw new IOException(
                              "Failed to generate MediaStore record framework context.");

                        pfd = getContentResolver().openFileDescriptor(outputUri, "wt");
                        if (pfd == null)
                          throw new IOException(
                              "Failed to acquire atomic partition descriptors descriptor.");

                        outputStream = new FileOutputStream(pfd.getFileDescriptor());

                        chunkBuffer.writeTo(outputStream);
                        outputStream.flush();

                        try {
                          pfd.getFileDescriptor().sync();
                        } catch (Exception ignored) {
                        }

                        outputStream.close();
                        pfd.close();
                        outputStream = null;
                        pfd = null;
                      } else {
                        fileIndex--;
                      }

                      final int totalSplits = fileIndex;

                      mainHandler.post(
                          new Runnable() {
                            @Override
                            public void run() {
                              hideLoadingProgress();
                              Toast.makeText(
                                      getApplicationContext(),
                                      "Successfully split into "
                                          + totalSplits
                                          + " files inside Downloads/carets/notepad/splits/",
                                      Toast.LENGTH_LONG)
                                  .show();
                            }
                          });

                    } catch (final Exception e) {
                      mainHandler.post(
                          new Runnable() {
                            @Override
                            public void run() {
                              hideLoadingProgress();
                              Toast.makeText(
                                      getApplicationContext(),
                                      "Split Error: " + e.getMessage(),
                                      Toast.LENGTH_LONG)
                                  .show();
                            }
                          });
                    } finally {
                      try {
                        if (outputStream != null) {
                          outputStream.close();
                          outputStream = null;
                        }
                      } catch (Exception ignored) {
                      }

                      try {
                        if (pfd != null) {
                          pfd.close();
                          pfd = null;
                        }
                      } catch (Exception ignored) {
                      }

                      try {
                        if (chunkBuffer != null) {
                          chunkBuffer.close();
                          chunkBuffer = null;
                        }
                      } catch (Exception ignored) {
                      }

                      try {
                        if (inputStream != null) {
                          inputStream.close();
                          inputStream = null;
                        }
                      } catch (Exception ignored) {
                      }
                    }
                  }
                });
          }
        });
  }

  private static class FileItem {
    private Uri uri;
    private String name;

    FileItem(Uri uri, String name) {
      this.uri = uri;
      this.name = name;
    }
  }

  private void mergeFiles(final Intent data) {
    if (data == null) return;
    showLoadingProgress();

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            ArrayList<FileItem> fileList = new ArrayList<FileItem>();

            if (data.getClipData() != null) {
              int count = data.getClipData().getItemCount();
              for (int i = 0; i < count; i++) {
                Uri fileUri = data.getClipData().getItemAt(i).getUri();
                String fileName = getFileName(fileUri);
                if (fileUri != null) {
                  fileList.add(new FileItem(fileUri, fileName));
                }
              }
            } else if (data.getData() != null) {
              Uri fileUri = data.getData();
              String fileName = getFileName(fileUri);
              fileList.add(new FileItem(fileUri, fileName));
            }

            if (fileList.isEmpty()) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Merge failed: No files detected in choice payload.",
                              Toast.LENGTH_LONG)
                          .show();
                      hideLoadingProgress();
                    }
                  });
              return;
            }

            Collections.sort(
                fileList,
                new Comparator<FileItem>() {
                  @Override
                  public int compare(FileItem o1, FileItem o2) {
                    if (o1.name == null && o2.name == null) return 0;
                    if (o1.name == null) return 1;
                    if (o2.name == null) return -1;
                    return o1.name.compareToIgnoreCase(o2.name);
                  }
                });

            String pfname = fileList.get(0).name;
            int pindex = pfname != null ? pfname.lastIndexOf(".") : -1;
            if (pindex > 0) {
              pfname = pfname.substring(0, pindex);
            } else if (pfname == null) {
              pfname = "merged_document";
            }

            final String prefix = pfname;

            saveMergedFileWithMediaStore(prefix, fileList);
          }
        });
  }

  private String getCustomTimestamp() {
    SimpleDateFormat sdf = new SimpleDateFormat("ddMMyy_HHmm", Locale.US);
    return sdf.format(new Date());
  }

  private void saveMergedFileWithMediaStore(
      final String prefix, final ArrayList<FileItem> fileList) {
    OutputStream outputStream = null;
    ParcelFileDescriptor pfd = null;
    Uri outputUri = null;

    try {
      final String displayName = prefix + "_" + getCustomTimestamp() + ".txt";

      ContentValues values = new ContentValues();
      values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
      values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
      values.put(
          MediaStore.MediaColumns.RELATIVE_PATH,
          Environment.DIRECTORY_DOWNLOADS + "/carets/notepad/merges");

      outputUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

      if (outputUri == null) {
        throw new IOException("Failed to create modern MediaStore record context reference.");
      }

      pfd = getContentResolver().openFileDescriptor(outputUri, "wt");
      if (pfd == null) {
        throw new IOException(
            "Failed to acquire atomic file descriptor references from content matrix.");
      }

      outputStream = new FileOutputStream(pfd.getFileDescriptor());

      byte[] byteBuffer = new byte[BUFSIZE];

      for (FileItem item : fileList) {
        InputStream inputStream = null;
        try {
          inputStream = getContentResolver().openInputStream(item.uri);
          if (inputStream != null) {
            String header =
                "---Starts: " + (item.name != null ? item.name : "Unknown") + " ---\n\n";
            outputStream.write(header.getBytes("UTF-8"));

            int bytesRead;
            while ((bytesRead = inputStream.read(byteBuffer)) != -1) {
              outputStream.write(byteBuffer, 0, bytesRead);
            }

            String footer =
                "\n---Ends: " + (item.name != null ? item.name : "Unknown") + " ---\n\n";
            outputStream.write(footer.getBytes("UTF-8"));
          }
        } catch (Exception fileException) {
          final String faultyName = item.name;
          mainHandler.post(
              new Runnable() {
                @Override
                public void run() {
                  Toast.makeText(
                          getApplicationContext(),
                          "Warning: Skipped corrupted segment "
                              + (faultyName != null ? faultyName : ""),
                          Toast.LENGTH_SHORT)
                      .show();
                }
              });
        } finally {
          if (inputStream != null) {
            try {
              inputStream.close();
            } catch (IOException ignored) {
            }
            inputStream = null;
          }
        }
      }

      outputStream.flush();

      try {
        pfd.getFileDescriptor().sync();
      } catch (Exception ignored) {
      }

      mainHandler.post(
          new Runnable() {
            @Override
            public void run() {
              Toast.makeText(
                      getApplicationContext(),
                      "Files successfully merged into Downloads/carets/notepad/merges/",
                      Toast.LENGTH_LONG)
                  .show();
              hideLoadingProgress();
            }
          });

    } catch (final Exception e) {
      if (outputUri != null) {
        try {
          getContentResolver().delete(outputUri, null, null);
        } catch (Exception ignored) {
        }
      }

      mainHandler.post(
          new Runnable() {
            @Override
            public void run() {
              Toast.makeText(
                      getApplicationContext(),
                      "Failed to save merge output: " + e.getMessage(),
                      Toast.LENGTH_LONG)
                  .show();
              hideLoadingProgress();
            }
          });
    } finally {
      try {
        if (outputStream != null) {
          outputStream.close();
          outputStream = null;
        }
      } catch (Exception ignored) {
      }

      try {
        if (pfd != null) {
          pfd.close();
          pfd = null;
        }
      } catch (Exception ignored) {
      }
    }
  }

  private void showToastOnMainThread(final String textMessage) {
    if (textMessage == null) return;
    mainHandler.post(
        new Runnable() {
          @Override
          public void run() {
            Toast.makeText(getApplicationContext(), textMessage, Toast.LENGTH_LONG).show();
          }
        });
  }

  private void showEditorDetails() {
    if (editor == null) return;

    // Access text directly via CharSequence to eliminate catastrophic memory copy allocations
    final CharSequence textSeq = editor.getText();
    int totalCharsWithSpaces = textSeq.length();

    // Core metrics calculation
    int totalCharsNoSpaces = 0;
    int wordCount = 0;

    // Extract verified line metrics directly from our optimized MEditor engine
    int lineCount = editor.getLineCount();
    int paragraphCount = 0;

    // New metrics for caret tracking
    int caretPosition = editor.getSelectionStart();
    int caretLine = 1; // Default to line 1 if text is empty or caret is at start

    if (totalCharsWithSpaces > 0) {
      boolean inWord = false;
      boolean inParagraph = false;

      // Process all string statistics within a single atomic character traversal loop pass
      for (int i = 0; i < totalCharsWithSpaces; i++) {
        char c = textSeq.charAt(i);

        // 1. Character metrics parsing
        if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
          totalCharsNoSpaces++;
        }

        // 2. Track caret position matrix coordinates securely during the pass
        if (i < caretPosition && c == '\n') {
          caretLine++;
        }

        // 3. Ultra-fast state-machine tracking logic for total words counting without split arrays
        // overhead
        if (Character.isLetterOrDigit(c)) {
          if (!inWord) {
            wordCount++;
            inWord = true;
          }
        } else {
          inWord = false;
        }

        // 4. Structural paragraphs tracker without spawning heavy heap array structures
        if (c != '\n' && c != '\r' && c != ' ' && c != '\t') {
          if (!inParagraph) {
            paragraphCount++;
            inParagraph = true;
          }
        } else if (c == '\n') {
          inParagraph = false;
        }
      }
    } else {
      caretLine = 0;
    }

    // Zero-allocation memory footprint calculation scales line size by encoding metrics directly
    double encodingMultiplier = 1.0;
    if (UTF8.equalsIgnoreCase(currentEncoding)) {
      encodingMultiplier =
          1.2; // Statistical weighting multiplier for plain text UTF-8 metadata expansion
    }

    double currentBytes = (double) totalCharsWithSpaces * encodingMultiplier;
    double maxBytes = (double) MAX_CHUNK_SIZE;

    // Convert metrics to precise double KB values to eliminate integer division truncations
    double currentKb = currentBytes / ONEKBD;
    double limitKb = maxBytes / ONEKBD;

    double filePayloadPercentage = (currentBytes / maxBytes) * CIEN;
    if (filePayloadPercentage > CIEN) {
      filePayloadPercentage = CIEN;
    }

    // Kept exact original design framework pattern intact
    StringBuilder info = new StringBuilder();
    info.append("Characters (with spaces): ")
        .append(totalCharsWithSpaces)
        .append("\n")
        .append("Characters (no spaces): ")
        .append(totalCharsNoSpaces)
        .append("\n")
        .append("Word Count: ")
        .append(wordCount)
        .append("\n")
        .append("Line Count: ")
        .append(lineCount)
        .append("\n")
        .append("Paragraphs: ")
        .append(paragraphCount)
        .append("\n")
        .append("Caret Position: ")
        .append(caretPosition)
        .append("\n")
        .append("Caret Line: ")
        .append(caretLine)
        .append("\n")
        .append("Last Filename: ")
        .append(currentFileName)
        .append("\n")
        .append("Estimated Weight: ")
        .append(String.format("%.2f KB", currentKb))
        .append("\n")
        .append(String.format("%.0f", limitKb))
        .append(" KB Limit Usage: ")
        .append(String.format("%.1f%%", filePayloadPercentage));

    if (optionPane == null) {
      optionPane = new MOptionPane(this);
    }
    optionPane.showMessageDialog(info.toString());
  }

  private void showCharacterDetails() {
    int start = editor.getSelectionStart();
    int end = editor.getSelectionEnd();

    if (start == end) return;

    // Avoid full text memory duplication via substring/toString allocations on large documents
    CharSequence selectedSeq =
        editor.getText().subSequence(start, Math.min(start + 2, editor.getText().length()));

    /*
     * CRITICAL: Use codePointAt instead of charAt to support
     * Emojis and other supplementary characters (Surrogate Pairs).
     */
    int codePoint = Character.codePointAt(selectedSeq, 0);

    // To display the character correctly, convert the codePoint to a String
    String charDisplay = new String(Character.toChars(codePoint));

    // 1. Get Unicode Block (e.g., Latin, Arabic, Emoticons)
    Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
    String blockName = (block != null) ? block.toString() : "Unknown Block";

    // 2. Get Official Character Name (Requires API 24+)
    String charName = "N/A";
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      charName = Character.getName(codePoint);
    }

    // 3. Get Character Category (Letter, Number, Symbol etc.)
    int categoryType = Character.getType(codePoint);
    String categoryName = getCategoryName(categoryType);

    // Check if the character consists of surrogate pairs
    String surrogateInfo = "-";
    if (Character.isSupplementaryCodePoint(codePoint)) {
      char high = Character.highSurrogate(codePoint);
      char low = Character.lowSurrogate(codePoint);

      // Manual hex conversion pattern to ensure rapid deployment layout execution
      String highHex = Integer.toHexString((int) high).toUpperCase();
      String lowHex = Integer.toHexString((int) low).toUpperCase();

      // Pad single zero components safely without legacy format parser overheads
      while (highHex.length() < 4) highHex = "0" + highHex;
      while (lowHex.length() < 4) lowHex = "0" + lowHex;

      surrogateInfo = "\\u" + highHex + "\\u" + lowHex;
    }

    // Manual padding layout alignment for the final target unicode presentation hex string
    String uniHex = Integer.toHexString(codePoint).toUpperCase();
    while (uniHex.length() < 4) uniHex = "0" + uniHex;

    // Clean text mapping structure optimized purely for flat MOptionPane delivery channels
    StringBuilder info = new StringBuilder();
    info.append("Char: ")
        .append(charDisplay)
        .append("\n")
        .append("Name: ")
        .append(charName)
        .append("\n")
        .append("Block: ")
        .append(blockName)
        .append("\n")
        .append("Type: ")
        .append(categoryName)
        .append("\n")
        .append("Dec: ")
        .append(codePoint)
        .append("\n")
        .append("Hex: 0x")
        .append(Integer.toHexString(codePoint).toUpperCase())
        .append("\n")
        .append("Bin: ")
        .append(Integer.toBinaryString(codePoint))
        .append("\n")
        .append("Uni: U+")
        .append(uniHex)
        .append("\n")
        .append("Surrogate: ")
        .append(surrogateInfo);

    // Migrate the diagnostic alerts notification presentation channel safely over to MOptionPane
    // structures
    if (optionPane == null) {
      optionPane = new MOptionPane(this);
    }
    optionPane.showMessageDialog(info.toString());
  }

  /** Helper method to map Unicode category constants to English names. */
  private String getCategoryName(int type) {
    switch (type) {
      case Character.UPPERCASE_LETTER:
        return "Uppercase Letter";
      case Character.LOWERCASE_LETTER:
        return "Lowercase Letter";
      case Character.TITLECASE_LETTER:
        return "Titlecase Letter";
      case Character.MODIFIER_LETTER:
        return "Modifier Letter";
      case Character.OTHER_LETTER:
        return "Other Letter";
      case Character.NON_SPACING_MARK:
        return "Non-Spacing Mark";
      case Character.ENCLOSING_MARK:
        return "Enclosing Mark";
      case Character.COMBINING_SPACING_MARK:
        return "Combining Mark";
      case Character.DECIMAL_DIGIT_NUMBER:
        return "Decimal Number";
      case Character.LETTER_NUMBER:
        return "Letter-like Number";
      case Character.OTHER_NUMBER:
        return "Other Number";
      case Character.SPACE_SEPARATOR:
        return "Space";
      case Character.LINE_SEPARATOR:
        return "Line Separator";
      case Character.PARAGRAPH_SEPARATOR:
        return "Paragraph Separator";
      case Character.CONTROL:
        return "Control";
      case Character.FORMAT:
        return "Format";
      case Character.PRIVATE_USE:
        return "Private Use";
      case Character.SURROGATE:
        return "Surrogate";
      case Character.DASH_PUNCTUATION:
        return "Dash Punctuation";
      case Character.START_PUNCTUATION:
        return "Start Punctuation";
      case Character.END_PUNCTUATION:
        return "End Punctuation";
      case Character.CONNECTOR_PUNCTUATION:
        return "Connector Punctuation";
      case Character.OTHER_PUNCTUATION:
        return "Other Punctuation";
      case Character.MATH_SYMBOL:
        return "Math Symbol";
      case Character.CURRENCY_SYMBOL:
        return "Currency Symbol";
      case Character.MODIFIER_SYMBOL:
        return "Modifier Symbol";
      case Character.OTHER_SYMBOL:
        return "Other Symbol";
      case Character.INITIAL_QUOTE_PUNCTUATION:
        return "Initial Quote";
      case Character.FINAL_QUOTE_PUNCTUATION:
        return "Final Quote";
      default:
        return "Unknown";
    }
  }

  private void hideKeyboard() {
    if (imm == null) return;
    try {
      if (editor.getWindowToken() != null) {
        imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
      }
    } catch (Exception ignored) {
    }
  }

  // --- Lifecycle Methods ---

  @Override
  protected void onResume() {
    super.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
  }

  @Override
  protected void onStop() {
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
  }
} // class end
