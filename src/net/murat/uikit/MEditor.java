package net.murat.uikit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.OverScroller;
import android.widget.TextView;

/**
 * MEditor — read-only scrollable text viewer with a custom scrollbar.
 * Also supports a simple find-highlight range painted behind the text.
 */
public class MEditor extends TextView {
  
  private final int barW;
  private final int barM;
  private final int minThumbPx;
  private final int thumbSlopPx;
  
  private final Paint barPaint;
  private final Paint trackPaint;
  private final Paint highlightPaint;
  
  private int scrollY = 0;
  private int maxScrollY = 0;
  
  private int pendingCaret = -1;
  private int pendingScroll = -1;
  
  private boolean draggingThumb = false;
  private float thumbGrabOffset = 0f;
  
  private boolean draggingContent = false;
  private float lastTouchY = 0f;
  
  private OverScroller scroller;
  private VelocityTracker velocityTracker;
  private int minFlingVelocity;
  private int maxFlingVelocity;
  private int touchSlop;
  
  private int highlightStart = -1;
  private int highlightEnd = -1;
  
  public MEditor(Context c) { this(c, null); }
  public MEditor(Context c, AttributeSet a) { this(c, a, 0); }
  
  public MEditor(Context c, AttributeSet a, int d) {
    super(c, a, d);
    
    setShowSoftInputOnFocus(false);
    
    float density = getResources().getDisplayMetrics().density;
    barW = (int) (4 * density);
    barM = (int) (6 * density);
    minThumbPx = (int) (24 * density);
    thumbSlopPx = (int) (24 * density);
    
    barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    barPaint.setColor(0xCC3333DC);
    
    trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    trackPaint.setColor(0x22000000);
    
    highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    highlightPaint.setColor(0x66FFEB3B);
    
    setVerticalScrollBarEnabled(false);
    setHorizontallyScrolling(false);
    setClickable(true);
    setLongClickable(true);
    setFocusable(true);
    setFocusableInTouchMode(false);
    
    scroller = new OverScroller(c);
    ViewConfiguration vc = ViewConfiguration.get(c);
    minFlingVelocity = vc.getScaledMinimumFlingVelocity();
    maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
    touchSlop = vc.getScaledTouchSlop();
  }
  
  public void setEditorText(String text) {
    highlightStart = -1;
    highlightEnd = -1;
    setText(text == null ? "" : text);
  }
  
  public String getFullText() {
    return getText() == null ? "" : getText().toString();
  }
  
  public int getEditorScrollY() {
    return scrollY;
  }
  
  public void setEditorScrollY(int y) {
    if (scroller != null && !scroller.isFinished()) scroller.abortAnimation();
    if (getLayout() == null) return;
    recomputeMax();
    if (y < 0) y = 0;
    if (y > maxScrollY) y = maxScrollY;
    scrollY = y;
    invalidate();
  }
  
  /**
   * Sets the caret position. Clamped to the current text length.
   * Uses Selection.setSelection() instead of TextView.setSelection()
   * because the latter is not part of the public SDK stubs.
   */
  public void setCaretPos(int pos) {
    CharSequence text = getText();
    if (text instanceof Spannable) {
      int len = text.length();
      if (pos < 0) pos = 0;
      if (pos > len) pos = len;
      Selection.setSelection((Spannable) text, pos);
    }
  }
  
  /**
   * Applies a line spacing multiplier (extra spacing is fixed at 0).
   * The new line height changes the layout metrics, so we recompute the
   * maximum scroll distance and repaint on the next frame — this keeps the
   * scrollbar thumb and scroll bounds consistent with the new geometry.
   */
  public void setEditorLineSpacing(final float multiplier) {
    if (multiplier <= 0f) return;
    setLineSpacing(0f, multiplier);
    post(new Runnable() {
        @Override public void run() {
          recomputeMax();
          invalidate();
        }
    });
  }
  
  public void setEditorHighlight(int start, int end) {
    if (start < 0) start = 0;
    if (end < start) end = start;
    int len = getFullText().length();
    if (start > len) start = len;
    if (end > len) end = len;
    highlightStart = start;
    highlightEnd = end;
    invalidate();
  }
  
  public void clearEditorHighlight() {
    highlightStart = -1;
    highlightEnd = -1;
    invalidate();
  }
  
  @Override
  public int getLineCount() {
    CharSequence t = getText();
    if (t == null || t.length() == 0) return 0;
    String s = t.toString();
    int n = 1, i = 0;
    while ((i = s.indexOf('\n', i)) != -1) { n++; i++; }
      return n;
  }
  
