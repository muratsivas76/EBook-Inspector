package net.murat.decodebook;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.Layout;
import android.text.Spanned;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.murat.uikit.*;
import net.murat.util.*;

/**
 * EBook Inspector — decompiles an APK and extracts the String[] arrays found in the Sayfa_XXXXX
 * classes, showing the first few pages on screen. Also supports plain TXT and HTML input files
 * (UTF-8) that are rendered in the same reader view.
 *
 * @author Murat
 */
public class DecodeBook extends Activity {

  private MEditor editor;

  private MFilePicker filePicker;
  private MOptionPane optionPane;
  private MColorPicker colorPicker;

  private LinearLayout searchToolbar;
  private Button btnFindPrev;
  private Button btnFindNext;
  private Button btnFindClose;
  private TextView searchStatus;

  private Uri selectedApkUri = null;
  private String selectedApkName = "";

  // 0 = none, 1 = APK (DEX), 2 = TXT, 3 = HTML.
  private int inputKind = 0;

  // Cached raw text of the current TXT/HTML input.
  private String cachedTextContent = null;

  private int lastStart = 0;
  private int lastEnd = 0;

  private boolean lastBeautified = false;
  private boolean lastCleaned = false;

  private String mainPath = null;
  private File INFOFILE = null;

  private int fgColor = 0xFF000000;
  private int bgColor = 0xFFFFFFFF;
  private int fontStyle = 0;
  private float fontSize = 24.0f;
  private float lineSpacing = 1.0f;

  // 0 = Left, 1 = Center, 2 = Right, 3 = Justify.
  private int alignment = 0;

  private int orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

  private String searchTerm = "";
  private boolean searchIsRegex = false;
  private int lastSearchFrom = 0;
  private int lastMatchStart = 0;

  private String srcRegex = "<p>";
  private String dstRegex = "\\n\\n";
  private String mode = "none";

  private int totalPages = 0;

  private String lastPagesRange = "";

  private Menu mMenu;

  private GestureDetector gestureDetector;

  private boolean landscapeFullscreen = false;

  private final ExecutorService fileIOExecutor = Executors.newSingleThreadExecutor();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  // TXT/HTML page model: 1 page = 512 bytes (0.5 KB).
  private static final int PAGE_BYTES = 512;
  // Decode shows the first 9 pages = 4.5 KB = 4608 bytes.
  private static final int DECODE_BYTES = PAGE_BYTES * 9;
  // Hard upper limit for a Pages range. A range whose page count is >= this
  // value is rejected. Page count is inclusive: end - start + 1.
  private static final int MAX_PAGE_RANGE = 1001;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    mainPath = getMainPathName();

    File carets = new File(mainPath + "/carets");
    if (!carets.exists()) carets.mkdir();

    File inspector = new File(mainPath + "/carets/inspector");
    if (!inspector.exists()) inspector.mkdir();

    File merges = new File(mainPath + "/carets/inspector/merges");
    if (!merges.exists()) merges.mkdir();

    INFOFILE = new File(mainPath + "/carets/inspector/inspectorInfo.txt");

    editor = (MEditor) findViewById(R.id.editor);

    searchToolbar = (LinearLayout) findViewById(R.id.search_toolbar);
    btnFindPrev = (Button) findViewById(R.id.btn_prev);
    btnFindNext = (Button) findViewById(R.id.btn_next);
    btnFindClose = (Button) findViewById(R.id.btn_close_search);
    searchStatus = (TextView) findViewById(R.id.search_status);

    filePicker = new MFilePicker(this);
    filePicker.setMimeTypes(
        new String[] {"application/vnd.android.package-archive", "text/plain", "text/html", "*/*"});
    optionPane = new MOptionPane(this);
    colorPicker = new MColorPicker(this);

    initSearchToolbar();
    initDoubleTapDetector();

