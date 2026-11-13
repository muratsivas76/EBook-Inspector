package net.murat.notepad;

import java.io.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
* Professional utility class for text processing.
* All method signatures are strictly preserved for backward compatibility.
*/
public final class V2UTFToISO extends Object implements Serializable {

  private static final long serialVersionUID = 1L;
  private static boolean isTR = false;

  private static final String VESTR = "ve";
  private static final String KISTR = "ki";
  private static final String DESTR = "de";
  private static final String DASTR = "da";
  private static final String ILESTR = "ile";
  private static final String ILASTR = "ila";

  // Reusable set for faster lookup in isFirstUpper
  private static final Set<String> CONJUNCTIONS = new HashSet<String>(Arrays.asList(
  VESTR, KISTR, DESTR, DASTR, ILESTR, ILASTR
  ));

  private V2UTFToISO() {
    super();
  }

  @Override
  public String toString() {
    return "";
  }

  /**
  * Main processing method with original signature.
  */
  public static String cnt(String src) throws IOException {
    if (src == null) return "";

    Reader isr = new StringReader(src);
    BufferedReader br = new BufferedReader(isr);
    StringBuilder sbr = new StringBuilder();

    String line;
    String moline;
    final String TIRE = "-";
    final String YYU = Character.toString((char) 173);
    final int CERO = 0;
    final int UNO = 1;

    final String NOKTA = ".";
    final String UNLEM = "!";
    final String SORU = "?";

    while ((line = br.readLine()) != null) {
      moline = line.trim().replace('\t', ' ').trim();

      if (moline.length() < 1) {
        sbr.append("\n");
      }

      // Replacing triple spaces or more with single space efficiently
      while (moline.contains("  ")) {
        moline = moline.replace("  ", " ");
      }

      if (sfnum(moline)) continue;

      if (moline.endsWith(TIRE) || moline.endsWith(YYU)) {
        sbr.append(moline.substring(CERO, moline.length() - UNO));
      } else {
        sbr.append(moline);

        if (moline.endsWith(NOKTA) || moline.endsWith(UNLEM) ||
        moline.endsWith(SORU) || isFirstUpper(moline) || isRomen(moline)) {
          sbr.append("\n");
        } else {
          sbr.append(" ");
        }
      }
    }

    br.close();
    isr.close();

    String text = sbr.toString();
    V2Utils.setTR(false);

    String mext = V2Utils.getBeautifiedText(text);
    mext = V2Utils.ygetBeautifiedText(mext);

    // Performance: Use replace instead of replaceAll for static strings
    mext = mext.replace(" \n", "\n");

    // Punctuation spacing
    mext = fixSpacings(mext);

    mext = mext.replace(" \n", "\n");
    mext = mext.replace("\"\n", "\"\n\n");

    mext = getTirnaked(mext);

    mext = mext.replace(". . .", "...");

    while (mext.contains("\n\n\n")) {
      mext = mext.replace("\n\n\n", "\n\n");
    }

    return mext;
  }

  /**
  * Preserved signature: getTirnaked
  */
  private static String getTirnaked(String metinx) {
    if (metinx == null) return null;

    // Use a single StringBuilder to avoid massive String object creation in memory
    String upperChars = "ABCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVYZWQ";
    StringBuilder sb = new StringBuilder(metinx);

    for (int i = sb.length() - 3; i >= 0; i--) {
      if (sb.charAt(i) == '"' && sb.charAt(i + 1) == ' ') {
        char c = sb.charAt(i + 2);
        if (upperChars.indexOf(c) != -1) {
          sb.replace(i + 1, i + 2, "\n\n");
        }
      }
    }

    String result = sb.toString();
    return result.replace("Hz.\n\n", "Hz. ");
  }

  /**
  * Preserved signature: isTotalUpper
  */
  private static boolean isTotalUpper(String xstr) {
    if (xstr == null || xstr.length() < 1) return false;

    for (int i = 0; i < xstr.length(); i++) {
      if (Character.isLowerCase(xstr.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
  * Preserved signature: isFirstUpper
  */
  private static boolean isFirstUpper(String xstr) {
    if (xstr == null || xstr.length() < 1) return false;

    String[] words = xstr.split(" ");
    if (words.length < 1) return false;

    for (int i = 0; i < words.length; i++) {
      String word = words[i].trim();
      if (word.length() < 1) continue;

      if (CONJUNCTIONS.contains(word.toLowerCase())) {
        continue;
      }

      if (Character.isLowerCase(word.charAt(0))) {
        return false;
      }
    }
    return true;
  }

  /**
  * Preserved signature: isRomen
  */
  private static boolean isRomen(String xstr) {
    if (xstr == null) return false;
    String str = xstr.trim().replace(" ", "").toUpperCase();

    if (str.equals("M") || str.equals("C") || str.equals("L")) return true;

    // Hardcoded list preserved for exact behavior match
    String[] romenNumbers = {
      "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
      "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX",
      "XXI", "XXII", "XXIII", "XXIV", "XXV", "XXVI", "XXVII", "XXVIII", "XXIX", "XXX",
      "XXXI", "XXXII", "XXXIII", "XXXIV", "XXXV", "XXXVI", "XXXVII", "XXXVIII", "XXXIX", "XXXX",
      "XXXXI", "XXXXII", "XXXXIII", "XXXXIV", "XXXXV", "XXXXVI", "XXXXVII", "XXXXVIII", "XXXXIX"
    };

    for (int i = 0; i < romenNumbers.length; i++) {
      if (str.equals(romenNumbers[i])) return true;
    }
    return false;
  }

  /**
  * Preserved signature: sfnum
  */
  private static boolean sfnum(String xstr) {
    if (xstr == null) return false;
    String str = xstr.trim().replace(" ", "");
    if (str.length() == 0) return false;

    for (int i = 0; i < str.length(); i++) {
      char ch = str.charAt(i);
      if (ch < '0' || ch > '9') {
        return false;
      }
    }
    return true;
  }

  /**
  * Internal helper to fix punctuation spacing without regex overhead.
  */
  private static String fixSpacings(String text) {
    String res = text;
    res = res.replace(". ", ".").replace(".", ". ");
    res = res.replace("? ", "?").replace("?", "? ");
    res = res.replace(", ", ",").replace(",", ", ");
    res = res.replace(". \"", ".\"");
    return res;
  }

  public static void main(final String[] args) {
    System.exit(0);
  }

}
