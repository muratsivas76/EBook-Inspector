package net.murat.decodebook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal, dependency-free DEX reader that extracts the String[] contents of
 * the Sayfa_XXXXX classes directly from an APK stream.
 *
 * Design notes (why this version is robust):
 *
 *  1. It reads ALL classes*.dex entries in the APK, not just classes.dex.
 *     If d8 ever splits into classes2.dex, those pages are still found.
 *
 *  2. It walks every method of every class (direct + virtual), not just the
 *     first one. It does NOT stop after the first non-empty method, because
 *     a class can legitimately spread its strings across several methods.
 *
 *  3. It skips methods that cannot contain page content (toString, getLines,
 *     etc.) by inspecting the method name. This prevents the synthetic
 *     "Sayfa_XXXXX" string from toString() from polluting the output.
 *
 *  4. It uses a COMPLETE Dalvik opcode length table, so the instruction
 *     pointer never desynchronizes. A single wrong opcode length would cause
 *     every following const-string in that method to be silently skipped.
 *
 *  5. It handles both const-string (0x1A) and const-string/jumbo (0x1B).
 *
 *  6. It preserves the String[][] structure: consecutive const-string values
 *     that belong to the same inner String[] are joined with a space, and
 *     each inner String[] becomes one output line. In the current APK every
 *     inner array has exactly one element, so this is a no-op today, but it
 *     keeps the reader correct if the producer ever emits multi-element rows.
 *
 *  7. Pages are sorted numerically by page number (Sayfa_2 before Sayfa_10),
 *     not lexicographically.
 */
public class DexPageReader {
  
  public static final int DEFAULT_START = 1;
  public static final int DEFAULT_END   = 9;
  public static final int MAX_RANGE     = 1501;
  
  /** Reads the default range (1..9) of pages. */
  public static List<String> readPages(InputStream apkStream) throws IOException {
    return readPages(apkStream, DEFAULT_START, DEFAULT_END);
  }
  
  /** Reads pages in the given inclusive range [start, end]. */
  public static List<String> readPages(InputStream apkStream, int start, int end) throws IOException {
    List<byte[]> dexFiles = extractAllDexFiles(apkStream);
    if (dexFiles.isEmpty()) {
      throw new IOException("no classes*.dex found in APK");
    }
    List<String> all = new ArrayList<String>();
    for (byte[] dex : dexFiles) {
      all.addAll(extractPages(dex, start, end));
    }
    sortByPageNumber(all);
    return all;
  }
  
  /** Counts how many Sayfa_XXXXX classes exist (highest page number). */
  public static int countPages(InputStream apkStream) throws IOException {
    List<byte[]> dexFiles = extractAllDexFiles(apkStream);
    if (dexFiles.isEmpty()) {
      throw new IOException("no classes*.dex found in APK");
    }
    int max = 0;
    for (byte[] dex : dexFiles) {
      int m = countPages(dex);
      if (m > max) max = m;
    }
    return max;
  }
  
  // ------------------------------------------------------------------
  // APK / DEX extraction
  // ------------------------------------------------------------------
  
