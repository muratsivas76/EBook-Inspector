package net.murat.kenc;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.ClipData;

import java.util.ArrayList;

public class Kenc extends Activity {

  private Handler mainHandler;
  private ScenePanel panel;
  public static final int FILE_PICKER_REQUEST_CODE = 1001;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    panel = new ScenePanel(this);
    setContentView(panel);
    
    mainHandler = new Handler(Looper.getMainLooper());
  }

  @Override
  protected void onStop() {
    super.onStop();
  }

  @Override
  protected void onResume() {
    super.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    
    mainHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
      }
    }, 250);
  }

  /* Method to launch modern SAF multi-file picker based on active mode filters */
  public void openFilePicker() {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    
    /* Apply filter dynamically based on our current operational mode */
    if (Values.CURRENT_MODE.equals(Values.ENC)) {
      intent.setType("*/*"); /* Show all files for encryption */
    } else {
      /* Restrict to octet-stream for both DEC and SHOW modes (.enc targets) */
      intent.setType("application/octet-stream"); 
    }
    
    startActivityForResult(intent, FILE_PICKER_REQUEST_CODE);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    
    if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
      ArrayList<Uri> uriList = new ArrayList<Uri>();
      
      /* Process multiple item selections via ClipData structure */
      if (data.getClipData() != null) {
        ClipData clipData = data.getClipData();
        int count = clipData.getItemCount();
        for (int i = 0; i < count; i++) {
          Uri uri = clipData.getItemAt(i).getUri();
          if (uri != null) {
            uriList.add(uri);
          }
        }
      } 
      /* Process single item selection via standard Intent Data fallback */
      else if (data.getData() != null) {
        uriList.add(data.getData());
      }
      
      /* Forward the gathered target URIs directly to the interface panel execution chain */
      if (!uriList.isEmpty() && panel != null) {
        panel.startProcessingChain(uriList);
      }
    }
  }
  
}