  public int getCaretLine() {
    CharSequence t = getText();
    int len = t == null ? 0 : t.length();
    if (len < 1) return 1;
    int pos = getSelectionStart();
    if (pos < 0) return 1;
    int line = 1;
    for (int i = 0; i < len; i++) {
      if (i < pos && t.charAt(i) == '\n') line++;
    }
    return line;
  }
  
  public void setExternalReadOnly(boolean ro) {
    setTextIsSelectable(ro);
  }
  
  public void resetFirstTapGuard() { scrollToTop(); }
  
  public void scrollToTop() {
    if (scroller != null && !scroller.isFinished()) scroller.abortAnimation();
    scrollY = 0;
    invalidate();
  }
  
  public void scrollToBottom() {
    if (scroller != null && !scroller.isFinished()) scroller.abortAnimation();
    recomputeMax();
    scrollY = maxScrollY;
    invalidate();
  }
  
  private void recomputeMax() {
    Layout l = getLayout();
    if (l == null) return;
    int content = l.getHeight() + getPaddingTop() + getPaddingBottom();
    int visible = getHeight() - getPaddingTop() - getPaddingBottom();
    maxScrollY = Math.max(0, content - visible);
    if (scrollY > maxScrollY) scrollY = maxScrollY;
    if (scrollY < 0) scrollY = 0;
  }
  
  @Override
  protected void onSizeChanged(int w, int h, int ow, int oh) {
    super.onSizeChanged(w, h, ow, oh);
    recomputeMax();
  }
  
  /**
  @Override
  public void setText(CharSequence text, BufferType type) {
  super.setText(text, type);
  post(new Runnable() {
  @Override public void run() {
  recomputeMax();
  scrollY = 0;
  invalidate();
  }
  });
  }
   */
  
  @Override
  public void setText(CharSequence text, BufferType type) {
    pendingCaret = -1;
    pendingScroll = -1;
    super.setText(text, BufferType.SPANNABLE);
    post(new Runnable() {
        @Override public void run() {
          recomputeMax();
          invalidate();
        }
    });
  }
  
  /**
   * Requests a caret and scroll restore that will be applied after the
   * next layout pass. Safe to call immediately after setText(), because
   * the layout is not ready yet at that point.
   */
  public void requestCaretAndScrollRestore(int caret, int scroll) {
    pendingCaret = caret;
    pendingScroll = scroll;
    post(new Runnable() {
        @Override public void run() {
          applyPendingCaretAndScroll();
        }
    });
  }
  
  private void applyPendingCaretAndScroll() {
    if (pendingCaret < 0 && pendingScroll < 0) return;
    if (getLayout() == null) {
      // Layout still not ready; retry on the next frame.
      post(new Runnable() {
          @Override public void run() { applyPendingCaretAndScroll(); }
      });
      return;
    }
    int len = getFullText().length();
    if (pendingCaret >= 0) {
      int safe = Math.min(Math.max(pendingCaret, 0), len);
      setCaretPos(safe);
    }
    if (pendingScroll >= 0) {
      setEditorScrollY(pendingScroll);
    }
    pendingCaret = -1;
    pendingScroll = -1;
  }
  
  private int thumbHeight() {
    int vh = getHeight();
    Layout l = getLayout();
    if (l == null || vh <= 0) return 0;
    int content = l.getHeight() + getPaddingTop() + getPaddingBottom();
    if (content <= 0) return 0;
    int th = (int) ((float) vh / content * vh);
    return Math.min(Math.max(th, minThumbPx), vh);
  }
  
  private int thumbTop() {
    if (maxScrollY <= 0) return 0;
    int th = thumbHeight();
    int range = getHeight() - th;
    if (range <= 0) return 0;
    float r = (float) scrollY / maxScrollY;
    if (r < 0f) r = 0f;
    if (r > 1f) r = 1f;
    return (int) (r * range);
  }
  
