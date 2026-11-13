package net.murat.uikit;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.OverScroller;

/**
 * Multi-line editor used by DecodeBook.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Fast-scroll strip on the right edge (linear mapping of touch Y to scroll position).
 *   <li>Drag scrolling with fling, implemented on top of the native EditText scroll.
 *   <li>A "first tap" focus guard so the keyboard does not pop up unexpectedly.
 *   <li>External read-only mode that disables typing and selection without losing the current
 *       scroll position.
 *   <li>Find highlight spans, alignment, line spacing and scroll helpers used by DecodeBook.
 * </ul>
 *
 * <p>Touch behavior (normal EditText feel, but robust against finger drift):
 *
 * <ul>
 *   <li>Short tap: the caret is placed at the touched position.
 *   <li>Press and hold (finger stays within touch slop): the word under the finger is selected;
 *       dragging afterwards extends the selection (handled by EditText).
 *   <li>Finger moves beyond touch slop before the long-press fires: the pending long-press is
 *       cancelled, so no word gets selected; only scrolling/fling happens.
 * </ul>
 *
 * @author Murat
 */
public class MEditor extends EditText {

  // --- Fast-scroll strip ---------------------------------------------------
  private boolean isFastScrolling = false;
  private int fastScrollWidthThreshold = 0;
  private int maxScrollableYCache = 0;

  // --- First-tap focus guard ----------------------------------------------
  private boolean pendingFirstTap = true;

  // --- External read-only mode --------------------------------------------
  private boolean externalReadOnlyMode = false;

  // --- Fling support -------------------------------------------------------
  private OverScroller scroller;
  private VelocityTracker velocityTracker;
  private int minFlingVelocity;
  private int maxFlingVelocity;
  private int touchSlop;

  private boolean isDraggingContent = false;
  private boolean dragThresholdCrossed = false;
  private float lastTouchY = 0f;
  private float downTouchX = 0f;
  private float downTouchY = 0f;

  // True once the native long-press fired for the current gesture (a word is
  // being selected). From that point on, all touch events belong to EditText.
  private boolean longPressFired = false;

  // --- Find highlight ------------------------------------------------------
  private Object activeHighlightSpan = null;

  // --- Line spacing --------------------------------------------------------
  private float lineSpacingMultiplier = 1.0f;
  private float lineSpacingExtra = 0f;

  // --- Pending caret/scroll restore ---------------------------------------
  private int pendingRestoreCaret = -1;
  private int pendingRestoreScroll = -1;

  public MEditor(Context context) {
    super(context);
    init(context);
  }

  public MEditor(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  public MEditor(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context);
  }

