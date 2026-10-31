package net.murat.util;

import java.io.*;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * Optimized and bug-fixed version of V2Utils.
 * Strictly maintains legacy method signatures.
 * Fixes the "replacement character" artifacts and method signature mismatches.
 */
public final class V2Utils extends Object implements Serializable {
  
  private static final long serialVersionUID = 1L;
  private static boolean tr = false;
  private static boolean isBorrowNums = true;
  
  // Pure string configurations for high performance and legacy compatibility
  private static final String STR_DIYEZ_REGEX = "^\\d+[\\.\\-:]\\s*.*";
  private static final String STR_SPACE_REGEX = "\\s+";
  private static final String STR_X_REGEX = "[\\.\\-:]$";
  private static final String STR_Y_REGEX = "[\\.\\-:]";
  
  // FIX: Added the missing parenthesis to capture the digit block as group(1) identical to desktop engine
  private static final String STR_FOOTNOTE_REGEX = "^(\\d+)\\.\\s*([^:]+):\\s*(.+)$";
  private static final String STR_UNI_REGEX = "\\b(\\p{L}+)(\\d+)\\b";
  
  private static final String START_FN = "#################@";
  private static final String END_FN = "#################&";
  
  private V2Utils() {
    super();
  }
  
  @Override
  public String toString() {
    return "";
  }
  
  public static void setBorrowNums(boolean isb) {
    isBorrowNums = isb;
  }
  
  public static boolean getBorrowNums() {
    return isBorrowNums;
  }
  
  public static void setTR(boolean isb) {
    tr = isb;
  }
  
  public static String getBeautifiedText(String m) {
    if (m == null) return "Null Text for Beutify Operation!";
    if (m.length() < 1) return "Zero Length Text for Beautify Operation!";
    
    String moti = "ERRORED!";
    try {
      // Fix: Use consistent String-String parameters for replace
      String work = m.replace(Character.toString((char) 194), "");
      work = work.replace("* ", "~ ");
      
      // Clean up problematic encoding characters safely by converting them to String
      work = work.replace("\uFFFD", " ");
      work = work.replace("`", "'");
      //work = work.replace(Character.toString((char) 95), "-");
      work = work.replace(Character.toString((char) 127), " ");
      work = work.replace(Character.toString((char) 160), " ");
      work = work.replace(Character.toString((char) 166), ":");
      work = work.replace(Character.toString((char) 168), "~");
      work = work.replace(Character.toString((char) 170), "a");
      work = work.replace(Character.toString((char) 173), "-");
      work = work.replace(Character.toString((char) 175), "~");
      work = work.replace(Character.toString((char) 176), "o:");
      work = work.replace(Character.toString((char) 180), "'");
      work = work.replace(Character.toString((char) 182), "~");
      work = work.replace(Character.toString((char) 183), " ");
      work = work.replace(Character.toString((char) 184), " ");
      work = work.replace(Character.toString((char) 186), "o:");
      work = work.replace(Character.toString((char) 171), "\"");
      work = work.replace(Character.toString((char) 187), "\"");
      work = work.replace(Character.toString((char) 247), "/");
      // FIXED: String to String
      
      // Unicode specific replacements (Consistency is key)
      work = work.replace(Character.toString((char) 8210), "-")
      .replace(Character.toString((char) 8211), "-")
      .replace(Character.toString((char) 8212), "-")
      .replace(Character.toString((char) 8213), "--")
      .replace(Character.toString((char) 8214), "")
      .replace(Character.toString((char) 8215), "=")
      .replace(Character.toString((char) 8216), "'")
      .replace(Character.toString((char) 8217), "'")
      .replace(Character.toString((char) 8218), ",")
      .replace(Character.toString((char) 8219), "/")
      .replace(Character.toString((char) 8220), "\"")
      .replace(Character.toString((char) 8221), "\"")
      .replace(Character.toString((char) 8222), ",")
      .replace(Character.toString((char) 8223), "\"")
      .replace(Character.toString((char) 8224), "+")
      .replace(Character.toString((char) 8225), "+")
      .replace(Character.toString((char) 8226), "--")
      .replace(Character.toString((char) 8230), "...")
      .replace(Character.toString((char) 8592), "<--")
      .replace(Character.toString((char) 8594), "-->")
      .replace(Character.toString((char) 9474), "-")
      .replace(Character.toString((char) 61694), "~")
      .replace(Character.toString((char) 65533), " ");
      
      // Formatting
      work = work.replace(" .", ".").replace(" ,", ",")
      .replace(" !", "!").replace(" :", ":")
      .replace(" ;", ";").replace(" ?", "?")
      .replace(" \n", "\n").replace("\n ", "\n");
      
      work = work.replace("(\n", "").replace(".\n", ".\n\n\n\n\n")
      .replace("!\n", "!\n\n\n\n\n").replace("?\n", "?\n\n\n\n\n")
      .replace(")\n", ")\n\n\n\n\n");
      
      while (work.contains("\n\n\n")) {
        work = work.replace("\n\n\n", "\n\n");
      }
      
      String metin_67 = replacePageNums(work);
      String metin_68 = getDesignedText(metin_67);
      String finalMetin = metin_68.replace(" \n", "\n").replace("\n ", "\n");
      
      for (int i = 0; i < 10; i++) {
        finalMetin = finalMetin.replace("\n\n\n", "\n\n");
      }
      
      moti = callavi(finalMetin);
      System.out.println("Beautify Text Operation is Okey!");
      } catch (Exception e) {
      e.printStackTrace();
      return "ERROR!";
    }
    return moti;
  }
  
