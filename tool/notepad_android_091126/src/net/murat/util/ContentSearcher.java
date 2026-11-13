package net.murat.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class ContentSearcher {

  // Corresponding file extensions mapped 1:1 with the MIME_TYPES array above
  public static final String[] MIME_EXTENSIONS = {
    // General text and documentation
    ".txt", ".md", ".note", ".text", ".csv", ".tsv",
    // Java, Kotlin, and Android environment
    ".java", ".kt", ".kts", ".gradle", ".xml", ".properties", ".groovy",
    // C, C++, C# and Microsoft eco-system
    ".c", ".cpp", ".h", ".hpp", ".cs", ".csx", ".config",
    // Web technologies and modern scripts
    ".html", ".htm", ".css", ".js", ".mjs", ".ts", ".jsx", ".tsx", ".json", ".xml",
    ".xhtml", ".php", ".phtml", ".php4", ".svg", ".style", ".sass", ".scss",
    // Multi-purpose programming languages
    ".py", ".pyw", ".rb", ".go", ".rs", ".swift", ".dart", ".lua", ".r", ".pl", ".pm", ".sh",
    // Configuration, environment, and system scripting
    ".env", ".ini", ".conf", ".yaml", ".yml", ".log", ".bak", ".bat", ".cmd", ".ps1",
    // Database queries and developer utilities
    ".sql", ".db", ".sqlitedb", ".diff", ".patch"
  };

  // Private constructor to prevent instantiation
  private ContentSearcher() {
    // This utility class should not be instantiated
  }

  /**
  * Searches for a specific term inside valid MIME files or directories recursively.
  * Compatible with Java 6-8 and Android API 29.
  *
  * @param fileOrDir The file or root directory to search within
  * @param searchTerm The term to search for inside the file content (case-sensitive)
  * @return A newline-separated list of matches in format: "absolutePath: lineNo: lineContent"
  */
  public static String result(File fileOrDir, String searchTerm) {
    if (fileOrDir == null || !fileOrDir.exists() || searchTerm == null || searchTerm.isEmpty()) {
      return "";
    }

    StringBuilder resultsBuilder = new StringBuilder();

    if (fileOrDir.isFile()) {
      // Process single file if its type is supported
      if (isMimeTypeSupported(fileOrDir.getName())) {
        searchInFile(fileOrDir, searchTerm, resultsBuilder);
      }
    } else if (fileOrDir.isDirectory()) {
      // Initiate recursive search for directory
      searchDirectoryRecursive(fileOrDir, searchTerm, resultsBuilder);
    }

    return resultsBuilder.toString();
  }

  /**
  * Recursively traverses directories to find matching files and scans their content.
  */
  private static void searchDirectoryRecursive(File currentDir, String searchTerm, StringBuilder sb) {
    File[] files = currentDir.listFiles();
    if (files == null) {
      return; // Handle restricted access directories safely
    }

    for (int i = 0; i < files.length; i++) {
      File file = files[i];

      if (file.isDirectory()) {
        searchDirectoryRecursive(file, searchTerm, sb);
      } else if (file.isFile()) {
        if (isMimeTypeSupported(file.getName())) {
          searchInFile(file, searchTerm, sb);
        }
      }
    }
  }

  /**
  * Reads a single file line by line to discover occurrences of the search term.
  * Uses classic BufferedReader for broad Java 6-8/Android compatibility.
  */
  private static void searchInFile(File file, String searchTerm, StringBuilder sb) {
    BufferedReader br = null;
    try {
      br = new BufferedReader(new FileReader(file));
      String line;
      int lineNumber = 1;

      while ((line = br.readLine()) != null) {
        if (line.contains(searchTerm)) {
          sb.append(file.getAbsolutePath())
          .append(": ")
          .append(lineNumber)
          .append(": ")
          .append(line.trim())
          .append("\n\n");
        }
        lineNumber++;
      }
    } catch (IOException e) {
      // Ignore individual file reading errors (e.g., locked file, corrupted sectors)
    } finally {
      if (br != null) {
        try {
          br.close();
        } catch (IOException e) {
          // Ignore exception on close
        }
      }
    }
  }

  /**
  * Validates if a file extension matches any index within the supported list.
  */
  private static boolean isMimeTypeSupported(String fileName) {
    String name = fileName.toLowerCase();
    for (int i = 0; i < MIME_EXTENSIONS.length; i++) {
      if (name.endsWith(MIME_EXTENSIONS[i])) {
        return true;
      }
    }
    return false;
  }

}