    if (!restoreSession()) {
      try {
        orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        setRequestedOrientation(orientation);
      } catch (Exception ignored) {
      }
      showIntroText();
    }
  }

  @Override
  public void onConfigurationChanged(android.content.res.Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    mainHandler.post(
        new Runnable() {
          @Override
          public void run() {
            if (getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
              if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                enterLandscapeFullscreen();
              }
            } else {
              exitLandscapeFullscreen();
            }
            if (editor != null) editor.invalidate();
          }
        });
  }

  private void initDoubleTapDetector() {
    gestureDetector =
        new GestureDetector(
            this,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onDown(MotionEvent e) {
                return true;
              }

              @Override
              public boolean onDoubleTap(MotionEvent e) {
                if (getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                  if (landscapeFullscreen) {
                    exitLandscapeFullscreen();
                  } else {
                    enterLandscapeFullscreen();
                  }
                  return true;
                }
                return false;
              }
            });
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    if (gestureDetector != null) {
      gestureDetector.onTouchEvent(ev);
    }
    return super.dispatchTouchEvent(ev);
  }

  private void showIntroText() {
    StringBuffer sbc = new StringBuffer();
    sbc.append("\"Select\" an APK, TXT or HTML file.\n\n");
    sbc.append("\"Decode\" for 1-9 pages (first 4.5 KB for TXT/HTML).\n\n");
    sbc.append("OR \"Pages\" to enter a custom page range.\n\n");
    sbc.append("\"Beautify\" text optionally.\n\n");
    sbc.append("\"Clean Page Numbers\" optionally.\n\n");
    sbc.append("\"Format\" to change FG / BG / Font Style / Font Size / Line Spacing / Align.\n\n");
    sbc.append("\"Orientation\" to switch between Portrait and Landscape.\n");
    sbc.append("In Landscape mode the screen becomes fullscreen; double-tap\n");
    sbc.append("anywhere to show or hide the top bar and system bars.\n\n");
    sbc.append("\"Change Search Term\" to set srcRegex, dstRegex and mode.\n\n");
    sbc.append("\"Find\" to search, \"Replace\" to regex replaceAll + case magic.\n\n");
    sbc.append("\"Tool\" for cleaning and merge utilities, plus file Info.\n\n");
    sbc.append("\"Export\" to copy content to carets/inspector/ folder.\n\n");
    sbc.append("\"Help\" for menu descriptions.\n\n");
    sbc.append("\"Exit\" app cleanly.\n");
    editor.setEditorText(sbc.toString());
  }

  private void initSearchToolbar() {
    if (btnFindNext != null) {
      btnFindNext.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              executeFind();
            }
          });
    }
    if (btnFindPrev != null) {
      btnFindPrev.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              executeFindPrevious();
            }
          });
    }
    if (btnFindClose != null) {
      btnFindClose.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              if (searchToolbar != null) searchToolbar.setVisibility(View.GONE);
              editor.clearEditorHighlight();
              hideKeyboardSafe();
            }
          });
    }
  }

  private void showSearchToolbar() {
    if (searchToolbar != null) searchToolbar.setVisibility(View.VISIBLE);
    updateSearchStatus(
        searchTerm == null || searchTerm.isEmpty() ? "Find..." : "Search: " + searchTerm);
  }

  private void updateSearchStatus(final String text) {
    if (searchStatus == null) return;
    mainHandler.post(
        new Runnable() {
          @Override
          public void run() {
            try {
              searchStatus.setText(text == null ? "" : text);
            } catch (Exception ignored) {
            }
          }
        });
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    this.mMenu = menu;
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();

    if (id == R.id.menu_select) {
      executeSelect();
      return true;
    } else if (id == R.id.menu_decode) {
      executeDecode();
      return true;
    } else if (id == R.id.menu_pages) {
      executePages();
      return true;
    } else if (id == R.id.menu_export) {
      executeExport();
      return true;
    } else if (id == R.id.menu_beautify) {
      executeBeautify();
      return true;
    } else if (id == R.id.menu_clean_page_numbers) {
      executeCleanPageNumbers();
      return true;
    } else if (id == R.id.menu_format_fg) {
      executeFormatFg();
      return true;
    } else if (id == R.id.menu_format_bg) {
      executeFormatBg();
      return true;
    } else if (id == R.id.menu_format_font_style) {
      executeFormatFontStyle();
      return true;
    } else if (id == R.id.menu_format_font_size) {
      executeFormatFontSize();
      return true;
    } else if (id == R.id.menu_format_line_spacing) {
      executeFormatLineSpacing();
      return true;
    } else if (id == R.id.menu_format_align_left) {
      executeFormatAlignLeft();
      return true;
    } else if (id == R.id.menu_format_align_center) {
      executeFormatAlignCenter();
      return true;
    } else if (id == R.id.menu_format_align_right) {
      executeFormatAlignRight();
      return true;
    } else if (id == R.id.menu_format_align_justify) {
      executeFormatAlignJustify();
      return true;
    } else if (id == R.id.menu_format_reset) {
      executeFormatReset();
      return true;
    } else if (id == R.id.menu_orientation_portrait) {
      executeOrientationPortrait();
      return true;
    } else if (id == R.id.menu_orientation_landscape) {
      executeOrientationLandscape();
      return true;
    } else if (id == R.id.menu_search_term) {
      executeChangeSearchTerm();
      return true;
    } else if (id == R.id.menu_find) {
      if (searchTerm == null || searchTerm.isEmpty()) {
        Toast.makeText(this, "Set a search term first (Change Search Term).", Toast.LENGTH_LONG)
            .show();
        return true;
      }
      showSearchToolbar();
      executeFind();
      return true;
    } else if (id == R.id.menu_replace) {
      executeReplace();
      return true;
    } else if (id == R.id.menu_tool_remove_odd_chars) {
      executeToolRemoveOddChars();
      return true;
    } else if (id == R.id.menu_tool_clean_extra_spaces) {
      executeToolCleanExtraSpaces();
      return true;
    } else if (id == R.id.menu_tool_trim_lines) {
      executeToolTrimLines();
      return true;
    } else if (id == R.id.menu_tool_clean_numbers_of_pages) {
      executeToolCleanNumbersOfPages();
      return true;
    } else if (id == R.id.menu_tool_remove_chars_bigger_than) {
      executeToolRemoveCharsBiggerThan();
      return true;
    } else if (id == R.id.menu_tool_remove_chars_smaller_than) {
      executeToolRemoveCharsSmallerThan();
      return true;
    } else if (id == R.id.menu_tool_merge_files) {
      executeToolMergeFiles();
      return true;
    } else if (id == R.id.menu_tool_info) {
      executeToolInfo();
      return true;
    } else if (id == R.id.menu_help) {
      executeHelp();
      return true;
    } else if (id == R.id.menu_exit) {
      exit();
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  // ---------------------------------------------------------------------
  // SELECT
  // ---------------------------------------------------------------------

  private void executeSelect() {
    filePicker.setMultiSelectionEnabled(false);
    filePicker.showOpenDialog(MFilePicker.OPEN_REQUEST_CODE);
  }

  /**
   * Validates the picked file extension. Accepted: .apk, .txt, .html (and .htm as a convenience).
   * Returns 0 for unsupported, 1 for APK, 2 for TXT, 3 for HTML.
   */
  private int detectInputKind(String name) {
    if (name == null) return 0;
    String lower = name.toLowerCase(Locale.US);
    if (lower.endsWith(".apk")) return 1;
    if (lower.endsWith(".txt")) return 2;
    if (lower.endsWith(".html") || lower.endsWith(".htm")) return 3;
    return 0;
  }

  // ---------------------------------------------------------------------
  // EXPORT
  // ---------------------------------------------------------------------

  /**
   * Saves the current editor text into carets/inspector/ using a dynamic file name derived from the
   * selected file base name and the decoded range: <base>_00001-00055.txt (5-digit zero padding).
   */
  private void executeExport() {
    try {
      if (selectedApkUri == null) {
        Toast.makeText(DecodeBook.this, "Select a file first.", Toast.LENGTH_SHORT).show();
        return;
      }
      if (lastStart < 1 || lastEnd < lastStart) {
        Toast.makeText(DecodeBook.this, "Decode a range first.", Toast.LENGTH_SHORT).show();
        return;
      }
      String text = editor.getFullText();
      if (text == null || text.length() < 1) {
        Toast.makeText(DecodeBook.this, "Text is empty.", Toast.LENGTH_SHORT).show();
        return;
      }

      String base = buildExportBaseName(selectedApkName);
      String fileName =
          base + "_" + String.format(Locale.US, "%05d-%05d", lastStart, lastEnd) + ".txt";
      File outFile = new File(mainPath + "/carets/inspector", fileName);

      OutputStream fos = new FileOutputStream(outFile);
      PrintStream ps = new PrintStream(fos, true, "UTF-8");
      ps.println(text);
      ps.flush();
      ps.close();
      fos.flush();
      fos.close();

      Toast.makeText(DecodeBook.this, "Wrote to: " + fileName, Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      Toast.makeText(DecodeBook.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  /**
   * Turns a display name into a safe export base name: - strips a known extension (.apk/.txt/.html)
   * - replaces path-hostile characters with '_' - falls back to "file" when the result is empty
   */
  private String buildExportBaseName(String displayName) {
    String base = (displayName == null) ? "" : displayName.trim();
    String lower = base.toLowerCase(Locale.US);
    if (lower.endsWith(".apk")) {
      base = base.substring(0, base.length() - 4);
    } else if (lower.endsWith(".html")) {
      base = base.substring(0, base.length() - 5);
    } else if (lower.endsWith(".htm")) {
      base = base.substring(0, base.length() - 4);
    } else if (lower.endsWith(".txt")) {
      base = base.substring(0, base.length() - 4);
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < base.length(); i++) {
      char c = base.charAt(i);
      if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<'
          || c == '>' || c == '|' || c < 0x20) {
        sb.append('_');
      } else {
        sb.append(c);
      }
    }
    String cleaned = sb.toString().trim();
    return cleaned.isEmpty() ? "file" : cleaned;
  }

  // ---------------------------------------------------------------------
  // DECODE
  // ---------------------------------------------------------------------

  private void executeDecode() {
    if (selectedApkUri == null) {
      Toast.makeText(DecodeBook.this, "Select a file first.", Toast.LENGTH_SHORT).show();
      return;
    }

    if (inputKind == 2 || inputKind == 3) {
      decodeTextPreview();
      return;
    }

    decodeRange(DexPageReader.DEFAULT_START, DexPageReader.DEFAULT_END);
  }

  /**
   * Decodes the first 9 pages (4.5 KB) of the cached TXT/HTML content as a single block. The last
   * word at the cut point is completed so the preview never ends mid-word.
   */
  private void decodeTextPreview() {
    final String content = cachedTextContent;
    if (content == null) {
      Toast.makeText(this, "Text content not loaded.", Toast.LENGTH_SHORT).show();
      return;
    }

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            final String slice = sliceByBytes(content, 0, DECODE_BYTES, true);
            final Spanned rendered = renderForEditor(slice);
            mainHandler.post(
                new Runnable() {
                  @Override
                  public void run() {
                    setEditorContent(rendered);
                    lastStart = 1;
                    lastEnd = 9;
                    lastBeautified = false;
                    lastCleaned = false;
                    lastSearchFrom = 0;
                    lastMatchStart = 0;
                    editor.setEditorLineSpacing(lineSpacing);
                    applyAlignment();
                  }
                });
          }
        });
  }

  // ---------------------------------------------------------------------
  // PAGES
  // ---------------------------------------------------------------------

  private void executePages() {
    if (selectedApkUri == null) {
      Toast.makeText(DecodeBook.this, "Select a file first.", Toast.LENGTH_SHORT).show();
      return;
    }

    if (inputKind == 2 || inputKind == 3) {
      final String content = cachedTextContent;
      if (content == null) {
        Toast.makeText(this, "Text content not loaded.", Toast.LENGTH_SHORT).show();
        return;
      }
      fileIOExecutor.execute(
          new Runnable() {
            @Override
            public void run() {
              final int byteLen = content.getBytes(Charset.forName("UTF-8")).length;
              final int count = (byteLen + PAGE_BYTES - 1) / PAGE_BYTES;
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      if (count <= 0) {
                        Toast.makeText(DecodeBook.this, "File is empty.", Toast.LENGTH_LONG).show();
                        return;
                      }
                      totalPages = count;
                      promptTextPageRange(count);
                    }
                  });
            }
          });
      return;
    }

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              InputStream is = getContentResolver().openInputStream(selectedApkUri);
              final int count = DexPageReader.countPages(is);
              is.close();
              totalPages = count;
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      if (count <= 0) {
                        Toast.makeText(
                                DecodeBook.this, "No Sayfa_XXXXX classes found.", Toast.LENGTH_LONG)
                            .show();
                        return;
                      }
                      promptPageRange(count);
                    }
                  });
            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      editor.setEditorText("Pages failed: " + e.getMessage());
                    }
                  });
            }
          }
        });
  }

  /**
   * Prompts for a page range on a TXT/HTML file. The upper bound is INCLUSIVE: "400-506" shows
   * pages 400..506 (107 pages). A range whose page count (end - start + 1) is >= MAX_PAGE_RANGE is
   * rejected with a warning and no action is taken.
   */
  private void promptTextPageRange(final int count) {
    String hint =
        (lastPagesRange != null && !lastPagesRange.isEmpty())
            ? lastPagesRange
            : ("1-" + Math.min(9, count));

    optionPane.showInputDialog(
        "There are "
            + count
            + " pages in this file.\n"
            + "Enter range (e.g. 20-40, max "
            + MAX_PAGE_RANGE
            + " pages):",
        hint,
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            if (input == null || input.trim().isEmpty()) return;
            String s = input.trim();
            int dash = s.indexOf('-');
            int start = 1, end = 1;
            if (dash < 0) {
              try {
                start = Integer.parseInt(s);
              } catch (NumberFormatException nfe) {
                Toast.makeText(DecodeBook.this, "Invalid numbers.", Toast.LENGTH_SHORT).show();
                return;
              }
              end = start;
            } else {
              try {
                start = Integer.parseInt(s.substring(0, dash).trim());
                end = Integer.parseInt(s.substring(dash + 1).trim());
              } catch (NumberFormatException nfe) {
                Toast.makeText(DecodeBook.this, "Invalid numbers.", Toast.LENGTH_SHORT).show();
                return;
              }
            }
            if (start < 1) start = 1;
            if (end < start) {
              Toast.makeText(DecodeBook.this, "End must be >= start.", Toast.LENGTH_SHORT).show();
              return;
            }
            // Inclusive page count.
            int pageCount = end - start + 1;
            if (pageCount >= MAX_PAGE_RANGE) {
              Toast.makeText(
                      DecodeBook.this,
                      "Range too large. Max " + MAX_PAGE_RANGE + " pages.",
                      Toast.LENGTH_LONG)
                  .show();
              return;
            }
            if (start > count) {
              Toast.makeText(DecodeBook.this, "Start page out of range.", Toast.LENGTH_SHORT)
                  .show();
              return;
            }
            if (end > count) end = count;
            lastPagesRange = s;
            decodeTextRange(start, end);
          }
        });
  }

  /**
   * Decodes a TXT/HTML page range [start, end] (INCLUSIVE) as a single block. Page N starts at byte
   * (N-1)*512 and ends at byte N*512 - 1. The last word at the end cut is completed, and the start
   * of the block is aligned to a word boundary when possible.
   */
  private void decodeTextRange(final int start, final int end) {
    final String content = cachedTextContent;
    if (content == null) return;

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            int fromByte = (start - 1) * PAGE_BYTES;
            // Inclusive: page "end" ends at end*PAGE_BYTES.
            int toByte = end * PAGE_BYTES;
            final String slice = sliceByBytes(content, fromByte, toByte, true);
            final Spanned rendered = renderForEditor(slice);
            mainHandler.post(
                new Runnable() {
                  @Override
                  public void run() {
                    setEditorContent(rendered);
                    lastStart = start;
                    lastEnd = end;
                    lastBeautified = false;
                    lastCleaned = false;
                    lastSearchFrom = 0;
                    lastMatchStart = 0;
                    editor.setEditorLineSpacing(lineSpacing);
                    applyAlignment();
                  }
                });
          }
        });
  }

  /**
   * Slices the UTF-8 content by byte offsets [fromByte, toByte). If completeLastWord is true, the
   * slice is extended forward to finish the word that straddles the end cut. The start cut is
   * aligned forward to the next word boundary when possible.
   */
  private String sliceByBytes(String content, int fromByte, int toByte, boolean completeLastWord) {
    if (content == null) return "";
    byte[] bytes = content.getBytes(Charset.forName("UTF-8"));
    if (fromByte < 0) fromByte = 0;
    if (toByte > bytes.length) toByte = bytes.length;
    if (fromByte > toByte) fromByte = toByte;

    // Align start forward to the next word boundary (skip partial leading word).
    if (fromByte > 0 && fromByte < bytes.length) {
      int probe = fromByte;
      while (probe < bytes.length && !isWhitespaceByte(bytes[probe])) probe++;
      if (probe - fromByte < 64) fromByte = probe;
    }

    int endByte = toByte;
    if (completeLastWord) {
      while (endByte < bytes.length && !isWhitespaceByte(bytes[endByte])) endByte++;
    }

    String raw = new String(bytes, fromByte, endByte - fromByte, Charset.forName("UTF-8"));
    return stripBrokenTail(raw);
  }

  private boolean isWhitespaceByte(byte b) {
    return b == ' ' || b == '\t' || b == '\n' || b == '\r';
  }

  /** Removes a trailing replacement character that may appear after a mid-UTF8 cut. */
  private String stripBrokenTail(String s) {
    if (s == null || s.length() == 0) return s == null ? "" : s;
    int end = s.length();
    while (end > 0 && s.charAt(end - 1) == '\uFFFD') end--;
    return s.substring(0, end);
  }

  /**
   * Renders the raw slice for the editor. HTML input is converted with Html.fromHtml so the
   * supported tags (<b>, <i>, <u>, <font color>,
   *
   * <p>) are honoured by the TextView.
   *
   * <p>Two issues are handled here:
   *
   * <p>1. Html.fromHtml does not resolve named colors like "red" or "blue" reliably; only hex
   * values work. Before parsing, every font color="name" is rewritten to its hex equivalent.
   *
   * <p>2. The Spanned result must be passed to the editor as a Spanned, not converted to a plain
   * String, otherwise every span (bold, italic, underline, color) is lost. The caller uses
   * setEditorContent(Spanned) to preserve them.
   */
  @SuppressWarnings("deprecation")
  private Spanned renderForEditor(String raw) {
    if (raw == null) return Html.fromHtml("");
    if (inputKind == 3) {
      String normalized = normalizeHtmlColors(raw);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return Html.fromHtml(normalized, Html.FROM_HTML_MODE_LEGACY);
      }
      return Html.fromHtml(normalized);
    }
    // TXT and APK content: plain text, no spans.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      return Html.fromHtml(escapeHtml(raw), Html.FROM_HTML_MODE_LEGACY);
    }
    return Html.fromHtml(escapeHtml(raw));
  }

  /**
   * Rewrites font color="name" / color='name' into the corresponding hex value so Html.fromHtml can
   * apply it. Covers the common HTML color names. Unknown names are left untouched.
   */
  private String normalizeHtmlColors(String html) {
    if (html == null || html.length() == 0) return "";
    String result = html;
    // Double-quoted attributes.
    result = replaceColorName(result, "\"red\"", "\"#FF0000\"");
    result = replaceColorName(result, "\"blue\"", "\"#0000FF\"");
    result = replaceColorName(result, "\"green\"", "\"#008000\"");
    result = replaceColorName(result, "\"black\"", "\"#000000\"");
    result = replaceColorName(result, "\"white\"", "\"#FFFFFF\"");
    result = replaceColorName(result, "\"yellow\"", "\"#FFFF00\"");
    result = replaceColorName(result, "\"orange\"", "\"#FFA500\"");
    result = replaceColorName(result, "\"purple\"", "\"#800080\"");
    result = replaceColorName(result, "\"gray\"", "\"#808080\"");
    result = replaceColorName(result, "\"grey\"", "\"#808080\"");
    result = replaceColorName(result, "\"cyan\"", "\"#00FFFF\"");
    result = replaceColorName(result, "\"magenta\"", "\"#FF00FF\"");
    result = replaceColorName(result, "\"brown\"", "\"#A52A2A\"");
    result = replaceColorName(result, "\"pink\"", "\"#FFC0CB\"");
    result = replaceColorName(result, "\"lime\"", "\"#00FF00\"");
    result = replaceColorName(result, "\"navy\"", "\"#000080\"");
    result = replaceColorName(result, "\"teal\"", "\"#008080\"");
    result = replaceColorName(result, "\"silver\"", "\"#C0C0C0\"");
    result = replaceColorName(result, "\"maroon\"", "\"#800000\"");
    result = replaceColorName(result, "\"olive\"", "\"#808000\"");
    // Single-quoted attributes.
    result = replaceColorName(result, "'red'", "'#FF0000'");
    result = replaceColorName(result, "'blue'", "'#0000FF'");
    result = replaceColorName(result, "'green'", "'#008000'");
    result = replaceColorName(result, "'black'", "'#000000'");
    result = replaceColorName(result, "'white'", "'#FFFFFF'");
    result = replaceColorName(result, "'yellow'", "'#FFFF00'");
    result = replaceColorName(result, "'orange'", "'#FFA500'");
    result = replaceColorName(result, "'purple'", "'#800080'");
    result = replaceColorName(result, "'gray'", "'#808080'");
    result = replaceColorName(result, "'grey'", "'#808080'");
    result = replaceColorName(result, "'cyan'", "'#00FFFF'");
    result = replaceColorName(result, "'magenta'", "'#FF00FF'");
    return result;
  }

  private String replaceColorName(String html, String from, String to) {
    return html.replace("color=" + from, "color=" + to)
        .replace("color=" + from.toUpperCase(Locale.US), "color=" + to);
  }

  /** Escapes the minimal HTML characters so plain text renders literally through Html.fromHtml. */
  private String escapeHtml(String raw) {
    if (raw == null) return "";
    return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  /**
   * Sends a Spanned to the editor while preserving its spans. MEditor extends TextView, and
   * TextView.setText(CharSequence, BufferType.SPANNABLE) is public, so we call it directly to keep
   * the spans. MEditor.setEditorText(String) would coerce the Spanned to a String and drop every
   * span (bold, italic, underline, color).
   */
  private void setEditorContent(Spanned content) {
    if (editor == null) return;
    editor.setText(content, TextView.BufferType.SPANNABLE);
  }

  // ---------------------------------------------------------------------
  // BEAUTIFY / CLEAN PAGE NUMBERS
  // ---------------------------------------------------------------------

  private void executeBeautify() {
    if (editor == null) return;
    final String content = editor.getFullText();
    if (content == null || content.length() < 1) {
      Toast.makeText(this, "Text is empty!", Toast.LENGTH_SHORT).show();
      return;
    }
    final int savedCaret = Math.max(0, editor.getSelectionStart());
    final int savedScroll = Math.max(0, editor.getEditorScrollY());

    V2Utils.setBorrowNums(false);
    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              final String processedText = V2UTFToISO.cnt(content);
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      if (processedText != null) {
                        editor.setEditorText(processedText);
                        lastBeautified = true;
                        restoreCaretAndScroll(savedCaret, savedScroll);
                        Toast.makeText(
                                getApplicationContext(), "Text Beautified!", Toast.LENGTH_SHORT)
                            .show();
                      } else {
                        Toast.makeText(
                                getApplicationContext(),
                                "Beautify returned null.",
                                Toast.LENGTH_SHORT)
                            .show();
                      }
                    }
                  });
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
                    }
                  });
            }
          }
        });
  }

  private void executeCleanPageNumbers() {
    if (editor == null) return;
    final String content = editor.getFullText();
    if (content == null || content.length() < 1) {
      Toast.makeText(this, "Text is empty!", Toast.LENGTH_SHORT).show();
      return;
    }
    final int savedCaret = Math.max(0, editor.getSelectionStart());
    final int savedScroll = Math.max(0, editor.getEditorScrollY());

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              V2Utils.setBorrowNums(false);
              final String stripped = content.replaceAll(V2UTFToISO.SFREG, "");
              String processedText = V2UTFToISO.cnt(stripped);
              if (processedText == null) processedText = stripped;
              final String finalText = processedText;
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      editor.setEditorText(finalText);
                      lastCleaned = true;
                      restoreCaretAndScroll(savedCaret, savedScroll);
                      Toast.makeText(
                              getApplicationContext(), "Page numbers cleaned!", Toast.LENGTH_SHORT)
                          .show();
                    }
                  });
            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Page Number Clean operation failed",
                              Toast.LENGTH_SHORT)
                          .show();
                    }
                  });
            }
          }
        });
  }

  private void restoreCaretAndScroll(final int savedCaret, final int savedScroll) {
    if (editor == null) return;
    editor.requestCaretAndScrollRestore(savedCaret, savedScroll);
  }

  // ---------------------------------------------------------------------
  // FORMAT
  // ---------------------------------------------------------------------

  private void executeFormatFg() {
    colorPicker.showColorPickerDialog(
        new MColorPicker.OnColorSelectedListener() {
          @Override
          public void onColorSelected(int color) {
            fgColor = color;
            editor.setTextColor(fgColor);
            Toast.makeText(DecodeBook.this, "FG updated.", Toast.LENGTH_SHORT).show();
          }
        });
  }

  private void executeFormatBg() {
    colorPicker.showColorPickerDialog(
        new MColorPicker.OnColorSelectedListener() {
          @Override
          public void onColorSelected(int color) {
            bgColor = color;
            editor.setBackgroundColor(bgColor);
            Toast.makeText(DecodeBook.this, "BG updated.", Toast.LENGTH_SHORT).show();
          }
        });
  }

  private void executeFormatFontStyle() {
    final String[] options = new String[] {"Plain", "Bold", "Italic", "Bold Italic"};
    optionPane.showSingleChoiceDialog(
        "Font Style",
        options,
        new MOptionPane.OnChoiceListener() {
          @Override
          public void onResult(int index) {
            if (index < 0) return;
            fontStyle = index;
            applyFontStyle();
            Toast.makeText(DecodeBook.this, "Style: " + options[index], Toast.LENGTH_SHORT).show();
          }
        });
  }

  private void applyFontStyle() {
    Typeface tf;
    switch (fontStyle) {
      case 1:
        tf = Typeface.DEFAULT_BOLD;
        break;
      case 2:
        tf = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC);
        break;
      case 3:
        tf = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC);
        break;
      default:
        tf = Typeface.DEFAULT;
        break;
    }
    editor.setTypeface(tf);
  }

  private void executeFormatFontSize() {
    String hint = (fontSize > 0f) ? String.valueOf(fontSize) : "24.0";
    optionPane.showInputDialog(
        "Enter font size (sp), e.g. 24.0",
        hint,
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            if (input == null || input.trim().isEmpty()) return;
            try {
              float s = Float.parseFloat(input.trim());
              if (s <= 0f) {
                Toast.makeText(DecodeBook.this, "Size must be > 0.", Toast.LENGTH_SHORT).show();
                return;
              }
              fontSize = s;
              editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize);
              Toast.makeText(DecodeBook.this, "Size: " + s, Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException nfe) {
              Toast.makeText(DecodeBook.this, "Invalid number.", Toast.LENGTH_SHORT).show();
            }
          }
        });
  }

  private void executeFormatLineSpacing() {
    String hint = (lineSpacing > 0f) ? String.valueOf(lineSpacing) : "1.0";
    optionPane.showInputDialog(
        "Line spacing multiplier (1.0 = normal, 1.2 = wider, 0.8 = tighter):",
        hint,
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            if (input == null || input.trim().isEmpty()) return;
            try {
              float m = Float.parseFloat(input.trim());
              if (m <= 0f) {
                Toast.makeText(DecodeBook.this, "Multiplier must be > 0.", Toast.LENGTH_SHORT)
                    .show();
                return;
              }
              lineSpacing = m;
              editor.setEditorLineSpacing(lineSpacing);
              Toast.makeText(DecodeBook.this, "Line spacing: " + m, Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException nfe) {
              Toast.makeText(DecodeBook.this, "Invalid number.", Toast.LENGTH_SHORT).show();
            }
          }
        });
  }

  private void executeFormatAlignLeft() {
    if (editor == null) return;
    alignment = 0;
    editor.setJustificationMode(Layout.JUSTIFICATION_MODE_NONE);
    editor.setEditorAlignment(Gravity.LEFT);
    Toast.makeText(this, "Align: Left", Toast.LENGTH_SHORT).show();
  }

  private void executeFormatAlignCenter() {
    if (editor == null) return;
    alignment = 1;
    editor.setJustificationMode(Layout.JUSTIFICATION_MODE_NONE);
    editor.setEditorAlignment(Gravity.CENTER_HORIZONTAL);
    Toast.makeText(this, "Align: Center", Toast.LENGTH_SHORT).show();
  }

  private void executeFormatAlignRight() {
    if (editor == null) return;
    alignment = 2;
    editor.setJustificationMode(Layout.JUSTIFICATION_MODE_NONE);
    editor.setEditorAlignment(Gravity.RIGHT);
    Toast.makeText(this, "Align: Right", Toast.LENGTH_SHORT).show();
  }

  private void executeFormatAlignJustify() {
    if (editor == null) return;
    alignment = 3;
    editor.setEditorAlignment(Gravity.LEFT);
    editor.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD);
    Toast.makeText(this, "Align: Justify", Toast.LENGTH_SHORT).show();
  }

  private void executeFormatReset() {
    fgColor = 0xFF000000;
    bgColor = 0xFFFFFFFF;
    fontStyle = 0;
    fontSize = 24.0f;
    lineSpacing = 1.0f;
    alignment = 0;
    editor.setTextColor(fgColor);
    editor.setBackgroundColor(bgColor);
    editor.setTypeface(Typeface.DEFAULT);
    editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize);
    editor.setEditorLineSpacing(lineSpacing);
    try {
      editor.setJustificationMode(Layout.JUSTIFICATION_MODE_NONE);
    } catch (Exception ignored) {
    }
    editor.setEditorAlignment(Gravity.LEFT);
    Toast.makeText(DecodeBook.this, "Format reset.", Toast.LENGTH_SHORT).show();
  }

  // ---------------------------------------------------------------------
  // ORIENTATION
  // ---------------------------------------------------------------------

  private void executeOrientationPortrait() {
    try {
      orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
      setRequestedOrientation(orientation);
      Toast.makeText(this, "Orientation: Portrait", Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      Toast.makeText(this, "Orientation change failed.", Toast.LENGTH_SHORT).show();
    }
  }

  private void executeOrientationLandscape() {
    try {
      orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
      setRequestedOrientation(orientation);
      Toast.makeText(this, "Orientation: Landscape", Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      Toast.makeText(this, "Orientation change failed.", Toast.LENGTH_SHORT).show();
    }
  }

  private void enterLandscapeFullscreen() {
    landscapeFullscreen = true;
    try {
      if (getActionBar() != null) getActionBar().hide();
    } catch (Exception ignored) {
    }
    if (Build.VERSION.SDK_INT >= 30) {
      try {
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
          controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
          controller.setSystemBarsBehavior(
              WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
      } catch (Exception ignored) {
      }
    }
  }

  private void exitLandscapeFullscreen() {
    landscapeFullscreen = false;
    try {
      if (getActionBar() != null) getActionBar().show();
    } catch (Exception ignored) {
    }
    if (Build.VERSION.SDK_INT >= 30) {
      try {
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
          controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        }
      } catch (Exception ignored) {
      }
    }
  }

  // ---------------------------------------------------------------------
  // CHANGE SEARCH TERM
  // ---------------------------------------------------------------------

  private void executeChangeSearchTerm() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(32, 16, 32, 16);

    final android.widget.EditText inSrc = new android.widget.EditText(this);
    inSrc.setHint("srcRegex (regex allowed)");
    inSrc.setText(
        srcRegex != null && !srcRegex.isEmpty()
            ? srcRegex
            : (searchTerm != null ? searchTerm : ""));

    final android.widget.EditText inDst = new android.widget.EditText(this);
    inDst.setHint("dstRegex (replacement)");
    inDst.setText(dstRegex != null ? dstRegex : "");

    final android.widget.EditText inMode = new android.widget.EditText(this);
    inMode.setHint("mode: none | upper | lower | firstUpper | sentence");
    inMode.setText(mode != null ? mode : "none");

    layout.addView(inSrc);
    layout.addView(inDst);
    layout.addView(inMode);

    new android.app.AlertDialog.Builder(this)
        .setTitle("Search / Replace Term")
        .setView(layout)
        .setPositiveButton(
            "OK",
            new android.content.DialogInterface.OnClickListener() {
              @Override
              public void onClick(android.content.DialogInterface dialog, int which) {
                srcRegex = inSrc.getText().toString();
                dstRegex = inDst.getText().toString();
                String rawMode = inMode.getText().toString();
                mode = normalizeMode(rawMode);
                searchTerm = srcRegex;
                searchIsRegex = true;
                try {
                  Pattern.compile(searchTerm);
                } catch (PatternSyntaxException pse) {
                  searchIsRegex = false;
                }
                lastSearchFrom = 0;
                lastMatchStart = 0;
                updateSearchStatus(searchTerm.isEmpty() ? "Find..." : "Search: " + searchTerm);
                Toast.makeText(DecodeBook.this, "Term set (mode=" + mode + ").", Toast.LENGTH_SHORT)
                    .show();
              }
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private String normalizeMode(String raw) {
    if (raw == null) return "none";
    String[] parts = raw.split("\\|");
    for (int i = 0; i < parts.length; i++) {
      String t = parts[i].trim();
      if (t.isEmpty()) continue;
      String lower = t.toLowerCase(Locale.US);
      if (lower.equals("none")
          || lower.equals("upper")
          || lower.equals("lower")
          || lower.equals("firstupper")
          || lower.equals("sentence")) {
        return lower;
      }
    }
    return "none";
  }

  // ---------------------------------------------------------------------
  // REPLACE — mode applies ONLY to the replacement, not the whole text
  // ---------------------------------------------------------------------

  /**
   * Runs replaceAll(srcRegex -> dstRegex) over the whole editor text, then applies the case magic
   * transformation indicated by mode ONLY to the replacement text produced by dstRegex. The
   * surrounding text is left untouched.
   */
  private void executeReplace() {
    if (editor == null) return;
    final String content = editor.getFullText();
    if (content == null || content.length() < 1) {
      Toast.makeText(this, "Text is empty!", Toast.LENGTH_SHORT).show();
      return;
    }
    if (srcRegex == null || srcRegex.isEmpty()) {
      Toast.makeText(this, "Set srcRegex first (Change Search Term).", Toast.LENGTH_SHORT).show();
      return;
    }

    final int savedCaret = Math.max(0, editor.getSelectionStart());
    final int savedScroll = Math.max(0, editor.getEditorScrollY());

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            final String result;
            try {
              Pattern p = Pattern.compile(srcRegex, Pattern.MULTILINE | Pattern.UNICODE_CASE);
              Matcher m = p.matcher(content);

              // Pre-apply the case mode to dstRegex once. Because dstRegex is a
              // replacement template (may contain $1, $2 backreferences), we
              // cannot simply uppercase it blindly. Instead, we expand each
              // match individually and apply the case mode to the expanded
              // replacement, leaving the rest of the text untouched.
              StringBuffer sb = new StringBuffer(content.length());
              while (m.find()) {
                String expanded = expandReplacement(dstRegex, m);
                String applied = applyCaseMode(expanded, mode);
                m.appendReplacement(sb, Matcher.quoteReplacement(applied));
              }
              m.appendTail(sb);
              result = sb.toString();
            } catch (final PatternSyntaxException pse) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Invalid regex: " + pse.getDescription(),
                              Toast.LENGTH_LONG)
                          .show();
                    }
                  });
              return;
            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(getApplicationContext(), "Replace failed.", Toast.LENGTH_SHORT)
                          .show();
                    }
                  });
              return;
            }

            mainHandler.post(
                new Runnable() {
                  @Override
                  public void run() {
                    editor.setEditorText(result);
                    restoreCaretAndScroll(savedCaret, savedScroll);
                    Toast.makeText(getApplicationContext(), "Replace done.", Toast.LENGTH_SHORT)
                        .show();
                  }
                });
          }
        });
  }

  /**
   * Expands a replacement template against the current match. Supports $0..$9 group references, the
   * escaped forms \\ and \$, and the common control escapes \n, \t and \r. Any other backslash
   * sequence is kept literally.
   */
  private String expandReplacement(String template, Matcher m) {
    if (template == null) return "";
    StringBuilder out = new StringBuilder(template.length());
    for (int i = 0; i < template.length(); i++) {
      char c = template.charAt(i);
      if (c == '\\' && i + 1 < template.length()) {
        char next = template.charAt(i + 1);
        if (next == '\\') {
          out.append('\\');
          i++;
        } else if (next == '$') {
          out.append('$');
          i++;
        } else if (next == 'n') {
          out.append('\n');
          i++;
        } else if (next == 't') {
          out.append('\t');
          i++;
        } else if (next == 'r') {
          out.append('\r');
          i++;
        } else {
          out.append(c);
        }
      } else if (c == '$' && i + 1 < template.length()) {
        char next = template.charAt(i + 1);
        if (next >= '0' && next <= '9') {
          int idx = next - '0';
          try {
            String group = m.group(idx);
            out.append(group != null ? group : "");
          } catch (Exception ignored) {
          }
          i++;
        } else {
          out.append(c);
        }
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  /**
   * Applies the case magic transformation. Supported modes: none, upper, lower, firstUpper (every
   * word's first letter uppercased), sentence (first letter after . ! ? … is uppercased).
   */
  private String applyCaseMode(String text, String mode) {
    if (text == null || text.length() == 0) return "";
    if (mode == null) return text;
    String m = mode.trim().toLowerCase(Locale.US);
    if (m.equals("upper")) return text.toUpperCase(Locale.getDefault());
    if (m.equals("lower")) return text.toLowerCase(Locale.getDefault());
    if (m.equals("firstupper")) return firstUpperEveryWord(text);
    if (m.equals("sentence")) return sentenceCase(text);
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

  // ---------------------------------------------------------------------
  // FIND
  // ---------------------------------------------------------------------

  private void executeFind() {
    if (editor == null) return;
    if (searchTerm == null || searchTerm.isEmpty()) {
      Toast.makeText(this, "Set a search term first (Change Search Term).", Toast.LENGTH_LONG)
          .show();
      return;
    }
    String text = editor.getFullText();
    if (text == null || text.length() == 0) {
      Toast.makeText(this, "Text is empty.", Toast.LENGTH_SHORT).show();
      return;
    }
    int from = lastSearchFrom;
    if (from < 0) from = 0;
    if (from > text.length()) from = 0;

    int[] range = findNext(text, from);
    if (range == null) {
      from = 0;
      range = findNext(text, from);
      if (range == null) {
        updateSearchStatus("No match: " + searchTerm);
        Toast.makeText(this, "No match found.", Toast.LENGTH_SHORT).show();
        return;
      }
      Toast.makeText(this, "Wrapped to top.", Toast.LENGTH_SHORT).show();
    }

    final int matchStart = range[0];
    final int matchEnd = range[1];
    lastSearchFrom = matchEnd;
    lastMatchStart = matchStart;

    editor.setEditorHighlight(matchStart, matchEnd);
    updateSearchStatus("Found @ " + matchStart + ".." + matchEnd);

    final int fs = matchStart;
    editor.post(
        new Runnable() {
          @Override
          public void run() {
            scrollToOffset(fs);
          }
        });
  }

  private void executeFindPrevious() {
    if (editor == null) return;
    if (searchTerm == null || searchTerm.isEmpty()) {
      Toast.makeText(this, "Set a search term first (Change Search Term).", Toast.LENGTH_LONG)
          .show();
      return;
    }
    String text = editor.getFullText();
    if (text == null || text.length() == 0) {
      Toast.makeText(this, "Text is empty.", Toast.LENGTH_SHORT).show();
      return;
    }

    int currentStart = lastMatchStart;
    if (currentStart < 0) currentStart = 0;
    if (currentStart > text.length()) currentStart = text.length();

    int[] best = null;
    int[] lastOverall = null;
    int from = 0;

    if (searchIsRegex) {
      Pattern p;
      try {
        p = Pattern.compile(searchTerm);
      } catch (PatternSyntaxException pse) {
        Toast.makeText(this, "Regex error: " + pse.getDescription(), Toast.LENGTH_SHORT).show();
        return;
      }
      Matcher m = p.matcher(text);
      while (m.find(from)) {
        int[] r = new int[] {m.start(), m.end()};
        lastOverall = r;
        if (r[0] < currentStart) best = r;
        else break;
        from = m.end();
        if (m.end() == m.start()) {
          if (from < text.length()) from++;
          else break;
        }
      }
    } else {
      int idx = 0;
      while ((idx = text.indexOf(searchTerm, idx)) >= 0) {
        int[] r = new int[] {idx, idx + searchTerm.length()};
        lastOverall = r;
        if (r[0] < currentStart) best = r;
        else break;
        idx = r[1];
      }
    }

    if (best == null) {
      if (lastOverall == null) {
        updateSearchStatus("No match: " + searchTerm);
        Toast.makeText(this, "No match found.", Toast.LENGTH_SHORT).show();
        return;
      }
      best = lastOverall;
      Toast.makeText(this, "Wrapped to bottom.", Toast.LENGTH_SHORT).show();
    }

    final int matchStart = best[0];
    final int matchEnd = best[1];
    lastSearchFrom = matchEnd;
    lastMatchStart = matchStart;

    editor.setEditorHighlight(matchStart, matchEnd);
    updateSearchStatus("Found @ " + matchStart + ".." + matchEnd);

    final int fs = matchStart;
    editor.post(
        new Runnable() {
          @Override
          public void run() {
            scrollToOffset(fs);
          }
        });
  }

  private int[] findNext(String text, int from) {
    if (searchIsRegex) {
      try {
        Pattern p = Pattern.compile(searchTerm);
        Matcher m = p.matcher(text);
        if (m.find(from)) return new int[] {m.start(), m.end()};
      } catch (PatternSyntaxException pse) {
        return null;
      }
      return null;
    } else {
      int idx = text.indexOf(searchTerm, from);
      if (idx < 0) return null;
      return new int[] {idx, idx + searchTerm.length()};
    }
  }

  private void scrollToOffset(int offset) {
    if (editor == null) return;
    Layout layout = editor.getLayout();
    if (layout == null) return;
    int safe = offset;
    if (safe < 0) safe = 0;
    if (safe > editor.getFullText().length()) safe = editor.getFullText().length();
    int line = layout.getLineForOffset(safe);
    int lineTop = layout.getLineTop(line);
    int target = lineTop - (editor.getHeight() / 3);
    if (target < 0) target = 0;
    editor.setEditorScrollY(target);
  }

  private void hideKeyboardSafe() {
    try {
      android.view.inputmethod.InputMethodManager imm =
          (android.view.inputmethod.InputMethodManager)
              getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
      if (imm != null && editor != null && editor.getWindowToken() != null) {
        imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
      }
    } catch (Exception ignored) {
    }
  }

  // ---------------------------------------------------------------------
  // TOOL — common helper
  // ---------------------------------------------------------------------

  private interface TextTransform {
    String apply(String input);
  }

  /**
   * Applies a background text transformation to the whole editor content and pushes the result back
   * on the main thread. The transform runs on the shared file I/O executor.
   */
  private void applyTextTransform(final String busyLabel, final TextTransform transform) {
    if (editor == null) return;
    final String content = editor.getFullText();
    if (content == null || content.length() < 1) {
      Toast.makeText(this, "Text is empty!", Toast.LENGTH_SHORT).show();
      return;
    }
    final int savedCaret = Math.max(0, editor.getSelectionStart());
    final int savedScroll = Math.max(0, editor.getEditorScrollY());

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            final String result;
            try {
              result = transform.apply(content);
            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              getApplicationContext(),
                              "Tool failed: " + e.getMessage(),
                              Toast.LENGTH_LONG)
                          .show();
                    }
                  });
              return;
            }
            mainHandler.post(
                new Runnable() {
                  @Override
                  public void run() {
                    editor.setEditorText(result);
                    restoreCaretAndScroll(savedCaret, savedScroll);
                    if (busyLabel != null && !busyLabel.isEmpty()) {
                      Toast.makeText(getApplicationContext(), busyLabel, Toast.LENGTH_SHORT).show();
                    }
                  }
                });
          }
        });
  }

  // ---------------------------------------------------------------------
  // TOOL — Remove Odd Chars
  // ---------------------------------------------------------------------

  private void executeToolRemoveOddChars() {
    applyTextTransform(
        "Odd chars removed.",
        new TextTransform() {
          @Override
          public String apply(String input) {
            String text = input;
            if (text.startsWith("\uFEFF")) text = text.substring(1);

            StringBuilder sb = new StringBuilder(text.length());
            int len = text.length();
            for (int i = 0; i < len; i++) {
              char c = text.charAt(i);
              int n = (int) c;

              if (c == '\r') {
                if (i + 1 < len && text.charAt(i + 1) == '\n') continue;
                sb.append('\n');
                continue;
              }

              if (c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\uFEFF') {
                sb.append('\u2022');
                continue;
              }

              if (n < 32 && n != 9 && n != 10) continue;

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
            return sb.toString();
          }
        });
  }

  // ---------------------------------------------------------------------
  // TOOL — Clean Extra Spaces
  // ---------------------------------------------------------------------

  private void executeToolCleanExtraSpaces() {
    applyTextTransform(
        "Extra spaces cleaned.",
        new TextTransform() {
          @Override
          public String apply(String input) {
            StringBuilder sb = new StringBuilder(input.length());
            boolean inSpace = false;
            for (int i = 0; i < input.length(); i++) {
              char c = input.charAt(i);
              if (c == ' ' || c == '\t') {
                if (!inSpace) {
                  sb.append(' ');
                  inSpace = true;
                }
              } else {
                sb.append(c);
                inSpace = false;
              }
            }
            return sb.toString();
          }
        });
  }

  // ---------------------------------------------------------------------
  // TOOL — Trim Lines
  // ---------------------------------------------------------------------

  private void executeToolTrimLines() {
    applyTextTransform(
        "Lines trimmed.",
        new TextTransform() {
          @Override
          public String apply(String input) {
            StringBuilder sb = new StringBuilder(input.length());
            int start = 0;
            int len = input.length();
            boolean first = true;
            while (start <= len) {
              int nl = input.indexOf('\n', start);
              int end = (nl == -1) ? len : nl;
              String line = input.substring(start, end);
              int a = 0;
              int b = line.length();
              while (a < b && line.charAt(a) <= ' ') a++;
              while (b > a && line.charAt(b - 1) <= ' ') b--;
              if (!first) sb.append('\n');
              sb.append(line, a, b);
              first = false;
              if (nl == -1) break;
              start = nl + 1;
            }
            return sb.toString();
          }
        });
  }

  // ---------------------------------------------------------------------
  // TOOL — Clean Numbers of Pages
  // ---------------------------------------------------------------------

  private void executeToolCleanNumbersOfPages() {
    applyTextTransform(
        "Page numbers removed.",
        new TextTransform() {
          @Override
          public String apply(String input) {
            String[] lines = input.split("\n", -1);
            StringBuilder sb = new StringBuilder(input.length());
            boolean first = true;
            for (int i = 0; i < lines.length; i++) {
              String line = lines[i];
              String probe = line.trim().replace(" ", "").replace("\t", "");
              boolean onlyDigits = probe.length() > 0;
              for (int j = 0; j < probe.length() && onlyDigits; j++) {
                if (!Character.isDigit(probe.charAt(j))) onlyDigits = false;
              }
              if (onlyDigits) continue;
              if (!first) sb.append('\n');
              sb.append(line);
              first = false;
            }
            return sb.toString();
          }
        });
  }

  // ---------------------------------------------------------------------
  // TOOL — Remove Chars Bigger / Smaller than
  // ---------------------------------------------------------------------

  private void executeToolRemoveCharsBiggerThan() {
    optionPane.showInputDialog(
        "Remove chars bigger than (decimal or 0xHEX):",
        "0xFFFF",
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            final int threshold = parseCodePoint(input);
            if (threshold < 0) {
              Toast.makeText(DecodeBook.this, "Invalid number.", Toast.LENGTH_SHORT).show();
              return;
            }
            applyTextTransform(
                "Chars removed.",
                new TextTransform() {
                  @Override
                  public String apply(String s) {
                    StringBuilder sb = new StringBuilder(s.length());
                    for (int i = 0; i < s.length(); ) {
                      int cp = s.codePointAt(i);
                      if (cp <= threshold) sb.appendCodePoint(cp);
                      i += Character.charCount(cp);
                    }
                    return sb.toString();
                  }
                });
          }
        });
  }

  private void executeToolRemoveCharsSmallerThan() {
    optionPane.showInputDialog(
        "Remove chars smaller than (decimal or 0xHEX):",
        "0x20",
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            final int threshold = parseCodePoint(input);
            if (threshold < 0) {
              Toast.makeText(DecodeBook.this, "Invalid number.", Toast.LENGTH_SHORT).show();
              return;
            }
            applyTextTransform(
                "Chars removed.",
                new TextTransform() {
                  @Override
                  public String apply(String s) {
                    StringBuilder sb = new StringBuilder(s.length());
                    for (int i = 0; i < s.length(); ) {
                      int cp = s.codePointAt(i);
                      if (cp >= threshold) sb.appendCodePoint(cp);
                      i += Character.charCount(cp);
                    }
                    return sb.toString();
                  }
                });
          }
        });
  }

  private int parseCodePoint(String raw) {
    if (raw == null) return -1;
    String s = raw.trim();
    if (s.isEmpty()) return -1;
    try {
      if (s.toLowerCase(Locale.US).startsWith("0x")) {
        return Integer.parseInt(s.substring(2), 16);
      }
      return Integer.parseInt(s);
    } catch (NumberFormatException nfe) {
      return -1;
    }
  }

  // ---------------------------------------------------------------------
  // TOOL — Merge Files
  // ---------------------------------------------------------------------

  private void executeToolMergeFiles() {
    filePicker.setMimeTypes(new String[] {"text/plain"});
    filePicker.setMultiSelectionEnabled(true);
    filePicker.showOpenDialog(MFilePicker.MERGE_REQUEST_CODE);
  }

  private void performMergeFiles(final Intent data) {
    if (data == null) {
      Toast.makeText(this, "Merge cancelled.", Toast.LENGTH_SHORT).show();
      return;
    }

    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            final List<Uri> uris = new ArrayList<Uri>();
            final List<String> names = new ArrayList<String>();

            if (data.getClipData() != null) {
              int count = data.getClipData().getItemCount();
              for (int i = 0; i < count; i++) {
                Uri u = data.getClipData().getItemAt(i).getUri();
                if (u != null) {
                  uris.add(u);
                  names.add(getFileName(u));
                }
              }
            } else if (data.getData() != null) {
              Uri u = data.getData();
              uris.add(u);
              names.add(getFileName(u));
            }

            if (uris.isEmpty()) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(DecodeBook.this, "No files selected.", Toast.LENGTH_SHORT)
                          .show();
                    }
                  });
              return;
            }

            final List<Integer> order = new ArrayList<Integer>();
            for (int i = 0; i < uris.size(); i++) order.add(Integer.valueOf(i));
            Collections.sort(
                order,
                new Comparator<Integer>() {
                  @Override
                  public int compare(Integer a, Integer b) {
                    String na = names.get(a.intValue());
                    String nb = names.get(b.intValue());
                    if (na == null) na = "";
                    if (nb == null) nb = "";
                    return na.compareToIgnoreCase(nb);
                  }
                });

            String prefix = deriveCommonPrefix(names, order);

            String stamp =
                new java.text.SimpleDateFormat("ddMMyy_HHmmss", Locale.US)
                    .format(new java.util.Date());
            String fileName = "merged_" + prefix + "_" + stamp + ".txt";
            File outFile = new File(mainPath + "/carets/inspector/merges", fileName);

            OutputStream out = null;
            try {
              out = new FileOutputStream(outFile);
              byte[] buffer = new byte[8192];
              for (int k = 0; k < order.size(); k++) {
                int idx = order.get(k).intValue();
                Uri u = uris.get(idx);
                InputStream in = getContentResolver().openInputStream(u);
                if (in == null) continue;
                try {
                  int n;
                  while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                  }
                  out.write('\n');
                } finally {
                  try {
                    in.close();
                  } catch (Exception ignored) {
                  }
                }
              }
              out.flush();
            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Toast.makeText(
                              DecodeBook.this, "Merge failed: " + e.getMessage(), Toast.LENGTH_LONG)
                          .show();
                    }
                  });
              return;
            } finally {
              if (out != null) {
                try {
                  out.close();
                } catch (Exception ignored) {
                }
              }
            }

            mainHandler.post(
                new Runnable() {
                  @Override
                  public void run() {
                    Toast.makeText(
                            DecodeBook.this, "Merged into: merges/" + fileName, Toast.LENGTH_LONG)
                        .show();
                  }
                });
          }
        });
  }

  private String deriveCommonPrefix(List<String> names, List<Integer> order) {
    if (order.isEmpty()) return "files";
    String first = names.get(order.get(0).intValue());
    if (first == null) first = "files";
    String base = first;
    int dot = base.lastIndexOf('.');
    if (dot > 0) base = base.substring(0, dot);
    int us = base.indexOf('_');
    if (us > 0) base = base.substring(0, us);
    base = base.trim();
    if (base.isEmpty()) base = "files";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < base.length(); i++) {
      char c = base.charAt(i);
      if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<'
          || c == '>' || c == '|' || c < 0x20) {
        sb.append('_');
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  // ---------------------------------------------------------------------
  // TOOL — Info
  // ---------------------------------------------------------------------

  private void executeToolInfo() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Name: ").append(selectedApkName == null ? "" : selectedApkName).append("\n");
    sb.append("Uri: ").append(selectedApkUri == null ? "" : selectedApkUri.toString()).append("\n");
    sb.append("Kind: ");
    switch (inputKind) {
      case 1:
        sb.append("APK");
        break;
      case 2:
        sb.append("TXT");
        break;
      case 3:
        sb.append("HTML");
        break;
      default:
        sb.append("none");
        break;
    }
    sb.append("\n");

    long byteSize = -1;
    if (selectedApkUri != null) {
      try {
        android.database.Cursor c =
            getContentResolver().query(selectedApkUri, null, null, null, null);
        if (c != null) {
          try {
            int idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
            if (idx != -1 && c.moveToFirst()) byteSize = c.getLong(idx);
          } finally {
            c.close();
          }
        }
      } catch (Exception ignored) {
      }
    }
    sb.append("Size: ").append(byteSize >= 0 ? (byteSize + " bytes") : "unknown").append("\n");

    if (inputKind == 2 || inputKind == 3) {
      if (cachedTextContent != null) {
        int bytes = cachedTextContent.getBytes(Charset.forName("UTF-8")).length;
        int pages = (bytes + PAGE_BYTES - 1) / PAGE_BYTES;
        sb.append("Bytes (UTF-8): ").append(bytes).append("\n");
        sb.append("Pages (0.5 KB): ").append(pages).append("\n");
      }
    } else if (totalPages > 0) {
      sb.append("Pages (DEX): ").append(totalPages).append("\n");
    }

    if (editor != null) {
      String t = editor.getFullText();
      sb.append("Editor chars: ").append(t == null ? 0 : t.length()).append("\n");
    }
    sb.append("Last range: ").append(lastStart).append("-").append(lastEnd);

    optionPane.showMessageDialog(sb.toString());
  }

  // ---------------------------------------------------------------------
  // HELP
  // ---------------------------------------------------------------------

  private void executeHelp() {
    StringBuilder sb = new StringBuilder();
    sb.append("\"Select\" — pick an APK, TXT or HTML file.\n\n");
    sb.append("\"Decode\" — APK: pages 1-9. TXT/HTML: first 4.5 KB (9 pages of 0.5 KB).\n\n");
    sb.append("\"Pages\" — APK: page range from Sayfa_XXXXX classes. TXT/HTML: 0.5 KB per page,\n");
    sb.append("inclusive range (\"400-506\" shows pages 400..506). Max ")
        .append(MAX_PAGE_RANGE)
        .append(" pages per range.\n\n");
    sb.append("\"Beautify\" — run the text beautify engine on the current content.\n\n");
    sb.append("\"Clean Page Numbers\" — run the legacy page-number cleaner.\n\n");
    sb.append("\"Format\" → FG / BG / Font Style / Font Size / Line Spacing / Align / Reset.\n\n");
    sb.append("\"Orientation\" → Portrait / Landscape. Landscape is fullscreen; double-tap to\n");
    sb.append("show or hide the top bar.\n\n");
    sb.append("\"Change Search Term\" — set srcRegex, dstRegex and mode in one dialog.\n");
    sb.append("mode: none | upper | lower | firstUpper | sentence (first valid token wins,\n");
    sb.append("invalid falls back to none).\n\n");
    sb.append("\"Find\" — search forward with the current srcRegex (regex or plain).\n\n");
    sb.append("\"Replace\" — regex replaceAll(srcRegex -> dstRegex) over the whole text.\n");
    sb.append("The case mode is applied ONLY to the replacement text, not to the\n");
    sb.append("surrounding content.\n\n");
    sb.append("\"Tool\" → Remove Odd Chars — strip control / zero-width / junk characters.\n\n");
    sb.append("\"Tool\" → Clean Extra Spaces — collapse runs of spaces / tabs.\n\n");
    sb.append("\"Tool\" → Trim Lines — trim leading / trailing whitespace on every line.\n\n");
    sb.append("\"Tool\" → Clean Numbers of Pages — drop lines that are only digits.\n\n");
    sb.append(
        "\"Tool\" → Remove Chars Bigger than — prompt for a code point, drop chars above it.\n\n");
    sb.append(
        "\"Tool\" → Remove Chars Smaller than — prompt for a code point, drop chars below it.\n\n");
    sb.append("\"Tool\" → Merge Files — pick multiple TXT files, merge alphabetically into\n");
    sb.append("carets/inspector/merges/merged_<prefix>_<ddMMyy_HHmmss>.txt.\n\n");
    sb.append(
        "\"Tool\" → Info — show file name, Uri, kind, size, pages and editor char count.\n\n");
    sb.append("\"Export\" — save current content to carets/inspector/<base>_00001-00055.txt.\n\n");
    sb.append("\"Help\" — show this help.\n\n");
    sb.append("\"Exit\" — save session state and close the app cleanly.\n\n");
    sb.append("Session state is stored in carets/inspector/inspectorInfo.txt and restored\n");
    sb.append("on next launch.");
    optionPane.showMessageDialog(sb.toString());
  }

  private void exit() {
    saveSession();
    finishAndRemoveTask();
  }

  // ---------------------------------------------------------------------
  // SESSION SAVE / RESTORE
  // ---------------------------------------------------------------------

  private void saveSession() {
    try {
      OutputStream fos = new FileOutputStream(INFOFILE);
      PrintStream ps = new PrintStream(fos, true, "UTF-8");
      String uriStr = (selectedApkUri != null) ? selectedApkUri.toString() : "";
      int scroll = (editor != null) ? editor.getEditorScrollY() : 0;
      ps.println("uri=" + uriStr);
      ps.println("kind=" + inputKind);
      ps.println("start=" + lastStart);
      ps.println("end=" + lastEnd);
      ps.println("scroll=" + scroll);
      ps.println("fg=" + fgColor);
      ps.println("bg=" + bgColor);
      ps.println("style=" + fontStyle);
      ps.println("size=" + fontSize);
      ps.println("linespacing=" + lineSpacing);
      ps.println("alignment=" + alignment);
      ps.println("orientation=" + orientation);
      ps.println("beautified=" + (lastBeautified ? 1 : 0));
      ps.println("cleaned_page_numbers=" + (lastCleaned ? 1 : 0));
      ps.println("srcRegex=" + safeLine(srcRegex));
      ps.println("dstRegex=" + safeLine(dstRegex));
      ps.println("mode=" + safeLine(mode));
      ps.flush();
      ps.close();
      fos.flush();
      fos.close();
    } catch (Exception e) {
      Toast.makeText(DecodeBook.this, "Session save error: " + e.getMessage(), Toast.LENGTH_SHORT)
          .show();
    }
  }

  private String safeLine(String s) {
    if (s == null) return "";
    return s.replace("\r", " ").replace("\n", " ");
  }

  private boolean restoreSession() {
    try {
      if (INFOFILE == null || !INFOFILE.exists()) return false;

      String uriStr = "";
      int kind = 0;
      int start = 0, end = 0, scroll = 0;
      int rFg = fgColor, rBg = bgColor, rStyle = fontStyle;
      float rSize = fontSize;
      float rLine = lineSpacing;
      int rAlign = alignment;
      int rOrient = orientation;
      boolean rBeautified = false;
      boolean rCleaned = false;
      String rSrc = "";
      String rDst = "";
      String rMode = "none";

      BufferedReader br = null;
      try {
        br = new BufferedReader(new InputStreamReader(new FileInputStream(INFOFILE), "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) {
          int eq = line.indexOf('=');
          if (eq < 0) continue;
          String key = line.substring(0, eq).trim();
          String val = line.substring(eq + 1);
          if (key.equals("uri")) uriStr = val.trim();
          else if (key.equals("kind")) kind = parseIntSafe(val.trim(), 0);
          else if (key.equals("start")) start = parseIntSafe(val.trim(), 0);
          else if (key.equals("end")) end = parseIntSafe(val.trim(), 0);
          else if (key.equals("scroll")) scroll = parseIntSafe(val.trim(), 0);
          else if (key.equals("fg")) rFg = parseIntSafe(val.trim(), rFg);
          else if (key.equals("bg")) rBg = parseIntSafe(val.trim(), rBg);
          else if (key.equals("style")) rStyle = parseIntSafe(val.trim(), rStyle);
          else if (key.equals("size")) rSize = parseFloatSafe(val.trim(), rSize);
          else if (key.equals("linespacing")) rLine = parseFloatSafe(val.trim(), rLine);
          else if (key.equals("alignment")) rAlign = parseIntSafe(val.trim(), rAlign);
          else if (key.equals("orientation")) rOrient = parseIntSafe(val.trim(), rOrient);
          else if (key.equals("beautified")) rBeautified = parseIntSafe(val.trim(), 0) != 0;
          else if (key.equals("cleaned_page_numbers")) rCleaned = parseIntSafe(val.trim(), 0) != 0;
          else if (key.equals("srcRegex")) rSrc = val;
          else if (key.equals("dstRegex")) rDst = val;
          else if (key.equals("mode")) rMode = val.trim();
        }
      } finally {
        if (br != null)
          try {
            br.close();
          } catch (IOException ignored) {
          }
      }

      if (uriStr.isEmpty()) return false;

      Uri uri;
      try {
        uri = Uri.parse(uriStr);
      } catch (Exception e) {
        return false;
      }

      try {
        fgColor = rFg;
        bgColor = rBg;
        fontStyle = rStyle;
        fontSize = rSize;
        lineSpacing = (rLine > 0f) ? rLine : 1.0f;
        alignment = (rAlign >= 0 && rAlign <= 3) ? rAlign : 0;
        orientation = rOrient;
        srcRegex = rSrc != null ? rSrc : "";
        dstRegex = rDst != null ? rDst : "";
        mode = normalizeMode(rMode);
        searchTerm = srcRegex;
        searchIsRegex = true;
        try {
          Pattern.compile(searchTerm);
        } catch (PatternSyntaxException pse) {
          searchIsRegex = false;
        }

        editor.setTextColor(fgColor);
        editor.setBackgroundColor(bgColor);
        applyFontStyle();
        if (fontSize > 0f) {
          editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize);
        }
        editor.setEditorLineSpacing(lineSpacing);
        applyAlignment();
      } catch (Exception ignored) {
      }

      try {
        if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
          setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
          setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
      } catch (Exception ignored) {
      }

      if (start >= 1 && end >= start) {
        lastPagesRange = start + "-" + end;
      }

      if (start < 1 || end < start) {
        try {
          editor.setEditorText("Session restored (format only).\nSelect a file to continue.");
        } catch (Exception ignored) {
        }
        return true;
      }

      selectedApkUri = uri;
      selectedApkName = getFileName(uri);
      if (selectedApkName == null || selectedApkName.trim().isEmpty()) {
        selectedApkName = uri.toString();
      }
      inputKind = (kind != 0) ? kind : detectInputKind(selectedApkName);

      if (inputKind == 2 || inputKind == 3) {
        final int fStart = start;
        final int fEnd = end;
        final int fScroll = scroll;
        final boolean fBeautify = rBeautified;
        final boolean fClean = rCleaned;
        fileIOExecutor.execute(
            new Runnable() {
              @Override
              public void run() {
                try {
                  InputStream is = getContentResolver().openInputStream(selectedApkUri);
                  cachedTextContent = readAllText(is);
                  if (is != null) is.close();
                } catch (Exception e) {
                  cachedTextContent = null;
                }
                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        if (cachedTextContent == null) {
                          editor.setEditorText("Session restored (text re-read failed).");
                          return;
                        }
                        // Inclusive page range: page fEnd ends at fEnd * PAGE_BYTES.
                        int fromByte = (fStart - 1) * PAGE_BYTES;
                        int toByte = fEnd * PAGE_BYTES;
                        String slice = sliceByBytes(cachedTextContent, fromByte, toByte, true);
                        Spanned rendered = renderForEditor(slice);
                        if (fBeautify) {
                          try {
                            V2Utils.setBorrowNums(false);
                            String b = V2UTFToISO.cnt(rendered.toString());
                            if (b != null) rendered = Html.fromHtml(escapeHtml(b), Html.FROM_HTML_MODE_LEGACY);
                          } catch (Exception ignored) {
                          }
                        }
                        if (fClean) {
                          try {
                            String stripped = rendered.toString().replaceAll(V2UTFToISO.SFREG, "");
                            String c = V2UTFToISO.cnt(stripped);
                            String use = (c != null) ? c : stripped;
                            rendered = Html.fromHtml(escapeHtml(use), Html.FROM_HTML_MODE_LEGACY);
                          } catch (Exception ignored) {
                          }
                        }
                        setEditorContent(rendered);
                        lastStart = fStart;
                        lastEnd = fEnd;
                        lastBeautified = fBeautify;
                        lastCleaned = fClean;
                        editor.setEditorLineSpacing(lineSpacing);
                        applyAlignment();
                        final int s = fScroll;
                        mainHandler.post(
                            new Runnable() {
                              @Override
                              public void run() {
                                try {
                                  editor.setEditorScrollY(s);
                                } catch (Exception ignored) {
                                }
                              }
                            });
                      }
                    });
              }
            });
      } else {
        decodeRangeForRestore(start, end, scroll, rBeautified, rCleaned);
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private void applyAlignment() {
    if (editor == null) return;
    try {
      switch (alignment) {
        case 1:
          editor.setJustificationMode(Layout.JUSTIFICATION_MODE_NONE);
          editor.setEditorAlignment(Gravity.CENTER_HORIZONTAL);
          break;
        case 2:
          editor.setJustificationMode(Layout.JUSTIFICATION_MODE_NONE);
          editor.setEditorAlignment(Gravity.RIGHT);
          break;
        case 3:
          editor.setEditorAlignment(Gravity.LEFT);
          editor.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD);
          break;
        case 0:
        default:
          editor.setJustificationMode(Layout.JUSTIFICATION_MODE_NONE);
          editor.setEditorAlignment(Gravity.LEFT);
          break;
      }
    } catch (Exception ignored) {
    }
  }

  private void decodeRangeForRestore(
      final int start,
      final int end,
      final int scrollY,
      final boolean beautify,
      final boolean pgncleaned) {
    try {
      fileIOExecutor.execute(
          new Runnable() {
            @Override
            public void run() {
              String output = null;
              try {
                InputStream is = getContentResolver().openInputStream(selectedApkUri);
                if (is == null) throw new IOException("Cannot open APK stream.");
                List<String> pages = DexPageReader.readPages(is, start, end);
                is.close();
                StringBuilder sb = new StringBuilder();
                for (String page : pages) sb.append(page).append("\n\n");
                output =
                    sb.length() > 0
                        ? sb.toString()
                        : "No pages found in range " + start + "-" + end + ".";
                if (beautify) {
                  try {
                    V2Utils.setBorrowNums(false);
                    String beautified = V2UTFToISO.cnt(output);
                    if (beautified != null) output = beautified;
                  } catch (Exception ignored) {
                  }
                }
                if (pgncleaned) {
                  try {
                    V2Utils.setBorrowNums(false);
                    String stripped = output.replaceAll(V2UTFToISO.SFREG, "");
                    String cleaned = V2UTFToISO.cnt(stripped);
                    output = (cleaned != null) ? cleaned : stripped;
                  } catch (Exception ignored) {
                  }
                }
              } catch (Exception e) {
                final String msg =
                    (e.getMessage() != null) ? e.getMessage() : e.getClass().getSimpleName();
                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        try {
                          editor.setEditorText(
                              "Restore skipped: "
                                  + msg
                                  + "\n\nSelect the APK again and press Decode.");
                        } catch (Exception ignored) {
                        }
                      }
                    });
                return;
              }
              final String finalOutput = output;
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      try {
                        editor.setEditorText(finalOutput);
                        lastStart = start;
                        lastEnd = end;
                        lastBeautified = beautify;
                        lastCleaned = pgncleaned;
                        editor.setEditorLineSpacing(lineSpacing);
                        applyAlignment();
                        mainHandler.post(
                            new Runnable() {
                              @Override
                              public void run() {
                                try {
                                  editor.setEditorScrollY(scrollY);
                                } catch (Exception ignored) {
                                }
                              }
                            });
                      } catch (Exception ignored) {
                      }
                    }
                  });
            }
          });
    } catch (Exception e) {
    }
  }

  private static int parseIntSafe(String s, int def) {
    try {
      return Integer.parseInt(s);
    } catch (Exception e) {
      return def;
    }
  }

  private static float parseFloatSafe(String s, float def) {
    try {
      return Float.parseFloat(s);
    } catch (Exception e) {
      return def;
    }
  }

  // ---------------------------------------------------------------------
  // PAGE RANGE PROMPT (APK only — inclusive, unchanged behaviour)
  // ---------------------------------------------------------------------

  private void promptPageRange(final int count) {
    String hint =
        (lastPagesRange != null && !lastPagesRange.isEmpty())
            ? lastPagesRange
            : ("1-" + Math.min(9, count));

    optionPane.showInputDialog(
        "There are "
            + count
            + " pages in this ebook APK.\n"
            + "Enter range (e.g. 20-40, max "
            + MAX_PAGE_RANGE
            + " pages):",
        hint,
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            if (input == null || input.trim().isEmpty()) return;
            String s = input.trim();
            int dash = s.indexOf('-');
            int start = 1, end = 1;
            if (dash < 0) {
              try {
                start = Integer.parseInt(s);
              } catch (NumberFormatException nfe) {
                Toast.makeText(DecodeBook.this, "Invalid numbers.", Toast.LENGTH_SHORT).show();
                return;
              }
              lastPagesRange = s;
              decodeRange(start, start);
              return;
            }
            try {
              start = Integer.parseInt(s.substring(0, dash).trim());
              end = Integer.parseInt(s.substring(dash + 1).trim());
            } catch (NumberFormatException nfe) {
              Toast.makeText(DecodeBook.this, "Invalid numbers.", Toast.LENGTH_SHORT).show();
              return;
            }
            if (start < 1) start = 1;
            if (end > count) end = count;
            if (end < start) {
              Toast.makeText(DecodeBook.this, "End must be >= start.", Toast.LENGTH_SHORT).show();
              return;
            }
            if (end - start + 1 >= MAX_PAGE_RANGE) {
              Toast.makeText(
                      DecodeBook.this,
                      "Range too large. Max " + MAX_PAGE_RANGE + " pages.",
                      Toast.LENGTH_LONG)
                  .show();
              return;
            }
            lastPagesRange = s;
            decodeRange(start, end);
          }
        });
  }

  private void decodeRange(final int start, final int end) {
    fileIOExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              InputStream is = getContentResolver().openInputStream(selectedApkUri);
              List<String> pages = DexPageReader.readPages(is, start, end);
              is.close();
              StringBuilder sb = new StringBuilder();
              for (String page : pages) sb.append(page).append("\n\n");
              final String output =
                  sb.length() > 0
                      ? sb.toString()
                      : "No pages found in range " + start + "-" + end + ".";
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      editor.setEditorText(output);
                      lastStart = start;
                      lastEnd = end;
                      lastBeautified = false;
                      lastCleaned = false;
                      lastSearchFrom = 0;
                      lastMatchStart = 0;
                      editor.setEditorLineSpacing(lineSpacing);
                      applyAlignment();
                    }
                  });
            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      editor.setEditorText("Decode failed: " + e.getMessage());
                    }
                  });
            }
          }
        });
  }

  // ---------------------------------------------------------------------
  // ACTIVITY RESULT
  // ---------------------------------------------------------------------

  @Override
  protected void onActivityResult(final int requestCode, int resultCode, Intent data) {
    if (requestCode == MFilePicker.MERGE_REQUEST_CODE) {
      if (resultCode == Activity.RESULT_OK) {
        performMergeFiles(data);
      }
      super.onActivityResult(requestCode, resultCode, data);
      return;
    }

    if (filePicker.handleResult(requestCode, resultCode, data)) {
      final Uri targetUri = filePicker.getSelectedUri();
      if (requestCode == MFilePicker.OPEN_REQUEST_CODE && targetUri != null) {
        final String name = getFileName(targetUri);
        final int kind = detectInputKind(name);
        if (kind == 0) {
          Toast.makeText(
                  DecodeBook.this,
                  "Unsupported file type. Pick .apk, .txt or .html.",
                  Toast.LENGTH_LONG)
              .show();
          super.onActivityResult(requestCode, resultCode, data);
          return;
        }

        selectedApkUri = targetUri;
        selectedApkName = (name != null && !name.trim().isEmpty()) ? name : targetUri.toString();
        inputKind = kind;
        cachedTextContent = null;
        lastStart = 0;
        lastEnd = 0;
        lastBeautified = false;
        lastCleaned = false;

        try {
          getContentResolver()
              .takePersistableUriPermission(targetUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }

        if (kind == 1) {
          Toast.makeText(DecodeBook.this, "Selected: " + selectedApkName, Toast.LENGTH_SHORT)
              .show();
          editor.setEditorText(
              "Selected: " + selectedApkName + "\n\nPress Decode to extract pages.");
        } else {
          fileIOExecutor.execute(
              new Runnable() {
                @Override
                public void run() {
                  try {
                    InputStream is = getContentResolver().openInputStream(targetUri);
                    final String content = readAllText(is);
                    if (is != null) is.close();
                    cachedTextContent = content;
                    mainHandler.post(
                        new Runnable() {
                          @Override
                          public void run() {
                            Toast.makeText(
                                    DecodeBook.this,
                                    "Selected: " + selectedApkName,
                                    Toast.LENGTH_SHORT)
                                .show();
                            editor.setEditorText(
                                "Selected: "
                                    + selectedApkName
                                    + "\n\n"
                                    + "Press Decode for the first 9 pages, or Pages for a range.");
                          }
                        });
                  } catch (final Exception e) {
                    mainHandler.post(
                        new Runnable() {
                          @Override
                          public void run() {
                            editor.setEditorText("Read failed: " + e.getMessage());
                          }
                        });
                  }
                }
              });
        }
      }
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  private String readAllText(InputStream is) throws IOException {
    if (is == null) return "";
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int n;
    while ((n = is.read(buffer)) != -1) {
      baos.write(buffer, 0, n);
    }
    return new String(baos.toByteArray(), Charset.forName("UTF-8"));
  }

  private final String getMainPathName() {
    File path = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
    String px = path.getAbsolutePath();
    String exclude = "Android/data/" + (getPackageName()) + "/files/";
    px = px.replace(exclude, "");
    return px;
  }

  private String getFileName(Uri uri) {
    String result = null;
    if (uri != null && uri.getScheme() != null && uri.getScheme().equals("content")) {
      android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
      try {
        if (cursor != null && cursor.moveToFirst()) {
          int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
          if (index != -1) result = cursor.getString(index);
        }
      } finally {
        if (cursor != null) cursor.close();
      }
    }
    if (result == null && uri != null) {
      result = uri.getPath();
      if (result != null) {
        int cut = result.lastIndexOf('/');
        if (cut != -1) result = result.substring(cut + 1);
      }
    }
    return result;
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    fileIOExecutor.shutdown();
  }
  
} // class end