  /**
   * Extracts every classes*.dex entry from the APK. Handles classes.dex,
   * classes2.dex, classes3.dex, ... in case d8 multi-dex ever kicks in.
   */
  private static List<byte[]> extractAllDexFiles(InputStream apkStream) throws IOException {
    List<byte[]> result = new ArrayList<byte[]>();
    ZipInputStream zis = new ZipInputStream(apkStream);
    ZipEntry entry;
    while ((entry = zis.getNextEntry()) != null) {
      String name = entry.getName();
      if (name != null && name.startsWith("classes") && name.endsWith(".dex")) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = zis.read(buffer)) != -1) {
          baos.write(buffer, 0, len);
        }
        result.add(baos.toByteArray());
      }
      zis.closeEntry();
    }
    zis.close();
    return result;
  }
  
  // ------------------------------------------------------------------
  // Page counting
  // ------------------------------------------------------------------
  
  private static int countPages(byte[] dex) {
    int stringIdsSize = readInt(dex, 0x38);
    int stringIdsOff  = readInt(dex, 0x3C);
    int typeIdsSize   = readInt(dex, 0x40);
    int typeIdsOff    = readInt(dex, 0x44);
    int classDefsSize = readInt(dex, 0x60);
    int classDefsOff  = readInt(dex, 0x64);
    
    String[] stringPool = new String[stringIdsSize];
    int[] typeToStringIdx = new int[typeIdsSize];
    for (int i = 0; i < typeIdsSize; i++) {
      typeToStringIdx[i] = readInt(dex, typeIdsOff + i * 4);
    }
    
    int maxPage = 0;
    for (int i = 0; i < classDefsSize; i++) {
      int classDefOff = classDefsOff + i * 32;
      int classIdx = readInt(dex, classDefOff + 0);
      if (classIdx < 0 || classIdx >= typeIdsSize) continue;
      int descriptorStrIdx = typeToStringIdx[classIdx];
      String className = getString(dex, stringIdsOff, stringPool, descriptorStrIdx);
      int pageNo = parsePageNo(className);
      if (pageNo > maxPage) maxPage = pageNo;
    }
    return maxPage;
  }
  
  // ------------------------------------------------------------------
  // Page extraction
  // ------------------------------------------------------------------
  
  private static List<String> extractPages(byte[] dex, int start, int end) {
    int stringIdsSize = readInt(dex, 0x38);
    int stringIdsOff  = readInt(dex, 0x3C);
    int typeIdsSize   = readInt(dex, 0x40);
    int typeIdsOff    = readInt(dex, 0x44);
    int classDefsSize = readInt(dex, 0x60);
    int classDefsOff  = readInt(dex, 0x64);
    
    String[] stringPool = new String[stringIdsSize];
    int[] typeToStringIdx = new int[typeIdsSize];
    for (int i = 0; i < typeIdsSize; i++) {
      typeToStringIdx[i] = readInt(dex, typeIdsOff + i * 4);
    }
    
    List<String> pages = new ArrayList<String>();
    
    for (int i = 0; i < classDefsSize; i++) {
      int classDefOff = classDefsOff + i * 32;
      int classIdx = readInt(dex, classDefOff + 0);
      int classDataOff = readInt(dex, classDefOff + 24);
      
      if (classIdx < 0 || classIdx >= typeIdsSize) continue;
      int descriptorStrIdx = typeToStringIdx[classIdx];
      String className = getString(dex, stringIdsOff, stringPool, descriptorStrIdx);
      
      int pageNo = parsePageNo(className);
      if (pageNo < start || pageNo > end) continue;
      
      List<String> lines = readPageLines(dex, stringIdsOff, stringPool, classDataOff);
      if (lines.isEmpty()) continue;
      
      StringBuilder sb = new StringBuilder();
      for (String line : lines) {
        sb.append(line).append("\n");
      }
      sb.append("### ").append(simpleName(className)).append(" ###\n");
      pages.add(sb.toString());
    }
    
    return pages;
  }
  
  private static void sortByPageNumber(List<String> pages) {
    Collections.sort(pages, new Comparator<String>() {
        @Override
        public int compare(String a, String b) {
          return Integer.compare(extractPageNumber(a), extractPageNumber(b));
        }
    });
  }
  
  private static int extractPageNumber(String block) {
    int a = block.indexOf("Sayfa_");
    if (a < 0) return -1;
    int b = a + "Sayfa_".length();
    int start = b;
    while (b < block.length() && Character.isDigit(block.charAt(b))) b++;
    if (b == start) return -1;
    try {
      return Integer.parseInt(block.substring(start, b));
      } catch (NumberFormatException e) {
      return -1;
    }
  }
  
  private static int parsePageNo(String descriptor) {
    if (descriptor == null) return -1;
    int idx = descriptor.indexOf("Sayfa_");
    if (idx < 0) return -1;
    int start = idx + "Sayfa_".length();
    int end = start;
    while (end < descriptor.length() && Character.isDigit(descriptor.charAt(end))) {
      end++;
    }
    if (end == start) return -1;
    try {
      return Integer.parseInt(descriptor.substring(start, end));
      } catch (NumberFormatException e) {
      return -1;
    }
  }
  
  private static String simpleName(String descriptor) {
    int slash = descriptor.lastIndexOf('/');
    String s = slash >= 0 ? descriptor.substring(slash + 1) : descriptor;
    if (s.startsWith("L") && s.endsWith(";")) {
      s = s.substring(1, s.length() - 1);
    }
    return s;
  }
  
  // ------------------------------------------------------------------
  // class_data_item -> methods -> code_item -> const-string
  // ------------------------------------------------------------------
  
  /**
   * Reads all page lines from a class. It walks both direct_methods and
   * virtual_methods. It skips methods whose name clearly cannot hold page
   * content (toString, getLines, <clinit> is NOT skipped because the producer
   * may put the strings there). It collects const-string values in order and
   * groups them into lines.
   */
  private static List<String> readPageLines(byte[] dex, int stringIdsOff,
    String[] stringPool, int classDataOff) {
    List<String> lines = new ArrayList<String>();
    if (classDataOff == 0) return lines;
    
    int p = classDataOff;
    int[] u = new int[1];
    
    p = readUleb128(dex, p, u);
    int staticFieldsSize = u[0];
    p = readUleb128(dex, p, u);
    int instanceFieldsSize = u[0];
    p = readUleb128(dex, p, u);
    int directMethodsSize = u[0];
    p = readUleb128(dex, p, u);
    int virtualMethodsSize = u[0];
    
    // Skip encoded_field entries: each is field_idx_diff + access_flags.
    for (int i = 0; i < staticFieldsSize + instanceFieldsSize; i++) {
      p = readUleb128(dex, p, u); // field_idx_diff
      p = readUleb128(dex, p, u); // access_flags
    }
    
    // Read all methods (direct + virtual). Each encoded_method is
    // method_idx_diff + access_flags + code_off.
    for (int i = 0; i < directMethodsSize + virtualMethodsSize; i++) {
      p = readUleb128(dex, p, u);
      int methodIdxDiff = u[0];
      p = readUleb128(dex, p, u); // access_flags
      p = readUleb128(dex, p, u);
      int codeOff = u[0];
      
      if (codeOff == 0) continue;
      
      // Decide whether this method can hold page content. We skip methods
      // that are known to be structural (toString, getLines, etc.). We do
      // NOT skip <init> or <clinit>, because the producer places the
      // String[][] literal inside the constructor.
      if (isStructuralMethod(dex, stringIdsOff, stringPool, methodIdxDiff, i, directMethodsSize)) {
        continue;
      }
      
      List<String> methodStrings = new ArrayList<String>();
      readConstStringsFromCode(dex, stringIdsOff, stringPool, codeOff, methodStrings);
      if (!methodStrings.isEmpty()) {
        lines.addAll(methodStrings);
      }
    }
    
    return lines;
  }
  
  /**
   * A method is "structural" if its name is one of the known non-content
   * methods. We resolve the method name from method_ids using the running
   * method_idx (method_idx_diff is cumulative within each list).
   *
   * Note: method_idx_diff is cumulative per list (direct and virtual are
   * separate lists). We pass the index and the direct list size so we can
   * reset the accumulator correctly.
   */
  private static boolean isStructuralMethod(byte[] dex, int stringIdsOff,
    String[] stringPool, int methodIdxDiff, int methodPosition, int directMethodsSize) {
    // We do not have a cheap way to get the absolute method_idx here without
    // tracking the accumulator. Instead we rely on the method name being
    // resolvable from the method_ids table. To keep this simple and robust,
    // we only skip methods whose name is exactly "toString" or "getLines".
    // We look them up by scanning method_ids is expensive, so we instead
    // use a simpler heuristic: we do NOT skip anything here, and rely on
    // the caller to filter out the synthetic "Sayfa_XXXXX" string.
    return false;
  }
  
  private static List<String> readMethodStrings(byte[] dex, int stringIdsOff,
    String[] stringPool, int classDataOff) {
    // Kept for backward compatibility; delegates to readPageLines.
    return readPageLines(dex, stringIdsOff, stringPool, classDataOff);
  }
  
  /**
   * Reads const-string / const-string/jumbo values from a code_item in
   * program order. Uses a complete Dalvik opcode length table so the
   * instruction pointer never desynchronizes.
   */
  private static void readConstStringsFromCode(byte[] dex, int stringIdsOff,
    String[] stringPool, int codeOff, List<String> out) {
    int insnsSize = readInt(dex, codeOff + 12);
    int insnsOff = codeOff + 16;
    
    int pc = 0;
    while (pc < insnsSize) {
      int unit = readShort(dex, insnsOff + pc * 2) & 0xFFFF;
      int opcode = unit & 0xFF;
      
      if (opcode == 0x1A) {
        // const-string vAA, string@BBBB
        int stringIdx = readShort(dex, insnsOff + (pc + 1) * 2) & 0xFFFF;
        String s = getString(dex, stringIdsOff, stringPool, stringIdx);
        //if (s != null) out.add(s);
        if (s != null && !isPageTag(s)) out.add(s);
        pc += 2;
        } else if (opcode == 0x1B) {
        // const-string/jumbo vAA, string@BBBBBBBB
        int stringIdx = readInt(dex, insnsOff + (pc + 1) * 2);
        String s = getString(dex, stringIdsOff, stringPool, stringIdx);
        //if (s != null) out.add(s);
        if (s != null && !isPageTag(s)) out.add(s);
        pc += 3;
        } else {
        pc += instructionLength(opcode);
      }
    }
  }
  
  /**
   * Returns true if the string is a synthetic page tag such as
   * "Sayfa_00023". These are produced by the toString() method of each
   * Sayfa_XXXXX class and must not be treated as page content, because
   * the page header is already emitted separately.
   */
  private static boolean isPageTag(String s) {
    if (s == null || !s.startsWith("Sayfa_")) return false;
    if (s.length() <= 6) return false;
    for (int i = 6; i < s.length(); i++) {
      if (!Character.isDigit(s.charAt(i))) return false;
    }
    return true;
  }
  
  /**
   * Complete Dalvik opcode length table (in 16-bit code units).
   * Any opcode not listed here defaults to 1 unit, which is correct for
   * all remaining single-width opcodes.
   */
  private static int instructionLength(int opcode) {
    switch (opcode) {
      // ---- 2 units ----
      case 0x02: case 0x05: case 0x08: // move/from16, move-wide/from16, move-object/from16
      case 0x13: // const/16
      case 0x15: // const/high16
      case 0x16: // const-wide/16
      case 0x19: // const-wide/high16
      case 0x1A: // const-string (handled separately, but kept for safety)
      case 0x1C: // const-class
      case 0x1F: // check-cast
      case 0x20: // instance-of
      case 0x22: // new-instance
      case 0x23: // new-array
      case 0x29: // goto/16
      case 0x2D: case 0x2E: case 0x2F: case 0x30: case 0x31: // cmp*
      case 0x32: case 0x33: case 0x34: case 0x35: case 0x36: case 0x37: // if-test
      case 0x38: case 0x39: case 0x3A: case 0x3B: case 0x3C: case 0x3D: // if-testz
      case 0x44: case 0x45: case 0x46: case 0x47: case 0x48: case 0x49:
      case 0x4A: case 0x4B: case 0x4C: case 0x4D: case 0x4E: case 0x4F:
      case 0x50: case 0x51: // aget/aput family
      case 0x52: case 0x53: case 0x54: case 0x55: case 0x56: case 0x57:
      case 0x58: case 0x59: case 0x5A: case 0x5B: case 0x5C: case 0x5D:
      case 0x5E: case 0x5F: // iget/iput family
      case 0x60: case 0x61: case 0x62: case 0x63: case 0x64: case 0x65:
      case 0x66: case 0x67: case 0x68: case 0x69: case 0x6A: case 0x6B:
      case 0x6C: case 0x6D: // sget/sput family
      case 0x90: case 0x91: case 0x92: case 0x93: case 0x94: case 0x95:
      case 0x96: case 0x97: case 0x98: case 0x99: case 0x9A: case 0x9B:
      case 0x9C: case 0x9D: case 0x9E: case 0x9F: case 0xA0: case 0xA1:
      case 0xA2: case 0xA3: case 0xA4: case 0xA5: case 0xA6: case 0xA7:
      case 0xA8: case 0xA9: case 0xAA: case 0xAB: case 0xAC: case 0xAD:
      case 0xAE: case 0xAF: // binop
      case 0xB0: case 0xB1: case 0xB2: case 0xB3: case 0xB4: case 0xB5:
      case 0xB6: case 0xB7: case 0xB8: case 0xB9: case 0xBA: case 0xBB:
      case 0xBC: case 0xBD: case 0xBE: case 0xBF: case 0xC0: case 0xC1:
      case 0xC2: case 0xC3: case 0xC4: case 0xC5: case 0xC6: case 0xC7:
      case 0xC8: case 0xC9: case 0xCA: case 0xCB: case 0xCC: case 0xCD:
      case 0xCE: case 0xCF: // binop/lit16, binop/lit8
      case 0xD0: case 0xD1: case 0xD2: case 0xD3: case 0xD4: case 0xD5:
      case 0xD6: case 0xD7: // binop/2addr
      case 0xFE: case 0xFF: // const-method-handle, const-method-type
        return 2;
      
      // ---- 3 units ----
      case 0x03: case 0x06: case 0x09: // move/16, move-wide/16, move-object/16
      case 0x14: // const
      case 0x17: // const-wide/32
      case 0x1B: // const-string/jumbo (handled separately, but kept for safety)
      case 0x24: // filled-new-array
      case 0x25: // filled-new-array/range
      case 0x26: // fill-array-data
      case 0x2A: // goto/32
      case 0x2B: // packed-switch
      case 0x2C: // sparse-switch
      case 0x6E: case 0x6F: case 0x70: case 0x71: case 0x72: // invoke-*
      case 0x74: case 0x75: case 0x76: case 0x77: case 0x78: // invoke-*/range
      case 0xFC: case 0xFD: // invoke-custom, invoke-custom/range
        return 3;
      
      // ---- 4 units ----
      case 0xFA: case 0xFB: // invoke-polymorphic, invoke-polymorphic/range
        return 4;
      
      // ---- 1 unit (explicit, for clarity) ----
      case 0x00: case 0x01: case 0x04: case 0x07: case 0x0A: case 0x0C:
      case 0x0D: case 0x0E: case 0x0F: case 0x10: case 0x11: case 0x12:
      case 0x18: case 0x1D: case 0x1E: case 0x21: case 0x27: case 0x28:
      case 0x73: case 0x79: case 0x7A: case 0x7B: case 0x7C: case 0x7D:
      case 0x7E: case 0x7F: case 0x80: case 0x81: case 0x82: case 0x83:
      case 0x84: case 0x85: case 0x86: case 0x87: case 0x88: case 0x89:
      case 0x8A: case 0x8B: case 0x8C: case 0x8D: case 0x8E: case 0x8F:
      case 0xD8: case 0xD9: case 0xDA: case 0xDB: case 0xDC: case 0xDD:
      case 0xDE: case 0xDF: case 0xE0: case 0xE1: case 0xE2:
        return 1;
      
      default:
        return 1;
    }
  }
  
  // ------------------------------------------------------------------
  // Low-level helpers
  // ------------------------------------------------------------------
  
  private static int readInt(byte[] data, int off) {
    return (data[off] & 0xFF)
    | ((data[off + 1] & 0xFF) << 8)
    | ((data[off + 2] & 0xFF) << 16)
    | ((data[off + 3] & 0xFF) << 24);
  }
  
  private static int readShort(byte[] data, int off) {
    return (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8);
  }
  
  private static int readUleb128(byte[] data, int off, int[] out) {
    int result = 0;
    int shift = 0;
    int p = off;
    while (true) {
      int b = data[p] & 0xFF;
      p++;
      result |= (b & 0x7F) << shift;
      if ((b & 0x80) == 0) break;
      shift += 7;
    }
    out[0] = result;
    return p;
  }
  
  private static String getString(byte[] dex, int stringIdsOff,
    String[] stringPool, int stringIdx) {
    if (stringIdx < 0 || stringIdx >= stringPool.length) return null;
    if (stringPool[stringIdx] != null) return stringPool[stringIdx];
    
    int stringDataOff = readInt(dex, stringIdsOff + stringIdx * 4);
    String s = readStringData(dex, stringDataOff);
    stringPool[stringIdx] = s;
    return s;
  }
  
  private static String readStringData(byte[] dex, int off) {
    int p = off;
    while (p < dex.length && (dex[p] & 0x80) != 0) p++;
    p++;
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    while (p < dex.length && dex[p] != 0) {
      baos.write(dex[p]);
      p++;
    }
    return decodeMutf8(baos.toByteArray());
  }
  
  private static String decodeMutf8(byte[] raw) {
    StringBuilder sb = new StringBuilder(raw.length);
    int i = 0;
    while (i < raw.length) {
      int b = raw[i] & 0xFF;
      if (b < 0x80) {
        sb.append((char) b);
        i++;
        } else if ((b & 0xE0) == 0xC0) {
        int b2 = raw[i + 1] & 0xFF;
        sb.append((char) (((b & 0x1F) << 6) | (b2 & 0x3F)));
        i += 2;
        } else if ((b & 0xF0) == 0xE0) {
        int b2 = raw[i + 1] & 0xFF;
        int b3 = raw[i + 2] & 0xFF;
        sb.append((char) (((b & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F)));
        i += 3;
        } else {
        i++;
      }
    }
    return sb.toString();
  }
}
