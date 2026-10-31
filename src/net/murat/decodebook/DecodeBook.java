package net.murat.decodebook;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.murat.uikit.*;
import net.murat.util.*;

/**
 * EBook Inspector — decompiles an APK and extracts the String[] arrays
 * found in the Sayfa_XXXXX classes, showing the first few pages on screen.
 * Intended for personal use only.
 *
 * @author Murat
 */
public class DecodeBook extends Activity {
  
  private MEditor editor;
  
  private MFilePicker filePicker;
  private MOptionPane optionPane;
  private MColorPicker colorPicker;
  
  // Find toolbar views (hidden by default, shown when Find is selected)
  private LinearLayout searchToolbar;
  private Button btnFindPrev;
  private Button btnFindNext;
  private Button btnFindClose;
  private TextView searchStatus;
  
  // URI and display name of the currently selected APK
  private Uri selectedApkUri = null;
  private String selectedApkName = "";
  
  // Last decoded range (persisted to inspectorInfo.txt)
  private int lastStart = 0;
  private int lastEnd = 0;
  
  // True if the current editor content was produced by the beautify engine.
  // Persisted so that a session restore can reproduce the exact same text.
  private boolean lastBeautified = false;
  private boolean lastCleaned = false;
  
  // Main working directory resolved by getMainPathName()
  private String mainPath = null;
  private File INFOFILE = null;
  
  // Format state
  private int fgColor = 0xFF000000;   // default black
  private int bgColor = 0xFFFFFFFF;   // default white
  private int fontStyle = 0;          // 0=Plain 1=Bold 2=Italic 3=Bold-Italic
  private float fontSize = 24.0f;     // default 24sp
  private float lineSpacing = 1.0f;   // default line spacing multiplier
  
  // Find state
  private String searchTerm = "";
  private boolean searchIsRegex = false;
  // Cursor used by Find / Find Previous. It always points at the character
  // position where the *next* forward search should start, and where the
  // next backward search should stop. Updated by both directions.
  private int lastSearchFrom = 0;
  // Start position of the most recently found match. Used by Find Previous
  // so a single "<" press jumps to the previous match immediately, instead
  // of re-selecting the current one first.
  private int lastMatchStart = 0;
  
  private int totalPages = 0;
  
  // Last page range the user entered during this session (in-memory only).
  // It seeds the Pages dialog hint; on the next launch it is derived from
  // inspectorInfo.txt's start/end fields.
  private String lastPagesRange = "";
  
  private Menu mMenu;
  
  // Single-threaded executor reused for file I/O and DEX processing
  private final ExecutorService fileIOExecutor = Executors.newSingleThreadExecutor();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    
    // Resolve the main working directory (kept from the previous project)
    mainPath = getMainPathName();
    
    // Ensure the carets directory exists
    File carets = new File(mainPath + "/carets");
    if (!carets.exists()) carets.mkdir();
    
    // Ensure the carets/inspector directory exists (holds session + exports)
    File inspector = new File(mainPath + "/carets/inspector");
    if (!inspector.exists()) inspector.mkdir();
    
    INFOFILE = new File(mainPath + "/carets/inspector/inspectorInfo.txt");
    
    editor = (MEditor) findViewById(R.id.editor);
    
    // Bind find toolbar views (hidden by default in the layout)
    searchToolbar = (LinearLayout) findViewById(R.id.search_toolbar);
    btnFindPrev   = (Button) findViewById(R.id.btn_prev);
    btnFindNext   = (Button) findViewById(R.id.btn_next);
    btnFindClose  = (Button) findViewById(R.id.btn_close_search);
    searchStatus  = (TextView) findViewById(R.id.search_status);
    
    // Initialize custom UI framework components
    filePicker  = new MFilePicker(this);
    filePicker.addMimeType("application/vnd.android.package-archive");
    optionPane  = new MOptionPane(this);
    colorPicker = new MColorPicker(this);
    
    // Wire up the toolbar buttons once
    initSearchToolbar();
    