  private void init(Context context) {
    setInputType(
        InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

    setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
    setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
    setHorizontallyScrolling(false);
    setVerticalScrollBarEnabled(true);

    float density = getResources().getDisplayMetrics().density;
    fastScrollWidthThreshold = (int) (40 * density);

    setFocusable(false);
    setFocusableInTouchMode(false);

    setEditableFactory(
        new Editable.Factory() {
          @Override
          public Editable newEditable(CharSequence source) {
            return super.newEditable(source);
          }
        });

    scroller = new OverScroller(context);
    ViewConfiguration vc = ViewConfiguration.get(context);
    minFlingVelocity = vc.getScaledMinimumFlingVelocity();
    maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
    touchSlop = vc.getScaledTouchSlop();
  }

  // =====================================================================
  // READ-ONLY / EDITABLE MODE
  // =====================================================================

  /**
   * Enables or disables the external read-only mode.
   *
   * <p>When read-only: no focus, no cursor, no selection (an active selection is collapsed to a
   * caret), and the scroll position is preserved.
   *
   * <p>When not read-only: focus and cursor are enabled so the user can type and select.
   */
  public void setExternalReadOnly(boolean readOnly) {
    externalReadOnlyMode = readOnly;
    if (readOnly) {
      setFocusable(false);
      setFocusableInTouchMode(false);
      setCursorVisible(false);
      try {
        // Only clear an active selection; if there is none, do nothing so the
        // layout is not recalculated and the view does not flicker.
        int selStart = getSelectionStart();
        int selEnd = getSelectionEnd();
        if (selStart != selEnd && selStart >= 0 && selEnd >= 0) {
          final int savedScroll = getScrollY();
          int caret = Math.min(selStart, selEnd);
          setSelection(caret);
          post(
              new Runnable() {
                @Override
                public void run() {
                  scrollTo(0, savedScroll);
                }
              });
        }
      } catch (Exception ignored) {
      }
    } else {
      setFocusable(true);
      setFocusableInTouchMode(true);
      setCursorVisible(true);
    }
  }

  /**
   * Enables or disables typing and selection independently from the read-only flag.
   *
   * <p>Called by DecodeBook when the user toggles "Read Only". When editable is true, the view
   * becomes focusable and the cursor is shown; when false, focus and cursor are removed.
   */
  public void setEditableMode(boolean editable) {
    externalReadOnlyMode = !editable;

    if (editable) {
      setFocusable(true);
      setFocusableInTouchMode(true);
      setCursorVisible(true);
    } else {
      clearFocus();
      setFocusable(false);
      setFocusableInTouchMode(false);
      setCursorVisible(false);

      try {
        int selStart = getSelectionStart();
        int selEnd = getSelectionEnd();
        if (selStart != selEnd && selStart >= 0 && selEnd >= 0) {
          final int savedScroll = getScrollY();
          int caret = Math.min(selStart, selEnd);
          setSelection(caret);
          post(
              new Runnable() {
                @Override
                public void run() {
                  scrollTo(0, savedScroll);
                }
              });
        }
      } catch (Exception ignored) {
      }
    }
  }

  @Override
  public boolean onCheckIsTextEditor() {
    return !externalReadOnlyMode && super.onCheckIsTextEditor();
  }

  /** Re-arms the first-tap focus guard. */
  public void resetFirstTapGuard() {
    pendingFirstTap = true;
    if (!externalReadOnlyMode) {
      setFocusable(false);
      setFocusableInTouchMode(false);
    }
  }

  // =====================================================================
  // TEXT SETTERS
  // =====================================================================

  /**
   * Replaces the whole editor content and moves the caret to the start.
   *
   * <p>If the content is empty, the text is cleared with {@link BufferType#EDITABLE}. The first-tap
   * guard is reset when the editor is not read-only.
   */
  public void setEditorText(String fullText) {
    if (fullText == null || fullText.isEmpty()) {
      setText("", BufferType.EDITABLE);
    } else {
      setText(fullText, BufferType.EDITABLE);
      try {
        setSelection(0);
      } catch (Exception ignored) {
      }
    }
    if (!externalReadOnlyMode) {
      resetFirstTapGuard();
    }
  }

  /**
   * Sets a Spanned content while preserving its spans. MEditor extends TextView, and {@code
   * setText(CharSequence, BufferType.SPANNABLE)} is public, so the spans survive.
   */
  @Override
  public void setText(CharSequence text, BufferType type) {
    if (text instanceof Spanned && type == BufferType.SPANNABLE) {
      super.setText(text, BufferType.SPANNABLE);
    } else {
      super.setText(text, type);
    }
  }

  /** Returns the full text as a plain String (never null). */
  public String getFullText() {
    return (getText() != null) ? getText().toString() : "";
  }

  // =====================================================================
  // SCROLL HELPERS
  // =====================================================================

  /** Returns the current vertical scroll offset. */
  public int getEditorScrollY() {
    return getScrollY();
  }

  /**
   * Sets the vertical scroll offset, clamped to the scrollable range. Safe to call before the
   * layout is measured: the value is applied after the next layout pass.
   */
  public void setEditorScrollY(int y) {
    if (getLayout() == null) {
      pendingRestoreScroll = y;
      return;
    }
    int maxY = computeMaxScrollY();
    if (y < 0) y = 0;
    if (y > maxY) y = maxY;
    scrollTo(getScrollX(), y);
    invalidate();
  }

  /** Computes the maximum scrollable Y offset for the current layout. */
  private int computeMaxScrollY() {
    if (getLayout() == null) return 0;
    int totalTextHeight = getLayout().getHeight() + getPaddingTop() + getPaddingBottom();
    int maxY = totalTextHeight - getHeight();
    return Math.max(0, maxY);
  }

  /**
   * Requests a caret position and scroll offset to be restored after the next layout pass. Used by
   * DecodeBook after replacing the content (replace, clean, etc.).
   */
  public void requestCaretAndScrollRestore(final int caret, final int scrollY) {
    pendingRestoreCaret = caret;
    pendingRestoreScroll = scrollY;
    post(
        new Runnable() {
          @Override
          public void run() {
            applyPendingRestore();
          }
        });
  }

  /** Applies any pending caret/scroll restore once the layout is ready. */
  private void applyPendingRestore() {
    if (getLayout() == null) {
      // Try again on the next frame.
      post(
          new Runnable() {
            @Override
            public void run() {
              applyPendingRestore();
            }
          });
      return;
    }
    if (pendingRestoreCaret >= 0) {
      int caret = pendingRestoreCaret;
      pendingRestoreCaret = -1;
      int len = getText() != null ? getText().length() : 0;
      if (caret > len) caret = len;
      try {
        setSelection(caret);
      } catch (Exception ignored) {
      }
    }
    if (pendingRestoreScroll >= 0) {
      int scroll = pendingRestoreScroll;
      pendingRestoreScroll = -1;
      setEditorScrollY(scroll);
    }
  }

  // =====================================================================
  // FIND HIGHLIGHT
  // =====================================================================

  /**
   * Highlights the range [start, end) with a background color span. Any previous highlight is
   * removed first. The span is stored so it can be cleared later.
   */
  public void setEditorHighlight(int start, int end) {
    clearEditorHighlight();
    CharSequence cs = getText();
    if (!(cs instanceof Spannable)) return;
    Spannable sp = (Spannable) cs;
    int len = sp.length();
    if (start < 0) start = 0;
    if (end > len) end = len;
    if (start >= end) return;
    BackgroundColorSpan span = new BackgroundColorSpan(0x66FFEB3B);
    try {
      sp.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      activeHighlightSpan = span;
    } catch (Exception ignored) {
    }
  }

  /** Removes the active find highlight, if any. */
  public void clearEditorHighlight() {
    if (activeHighlightSpan == null) return;
    CharSequence cs = getText();
    if (cs instanceof Spannable) {
      try {
        ((Spannable) cs).removeSpan(activeHighlightSpan);
      } catch (Exception ignored) {
      }
    }
    activeHighlightSpan = null;
  }

  // =====================================================================
  // LINE SPACING / ALIGNMENT
  // =====================================================================

  /**
   * Sets the line spacing multiplier (1.0 = normal). The extra spacing is reset to 0 so the
   * multiplier fully controls the spacing.
   */
  public void setEditorLineSpacing(float multiplier) {
    if (multiplier <= 0f) multiplier = 1.0f;
    lineSpacingMultiplier = multiplier;
    lineSpacingExtra = 0f;
    setLineSpacing(lineSpacingExtra, lineSpacingMultiplier);
    requestLayout();
    invalidate();
  }

  /** Returns the current line spacing multiplier. */
  public float getEditorLineSpacing() {
    return lineSpacingMultiplier;
  }

  /**
   * Sets the horizontal alignment of the text. Accepts the standard Gravity constants (START/LEFT,
   * CENTER_HORIZONTAL, END/RIGHT).
   */
  public void setEditorAlignment(int gravity) {
    setGravity(gravity);
    requestLayout();
    invalidate();
  }

  // =====================================================================
  // LINE COUNT / CARET LINE
  // =====================================================================

  /**
   * Returns the number of logical lines (separated by '\n'), not visual wrapped lines. An empty
   * text has 0 lines.
   */
  @Override
  public int getLineCount() {
    CharSequence textPayload = getText();
    if (textPayload == null || textPayload.length() == 0) {
      return 0;
    }
    String rawString = textPayload.toString();
    int totalLinesCount = 1;
    int currentSearchIndex = 0;
    while ((currentSearchIndex = rawString.indexOf('\n', currentSearchIndex)) != -1) {
      totalLinesCount++;
      currentSearchIndex++;
    }
    return totalLinesCount;
  }

  /** Returns the 1-based logical line number of the caret. */
  public int getCaretLine() {
    final CharSequence textSeq = getText();
    if (textSeq == null) return 1;
    final int len = textSeq.length();
    if (len < 1) return 1;
    int caretPosition = getSelectionStart();
    if (caretPosition < 0) caretPosition = 0;
    int caretLine = 1;
    final char NLN = '\n';
    for (int i = 0; i < len; i++) {
      if (i < caretPosition && textSeq.charAt(i) == NLN) {
        caretLine++;
      }
    }
    return caretLine;
  }

  // =====================================================================
  // TOUCH HANDLING
  // =====================================================================

  /**
   * Called by the framework when the long-press timer expires (finger held down without being
   * cancelled). We remember it so that the rest of the gesture is handed over to EditText, which
   * lets the user extend the word selection by dragging.
   */
  @Override
  public boolean performLongClick() {
    longPressFired = true;
    return super.performLongClick();
  }

  /**
   * Sends a synthetic ACTION_CANCEL to EditText/View. This cancels its pending long-press timer and
   * resets the internal Editor touch state, without affecting our own gesture handling.
   */
  private void cancelSuperTouch(MotionEvent src) {
    MotionEvent cancel = MotionEvent.obtain(src);
    cancel.setAction(MotionEvent.ACTION_CANCEL);
    super.onTouchEvent(cancel);
    cancel.recycle();
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    // Layout not ready yet: only handle the first-tap focus guard and defer to super.
    if (getLayout() == null) {
      if (pendingFirstTap && event.getAction() == MotionEvent.ACTION_UP && !externalReadOnlyMode) {
        pendingFirstTap = false;
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
      }
      return super.onTouchEvent(event);
    }

    if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
    velocityTracker.addMovement(event);

    float touchX = event.getX();
    float touchY = event.getY();
    int viewWidth = getWidth();
    int viewHeight = getHeight();

    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        if (!scroller.isFinished()) scroller.abortAnimation();

        int totalTextHeight = getLayout().getHeight() + getPaddingTop() + getPaddingBottom();
        maxScrollableYCache = totalTextHeight - viewHeight;

        downTouchX = touchX;
        downTouchY = touchY;
        lastTouchY = touchY;
        dragThresholdCrossed = false;
        isDraggingContent = false;
        longPressFired = false;

        // Content fits in the view: nothing to scroll, let EditText handle the touch.
        if (maxScrollableYCache <= 0) {
          isFastScrolling = false;
          if (pendingFirstTap && !externalReadOnlyMode) {
            pendingFirstTap = false;
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
          }
          return super.onTouchEvent(event);
        }

        // Touch started in the fast-scroll strip on the right edge.
        if (touchX >= (viewWidth - fastScrollWidthThreshold)) {
          isFastScrolling = true;
          performLinearScrollUpdate(touchY, viewHeight, maxScrollableYCache);
          return true;
        }

        if (pendingFirstTap && !externalReadOnlyMode) {
          pendingFirstTap = false;
          setFocusable(true);
          setFocusableInTouchMode(true);
          requestFocus();
        }

        // Forward DOWN to super so EditText can arm its long-press timer
        // (word selection) and record the initial touch position for caret placement.
        super.onTouchEvent(event);
        return true;

      case MotionEvent.ACTION_MOVE:
        if (isFastScrolling && maxScrollableYCache > 0) {
          performLinearScrollUpdate(touchY, viewHeight, maxScrollableYCache);
          return true;
        }

        // Long-press already fired: a word is selected, so extending the selection
        // by dragging is EditText's job.
        if (longPressFired) {
          return super.onTouchEvent(event);
        }

        float dx = Math.abs(touchX - downTouchX);
        float dy = Math.abs(touchY - downTouchY);

        if (!dragThresholdCrossed) {
          if (dx > touchSlop || dy > touchSlop) {
            // The finger moved beyond touch slop: this is no longer a tap or a
            // long-press, it is a scroll. Cancel EditText's pending long-press
            // timer and Editor state so no word gets selected by accident.
            cancelSuperTouch(event);
            dragThresholdCrossed = true;
            isDraggingContent = true;
            lastTouchY = touchY;
            return true;
          }
          // Small movement inside touch slop: swallow it, but leave the long-press
          // timer alive so a deliberate press-and-hold still selects the word.
          return true;
        }

        if (isDraggingContent) {
          float deltaY = lastTouchY - touchY;
          lastTouchY = touchY;
          int newScroll = getScrollY() + (int) deltaY;
          if (newScroll < 0) newScroll = 0;
          if (newScroll > maxScrollableYCache) newScroll = maxScrollableYCache;
          scrollTo(getScrollX(), newScroll);
          invalidate();
          return true;
        }
        return super.onTouchEvent(event);

      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (isFastScrolling) {
          isFastScrolling = false;
          maxScrollableYCache = 0;
          recycleTracker();
          return true;
        }

        // Long-press fired during this gesture: let EditText commit the selection.
        if (longPressFired) {
          longPressFired = false;
          dragThresholdCrossed = false;
          isDraggingContent = false;
          recycleTracker();
          return super.onTouchEvent(event);
        }

        if (isDraggingContent) {
          isDraggingContent = false;
          dragThresholdCrossed = false;
          if (velocityTracker != null) {
            velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
            int vy = (int) velocityTracker.getYVelocity();
            recycleTracker();
            if (Math.abs(vy) > minFlingVelocity) {
              scroller.fling(
                  getScrollX(), getScrollY(), 0, -vy, 0, 0, 0, Math.max(0, maxScrollableYCache));
              postInvalidateOnAnimation();
            }
          } else {
            recycleTracker();
          }
          return true;
        }

        // Finger never crossed the drag threshold: this is a tap.
        // Forward UP to super so EditText can place the caret.
        recycleTracker();
        return super.onTouchEvent(event);
    }

    return super.onTouchEvent(event);
  }

  /** Linear mapping of touch Y to the scrollable range, used by the fast-scroll strip. */
  private void performLinearScrollUpdate(float touchY, int viewHeight, int maxScrollY) {
    if (touchY < 0) touchY = 0;
    if (touchY > viewHeight) touchY = viewHeight;
    float scrollRatio = touchY / (float) viewHeight;
    int targetScrollY = (int) (scrollRatio * maxScrollY);
    scrollTo(getScrollX(), targetScrollY);
    invalidate();
  }

  @Override
  public void computeScroll() {
    super.computeScroll();
    if (scroller.computeScrollOffset()) {
      int y = scroller.getCurrY();
      if (y < 0) y = 0;
      if (y > maxScrollableYCache) y = maxScrollableYCache;
      scrollTo(getScrollX(), y);
      postInvalidateOnAnimation();
    }
  }

  private void recycleTracker() {
    if (velocityTracker != null) {
      velocityTracker.recycle();
      velocityTracker = null;
    }
  }

  // =====================================================================
  // LIFECYCLE
  // =====================================================================

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    recycleTracker();
    if (scroller != null && !scroller.isFinished()) scroller.abortAnimation();
  }
}