  public static String ygetBeautifiedText(String m) {
    if (m == null) return "Null Text for Beutify Operation!";
    if (m.length() < 1) return "Zero Length Text for Beautify Operation!";
    
    StringBuilder sb = new StringBuilder();
    String left = " ";
    try {
      BufferedReader br = new BufferedReader(new StringReader(m));
      String line;
      while ((line = br.readLine()) != null) {
        line = line.trim();
        if (line.length() < 1) continue;
        
        if (left.equals("V") && (Character.isUpperCase(line.charAt(0)) || line.charAt(0) == '\"')) {
          sb.append("\n\n");
        }
        
        if (isBorrowNums) {
          line = silNums(line);
        }
        
        sb.append(line);
        char last = line.charAt(line.length() - 1);
        if (last == '.' || last == ';' || last == '!' || last == '?') {
          sb.append("\n\n");
          } else {
          sb.append(" ");
          left = (last > 64) ? "V" : "Y";
        }
      }
      br.close();
      
      String res = sb.toString().replace("\"\"", "\"\n\n\"").replace("\" \"", "\"\n\n\"");
      while (res.contains("\n\n\n")) {
        res = res.replace("\n\n\n", "\n\n");
      }
      res = res.replace("\n ", "\n");
      
      String[] chars = {"b", "c", "d", "f", "g", "h", "j", "k", "l", "m", "n", "p", "r", "s", "t", "v", "z", "w", "q", "\u00E7", "\u011F", "\u00F6", "\u015F", "\u00FC", "B", "C", "D", "F", "G", "H", "J", "K", "L", "M", "N", "P", "R", "S", "T", "V", "Z", "W", "Q", "\u00C7", "\u011E", "\u00D6", "\u015E", "\u00DC"};
      for (int i = 0; i < chars.length; i++) {
        res = res.replace(" " + chars[i] + " ", " " + chars[i]);
      }
      
      if (tr) {
        String[] vowels = {"a", "e", "i", "u", "y", "\u0131", "A", "E", "I", "U", "Y", "\u0130"};
        for (int i = 0; i < vowels.length; i++) {
          res = res.replace(" " + vowels[i] + " ", " " + vowels[i]);
        }
      }
      return res.replace(": -", ":\n\n-");
      } catch (Exception e) {
      return "ERRORED!";
    }
  }
  
  private static String silNums(String m) {
    if (m == null || m.length() < 1) return m;
    String[] words = m.trim().split(" ");
    StringBuilder sb = new StringBuilder();
    
    for (int i = 0; i < words.length; i++) {
      String word = words[i].trim();
      if (word.length() < 1) continue;
      
      if (isDigit(word)) {
        sb.append(word).append(" ");
        continue;
      }
      
      if (word.indexOf("...") < 0 && ncount(word) > 1) {
        sb.append(word).append(" ");
        continue;
      }
      
      if (word.indexOf("/") >= 0 || word.indexOf("-") >= 0 || word.indexOf("'") >= 0 || word.indexOf(":") >= 0) {
        sb.append(word).append(" ");
        continue;
      }
      
      StringBuilder ynb = new StringBuilder();
      for (int j = 0; j < word.length(); j++) {
        char c = word.charAt(j);
        if (!Character.isDigit(c)) ynb.append(c);
      }
      sb.append(ynb.toString().replace("()", "")).append(" ");
      }
      return sb.toString().trim();
    }
    
