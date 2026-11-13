package net.murat.kenc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.EditText;
import android.widget.ScrollView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;

public class ScenePanel extends SurfaceView implements SurfaceHolder.Callback {

  private Context context;
  private Activity activity;
  private SurfaceHolder holder;
  private Handler uiHandler;
  private ExecutorService engineExecutor;

  private Bitmap bufferBimg;
  private final Canvas bufferG2D = new Canvas();

  private int width = 1;
  private int height = 1;
  private float fwidth = 1.0F;
  private float fheight = 1.0F;
  private float FNISF = 1.0F;

  /* Button width adapted for 4 horizontal slots instead of 3 */
  private float btnWidth4 = 1.0F;
  private float topRowBottom = 1.0F;
  private float startExitLineY = 1.0F;

  private int BG = 0xFFFFFFFF;
  private int FG = 0xFF0000FF;
  private int RG = 0xFF00CC00;
  private int BTN_BG = 0xFFE0E0E0;
  
  private String PATHENC = "";
  
  private final Paint textPaint = new Paint();
  private final Paint progressPaint = new Paint();
  private final Paint bgPaint = new Paint();
  private final Paint borderPaint = new Paint();

  /**
   * Primary constructor to initialize structural layout properties, paints, and modern handler bridges.
   */
  public ScenePanel(Context contxt) {
    super(contxt);
    this.context = contxt;
    this.activity = (Activity) context;
    this.holder = getHolder();
    this.holder.addCallback(this);
    
    PATHENC = getMainPathName();
    
    File pf = new File(PATHENC + "/encdec");
    if (!pf.exists()) {
      pf.mkdir();
    }
	
    this.uiHandler = new Handler(Looper.getMainLooper());
    this.engineExecutor = Executors.newSingleThreadExecutor();

    /* Pull real pixel metrics from the device configuration envelope */
    width = contxt.getResources().getDisplayMetrics().widthPixels;
    height = contxt.getResources().getDisplayMetrics().heightPixels;
    
    fwidth = (float) width;
    fheight = (float) height;
    FNISF = fwidth / Values.DOSF;

    /* Allocate structural grid coordinate spaces for 4 action buttons */
    btnWidth4 = fwidth / (float) Values.CUATRO;
    topRowBottom = fheight * 0.15F; 
    startExitLineY = fheight * 0.65F; 

    bufferBimg = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    bufferG2D.setBitmap(bufferBimg);

    Typeface boldFont = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
    textPaint.setTypeface(boldFont);
    textPaint.setTextSize(Values.TSIZE);
    textPaint.setColor(FG);

    progressPaint.setColor(RG);
    bgPaint.setColor(BTN_BG);
    bgPaint.setStyle(Paint.Style.FILL);
    
    borderPaint.setColor(FG);
    borderPaint.setStyle(Paint.Style.STROKE);
    borderPaint.setStrokeWidth(3.0F);
  }

  /**
   * Enforces immediate surface canvas synchronization and updates the display bitmap layer.
   */
  protected final void drawx() {
    Canvas canvas = null;
    try {
      if (holder == null) return;
      canvas = holder.lockCanvas();
      if (canvas == null) return;

      synchronized (holder) {
        if (holder.getSurface().isValid()) {
          bufferG2D.drawColor(BG);
          render();
          canvas.drawBitmap(bufferBimg, Values.CERO, Values.CERO, null);
        }
      }
    } finally {
      if (canvas != null && holder != null) {
        holder.unlockCanvasAndPost(canvas);
      }
    }
  }

