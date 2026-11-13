import java.io.*;
import java.util.*;

public class ConstructDictMeanings {
  
  public static void main(String[] args) {
    if (args.length < 4) {
      System.out.println("Usage: ConstructDictMeanings <mode: 0|1> <dictFile> <src> <dst>");
      System.out.println("Mode 0: TR -> FA (Matches left side)");
      System.out.println("Mode 1: FA -> TR (Matches right side)");
      return;
    }
    
    int mode = 0; // Default value
    try {
      mode = Integer.parseInt(args[0]);
      } catch (NumberFormatException e) {
      System.out.println("Invalid mode input. Defaulting to mode 0.");
      mode = 0;
    }
    
    String dictPath = args[1];
    String srcPath = args[2];
    String dstPath = args[3];
    
    Map<String, String> dictionary = loadDictionary(dictPath, mode);
    processText(srcPath, dstPath, dictionary);
  }
  
  private static Map<String, String> loadDictionary(String path, int mode) {
    Map<String, String> map = new HashMap<String, String>();
    BufferedReader br = null;
    try {
      br = new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"));
      String line;
      while ((line = br.readLine()) != null) {
        if (line.contains(":")) {
          String[] parts = line.split(":", 2);
          String left = parts[0].trim().toLowerCase();
          String right = parts[1].trim().toLowerCase();
          
          if (mode == 0) {
            map.put(left, right);
            } else {
            map.put(right, left);
          }
        }
      }
      } catch (Exception e) {
      System.err.println("Dictionary Error: " + e.getMessage());
      } finally {
      try { if (br != null) br.close(); } catch (IOException e) {}
      }
    return map;
  }
  
  private static void processText(String src, String dst, Map<String, String> dictionary) {
    BufferedReader br = null;
    BufferedWriter bw = null;
    try {
      br = new BufferedReader(new InputStreamReader(new FileInputStream(src), "UTF-8"));
      bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dst), "UTF-8"));
      
      String line;
      while ((line = br.readLine()) != null) {
        String[] words = line.split(" ");
        for (int i = 0; i < words.length; i++) {
          bw.write(transformWord(words[i], dictionary));
          if (i < words.length - 1) bw.write(" ");
        }
        bw.newLine();
      }
      } catch (Exception e) {
      System.err.println("Processing Error: " + e.getMessage());
      } finally {
      try { if (br != null) br.close(); } catch (IOException e) {}
      try { if (bw != null) bw.close(); } catch (IOException e) {}
      }
  }
  
  private static String transformWord(String rawWord, Map<String, String> dictionary) {
    String clean = rawWord.replaceAll("[\\p{Punct}&&[^-]]", "").toLowerCase();
    
    // Handle hyphenated words like 'Mah-ı'
    String searchKey = clean.contains("-") ? clean.split("-")[0] : clean;
    
    String meaning = flexibleSearch(searchKey, dictionary);
    
    if (meaning != null) {
      return rawWord + " [" + meaning + "]";
    }
    return rawWord;
  }
  
  private static String flexibleSearch(String word, Map<String, String> dictionary) {
    if (dictionary.containsKey(word)) return dictionary.get(word);
    
    // Stemming and consonant softening (p\\u00E7tk -> bcde/g)
    for (int i = word.length(); i > 2; i--) {
      String candidate = word.substring(0, i);
      if (dictionary.containsKey(candidate)) return dictionary.get(candidate);
      
      String hardened = harden(candidate);
      if (dictionary.containsKey(hardened)) return dictionary.get(hardened);
    }
    return null;
  }
  
  private static String harden(String word) {
    if (word.length() == 0) return word;
    char lastChar = word.charAt(word.length() - 1);
    String body = word.substring(0, word.length() - 1);
    
    switch (lastChar) {
      case 'b': return body + "p";
      case 'c': return body + "\u00E7";
      case 'd': return body + "t";
      case 'g':
        case '\u011F': return body + "k";
      default: return word;
    }
  }
  
}
