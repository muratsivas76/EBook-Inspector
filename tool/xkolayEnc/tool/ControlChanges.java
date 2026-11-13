import java.io.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ControlChanges: Optimized for lightweight tracking.
 * File modification times are displayed on console only, not stored in the tracker.
 */
public class ControlChanges {
  
  private static final String TRACK_FILE = "changes.txt";
  private static final String CLASS_NAME = "ControlChanges";
  private static final long MAX_SIZE = 5 * 1024 * 1024; // 5 MB Limit
  private static final String[] IGNORE_EXT = {".class", ".obj", ".o", ".exe", ".out", ".bin"};
  private static final SimpleDateFormat SDF = new SimpleDateFormat("dd MMMM yyyy HH:mm:ss", java.util.Locale.ENGLISH);
  
  public static void main(String[] args) {
    try {
      File currentDir = new File(".");
      String baseDir = currentDir.getCanonicalPath();
      File tracker = new File(TRACK_FILE);
      Map<String, String> currentMap = new HashMap<String, String>();
      
      scan(currentDir, currentMap, baseDir);
      
      if (!tracker.exists()) {
        System.out.println("First run: Initializing index...");
        save(tracker, currentMap);
        System.out.println("Done. " + currentMap.size() + " files indexed.");
        } else {
        Map<String, String> oldMap = load(tracker);
        compareAndReport(oldMap, currentMap, tracker, baseDir);
      }
      } catch (IOException e) {
      System.err.println("Error: " + e.getMessage());
    }
  }
  
  private static void scan(File dir, Map<String, String> map, String base) {
    File[] list = dir.listFiles();
    if (list == null) return;
    
    for (int i = 0; i < list.length; i++) {
      File f = list[i];
      if (f.isDirectory()) {
        scan(f, map, base);
        } else {
        try {
          String name = f.getName();
          if (isIgnored(name)) continue;
          if (f.length() > MAX_SIZE) continue;
          
          String fullPath = f.getCanonicalPath();
          String relPath = fullPath.substring(base.length());
          if (relPath.startsWith(File.separator)) relPath = relPath.substring(1);
          
          map.put(relPath, calculateMD5(f));
        } catch (IOException e) {}
      }
    }
  }
  
  private static boolean isIgnored(String name) {
    if (name.equals(TRACK_FILE) || name.contains(CLASS_NAME)) return true;
    String lowerName = name.toLowerCase();
    for (int i = 0; i < IGNORE_EXT.length; i++) {
      if (lowerName.endsWith(IGNORE_EXT[i])) return true;
    }
    return false;
  }
  
  private static String calculateMD5(File f) {
    FileInputStream fis = null;
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      fis = new FileInputStream(f);
      byte[] buffer = new byte[8192];
      int n;
      while ((n = fis.read(buffer)) != -1) digest.update(buffer, 0, n);
      byte[] bytes = digest.digest();
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < bytes.length; i++) {
        sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
      }
      return sb.toString();
    } catch (Exception e) { return "ERR"; }
    finally { if (fis != null) try { fis.close(); } catch (Exception e) {} }
    }
  
  private static void save(File target, Map<String, String> data) {
    PrintWriter writer = null;
    try {
      writer = new PrintWriter(new FileWriter(target));
      for (Map.Entry<String, String> entry : data.entrySet()) {
        writer.println(entry.getKey() + "|" + entry.getValue());
      }
    } catch (IOException e) { e.printStackTrace(); }
    finally { if (writer != null) writer.close(); }
    }
  
  private static Map<String, String> load(File target) {
    Map<String, String> data = new HashMap<String, String>();
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(target));
      String line;
      while ((line = reader.readLine()) != null) {
        int idx = line.lastIndexOf("|");
        if (idx != -1) data.put(line.substring(0, idx), line.substring(idx + 1));
      }
    } catch (IOException e) { e.printStackTrace(); }
    finally { if (reader != null) try { reader.close(); } catch (Exception e) {} }
      return data;
  }
  
  private static void compareAndReport(Map<String, String> oldMap, Map<String, String> currentMap, File tracker, String baseDir) {
    int newCount = 0, changedCount = 0, deletedCount = 0;
    
    for (String path : currentMap.keySet()) {
      if (!oldMap.containsKey(path)) {
        File f = new File(baseDir, path);
        String lastMod = SDF.format(new Date(f.lastModified()));
        System.out.println("[NEW]     (" + lastMod + ") " + path);
        newCount++;
        } else if (!oldMap.get(path).equals(currentMap.get(path))) {
        File f = new File(baseDir, path);
        String lastMod = SDF.format(new Date(f.lastModified()));
        System.out.println("[CHANGED] (" + lastMod + ") " + path);
        changedCount++;
      }
    }
    
    for (String path : oldMap.keySet()) {
      if (!currentMap.containsKey(path)) {
        System.out.println("[DELETED] " + path);
        deletedCount++;
      }
    }
    
    if (newCount > 0 || changedCount > 0 || deletedCount > 0) {
      System.out.println("---------------------------------");
      System.out.println("Summary: " + newCount + " New, " + changedCount + " Changed, " + deletedCount + " Deleted.");
      save(tracker, currentMap);
      } else {
      System.out.println("Status: Up to date.");
    }
  }
  
}