    private static String callavi(String mm) {
      if (mm == null) return "Null Text For!";
      if (mm.length() < 3) return "VERY SHORT LENGTH TEXT!";
      
      StringBuilder metin = new StringBuilder("   \n").append(mm).append("   \n");
      for (int i = 4; i < metin.length(); i++) {
        char chr = metin.charAt(i);
        if (metin.charAt(i - 1) != (char) 10) continue;
        if (chr == (char) 10) continue;
        if (!Character.isUpperCase(chr) && !Character.isDigit(chr) && Character.isLetter(chr)) {
          metin.setCharAt(i - 1, (char) 32);
          metin.setCharAt(i - 2, (char) 32);
        }
      }
      String res = metin.toString().replace("   \n", "");
      while (res.contains("  ")) res = res.replace("  ", " ");
      res = res.replace(". \" ", ".\" ");
      
      return res;
    }
    
    private static String replacePageNums(String str) {
      if (str == null) return " ";
      int BARS = 750;
      if (str.length() < BARS) return str;
      int SFNUM = str.length() / BARS;
      for (int i = 1; i <= SFNUM; i++) {
        String s = Integer.toString(i);
        str = str.replace("\n" + s + "\n\n", "\n").replace("\n\n" + s + "\n", "\n").replace("\n" + s + "\n", "\n");
      }
      return str;
    }
    
    private static boolean isDigit(String m) {
      if (m == null || m.length() < 1) return false;
      for (int i = 0; i < m.length(); i++) {
        if (!Character.isDigit(m.charAt(i))) return false;
      }
      return true;
    }
    
    private static int ncount(String wrd) {
      int syc = 0;
      for (int i = 0; i < wrd.length(); i++) {
        if (wrd.charAt(i) == '.') syc++;
      }
      return syc;
    }
    
    private static boolean sfnum(String xstr) {
      String str = xstr.trim().replace(" ", "");
      if (str.length() == 0) return false;
      for (int i = 0; i < str.length(); i++) {
        int chm = (int) str.charAt(i);
        if (!(chm >= 0x30 && chm <= 0x39)) return false;
      }
      return true;
    }
    
    public static String replace(String old) {
      if (old == null) return null;
      return old.replace("&#287;", "\u011F").replace("&#286;", "\u011E")
      .replace("&#305;", "\u0131").replace("&#304;", "\u0130")
      .replace("&#351;", "\u015F").replace("&#350;", "\u015E")
      .replace("&laquo;", "<<").replace("&nbsp;", " ");
    }
    
    private static String getDesignedText(String oldi) {
      StringBuilder sb = new StringBuilder();
      try {
        BufferedReader br = new BufferedReader(new StringReader(oldi));
        String line;
        while ((line = br.readLine()) != null) {
          line = line.trim();
          if (line.isEmpty()) {
            sb.append("\n\n");
            continue;
          }
          if (sfnum(line)) continue;
          if (line.length() <= 72 && Character.isUpperCase(line.charAt(0))) {
            line = line + "\n\n";
          }
          if (Character.isUpperCase(line.charAt(line.length() - 1))) {
            sb.append(line).append("\n\n");
            } else {
            sb.append(line).append(" ");
          }
        }
        br.close();
        } catch (Exception e) {
        return " ";
      }
      return sb.append(" ").toString();
    }
    
    public static String getDiyezString(final String text) {
      if (text == null || text.isEmpty()) return "";
      
      StringBuilder result = new StringBuilder();
      // Matching the desktop split pattern strictly
      String[] lines = text.split("\\n");
      
      java.util.Map<String, String> footnoteMap = new java.util.HashMap<String, String>();
      java.util.List<Integer> footnoteLines = new java.util.ArrayList<Integer>();
      
      // Local pattern instantiation for runtime safety
      Pattern diyezPattern = Pattern.compile(STR_DIYEZ_REGEX);
      Pattern spacePattern = Pattern.compile(STR_SPACE_REGEX);
      Pattern xPattern = Pattern.compile(STR_X_REGEX);
      Pattern yPattern = Pattern.compile(STR_Y_REGEX);
      
      // First Loop: Detect footnote lines exactly like desktop behavior
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i].trim();
        