  @Override
  protected void onDraw(Canvas canvas) {
    Layout layout = getLayout();
    if (layout == null) return;
    
    layout.getPaint().setColor(getCurrentTextColor());
    
    int vw = getWidth();
    int vh = getHeight();
    int pt = getPaddingTop();
    int pl = getPaddingLeft();
    
    int save = canvas.save();
    canvas.clipRect(0, 0, vw, vh);
    canvas.translate(pl, pt - scrollY);
    
    if (highlightStart >= 0 && highlightEnd > highlightStart) {
      drawHighlight(canvas, layout, highlightStart, highlightEnd);
    }
    
    layout.draw(canvas);
    canvas.restoreToCount(save);
    
    if (maxScrollY > 0) {
      int x = vw - barM - barW;
      int th = thumbHeight();
      int ty = thumbTop();
      canvas.drawRoundRect(x, 0, x + barW, vh, barW / 2f, barW / 2f, trackPaint);
      canvas.drawRoundRect(x, ty, x + barW, ty + th, barW / 2f, barW / 2f, barPaint);
    }
  }
  
  private void drawHighlight(Canvas canvas, Layout layout, int start, int end) {
    try {
      int startLine = layout.getLineForOffset(start);
      int endLine = layout.getLineForOffset(end);
      for (int line = startLine; line <= endLine; line++) {
        float left = layout.getLineLeft(line);
        float right = layout.getLineRight(line);
        float top = layout.getLineTop(line);
        float bottom = layout.getLineBottom(line);
        if (line == startLine && line == endLine) {
          left = layout.getPrimaryHorizontal(start);
          right = layout.getPrimaryHorizontal(end);
          if (right < left) { float tmp = left; left = right; right = tmp; }
            } else if (line == startLine) {
          left = layout.getPrimaryHorizontal(start);
          } else if (line == endLine) {
          right = layout.getPrimaryHorizontal(end);
        }
        canvas.drawRect(left, top, right, bottom, highlightPaint);
      }
    } catch (Exception ignored) {}
  }
  
  @Override
  public boolean onTouchEvent(MotionEvent e) {
    recomputeMax();
    if (maxScrollY <= 0) return super.onTouchEvent(e);
    
    if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
    velocityTracker.addMovement(e);
    
    float x = e.getX();
    float y = e.getY();
    int vw = getWidth();
    int vh = getHeight();
    
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        if (!scroller.isFinished()) scroller.abortAnimation();
        int th = thumbHeight();
        int ty = thumbTop();
        int xLeft = vw - barM - barW - thumbSlopPx;
        int xRight = vw - barM + thumbSlopPx;
        if (x >= xLeft && x <= xRight
          && y >= ty - thumbSlopPx && y <= ty + th + thumbSlopPx) {
          draggingThumb = true;
          draggingContent = false;
          thumbGrabOffset = y - ty;
          return true;
        }
        draggingThumb = false;
        draggingContent = true;
        lastTouchY = y;
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (draggingThumb) {
          int th = thumbHeight();
          int range = vh - th;
          if (range <= 0) return true;
          float newTop = y - thumbGrabOffset;
          if (newTop < 0) newTop = 0;
          if (newTop > range) newTop = range;
          int target = (int) (newTop / range * maxScrollY);
          if (target < 0) target = 0;
          if (target > maxScrollY) target = maxScrollY;
          scrollY = target;
          invalidate();
          return true;
        }
        if (draggingContent) {
          float dy = lastTouchY - y;
          lastTouchY = y;
          int newScroll = scrollY + (int) dy;
          if (newScroll < 0) newScroll = 0;
          if (newScroll > maxScrollY) newScroll = maxScrollY;
          scrollY = newScroll;
          invalidate();
          return true;
        }
        return true;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        if (draggingThumb) {
          draggingThumb = false;
          recycleTracker();
          return true;
        }
        if (draggingContent) {
          draggingContent = false;
          velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
          int vy = (int) velocityTracker.getYVelocity();
          recycleTracker();
          if (Math.abs(vy) > minFlingVelocity) {
            scroller.fling(0, scrollY, 0, -vy, 0, 0, 0, maxScrollY);
            postInvalidateOnAnimation();
          }
          return true;
        }
        recycleTracker();
        return true;
      }
    }
    return true;
  }
  
  private void recycleTracker() {
    if (velocityTracker != null) {
      velocityTracker.recycle();
      velocityTracker = null;
    }
  }
  
  @Override
  public void computeScroll() {
    super.computeScroll();
    if (scroller.computeScrollOffset()) {
      int y = scroller.getCurrY();
      if (y < 0) y = 0;
      if (y > maxScrollY) y = maxScrollY;
      scrollY = y;
      postInvalidateOnAnimation();
    }
  }
  
  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    recycleTracker();
    if (scroller != null && !scroller.isFinished()) scroller.abortAnimation();
  }
  
}
