package net.murat.decodebook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal DEX reader that extracts the String[] contents of the
 * Sayfa_XXXXX classes directly from an APK stream.
 *
 * No external libraries required.
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
    byte[] dex = extractClassesDex(apkStream);
    if (dex == null) {
      throw new IOException("classes.dex not found in APK");
    }
    return extractPages(dex, start, end);
  }
  
  /** Counts how many Sayfa_XXXXX classes exist (highest page number). */
  public static int countPages(InputStream apkStream) throws IOException {
    byte[] dex = extractClassesDex(apkStream);
    if (dex == null) {
      throw new IOException("classes.dex not found in APK");
    }
    return countPages(dex);
  }
  
  // ------------------------------------------------------------------
  // APK / DEX extraction
  // ------------------------------------------------------------------
  
  private static byte[] extractClassesDex(InputStream apkStream) throws IOException {
    ZipInputStream zis = new ZipInputStream(apkStream);
    ZipEntry entry;
    while ((entry = zis.getNextEntry()) != null) {
      if ("classes.dex".equals(entry.getName())) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = zis.read(buffer)) != -1) {
          baos.write(buffer, 0, len);
        }
        zis.closeEntry();
        zis.close();
        return baos.toByteArray();
      }
      zis.closeEntry();
    }
    zis.close();
    return null;
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
      
      List<String> lines = readMethodStrings(dex, stringIdsOff, stringPool, classDataOff);
      if (lines.isEmpty()) continue;
      
      StringBuilder sb = new StringBuilder();
      for (String line : lines) {
        sb.append(line).append("\n");
      }
      sb.append("### ").append(simpleName(className)).append(" ###\n");
      pages.add(sb.toString());
    }
    
    java.util.Collections.sort(pages, new java.util.Comparator<String>() {
        @Override
        public int compare(String a, String b) {
          return extractPageTag(a).compareTo(extractPageTag(b));
        }
    });
    
    return pages;
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
  
  private static String extractPageTag(String block) {
    int a = block.indexOf("Sayfa_");
    if (a < 0) return "";
    int b = a + "Sayfa_".length();
    while (b < block.length() && Character.isDigit(block.charAt(b))) b++;
    return block.substring(a, b);
  }
  
  // ------------------------------------------------------------------
  // class_data_item -> method -> code -> const-string
  // ------------------------------------------------------------------
  
  private static List<String> readMethodStrings(byte[] dex, int stringIdsOff,
    String[] stringPool, int classDataOff) {
    List<String> result = new ArrayList<String>();
    if (classDataOff == 0) return result;
    
    int p = classDataOff;
    int[] u = new int[4];
    
    p = readUleb128(dex, p, u);
    int staticFieldsSize = u[0];
    p = readUleb128(dex, p, u);
    int instanceFieldsSize = u[0];
    p = readUleb128(dex, p, u);
    int directMethodsSize = u[0];
    p = readUleb128(dex, p, u);
    int virtualMethodsSize = u[0];
    
    for (int i = 0; i < staticFieldsSize + instanceFieldsSize; i++) {
      p = readUleb128(dex, p, u);
      p = readUleb128(dex, p, u);
    }
    
    for (int i = 0; i < directMethodsSize + virtualMethodsSize; i++) {
      p = readUleb128(dex, p, u);
      p = readUleb128(dex, p, u);
      p = readUleb128(dex, p, u);
      int codeOff = u[0];
      
      if (codeOff != 0) {
        readConstStringsFromCode(dex, stringIdsOff, stringPool, codeOff, result);
        if (!result.isEmpty()) break;
      }
    }
    
    return result;
  }
  
  private static void readConstStringsFromCode(byte[] dex, int stringIdsOff,
    String[] stringPool, int codeOff,
    List<String> out) {
    int insnsSize = readInt(dex, codeOff + 12);
    int insnsOff = codeOff + 16;
    
    int pc = 0;
    while (pc < insnsSize) {
      int unit = readShort(dex, insnsOff + pc * 2) & 0xFFFF;
      int opcode = unit & 0xFF;
      
      if (opcode == 0x1A) {
        int stringIdx = readShort(dex, insnsOff + (pc + 1) * 2) & 0xFFFF;
        String s = getString(dex, stringIdsOff, stringPool, stringIdx);
        if (s != null) out.add(s);
        pc += 2;
        } else if (opcode == 0x1B) {
        int stringIdx = readInt(dex, insnsOff + (pc + 1) * 2);
        String s = getString(dex, stringIdsOff, stringPool, stringIdx);
        if (s != null) out.add(s);
        pc += 3;
        } else {
        pc += instructionLength(opcode);
      }
    }
  }
  
  private static int instructionLength(int opcode) {
    switch (opcode) {
      case 0x23: case 0x4D:
      case 0x0A: case 0x0B: case 0x0C:
      case 0x07: case 0x08: case 0x09:
      case 0x12: case 0x13: case 0x14: case 0x15:
      case 0x16: case 0x17: case 0x18: case 0x19:
      case 0x6E: case 0x6F: case 0x70: case 0x71: case 0x72:
      case 0x74: case 0x75: case 0x76: case 0x77: case 0x78:
      case 0x0F: case 0x10: case 0x11:
        return 2;
      case 0x0E:
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