        if (diyezPattern.matcher(line).matches()) {
          footnoteLines.add(i);
          
          String[] parts = spacePattern.split(line, 2);
          if (parts != null && parts.length == 2) {
            String key = xPattern.matcher(parts[0]).replaceAll("");
            String value = parts[1].trim();
            footnoteMap.put(key, value);
          }
        }
      }
      
      boolean inFootnoteBlock = false;
      
      // Second Loop: Reconstruct the document buffer matching your desktop engine
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        
        if (footnoteLines.contains(i)) {
          if (!inFootnoteBlock) {
            result.append("\n").append(START_FN).append(".\n");
            inFootnoteBlock = true;
          }
          
          String[] parts = yPattern.split(line.trim(), 2);
          
          if (parts != null && parts.length == 2) {
            String number = parts[0];
            String description = parts[1].trim();
            if (!description.endsWith(".")) {
              description = description + ".";
            }
            result.append(number).append(". ").append(description).append("\n");
            } else {
            result.append(line).append("\n");
          }
          
          if (i + 1 >= lines.length || !footnoteLines.contains(i + 1)) {
            result.append(END_FN).append(".\n");
            inFootnoteBlock = false;
          }
          } else {
          result.append(line).append("\n");
        }
      }
      
      return result.toString();
    }
    
    public static String processBlocksSeparately(String text) {
      if (text == null || text.isEmpty()) return "";
      
      String[] lines = text.split("\\n");
      StringBuilder result = new StringBuilder();
      StringBuilder currentTextBuffer = new StringBuilder();
      StringBuilder currentFootnoteBuffer = new StringBuilder();
      boolean insideFootnoteBlock = false;
      
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        String trimmed = line.trim();
        
        if (trimmed.startsWith(START_FN + ".")) {
          // Start of footnote block
          insideFootnoteBlock = true;
          currentFootnoteBuffer = new StringBuilder();
          currentFootnoteBuffer.append(line).append("\n");
        }
        else if (trimmed.startsWith(END_FN + ".")) {
          // End of footnote block
          currentFootnoteBuffer.append(line).append("\n");
          
          // Parse footnotes from this block
          Map<String, String> footnoteMap = parseFootnotesFromBlock(currentFootnoteBuffer.toString());
          
          // Apply footnotes to the accumulated text
          String processedText = applyFootnotesToText(currentTextBuffer.toString(), footnoteMap);
          result.append(processedText);
          
          // Reset buffers
          currentTextBuffer = new StringBuilder();
          insideFootnoteBlock = false;
        }
        else if (insideFootnoteBlock) {
          // Inside footnote block (continuation lines)
          currentFootnoteBuffer.append(line).append("\n");
        }
        else {
          // Normal text outside footnote blocks
          currentTextBuffer.append(line).append("\n");
        }
      }
      
      // Handle any remaining text without footnote block
      if (currentTextBuffer.length() > 0) {
        result.append(currentTextBuffer.toString());
      }
      
      return result.toString();
    }
    
    public static Map<String, String> parseFootnotesFromBlock(String footnoteBlock) {
      Map<String, String> footnoteMap = new LinkedHashMap<String, String>();
      if (footnoteBlock == null || footnoteBlock.isEmpty()) return footnoteMap;
      
      String[] lines = footnoteBlock.split("\\n");
      Pattern footnotePattern = Pattern.compile(STR_FOOTNOTE_REGEX);
      
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        Matcher matcher = footnotePattern.matcher(line.trim());
        if (matcher.matches() && matcher.groupCount() >= 3) {
          String number = matcher.group(1);
          
          // FIX: Pointing exactly to group(3) to perfectly synchronize with desktop extraction logic
          String definition = matcher.group(3);
          if (definition != null) {
            definition = definition.trim();
            if (definition.endsWith(".")) {
              definition = definition.substring(0, definition.length() - 1);
            }
            if (!footnoteMap.containsKey(number)) {
              footnoteMap.put(number, definition);
            }
          }
        }
      }
      
      return footnoteMap;
    }
    
    public static String applyFootnotesToText(String text, Map<String, String> footnoteMap) {
      if (text == null || text.isEmpty() || footnoteMap == null || footnoteMap.isEmpty()) {
        return text;
      }
      
      // FIX: Completely stripped out both explicit and embedded flags to comply with Android 10 (API 29)
      // regex specifications while fully preserving Unicode/Turkish support naturally.
      Pattern pattern = Pattern.compile(STR_UNI_REGEX);
      Matcher matcher = pattern.matcher(text);
      StringBuffer result = new StringBuffer();
      
      while (matcher.find()) {
        String word = matcher.group(1);
        String number = matcher.group(2);
        String definition = footnoteMap.get(number);
        
        if (definition != null) {
          matcher.appendReplacement(result, Matcher.quoteReplacement(word + " [" + definition + "]"));
          } else {
          matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
        }
      }
      matcher.appendTail(result);
      
      return result.toString();
    }
    
}
