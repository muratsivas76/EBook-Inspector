package net.murat.util;

import java.io.File;

public class FileSearcher {

  // Private constructor to prevent instantiation
  private FileSearcher() {
    // This utility class should not be instantiated
  }

  /**
  * Searches for files containing the given fileName string within the specified directory
  * and its subdirectories (case-sensitive).
  *
  * @param dir The root directory to start the search from
  * @param fileName The substring to look for in file names (case-sensitive)
  * @return A newline-separated String of matching file names, or empty string if invalid
  */
  public static String result(File dir, String fileName) {
    // Validate input parameters and ensure dir is a valid directory
    if (dir == null || !dir.isDirectory() || fileName == null || fileName.isEmpty()) {
      return "";
    }

    StringBuilder resultsBuilder = new StringBuilder();
    searchDirectory(dir, fileName, resultsBuilder);

    return resultsBuilder.toString();
  }

  /**
  * Recursively traverses directories to find matching file names.
  * Uses anonymous/standard loop structure compatible with Java 6-8.
  *
  * @param currentDir The current directory being traversed
  * @param query The filename substring to search for
  * @param sb The StringBuilder collecting the results
  */
  private static void searchDirectory(File currentDir, String query, StringBuilder sb) {
    File[] files = currentDir.listFiles();

    // Handle cases where read permissions are denied or I/O error occurs
    if (files == null) {
      return;
    }

    for (int i = 0; i < files.length; i++) {
      File file = files[i];

      if (file.isDirectory()) {
        // Recursive call for subdirectories
        searchDirectory(file, query, sb);
      } else {
        // Case-sensitive check if the file name contains the query string
        if (file.getName().contains(query)) {
          sb.append(file.getAbsolutePath()).append("\n\n");
        }
      }
    }
  }

}