  /**
   * Internal render routine to structure layout alignment blocks and texts onto the back buffer.
   */
  private final void render() {
    /* Generate 4 upper operational layout tiles (PASSW, ENC, DEC, SHOW) */
    for (int i = Values.CERO; i < Values.CUATRO; i++) {
      float left = (float) i * btnWidth4;
      float right = left + btnWidth4;
      bufferG2D.drawRect(left, Values.CEROF, right, topRowBottom, bgPaint);
      bufferG2D.drawRect(left, Values.CEROF, right, topRowBottom, borderPaint);
    }

    /* Print command titles aligned inside cells */
    float textY = topRowBottom / Values.DOSF + textPaint.getTextSize() / Values.DOSF;
    bufferG2D.drawText(Values.BTN_PASSW, btnWidth4 * 0.5F - textPaint.measureText(Values.BTN_PASSW) * 0.5F, textY, textPaint);
    bufferG2D.drawText(Values.BTN_ENC, btnWidth4 * 1.5F - textPaint.measureText(Values.BTN_ENC) * 0.5F, textY, textPaint);
    bufferG2D.drawText(Values.BTN_DEC, btnWidth4 * 2.5F - textPaint.measureText(Values.BTN_DEC) * 0.5F, textY, textPaint);
    bufferG2D.drawText(Values.BTN_SHOW, btnWidth4 * 3.5F - textPaint.measureText(Values.BTN_SHOW) * 0.5F, textY, textPaint);

    /* Print active operating logs feedback label */
    float feedbackY = fheight * 0.35F;
    bufferG2D.drawText(Values.SAVED, FNISF - textPaint.measureText(Values.SAVED) * 0.5F, feedbackY, textPaint);

    /* Track dynamic progress mapping along the horizontal display ray */
    float progTop = fheight * 0.45F;
    float progBottom = fheight * 0.52F;
    bufferG2D.drawRect(Values.CEROF, progTop, Values.RECTRIGHT, progBottom, progressPaint);
    bufferG2D.drawRect(Values.CEROF, progTop, fwidth, progBottom, borderPaint);

    /* Partition lower execution zone boundaries */
    bufferG2D.drawLine(Values.CEROF, startExitLineY, fwidth, startExitLineY, borderPaint);
    
    float operationalHeight = fheight - startExitLineY;
    float startTextY = startExitLineY + (operationalHeight * 0.35F);
    float exitTextY = startExitLineY + (operationalHeight * 0.65F);
    
    bufferG2D.drawText(Values.START, FNISF - textPaint.measureText(Values.START) * 0.5F, startTextY, textPaint);
    bufferG2D.drawText(Values.EXIT, FNISF - textPaint.measureText(Values.EXIT) * 0.5F, exitTextY, textPaint);
  }

  /**
   * Evaluates input coordinates and triggers active workflows matching the selected layout bounds.
   */
  @Override
  public boolean onTouchEvent(MotionEvent evt) {
    if (evt.getAction() != MotionEvent.ACTION_DOWN) return false;

    float x = evt.getX();
    float y = evt.getY();

    /* Check interaction inside action header squares */
    if (y >= Values.CEROF && y <= topRowBottom) {
      if (x >= Values.CEROF && x < btnWidth4) {
        showPasswordDialog();
        drawx(); 
      } else if (x >= btnWidth4 && x < btnWidth4 * Values.DOSF) {
        Values.CURRENT_MODE = Values.ENC;
        Values.SAVED = "MODE: enc";
        drawx();
      } else if (x >= btnWidth4 * Values.DOSF && x < btnWidth4 * (float) Values.TRES) {
        Values.CURRENT_MODE = Values.DEC;
        Values.SAVED = "MODE: dec";
        drawx();
      } else if (x >= btnWidth4 * (float) Values.TRES && x <= fwidth) {
        Values.CURRENT_MODE = Values.SHOW;
        Values.SAVED = "MODE: show (.enc target)";
        drawx();
      }
    } 
    /* Check interaction inside control base selections */
    else if (y >= startExitLineY && y <= fheight) {
      float segmentHeight = (fheight - startExitLineY) / Values.DOSF;
      if (y < startExitLineY + segmentHeight) {
        if (activity instanceof Kenc) {
          ((Kenc) activity).openFilePicker();
        }
      } else {
        if (activity != null) {
          activity.finishAndRemoveTask();
        }
      }
    }
    return true;
  }