    // If a previous session state exists, restore it; otherwise show the intro text.
    // restoreSession() is fully guarded and will never crash the app.
    if (!restoreSession()) showIntroText();
  }
  
  private void showIntroText() {
    StringBuffer sbc = new StringBuffer();
    sbc.append("\"Select\" EBook APK file.\n\n");
    sbc.append("\"Decode\" for 1-9 pages.\n\n");
    sbc.append("OR \"Pages\" for select your page range.\n\n");
    sbc.append("\"Beautify\" text optionally.\n\n");
    sbc.append("\"Clean Page Numbers\" optionally.\n\n");
    sbc.append("\"Format\" to change FG / BG / Font Style / Font Size / Line Spacing.\n\n");
    sbc.append("\"Find Op\" to set a search term (regex supported) and find matches.\n\n");
    sbc.append("\"Export\" for copy content to carets/inspector/ folder.\n\n");
    sbc.append("\"Help\" for menu descriptions.\n\n");
    sbc.append("\"Exit\" app cleanly.\n");
    editor.setEditorText(sbc.toString());
  }
  
  /**
   * Wires the find toolbar's prev/next/close buttons to their handlers.
   */
  private void initSearchToolbar() {
    if (btnFindNext != null) {
      btnFindNext.setOnClickListener(new View.OnClickListener() {
          @Override public void onClick(View v) { executeFind(); }
      });
    }
    if (btnFindPrev != null) {
      btnFindPrev.setOnClickListener(new View.OnClickListener() {
          @Override public void onClick(View v) { executeFindPrevious(); }
      });
    }
    if (btnFindClose != null) {
      btnFindClose.setOnClickListener(new View.OnClickListener() {
          @Override public void onClick(View v) {
            if (searchToolbar != null) searchToolbar.setVisibility(View.GONE);
            // Clear the find highlight so the last match doesn't stay yellow
            // after the user closes the find toolbar.
            editor.clearEditorHighlight();
            hideKeyboardSafe();
          }
      });
    }
  }
  
  private void showSearchToolbar() {
    if (searchToolbar != null) searchToolbar.setVisibility(View.VISIBLE);
    updateSearchStatus(searchTerm == null || searchTerm.isEmpty()
    ? "Find..." : "Search: " + searchTerm);
  }
  
  private void updateSearchStatus(final String text) {
    if (searchStatus == null) return;
    mainHandler.post(new Runnable() {
        @Override public void run() {
          try { searchStatus.setText(text == null ? "" : text); } catch (Exception ignored) {}
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
    
    if (id == R.id.menu_select) { executeSelect(); return true; }
    else if (id == R.id.menu_decode) { executeDecode(); return true; }
    else if (id == R.id.menu_pages) { executePages(); return true; }
    else if (id == R.id.menu_beautify) { executeBeautify(); return true; }
    else if (id == R.id.menu_clean_page_numbers) { executeCleanPageNumbers(); return true; }
    else if (id == R.id.menu_export) { executeExport(); return true; }
    else if (id == R.id.menu_format_fg) { executeFormatFg(); return true; }
    else if (id == R.id.menu_format_bg) { executeFormatBg(); return true; }
    else if (id == R.id.menu_format_font_style) { executeFormatFontStyle(); return true; }
    else if (id == R.id.menu_format_font_size) { executeFormatFontSize(); return true; }
    else if (id == R.id.menu_format_line_spacing) { executeFormatLineSpacing(); return true; }
    else if (id == R.id.menu_format_reset) { executeFormatReset(); return true; }
    else if (id == R.id.menu_search_term) { executeChangeSearchTerm(); return true; }
    else if (id == R.id.menu_find) {
      if (searchTerm == null || searchTerm.isEmpty()) {
        Toast.makeText(this,
          "Set a search term first (Find Op > Change Search Term).",
        Toast.LENGTH_LONG).show();
        return true;
      }
      showSearchToolbar();
      executeFind();
      return true;
    }
    else if (id == R.id.menu_help) { executeHelp(); return true; }
    else if (id == R.id.menu_exit) { exit(); return true; }
      
    return super.onOptionsItemSelected(item);
  }
  
  private void executeSelect() {
    filePicker.setMultiSelectionEnabled(false);
    filePicker.showOpenDialog(MFilePicker.OPEN_REQUEST_CODE);
  }
  
  /**
   * Saves the current editor text into carets/inspector/ using a dynamic
   * file name derived from the selected APK base name and the decoded range:
   *   <base>_<start>-<end>.txt   (e.g. abc_5-20.txt)
   */
  private void executeExport() {
    try {
      if (selectedApkUri == null) {
        Toast.makeText(DecodeBook.this, "Select an APK first.", Toast.LENGTH_SHORT).show();
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
      String fileName = base + "_" + lastStart + "-" + lastEnd + ".txt";
      File outFile = new File(mainPath + "/carets/inspector", fileName);
      
      OutputStream fos = new FileOutputStream(outFile);
      PrintStream ps = new PrintStream(fos, true, "UTF-8");
      ps.println(text);
      ps.flush();
      ps.close();
      fos.flush();
      fos.close();
      
      Toast.makeText(DecodeBook.this,
      "Wrote to: " + fileName, Toast.LENGTH_SHORT).show();
      } catch (Exception e) {
      Toast.makeText(DecodeBook.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }
  
  /**
   * Turns a display name into a safe export base name:
   *  - strips a trailing .apk (case-insensitive)
   *  - replaces path-hostile characters with '_'
   *  - falls back to "apk" when the result is empty
   */
  private String buildExportBaseName(String displayName) {
    String base = (displayName == null) ? "" : displayName.trim();
    if (base.toLowerCase().endsWith(".apk")) {
      base = base.substring(0, base.length() - 4);
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < base.length(); i++) {
      char c = base.charAt(i);
      if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?'
        || c == '"' || c == '<' || c == '>' || c == '|' || c < 0x20) {
        sb.append('_');
        } else {
        sb.append(c);
      }
    }
    String cleaned = sb.toString().trim();
    return cleaned.isEmpty() ? "apk" : cleaned;
  }
  
  private void executeDecode() {
    if (selectedApkUri == null) {
      Toast.makeText(DecodeBook.this, "Select an APK first.", Toast.LENGTH_SHORT).show();
      return;
    }
    decodeRange(DexPageReader.DEFAULT_START, DexPageReader.DEFAULT_END);
  }
  
  private void executePages() {
    if (selectedApkUri == null) {
      Toast.makeText(DecodeBook.this, "Select an APK first.", Toast.LENGTH_SHORT).show();
      return;
    }
    fileIOExecutor.execute(new Runnable() {
        @Override public void run() {
          try {
            InputStream is = getContentResolver().openInputStream(selectedApkUri);
            final int count = DexPageReader.countPages(is);
            is.close();
            totalPages = count;
            mainHandler.post(new Runnable() {
                @Override public void run() {
                  if (count <= 0) {
                    Toast.makeText(DecodeBook.this,
                    "No Sayfa_XXXXX classes found.", Toast.LENGTH_LONG).show();
                    return;
                  }
                  promptPageRange(count);
                }
            });
            } catch (final Exception e) {
            mainHandler.post(new Runnable() {
                @Override public void run() {
                  editor.setEditorText("Pages failed: " + e.getMessage());
                }
            });
          }
        }
    });
  }
  
  /**
   * Runs the beautify engine on the current editor content and replaces
   * the buffer with the result. Before replacing the text it captures the
   * caret position and scroll offset, then restores the caret close to
   * where it was so the user does not lose their place.
   */
  private void executeBeautify() {
    if (editor == null) return;
    final String content = editor.getFullText();
    if (content == null || content.length() < 1) {
      Toast.makeText(this, "Text is empty!", Toast.LENGTH_SHORT).show();
      return;
    }
    // Capture the caret and scroll BEFORE the text is replaced.
    final int savedCaret = Math.max(0, editor.getSelectionStart());
    final int savedScroll = Math.max(0, editor.getEditorScrollY());
    
    V2Utils.setBorrowNums(false);
    fileIOExecutor.execute(new Runnable() {
        @Override public void run() {
          try {
            final String processedText = V2UTFToISO.cnt(content);
            mainHandler.post(new Runnable() {
                @Override public void run() {
                  if (processedText != null) {
                    editor.setEditorText(processedText);
                    lastBeautified = true;
                    restoreCaretAndScroll(savedCaret, savedScroll);
                    Toast.makeText(getApplicationContext(),
                    "Text Beautified!", Toast.LENGTH_SHORT).show();
                    } else {
                    Toast.makeText(getApplicationContext(),
                    "Beautify returned null.", Toast.LENGTH_SHORT).show();
                  }
                }
            });
            } catch (final IOException e) {
            mainHandler.post(new Runnable() {
                @Override public void run() {
                  Toast.makeText(getApplicationContext(),
                  "Error in V2UTFToISO engine!", Toast.LENGTH_LONG).show();
                }
            });
            } catch (final Exception e) {
            mainHandler.post(new Runnable() {
                @Override public void run() {
                  Toast.makeText(getApplicationContext(),
                  "Beautify operation failed", Toast.LENGTH_SHORT).show();
                }
            });
          }
        }
    });
  }
  
  /**
   * Removes page-number artefacts from the current editor text and then
   * runs the beautify engine over the cleaned result. Before replacing
   * the text it captures the caret position and scroll offset, then
   * restores the caret close to where it was.
   */
  private void executeCleanPageNumbers() {
    if (editor == null) return;
    final String content = editor.getFullText();
    if (content == null || content.length() < 1) {
      Toast.makeText(this, "Text is empty!", Toast.LENGTH_SHORT).show();
      return;
    }
    // Capture the caret and scroll BEFORE the text is replaced.
    final int savedCaret = Math.max(0, editor.getSelectionStart());
    final int savedScroll = Math.max(0, editor.getEditorScrollY());
    
    fileIOExecutor.execute(new Runnable() {
        @Override public void run() {
          try {
            V2Utils.setBorrowNums(false);
            final String stripped = content.replaceAll(V2UTFToISO.SFREG, "");
            String processedText = V2UTFToISO.cnt(stripped);
            if (processedText == null) processedText = stripped;
            final String finalText = processedText;
            mainHandler.post(new Runnable() {
                @Override public void run() {
                  editor.setEditorText(finalText);
                  lastCleaned = true;
                  restoreCaretAndScroll(savedCaret, savedScroll);
                  Toast.makeText(getApplicationContext(),
                  "Page numbers cleaned!", Toast.LENGTH_SHORT).show();
                }
            });
            } catch (final Exception e) {
            mainHandler.post(new Runnable() {
                @Override public void run() {
                  Toast.makeText(getApplicationContext(),
                  "Page Number Clean operation failed", Toast.LENGTH_SHORT).show();
                }
            });
          }
        }
    });
  }
  
  /**
   * Restores the caret to the given position after the editor text has
   * been replaced, and scrolls so the caret line is visible. The caret
   * is clamped to the new text length because beautify or page-number
   * cleaning may shorten the buffer.
   *
   * If the caret line is still visible at the saved pixel scroll, the
   * saved scroll is kept so the view does not jump. Otherwise the editor
   * is scrolled so the caret line is brought into view.
   */
  private void restoreCaretAndScroll(final int savedCaret, final int savedScroll) {
    if (editor == null) return;
    editor.requestCaretAndScrollRestore(savedCaret, savedScroll);
  }
  
  private void executeFormatFg() {
    colorPicker.showColorPickerDialog(new MColorPicker.OnColorSelectedListener() {
        @Override public void onColorSelected(int color) {
          fgColor = color;
          editor.setTextColor(fgColor);
          Toast.makeText(DecodeBook.this, "FG updated.", Toast.LENGTH_SHORT).show();
        }
    });
  }
  
  private void executeFormatBg() {
    colorPicker.showColorPickerDialog(new MColorPicker.OnColorSelectedListener() {
        @Override public void onColorSelected(int color) {
          bgColor = color;
          editor.setBackgroundColor(bgColor);
          Toast.makeText(DecodeBook.this, "BG updated.", Toast.LENGTH_SHORT).show();
        }
    });
  }
  
  private void executeFormatFontStyle() {
    final String[] options = new String[] { "Plain", "Bold", "Italic", "Bold Italic" };
    optionPane.showSingleChoiceDialog("Font Style", options,
      new MOptionPane.OnChoiceListener() {
        @Override public void onResult(int index) {
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
      case 1: tf = Typeface.DEFAULT_BOLD; break;
      case 2: tf = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC); break;
      case 3: tf = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC); break;
      default: tf = Typeface.DEFAULT; break;
    }
    editor.setTypeface(tf);
  }
  
  private void executeFormatFontSize() {
    String hint = (fontSize > 0f) ? String.valueOf(fontSize) : "24.0";
    optionPane.showInputDialog(
      "Enter font size (sp), e.g. 24.0",
      hint,
      new MOptionPane.OnInputListener() {
        @Override public void onResult(String input) {
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
  
  /**
   * Prompts the user for a line spacing multiplier. Only the multiplier is
   * exposed; the extra pixel spacing is fixed at 0. Any value greater than
   * zero is accepted — the responsibility for readability is the user's.
   * The hint shows the current value so the user sees what is active now.
   */
  private void executeFormatLineSpacing() {
    String hint = (lineSpacing > 0f) ? String.valueOf(lineSpacing) : "1.0";
    optionPane.showInputDialog(
      "Line spacing multiplier (1.0 = normal, 1.2 = wider, 0.8 = tighter):",
      hint,
      new MOptionPane.OnInputListener() {
        @Override public void onResult(String input) {
          if (input == null || input.trim().isEmpty()) return;
          try {
            float m = Float.parseFloat(input.trim());
            if (m <= 0f) {
              Toast.makeText(DecodeBook.this, "Multiplier must be > 0.", Toast.LENGTH_SHORT).show();
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
  
  private void executeFormatReset() {
    fgColor = 0xFF000000;
    bgColor = 0xFFFFFFFF;
    fontStyle = 0;
    fontSize = 24.0f;
    lineSpacing = 1.0f;
    editor.setTextColor(fgColor);
    editor.setBackgroundColor(bgColor);
    editor.setTypeface(Typeface.DEFAULT);
    editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize);
    editor.setEditorLineSpacing(lineSpacing);
    Toast.makeText(DecodeBook.this, "Format reset.", Toast.LENGTH_SHORT).show();
  }
  
  private void executeChangeSearchTerm() {
    String hint = searchTerm.isEmpty() ? "Sayfa_\\d+" : searchTerm;
    optionPane.showInputDialog(
      "Search term (regex allowed):",
      hint,
      new MOptionPane.OnInputListener() {
        @Override public void onResult(String input) {
          if (input == null) return;
          String term = input.trim();
          if (term.isEmpty()) {
            searchTerm = "";
            searchIsRegex = false;
            lastSearchFrom = 0;
            lastMatchStart = 0;
            updateSearchStatus("Find...");
            Toast.makeText(DecodeBook.this, "Search term cleared.", Toast.LENGTH_SHORT).show();
            return;
          }
          boolean regexOk = true;
          try { Pattern.compile(term); } catch (PatternSyntaxException pse) { regexOk = false; }
            searchTerm = term;
          searchIsRegex = regexOk;
          lastSearchFrom = 0;
          lastMatchStart = 0;
          updateSearchStatus("Search: " + searchTerm);
          Toast.makeText(DecodeBook.this,
            "Search term set (" + (searchIsRegex ? "regex" : "plain") + ").",
          Toast.LENGTH_SHORT).show();
        }
    });
  }
  
  /**
   * Finds the next occurrence of the current search term, highlights it and
   * scrolls it into view. Wraps to the top when the end of the text is hit.
   */
  private void executeFind() {
    if (editor == null) return;
    if (searchTerm == null || searchTerm.isEmpty()) {
      Toast.makeText(this, "Set a search term first (Find Op > Change Search Term).",
      Toast.LENGTH_LONG).show();
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
      // Wrap around and try once more from the beginning.
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
    // Next forward search starts after this match; Find Previous reads
    // lastMatchStart to jump one match back without reselecting the current.
    lastSearchFrom = matchEnd;
    lastMatchStart = matchStart;
    
    editor.setEditorHighlight(matchStart, matchEnd);
    updateSearchStatus("Found @ " + matchStart + ".." + matchEnd);
    
    final int fs = matchStart;
    editor.post(new Runnable() {
        @Override public void run() { scrollToOffset(fs); }
    });
  }
  
  /**
   * Finds the previous occurrence of the current search term.
   *
   * Uses lastMatchStart (start of the last found match) as the upper bound,
   * so a single "<" press jumps straight to the match *before* the current
   * one, instead of reselecting the current one. If nothing earlier exists,
   * we wrap to the last match in the document.
   */
  private void executeFindPrevious() {
    if (editor == null) return;
    if (searchTerm == null || searchTerm.isEmpty()) {
      Toast.makeText(this, "Set a search term first (Find Op > Change Search Term).",
      Toast.LENGTH_LONG).show();
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
    
    int[] best = null;         // last match whose start is < currentStart
    int[] lastOverall = null;  // last match in the document (for wrap)
    int from = 0;
    
    if (searchIsRegex) {
      Pattern p;
      try { p = Pattern.compile(searchTerm); }
      catch (PatternSyntaxException pse) {
        Toast.makeText(this, "Regex error: " + pse.getDescription(), Toast.LENGTH_SHORT).show();
        return;
      }
      Matcher m = p.matcher(text);
      while (m.find(from)) {
        int[] r = new int[] { m.start(), m.end() };
        lastOverall = r;
        if (r[0] < currentStart) best = r; else break;
        from = m.end();
        if (m.end() == m.start()) {
          if (from < text.length()) from++; else break;
        }
      }
      } else {
      int idx = 0;
      while ((idx = text.indexOf(searchTerm, idx)) >= 0) {
        int[] r = new int[] { idx, idx + searchTerm.length() };
        lastOverall = r;
        if (r[0] < currentStart) best = r; else break;
        idx = r[1];
      }
    }
    
    if (best == null) {
      if (lastOverall == null) {
        updateSearchStatus("No match: " + searchTerm);
        Toast.makeText(this, "No match found.", Toast.LENGTH_SHORT).show();
        return;
      }
      // Nothing before the cursor: wrap to the last match in the document.
      best = lastOverall;
      Toast.makeText(this, "Wrapped to bottom.", Toast.LENGTH_SHORT).show();
    }
    
    final int matchStart = best[0];
    final int matchEnd = best[1];
    // Keep both cursors coherent: next ">" continues after this match,
    // next "<" jumps to the one before it.
    lastSearchFrom = matchEnd;
    lastMatchStart = matchStart;
    
    editor.setEditorHighlight(matchStart, matchEnd);
    updateSearchStatus("Found @ " + matchStart + ".." + matchEnd);
    
    final int fs = matchStart;
    editor.post(new Runnable() {
        @Override public void run() { scrollToOffset(fs); }
    });
  }
  
  /**
   * Returns the [start, end) range of the next match at or after "from",
   * or null if there is none.
   */
  private int[] findNext(String text, int from) {
    if (searchIsRegex) {
      try {
        Pattern p = Pattern.compile(searchTerm);
        Matcher m = p.matcher(text);
        if (m.find(from)) return new int[] { m.start(), m.end() };
        } catch (PatternSyntaxException pse) {
        return null;
      }
      return null;
      } else {
      int idx = text.indexOf(searchTerm, from);
      if (idx < 0) return null;
      return new int[] { idx, idx + searchTerm.length() };
    }
  }
  
  /**
   * Scrolls the editor so that the given character offset becomes visible.
   */
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
    } catch (Exception ignored) {}
  }
  
  private void executeHelp() {
    StringBuilder sb = new StringBuilder();
    sb.append("\"Select\" — pick an EBook APK file.\n\n");
    sb.append("\"Decode\" — extract pages 1-9 from the selected APK.\n\n");
    sb.append("\"Pages\" — count pages and prompt for a custom range (max ")
    .append(DexPageReader.MAX_RANGE).append(").\n\n");
    sb.append("\"Beautify\" — run the text beautify engine on the current content.\n\n");
    sb.append("\"Clean Page Numbers\" — run clean page numbers on the current content.\n\n");
    sb.append("\"Format\" → FG — change editor foreground (text) color.\n\n");
    sb.append("\"Format\" → BG — change editor background color.\n\n");
    sb.append("\"Format\" → Font Style — Plain / Bold / Italic / Bold Italic.\n\n");
    sb.append("\"Format\" → Font Size — enter a float size in sp.\n\n");
    sb.append("\"Format\" → Line Spacing Multiplier — enter a float (1.0 = normal, 1.2 = wider).\n\n");
    sb.append("\"Export\" — save current content to carets/inspector/<apk>_<start>-<end>.txt.\n\n");
    sb.append("\"Help\" — show this help.\n\n");
    sb.append("\"Exit\" — save session state and close the app cleanly.\n\n");
    sb.append("Session state is stored in carets/inspector/inspectorInfo.txt and restored\n");
    sb.append("on next launch (APK, range, scroll, FG, BG, style, size, line spacing).");
    optionPane.showMessageDialog(sb.toString());
  }
  
  private void exit() {
    saveSession();
    finishAndRemoveTask();
  }
  
  private void saveSession() {
    try {
      OutputStream fos = new FileOutputStream(INFOFILE);
      PrintStream ps = new PrintStream(fos, true, "UTF-8");
      String uriStr = (selectedApkUri != null) ? selectedApkUri.toString() : "";
      int scroll = (editor != null) ? editor.getEditorScrollY() : 0;
      ps.println("uri=" + uriStr);
      ps.println("start=" + lastStart);
      ps.println("end=" + lastEnd);
      ps.println("scroll=" + scroll);
      ps.println("fg=" + fgColor);
      ps.println("bg=" + bgColor);
      ps.println("style=" + fontStyle);
      ps.println("size=" + fontSize);
      ps.println("linespacing=" + lineSpacing);
      ps.println("beautified=" + (lastBeautified ? 1 : 0));
      ps.println("cleaned_page_numbers=" + (lastCleaned ? 1 : 0));
      ps.flush();
      ps.close();
      fos.flush();
      fos.close();
      } catch (Exception e) {
      Toast.makeText(DecodeBook.this, "Session save error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }
  
  /**
   * Tries to restore a previous session from inspectorInfo.txt.
   * Fully guarded: any exception is swallowed and the caller falls back to
   * the intro screen. On success, also seeds lastPagesRange from the saved
   * start/end so the Pages dialog shows the previous range immediately.
   */
  private boolean restoreSession() {
    try {
      if (INFOFILE == null || !INFOFILE.exists()) return false;
      
      String uriStr = "";
      int start = 0, end = 0, scroll = 0;
      int rFg = fgColor, rBg = bgColor, rStyle = fontStyle;
      float rSize = fontSize;
      float rLine = lineSpacing;
      boolean rBeautified = false;
      boolean rCleaned = false;
      
      BufferedReader br = null;
      try {
        br = new BufferedReader(new InputStreamReader(new FileInputStream(INFOFILE), "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) {
          int eq = line.indexOf('=');
          if (eq < 0) continue;
          String key = line.substring(0, eq).trim();
          String val = line.substring(eq + 1).trim();
          if (key.equals("uri")) uriStr = val;
          else if (key.equals("start")) start = parseIntSafe(val, 0);
          else if (key.equals("end")) end = parseIntSafe(val, 0);
          else if (key.equals("scroll")) scroll = parseIntSafe(val, 0);
          else if (key.equals("fg")) rFg = parseIntSafe(val, rFg);
          else if (key.equals("bg")) rBg = parseIntSafe(val, rBg);
          else if (key.equals("style")) rStyle = parseIntSafe(val, rStyle);
          else if (key.equals("size")) rSize = parseFloatSafe(val, rSize);
          else if (key.equals("linespacing")) rLine = parseFloatSafe(val, rLine);
          else if (key.equals("beautified")) rBeautified = parseIntSafe(val, 0) != 0;
          else if (key.equals("cleaned_page_numbers")) rCleaned = parseIntSafe(val, 0) != 0;
        }
        } finally {
        if (br != null) try { br.close(); } catch (IOException ignored) {}
        }
      
      if (uriStr.isEmpty()) return false;
      
      Uri uri;
      try { uri = Uri.parse(uriStr); } catch (Exception e) { return false; }
        
      // Apply saved format state immediately (visible even before decode finishes).
      // The order matters: format first, scroll later once layout is ready.
      try {
        fgColor = rFg; bgColor = rBg; fontStyle = rStyle; fontSize = rSize;
        lineSpacing = (rLine > 0f) ? rLine : 1.0f;
        editor.setTextColor(fgColor);
        editor.setBackgroundColor(bgColor);
        applyFontStyle();
        if (fontSize > 0f) {
          editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize);
        }
        editor.setEditorLineSpacing(lineSpacing);
      } catch (Exception ignored) {}
      
      // Seed the Pages dialog with the range from the saved session.
      if (start >= 1 && end >= start) {
        lastPagesRange = start + "-" + end;
      }
      
      if (start < 1 || end < start) {
        try {
          editor.setEditorText("Session restored (format only).\nSelect an APK to continue.");
        } catch (Exception ignored) {}
        return true;
      }
      
      selectedApkUri = uri;
      selectedApkName = getFileName(uri);
      if (selectedApkName == null || selectedApkName.trim().isEmpty()) {
        selectedApkName = uri.toString();
      }
      
      decodeRangeForRestore(start, end, scroll, rBeautified, rCleaned);
      return true;
      } catch (Exception e) {
      return false;
    }
  }
  
  private void decodeRangeForRestore(final int start, final int end,
    final int scrollY, final boolean beautify, final boolean pgncleaned) {
    try {
      fileIOExecutor.execute(new Runnable() {
          @Override public void run() {
            String output = null;
            try {
              InputStream is = getContentResolver().openInputStream(selectedApkUri);
              if (is == null) throw new IOException("Cannot open APK stream.");
              List<String> pages = DexPageReader.readPages(is, start, end);
              is.close();
              StringBuilder sb = new StringBuilder();
              for (String page : pages) sb.append(page).append("\n\n");
              output = sb.length() > 0 ? sb.toString()
              : "No pages found in range " + start + "-" + end + ".";
              if (beautify) {
                try {
                  V2Utils.setBorrowNums(false);
                  String beautified = V2UTFToISO.cnt(output);
                  if (beautified != null) output = beautified;
                } catch (Exception ignored) {}
              }
              if (pgncleaned) {
                try {
                  V2Utils.setBorrowNums(false);
                  String stripped = output.replaceAll(V2UTFToISO.SFREG, "");
                  String cleaned = V2UTFToISO.cnt(stripped);
                  output = (cleaned != null) ? cleaned : stripped;
                } catch (Exception ignored) {}
              }
              } catch (Exception e) {
              final String msg = (e.getMessage() != null) ? e.getMessage() : e.getClass().getSimpleName();
              mainHandler.post(new Runnable() {
                  @Override public void run() {
                    try {
                      editor.setEditorText("Restore skipped: " + msg +
                      "\n\nSelect the APK again and press Decode.");
                    } catch (Exception ignored) {}
                  }
              });
              return;
            }
            final String finalOutput = output;
            mainHandler.post(new Runnable() {
                @Override public void run() {
                  try {
                    editor.setEditorText(finalOutput);
                    lastStart = start;
                    lastEnd = end;
                    lastBeautified = beautify;
                    lastCleaned = pgncleaned;
                    // Re-apply line spacing after the text swap so the layout
                    // is computed with the correct multiplier, then scroll.
                    editor.setEditorLineSpacing(lineSpacing);
                    mainHandler.post(new Runnable() {
                        @Override public void run() {
                          try { editor.setEditorScrollY(scrollY); } catch (Exception ignored) {}
                          }
                    });
                  } catch (Exception ignored) {}
                }
            });
          }
      });
    } catch (Exception e) {}
  }
  
  private static int parseIntSafe(String s, int def) {
    try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
  private static float parseFloatSafe(String s, float def) {
    try { return Float.parseFloat(s); } catch (Exception e) { return def; }
    }
  
  // ---------------------------------------------------------------------
  // PAGE RANGE PROMPT
  // ---------------------------------------------------------------------
  
  private void promptPageRange(final int count) {
    // Prefer the value carried in memory; fall back to a safe default.
    String hint = (lastPagesRange != null && !lastPagesRange.isEmpty())
    ? lastPagesRange
    : ("1-" + Math.min(9, count));
    
    optionPane.showInputDialog(
      "There are " + count + " pages in this ebook APK.\n" +
      "Enter range (e.g. 20-40, max " + DexPageReader.MAX_RANGE + " pages):",
      hint,
      new MOptionPane.OnInputListener() {
        @Override public void onResult(String input) {
          if (input == null || input.trim().isEmpty()) return;
          String s = input.trim();
          int dash = s.indexOf('-');
          int start = 1, end = 1;
          if (dash < 0) {
            try { start = Integer.parseInt(s); }
            catch (NumberFormatException nfe) {
              Toast.makeText(DecodeBook.this, "Invalid numbers.", Toast.LENGTH_SHORT).show();
              return;
            }
            // Valid single-line input: remember it in memory only.
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
          if (end - start + 1 > DexPageReader.MAX_RANGE) {
            Toast.makeText(DecodeBook.this,
            "Max " + DexPageReader.MAX_RANGE + " pages per range.", Toast.LENGTH_LONG).show();
            return;
          }
          // Valid range: remember it in memory only.
          lastPagesRange = s;
          decodeRange(start, end);
        }
    });
  }
  
  private void decodeRange(final int start, final int end) {
    fileIOExecutor.execute(new Runnable() {
        @Override public void run() {
          try {
            InputStream is = getContentResolver().openInputStream(selectedApkUri);
            List<String> pages = DexPageReader.readPages(is, start, end);
            is.close();
            StringBuilder sb = new StringBuilder();
            for (String page : pages) sb.append(page).append("\n\n");
            final String output = sb.length() > 0 ? sb.toString()
            : "No pages found in range " + start + "-" + end + ".";
            mainHandler.post(new Runnable() {
                @Override public void run() {
                  editor.setEditorText(output);
                  lastStart = start;
                  lastEnd = end;
                  lastBeautified = false;
                  lastCleaned = false;
                  lastSearchFrom = 0;
                  lastMatchStart = 0;
                  // Re-apply line spacing for the fresh buffer.
                  editor.setEditorLineSpacing(lineSpacing);
                }
            });
            } catch (final Exception e) {
            mainHandler.post(new Runnable() {
                @Override public void run() {
                  editor.setEditorText("Decode failed: " + e.getMessage());
                }
            });
          }
        }
    });
  }
  
  @Override
  protected void onActivityResult(final int requestCode, int resultCode, Intent data) {
    if (filePicker.handleResult(requestCode, resultCode, data)) {
      final Uri targetUri = filePicker.getSelectedUri();
      if (requestCode == MFilePicker.OPEN_REQUEST_CODE && targetUri != null) {
        selectedApkUri = targetUri;
        try {
          getContentResolver().takePersistableUriPermission(
          targetUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        String name = getFileName(targetUri);
        if (name != null && !name.trim().isEmpty()) selectedApkName = name;
        else selectedApkName = targetUri.toString();
        Toast.makeText(DecodeBook.this,
        "Selected: " + selectedApkName, Toast.LENGTH_SHORT).show();
        editor.setEditorText(
        "Selected: " + selectedApkName + "\n\nPress Decode to extract pages.");
      }
    }
    super.onActivityResult(requestCode, resultCode, data);
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
    if (uri.getScheme() != null && uri.getScheme().equals("content")) {
      android.database.Cursor cursor =
      getContentResolver().query(uri, null, null, null, null);
      try {
        if (cursor != null && cursor.moveToFirst()) {
          int index = cursor.getColumnIndex(
          android.provider.OpenableColumns.DISPLAY_NAME);
          if (index != -1) result = cursor.getString(index);
        }
        } finally {
        if (cursor != null) cursor.close();
      }
    }
    if (result == null) {
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
  
}
