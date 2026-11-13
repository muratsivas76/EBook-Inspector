package net.murat.notepad;

/**
* This class holds character constants and provides transliteration engines.
*
* @author Murat iNAN, muratsivas76@gmail.com
*/
public class ArabicTool extends Object implements java.io.Serializable {

  /** Constructor */
  public ArabicTool () {}

  /** Arabic Characters */
  private static final char [] arabicchr = {
    /*Lafzi Celal*/'\uFDF2',
    /* Cim */   '\u062C' , /* Sa */   '\u062B' ,
    /* Ta */    '\u062A' , /* Ba */   '\u0628' ,
    /* Elif  */ '\u0627' , /* Ra */   '\u0631' ,
    /* Zal */   '\u0630' , /* Dal */  '\u062F' ,
    /* H.a */   '\u062E' , /* Ha */   '\u062D' ,
    /* Dad */   '\u0636' , /* Sad */  '\u0635' ,
    /* Shin...*/'\u0634',  /* Sin */  '\u0633' ,
    /* Za */    '\u0632' , /* Fa */   '\u0641' ,
    /* Gayn */  '\u063A' , /* Ayn */  '\u0639' ,
    /* Za() */  '\u0638' , /* Ta. */  '\u0637' ,
    /* Nun */   '\u0646' , /* Mim */  '\u0645' ,
    /* Lam */   '\u0644' , /* Kaf */  '\u0643' ,
    /* Gaf */   '\u0642' , /* Hemze1*/'\u0621' ,
    /* Ya */    '\u064A' , '\u0649' , /* Ha() */  '\u0647' ,
    /* Vav */   '\u0648' , /*  kapali te */ '\u0629' ,
    /*Diger hemzeler*/ '\u0623' , '\u0624' , '\u0625' , '\u0626' ,

    /*Rakamlar*/
    '\u0660', '\u0661', '\u0662', '\u0663',  '\u0664',
    '\u0665', '\u0666', '\u0667', '\u0668', '\u0669',

    /*,*/ '\u060C' ,
    /*-*/ '\u06D4', /*?*/ '\u061F',
    /*Ust tek tirnak*/'\u066C', /*Dalga*/'\u0653',
    /*Noktali virgul*/'\u061B', /*Nokta_che*/'\u0686', /*%*/'\u066A',

    /*slash*/'\u002F', /*(*/'\u0028', /*)*/'\u0029', /*:*/'\u003A',
    /*!*/'\u0021',
    /***/'\u066d',

    /*ustun*/      '\u064E' ,
    /*otre*/       '\u064F' ,
    /*esre*/       '\u0650' ,
    /*sedde*/      '\u0651' ,
    /*cezim*/      '\u0652' ,
    /*ustenvin*/   '\u064B' ,
    /*ottenvin*/   '\u064C' ,
    /*kestenvin*/  '\u064D' ,
    /*uzun ustun*/ '\u0670',
    /*uzun esre*/  '\u0656',

    /*shin*/       '\u0634',
    
    /* --- Farsi Extensions --- */
    /* Pe */       '\u067E',
    /* Che */      '\u0686',
    /* Je */       '\u0698',
    /* Gaf-i Farisi */ '\u06AF'
  };

  /** Latin Characters, Sorting is important */
  private static final char [] latinchr = {
    'W',
    'c', 'p', 't', 'b', 'a',
    'r', 'Z', 'd', 'j', 'h',
    'D', 'S', '\u015F', 's', 'z',
    'f', '\u011F', 'A', 'w', 'T',
    'n', 'm', 'l', 'k', 'g',
    'M', 'y', 'Y', 'Q', 'v',
    'P', 'R', 'F', 'N', 'K',
    '0', '1', '2', '3', '4',
    '5', '6', '7', '8', '9',
    ',', '-', '?', '\'', 'L', 
    ';', '\u00E7', '%','/', '(',
    ')', ':', '!', '*',

    '\u00FC', '\u00F6', 'e', 'H', 'C',
    'X', 'B', 'J', '^', 'E', '\u015B',
    
    'V', '\u0107', 'I', 'G'
  };

  private static final int alen = latinchr.length;

  /**
  * Converts a given Latin layout string into its matching Arabic script representation
  * based on the internal character mapping index constraints.
  */
  public static final String toArabic(String str) {
    if (str == null || str.isEmpty()) {
      return "";
    }

    char[] ch = str.toCharArray();
    final int chlen = ch.length;

    for (int i = 0; i < chlen; i++) {
      char sourceChar = ch[i];
      for (int j = 0; j < alen; j++) {
        if (sourceChar == latinchr[j]) {
          ch[i] = arabicchr[j];
          break;
        }
      }
    }

    return new String(ch);
  }

  /**
  * Converts a given Arabic layout string back into its matching Latin text representation
  * based on the internal character mapping index constraints.
  */
  public static final String fromArabic(String str) {
    if (str == null || str.isEmpty()) {
      return "";
    }

    char[] ch = str.toCharArray();
    final int chlen = ch.length;

    for (int i = 0; i < chlen; i++) {
      char sourceChar = ch[i];
      for (int j = 0; j < alen; j++) {
        if (sourceChar == arabicchr[j]) {
          ch[i] = latinchr[j];
          break;
        }
      }
    }

    return new String(ch);
  }

  public static final char [] getArabicchr () {
    return arabicchr;
  }

  public static final char [] getLatinchr() {
    return latinchr;
  }

  @Override
  public String toString () {
    return "Character constants for Arabic language";
  }

}