  /**
   * Prompts secure text editor dialog box containing current prepass context string.
   */
  private void showPasswordDialog() {
    AlertDialog.Builder alert = new AlertDialog.Builder(context);
    alert.setTitle(Values.ENTERNUM);

    final EditText inputField = new EditText(context);
    inputField.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
    inputField.setTextSize(Values.TEXTSIZE);
    inputField.setTextColor(FG);
    alert.setView(inputField);

    alert.setPositiveButton(Values.OK, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        String inputData = inputField.getText().toString().trim();
        if (inputData.length() != Values.CUATRO) {
          Values.SAVED = Values.CORTOLEN;
          return;
        }
        Values.PASS = Values.PREPASSW + inputData;
        Values.SAVED = "PASS CHANGED!";
      }
    });

    alert.setNegativeButton(Values.CANCEL, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
      }
    });

    alert.create().show();
  }

  /**
   * Safe entry gate to spin up asynchronous worker thread tasks.
   */
  public void startProcessingChain(final ArrayList<Uri> targetUris) {
    Values.RECTRIGHT = Values.CEROF;
    Values.SAVED = "Starting Engine...";
    drawx();

    /* Re-verify and spin up thread execution instance dynamically to guarantee availability */
    if (engineExecutor == null || engineExecutor.isShutdown()) {
      engineExecutor = Executors.newSingleThreadExecutor();
    }

    engineExecutor.execute(new Runnable() {
      @Override
      public void run() {
        processFilesAsync(targetUris);
      }
    });
  }

  /**
   * Core mechanical cryptographic algorithm looping worker routine with progressive byte-level tracking.
   */
  private void processFilesAsync(ArrayList<Uri> uris) {
    int totalFiles = uris.size();

    try {
      byte[] rawKeyBytes = Values.PASS.getBytes(Values.UTF8);
      SecretKeySpec cryptoKeySpec = new SecretKeySpec(rawKeyBytes, Values.ALGORITHM);
      Cipher cryptoCipher = Cipher.getInstance(Values.ALGORITHM);

      boolean processingModeIsEnc = Values.CURRENT_MODE.equals(Values.ENC);
      if (processingModeIsEnc) {
        cryptoCipher.init(Cipher.ENCRYPT_MODE, cryptoKeySpec);
      } else {
        /* Both DEC and SHOW modes use DECRYPT_MODE */
        cryptoCipher.init(Cipher.DECRYPT_MODE, cryptoKeySpec);
      }

      /* STEP 1: Compute total byte payload across all selected targets */
      long totalBatchBytes = 0L;
      ArrayList<File> validFiles = new ArrayList<File>();
      ArrayList<String> resolvedNames = new ArrayList<String>();

      for (int i = Values.CERO; i < totalFiles; i++) {
        Uri currentUri = uris.get(i);
        String path = getRealPathFromUri(currentUri);
        if (path != null && path.trim().length() > Values.CERO) {
          File f = new File(path);
          if (f.exists()) {
            validFiles.add(f);
            resolvedNames.add(getFileName(currentUri));
            totalBatchBytes += f.length();
          }
        }
      }

      if (totalBatchBytes <= 0L) {
        totalBatchBytes = 1L;
      }

      String mainDownloadPath = PATHENC + "/encdec";
      long bytesProcessedSoFar = 0L;
      String lastProducedFileName = "";

      /* STEP 2: Loop targets and process byte streams based on active mode */
      for (int i = Values.CERO; i < validFiles.size(); i++) {
        File srcFile = validFiles.get(i);
        String originalName = resolvedNames.get(i);

        String calculatedName = getOutputFileName(originalName, processingModeIsEnc);
        lastProducedFileName = calculatedName;
        
        float progressPositionX = ((float) bytesProcessedSoFar / (float) totalBatchBytes) * fwidth;
        updateUIFeedback("Processing: " + calculatedName, progressPositionX);

        InputStream sourceStream = new FileInputStream(srcFile);

        /* CORE UPGRADE: Handle immediate in-memory decryption for SHOW mode */
        if (Values.CURRENT_MODE.equals(Values.SHOW)) {
          CipherInputStream cis = new CipherInputStream(sourceStream, cryptoCipher);
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          byte[] bufferMemory = new byte[Values.CSATKB];
          int bytesRead;

          while ((bytesRead = cis.read(bufferMemory, Values.CERO, Values.CSATKB)) != Values.NEGATIVO) {
            baos.write(bufferMemory, Values.CERO, bytesRead);
            bytesProcessedSoFar += bytesRead;
            float currentProgressX = ((float) bytesProcessedSoFar / (float) totalBatchBytes) * fwidth;
            updateUIFeedback("Reading: " + calculatedName, currentProgressX);
          }
          cis.close();
          sourceStream.close();

          final String decodedContent = baos.toString(Values.UTF8);
          uiHandler.post(new Runnable() {
            @Override
            public void run() {
              showDecodedContentDialog(decodedContent);
            }
          });
          
          updateUIFeedback("Show Done: " + calculatedName, Values.CEROF);
          return;
        }

        /* Standard ENC / DEC File Storage Writing Pipeline */
        File targetOutputFile = new File(mainDownloadPath, calculatedName);
        OutputStream destinationStream = new FileOutputStream(targetOutputFile);

        if (processingModeIsEnc) {
          CipherOutputStream cos = new CipherOutputStream(destinationStream, cryptoCipher);
          byte[] bufferMemory = new byte[Values.ATKB];
          int bytesRead;
          int cycleCounter = Values.CERO;

          while ((bytesRead = sourceStream.read(bufferMemory)) != Values.NEGATIVO) {
            cos.write(bufferMemory, Values.CERO, bytesRead);
            cycleCounter++;
            bytesProcessedSoFar += bytesRead;
            float currentProgressX = ((float) bytesProcessedSoFar / (float) totalBatchBytes) * fwidth;
            
            if (cycleCounter % Values.FLUSH_THRESHOLD == Values.CERO) {
              cos.flush();
              updateUIFeedback("Processing: " + calculatedName, currentProgressX);
            }
          }
          cos.flush();
          cos.close();
        } else {
          CipherInputStream cis = new CipherInputStream(sourceStream, cryptoCipher);
          byte[] bufferMemory = new byte[Values.CSATKB];
          int bytesRead;
          int cycleCounter = Values.CERO;

          while ((bytesRead = cis.read(bufferMemory, Values.CERO, Values.CSATKB)) != Values.NEGATIVO) {
            destinationStream.write(bufferMemory, Values.CERO, bytesRead);
            cycleCounter++;
            bytesProcessedSoFar += bytesRead;
            float currentProgressX = ((float) bytesProcessedSoFar / (float) totalBatchBytes) * fwidth;
            
            if (cycleCounter % Values.FLUSH_THRESHOLD == Values.CERO) {
              destinationStream.flush();
              updateUIFeedback("Processing: " + calculatedName, currentProgressX);
            }
          }
          destinationStream.flush();
          cis.close();
        }
        
        destinationStream.close();
        sourceStream.close();
      }

      /* STEP 3: Display the exact produced filename upon successful batch termination */
      updateUIFeedback("Done: " + lastProducedFileName, Values.CEROF);

    } catch (Exception fatalError) {
      updateUIFeedback("Engine Error Occurred.", Values.CEROF);
    }
  }

  /**
   * Prompts scrollable alert view architecture containing decrypted raw content tokens.
   */
  private void showDecodedContentDialog(String content) {
    AlertDialog.Builder alert = new AlertDialog.Builder(context);
    alert.setTitle("Decoded File Content");

    final EditText displayField = new EditText(context);
    displayField.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
    displayField.setTextSize(Values.TEXTSIZE);
    displayField.setTextColor(FG);
    displayField.setText(content);
    
    ScrollView scrollView = new ScrollView(context);
    scrollView.setPadding(16, 16, 16, 16);
    scrollView.addView(displayField);
    alert.setView(scrollView);

    alert.setPositiveButton(Values.OK, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
      }
    });

    alert.create().show();
  }

  protected String getFileName(Uri uri) {
    String result = null;
    if (uri.getScheme().equals("content")) {
      Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
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

  protected String getRealPathFromUri(Uri uri) {
    if (uri == null) return "";
    if ("file".equals(uri.getScheme())) return uri.getPath();
    if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
      String docId = DocumentsContract.getDocumentId(uri);
      if (docId != null && docId.contains(":")) {
        String[] parts = docId.split(":");
        if ("primary".equalsIgnoreCase(parts[Values.CERO])) {
          return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + parts[Values.UNO];
        } else {
          return "/storage/" + parts[Values.CERO] + "/" + parts[Values.UNO];
        }
      }
    }
    return uri.getPath() != null ? uri.getPath() : uri.toString();
  }

  private final String getMainPathName() {
    File path = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
    String px = path.getAbsolutePath();
    String exclude = "Android/data/" + (context.getPackageName()) + "/files/";
    return px.replace(exclude, "");
  }

  private String getOutputFileName(String originalName, boolean modeIsEnc) {
    originalName = originalName.toLowerCase(Locale.US);
    if (modeIsEnc) {
      return originalName + Values.ALTENC;
    } else {
      return originalName.replace(Values.ALTENC, "");
    }
  }

  private void updateUIFeedback(final String message, final float dynamicProgressWidth) {
    uiHandler.post(new Runnable() {
      @Override
      public void run() {
        Values.SAVED = message;
        Values.RECTRIGHT = dynamicProgressWidth;
        drawx();
      }
    });
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    if (engineExecutor == null || engineExecutor.isShutdown()) {
      engineExecutor = Executors.newSingleThreadExecutor();
    }
    drawx();
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    if (engineExecutor != null) {
      try {
        engineExecutor.shutdownNow();
      } catch (Throwable t) {
        // Suppress leakage anomalies
      }
    }
  }
  
}
