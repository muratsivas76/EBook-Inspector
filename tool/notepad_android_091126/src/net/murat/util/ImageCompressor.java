package net.murat.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImageCompressor {

  // Private constructor to prevent instantiation
  private ImageCompressor() {
    // This utility class should not be instantiated
  }

  /**
  * Compresses a given image file and saves it in the same directory with a custom suffix.
  * Maps perfectly to Java 6-8 and Android API 29 environments.
  *
  * @param sourceFile The original image file (jpg, jpeg, png)
  * @param compressionRatio A float between 0.0f and 1.0f (e.g., 0.5f means 50% quality)
  * @return Absolute path of the compressed file, or empty string if it fails
  */
  public static String compress(File sourceFile, float compressionRatio) {
    if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
      return "";
    }

    // Clamp quality factor between 0.0 and 1.0 safely
    float ratio = compressionRatio;
    if (ratio < 0.0f) ratio = 0.0f;
    if (ratio > 1.0f) ratio = 1.0f;

    // Convert float ratio into 0-100 quality range required by Android engine
    int quality = (int) (ratio * 100);

    String originalPath = sourceFile.getAbsolutePath();
    int dotIndex = originalPath.lastIndexOf('.');
    if (dotIndex == -1) {
      return "";
    }

    // Build the target path dynamically (e.g., abc.png -> abc_compressed.jpg)
    // We enforce .jpg extension to guarantee size compression even for raw PNG inputs
    String newPath = originalPath.substring(0, dotIndex) + "_compressed.jpg";
    File targetFile = new File(newPath);

    Bitmap bitmap = null;
    FileOutputStream fos = null;

    try {
      // Decode the physical disk file directly into memory allocation buffer
      bitmap = BitmapFactory.decodeFile(originalPath);
      if (bitmap == null) {
        return "";
      }

      fos = new FileOutputStream(targetFile);

      // Execute standard native compression using JPEG algorithm to shrink heavy file byte footprint
      // Android engine automatically discards unnoticeable pixel metadata based on the quality index
      boolean success = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos);

      if (success) {
        return targetFile.getAbsolutePath();
      }
    } catch (IOException e) {
      // Safe catch block to suppress local storage and write layout block failures
    } finally {
      // Strict memory recycling structure to avoid devastating OutOfMemoryErrors on API 29
      if (bitmap != null) {
        bitmap.recycle();
      }
      if (fos != null) {
        try {
          fos.close();
        } catch (IOException e) {
          // Ignore exception on stream close pass
        }
      }
    }

    return "";
  }

}
