package net.murat.kenc;

final
public class Values extends Object {

  private Values() {
    super();
  }

  @Override
  public String toString() {
    return "_";
  }

  /* Core Numeric Constants */
  final public static int CERO = 0;
  final public static int UNO = 1;
  final public static int DOS = 2;
  final public static int TRES = 3;
  final public static int CUATRO = 4;
  final public static int CINCO = 5;
  final public static int OIK = 12;
  final public static int YDO = 24;
  
  /* Buffer and Memory Sizes (Mechanical Buffering) */
  final public static int PLEN = 16;
  final public static int KB = 1024;
  final public static int ATKB = (KB * 256);
  final public static int CSATKB = (KB * 8);
  final public static int FLUSH_THRESHOLD = 50;
  
  /* System Controls */
  final public static int NEGATIVO = -1;
  final public static int SLEEP = 16;
  public static int CIEN = 72;
  final public static long KBL = 1024L;
  
  /* UI Layout and Typography Dimensions */
  final public static float CEROF = 0.0F;
  final public static float DOSF = 2.0F;
  final public static float DIEZ = 16.0F;
  final public static float VEINTE = 20.0F;
  final public static float TREINTA = 30.0F;
  final public static float TSIZE = 40.0F;
  final public static float TEXTSIZE = 15.0F;
  final public static float NOVENTA = 90.0F;
  public static float CIENF = 72.0F;
  public static float RECTRIGHT = 0.0F;

  /* Password Configurations */
  final public static String PREPASSW = "5EmrE4325Emr";
  public static String PASS = "5EmrE4325EmrE432";
  
  /* UI Strings and Interaction Prompts */
  final public static String ENTERNUM = "Enter 4 Chars. Pre-pass is: " + PREPASSW;
  final public static String OK = "OK";
  final public static String CANCEL = "CANCEL";
  final public static String START = "START";
  final public static String EXIT = "EXIT";
  final public static String BTN_PASSW = "PASSW";
  final public static String BTN_ENC = "ENC";
  final public static String BTN_DEC = "DEC";
  final public static String BTN_SHOW = "SHOW";
  
  public static String SAVED = "MODE: enc";
  
  /* Algorithm and Filter Definitions */
  final public static String ENC = "enc";
  final public static String DEC = "dec";
  final public static String SHOW = "show";
  public static String CURRENT_MODE = ENC;
  
  final public static String NOKTA = ".";
  final public static String ALTENC = ".enc";
  final public static String ALGORITHM = "AES";
  final public static String UTF8 = "UTF-8";
  final public static String IKINOKTA = ":";
  
  /* Error Messaging Constants */
  final public static String CORTOLEN = "Error: Input must be exactly 4 chars!";
  final public static String REQNOK = "Required punto error!";
  final public static String SHARRAY = "Very short array error!";
  final public static String NULLPASSW = "Null Password Error!";
  final public static String NULLKEY = "Null SpecKey Error!";
  final public static String NULLPANO = "Null Panel Error!";
  
}
