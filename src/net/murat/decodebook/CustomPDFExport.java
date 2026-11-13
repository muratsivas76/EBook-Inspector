package net.murat.decodebook;

import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Lightweight PDF writer for Android Spanned content.
 *
 * <p>Uses the platform android.graphics.pdf.PdfDocument and StaticLayout. No third-party
 * dependencies. The Spanned is drawn directly, so ForegroundColorSpan, StyleSpan (bold/italic),
 * UnderlineSpan, StrikethroughSpan, SuperscriptSpan, SubscriptSpan, RelativeSizeSpan,
 * AbsoluteSizeSpan and TypefaceSpan are all honoured by the PDF renderer.
 *
 * <p>Page size is A4 (595 x 842 points) with a 40 pt margin on all sides. Long content is split
 * across pages automatically by re-slicing the Spanned at the last character that fits on each
 * page.
 *
 * @author Murat
 */
public final class CustomPDFExport {

  private static final int PAGE_WIDTH = 595;
  private static final int PAGE_HEIGHT = 842;
  private static final int MARGIN = 40;
  private static final int CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2);
  private static final int CONTENT_HEIGHT = PAGE_HEIGHT - (MARGIN * 2);

  private CustomPDFExport() {
    // no instances
  }

  /**
   * Writes the given Spanned as a PDF document to the given stream. The stream is flushed but not
   * closed; the caller owns it.
   */
  public static void writeTo(OutputStream out, Spanned text) throws IOException {
    if (out == null) throw new IOException("Output stream is null");
    if (text == null) text = new SpannableString("");

    PdfDocument doc = new PdfDocument();
    try {
      TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
      paint.setTextSize(12f);
      paint.setColor(0xFF000000);

      int remainingStart = 0;
      int pageNumber = 1;
      int totalLen = text.length();

      while (remainingStart < totalLen || pageNumber == 1) {
        // Build a layout for the remaining text at the full content width.
        // It may exceed CONTENT_HEIGHT; we will find the last line that fits.
        Spanned remaining = (Spanned) text.subSequence(remainingStart, totalLen);
        StaticLayout layout =
            StaticLayout.Builder.obtain(remaining, 0, remaining.length(), paint, CONTENT_WIDTH)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.0f)
                .setIncludePad(false)
                .build();

        // Find the last line whose bottom fits within CONTENT_HEIGHT.
        int lastLine = layout.getLineCount() - 1;
        while (lastLine > 0 && layout.getLineBottom(lastLine) > CONTENT_HEIGHT) {
          lastLine--;
        }
        // If even the first line does not fit, force at least one line to avoid an infinite loop.
        if (lastLine < 0) lastLine = 0;

        int endOffset = layout.getLineEnd(lastLine);
        // Safety: ensure we advance at least one character.
        if (endOffset <= 0) endOffset = Math.min(1, remaining.length());

        // Draw this page.
        PdfDocument.PageInfo info =
            new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
        PdfDocument.Page page = doc.startPage(info);
        Canvas canvas = page.getCanvas();
        canvas.save();
        canvas.translate(MARGIN, MARGIN);
        // Clip to the bottom of the last line that actually fits, so no partial
        // next-line ascender/descender leaks into the page.
        float clipBottom = layout.getLineBottom(lastLine);
        if (clipBottom > CONTENT_HEIGHT) clipBottom = CONTENT_HEIGHT;
        canvas.clipRect(0, 0, CONTENT_WIDTH, clipBottom);
        layout.draw(canvas);
        canvas.restore();
        doc.finishPage(page);

        remainingStart += endOffset;
        pageNumber++;

        // Guard against pathological input (e.g. one huge unbreakable line).
        if (pageNumber > 10000) break;
      }

      doc.writeTo(out);
      out.flush();
    } finally {
      doc.close();
    }
  }
}
