package net.murat.uikit;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.OverScroller;

/**
 * Multi-line editor with: - a fast-scroll strip on the right edge (linear mapping of touch Y to
 * scroll position), - drag scrolling with fling, implemented on top of the native EditText scroll,
 * - a "first tap" focus guard so the keyboard does not pop up unexpectedly, - an external read-only
 * mode.
 *
 * <p>Touch behavior (normal EditText feel, but robust against finger drift): - Short tap: the caret
 * is placed at the touched position. - Press and hold (finger stays within touch slop): the word
 * under the finger is selected; dragging afterwards extends the selection (handled by EditText). -
 * Finger moves beyond touch slop before the long-press fires: the pending long-press is cancelled,
 * so no word gets selected; only scrolling/fling happens.
 */
public class MEditor extends EditText {

  private boolean isFastScrolling = false;
  private int fastScrollWidthThreshold = 0;
  private int maxScrollableYCache = 0;
  private boolean pendingFirstTap = true;
  private boolean externalReadOnlyMode = false;

  // Last applied background and text colors, mirrored so they can be persisted on exit.
  private int persistedBgColor = Color.WHITE;
  private int persistedFgColor = Color.BLACK;

  // Fling support on top of the native EditText scroll.
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

  // When true, setText() will preserve the caret/selection across the
  // content swap (clamped to the new length). Defaults to true so that
  // in-place edits (encode/decode, replace, case transforms, etc.) keep
  // the user roughly where they were. setEditorText() sets this to false
  // internally unless the caller opts in.
  private boolean preserveCaretOnSetText = true;

  // True once the native long-press fired for the current gesture (a word is
  // being selected). From that point on, all touch events belong to EditText.
  private boolean longPressFired = false;

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

    setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
    setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
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

  public void resetFirstTapGuard() {
    pendingFirstTap = true;
    if (!externalReadOnlyMode) {
      setFocusable(false);
      setFocusableInTouchMode(false);
    }
  }

  /**
   * Sets the background color and remembers it so it can be persisted on exit. The remembered value
   * is returned by {@link #getPersistedBackgroundColor()}.
   */
  @Override
  public void setBackgroundColor(int color) {
    super.setBackgroundColor(color);
    persistedBgColor = color;
  }

  /**
   * Sets the text color and remembers it so it can be persisted on exit. The remembered value is
   * returned by {@link #getPersistedTextColor()}.
   */
  @Override
  public void setTextColor(int color) {
    super.setTextColor(color);
    persistedFgColor = color;
  }

  /** Returns the last background color applied via {@link #setBackgroundColor(int)}. */
  public int getPersistedBackgroundColor() {
    return persistedBgColor;
  }

  /** Returns the last text color applied via {@link #setTextColor(int)}. */
  public int getPersistedTextColor() {
    return persistedFgColor;
  }

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
          velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
          int vy = (int) velocityTracker.getYVelocity();
          recycleTracker();
          if (Math.abs(vy) > minFlingVelocity) {
            scroller.fling(
                getScrollX(), getScrollY(), 0, -vy, 0, 0, 0, Math.max(0, maxScrollableYCache));
            postInvalidateOnAnimation();
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

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    recycleTracker();
    if (scroller != null && !scroller.isFinished()) scroller.abortAnimation();
  }

  /**
   * Replaces the whole editor content and moves the caret to the start.
   *
   * <p>This is the "fresh document" variant used by file-open flows and anywhere the caller
   * explicitly wants the caret at 0. For in-place edits that should keep the user near where they
   * were, call {@link #setEditorText(String, boolean)} with {@code preserveCaret=true}.
   */
  public void setEditorText(String fullText) {
    setEditorText(fullText, false);
  }

  /**
   * Replaces the whole editor content.
   *
   * <p>When {@code preserveCaret} is true, the caret is kept near its previous position (clamped to
   * the new length). When false, the caret is moved to the start — matching the historical behavior
   * of {@link #setEditorText(String)}.
   *
   * <p>Both variants reset the first-tap focus guard so the next tap behaves as the "first tap" for
   * the new content.
   */
  public void setEditorText(String fullText, boolean preserveCaret) {
    // Route through setText() with the preserve flag toggled appropriately.
    // setText() reads this flag, so we set it, call super-class path via
    // setText(), then restore it to the default.
    final boolean previousFlag = preserveCaretOnSetText;
    preserveCaretOnSetText = preserveCaret;
    try {
      if (fullText == null || fullText.isEmpty()) {
        setText("", android.widget.TextView.BufferType.EDITABLE);
      } else {
        setText(fullText, android.widget.TextView.BufferType.EDITABLE);
      }
    } finally {
      preserveCaretOnSetText = previousFlag;
    }

    // When the caller asked for the historical "caret at 0" behavior,
    // enforce it explicitly: setText() may have run with preserve=false
    // already, but this also covers any future refactor of setText().
    if (!preserveCaret) {
      setSelection(0);
    }

    if (!externalReadOnlyMode) {
      resetFirstTapGuard();
    }
  }

  /**
   * Overrides EditText's content setter to preserve the caret position (and collapsed selection
   * anchor) across content swaps.
   *
   * <p>Behavior:
   *
   * <ul>
   *   <li>When {@link #preserveCaretOnSetText} is false, behaves exactly like the superclass (caret
   *       ends up wherever the framework puts it, typically 0 for fresh content).
   *   <li>When true, the current selection start is remembered, the content is swapped, and then
   *       the caret is restored to {@code min(savedPos, newLength)}. Any active selection is
   *       collapsed to the caret — we do not attempt to restore the anchor because the two ends may
   *       no longer correspond to the same text after a transformation.
   * </ul>
   *
   * <p>Must run on the main thread (all EditText mutations do).
   */
  @Override
  public void setText(CharSequence text, BufferType type) {
    if (!preserveCaretOnSetText) {
      super.setText(text, type);
      return;
    }

    // Capture the current caret position before the swap. getSelectionStart()
    // returns -1 when the widget has never had a selection; clamp to 0.
    int savedPos = getSelectionStart();
    if (savedPos < 0) savedPos = 0;

    super.setText(text, type);

    // Clamp against the new content length. EditText may internally set the
    // caret to 0 or to the end depending on BufferType; we override that.
    final CharSequence newText = getText();
    final int newLen = (newText != null) ? newText.length() : 0;
    final int target = Math.min(savedPos, newLen);

    // setSelection also collapses any surviving selection to a single caret.
    setSelection(target);
  }

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
    final int len = textSeq.length();

    if (len < 1) return 1;

    int caretPosition = getSelectionStart();
    int caretLine = 1;

    char c = 'a';
    final char NLN = '\n';

    for (int i = 0; i < len; i++) {
      c = textSeq.charAt(i);
      if (i < caretPosition && c == NLN) {
        caretLine++;
      }
    }

    return caretLine;
  }

  public String getFullText() {
    return (getText() != null) ? getText().toString() : "";
  }
}
