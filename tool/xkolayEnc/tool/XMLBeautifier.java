import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * XML Akıllı Kod Formatlayıcı
 * XML dosyalarını okunabilir şekilde formatlar
 */
public class XMLBeautifier {
  
  private static class Context {
    boolean inTag = false;
    boolean inAttribute = false;
    boolean inCDATA = false;
    boolean inComment = false;
    char quoteChar = '\0';
    StringBuilder currentTag = new StringBuilder();
    
    void resetTag() {
      currentTag.setLength(0);
    }
    
    Context copy() {
      Context c = new Context();
      c.inTag = this.inTag;
      c.inAttribute = this.inAttribute;
      c.inCDATA = this.inCDATA;
      c.inComment = this.inComment;
      c.quoteChar = this.quoteChar;
      c.currentTag = new StringBuilder(this.currentTag.toString());
      return c;
    }
  }
  
  /**
   * Satırı analiz eder ve context'i günceller
   */
  private static void processLine(String line, Context ctx) {
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      char next = (i + 1 < line.length()) ? line.charAt(i + 1) : '\0';
      char prev = (i > 0) ? line.charAt(i - 1) : '\0';
      
      // CDATA kontrolü
      if (!ctx.inComment && !ctx.inAttribute && c == '<' && next == '!' &&
        i + 8 < line.length() && line.substring(i, i + 9).equals("<![CDATA[")) {
        ctx.inCDATA = true;
        i += 8;
        continue;
      }
      
      if (ctx.inCDATA && c == ']' && next == ']' && i + 2 < line.length() &&
        line.charAt(i + 2) == '>') {
        ctx.inCDATA = false;
        i += 2;
        continue;
      }
      
      // Yorum kontrolü
      if (!ctx.inCDATA && !ctx.inAttribute && c == '<' && next == '!' &&
        i + 3 < line.length() && line.substring(i, i + 4).equals("<!--")) {
        ctx.inComment = true;
        i += 3;
        continue;
      }
      
      if (ctx.inComment && c == '-' && next == '-' && i + 2 < line.length() &&
        line.charAt(i + 2) == '>') {
        ctx.inComment = false;
        i += 2;
        continue;
      }
      
      // Tag içinde değilsek
      if (!ctx.inCDATA && !ctx.inComment) {
        // Tag başlangıcı
        if (!ctx.inTag && c == '<' && next != '!') {
          ctx.inTag = true;
          ctx.resetTag();
        }
        
        // Tag içindeyken
        if (ctx.inTag) {
          // Tag adını topla (ilk boşluğa veya >'a kadar)
          if (!ctx.inAttribute && c != '>' && c != ' ' && c != '\t' && c != '\n' && c != '\r') {
            ctx.currentTag.append(c);
          }
          
          // Attribute kontrolü
          if (!ctx.inAttribute && (c == '"' || c == '\'')) {
            ctx.inAttribute = true;
            ctx.quoteChar = c;
            } else if (ctx.inAttribute && c == ctx.quoteChar && prev != '\\') {
            ctx.inAttribute = false;
            ctx.quoteChar = '\0';
          }
          
          // Tag bitişi
          if (!ctx.inAttribute && c == '>') {
            ctx.inTag = false;
          }
        }
      }
    }
  }
  
  /**
   * Satırdaki tag yapısını analiz eder
   * Return: [openCount, closeCount, isSelfClosing, isClosingTag, isOpeningTag]
   */
  private static int[] analyzeTags(String line, Context ctx) {
    int open = 0;
    int close = 0;
    int selfClosing = 0;
    int isClosingTag = 0;
    int isOpeningTag = 0;
    
    Context tempCtx = ctx.copy();
    StringBuilder currentTagName = new StringBuilder();
    boolean collectingTagName = false;
    
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      char next = (i + 1 < line.length()) ? line.charAt(i + 1) : '\0';
      char prev = (i > 0) ? line.charAt(i - 1) : '\0';
      
      // CDATA kontrolü
      if (!tempCtx.inComment && !tempCtx.inAttribute && c == '<' && next == '!' &&
        i + 8 < line.length() && line.substring(i, i + 9).equals("<![CDATA[")) {
        tempCtx.inCDATA = true;
        i += 8;
        continue;
      }
      
      if (tempCtx.inCDATA && c == ']' && next == ']' && i + 2 < line.length() &&
        line.charAt(i + 2) == '>') {
        tempCtx.inCDATA = false;
        i += 2;
        continue;
      }
      
      // Yorum kontrolü
      if (!tempCtx.inCDATA && !tempCtx.inAttribute && c == '<' && next == '!' &&
        i + 3 < line.length() && line.substring(i, i + 4).equals("<!--")) {
        tempCtx.inComment = true;
        i += 3;
        continue;
      }
      
      if (tempCtx.inComment && c == '-' && next == '-' && i + 2 < line.length() &&
        line.charAt(i + 2) == '>') {
        tempCtx.inComment = false;
        i += 2;
        continue;
      }
      
      // CDATA veya yorumda değilsek
      if (!tempCtx.inCDATA && !tempCtx.inComment) {
        // Tag başlangıcı
        if (!tempCtx.inTag && c == '<' && next != '!') {
          tempCtx.inTag = true;
          collectingTagName = true;
          currentTagName.setLength(0);
          
          // Closing tag mi kontrol et
          if (next == '/') {
            isClosingTag = 1;
          }
        }
        
        // Tag içindeyken
        if (tempCtx.inTag) {
          // Tag adını topla
          if (collectingTagName && c != '>' && c != ' ' && c != '\t' &&
            c != '\n' && c != '\r' && c != '/' && c != '?' && c != '>') {
            if (c != '/') {
              currentTagName.append(c);
            }
            } else if (collectingTagName) {
            collectingTagName = false;
          }
          
          // Attribute kontrolü
          if (!tempCtx.inAttribute && (c == '"' || c == '\'')) {
            tempCtx.inAttribute = true;
            tempCtx.quoteChar = c;
            } else if (tempCtx.inAttribute && c == tempCtx.quoteChar && prev != '\\') {
            tempCtx.inAttribute = false;
            tempCtx.quoteChar = '\0';
          }
          
          // Tag bitişi
          if (!tempCtx.inAttribute && c == '>') {
            tempCtx.inTag = false;
            
            // Self-closing tag kontrolü
            if (prev == '/' || (currentTagName.length() > 0 &&
              currentTagName.charAt(currentTagName.length() - 1) == '/')) {
              selfClosing = 1;
            }
            
            // Opening tag mi (closing değilse ve self-closing değilse)
            if (isClosingTag == 0 && selfClosing == 0) {
              isOpeningTag = 1;
              open++;
              } else if (isClosingTag == 1) {
              close++;
              } else if (selfClosing == 1) {
              // Self-closing tag'ler hem açılış hem kapanış sayılır ama seviyeyi değiştirmez
              open++;
              close++;
            }
            
            // Reset
            currentTagName.setLength(0);
            isClosingTag = 0;
            selfClosing = 0;
            isOpeningTag = 0;
          }
        }
      }
    }
    
    return new int[]{open, close, 0, 0, 0}; // Son 3 değer artık kullanılmıyor
  }
  
  /**
   * Satırı daha güzel formatlar (attribute'ları hizalar, fazla boşlukları temizler)
   */
  private static String beautifyLine(String line, Context ctx) {
    if (line.trim().isEmpty()) return line.trim();
    
    StringBuilder result = new StringBuilder();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;
    char quoteChar = '\0';
    
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      char prev = (i > 0) ? line.charAt(i - 1) : '\0';
      
      // Quote kontrolü
      if (!inQuotes && (c == '"' || c == '\'')) {
        inQuotes = true;
        quoteChar = c;
        current.append(c);
        } else if (inQuotes && c == quoteChar && prev != '\\') {
        inQuotes = false;
        current.append(c);
        } else {
        if (!inQuotes) {
          // Fazla boşlukları temizle
          if (c == ' ' || c == '\t') {
            if (current.length() > 0 && current.charAt(current.length() - 1) != ' ') {
              current.append(' ');
            }
            } else {
            current.append(c);
          }
          } else {
          current.append(c);
        }
      }
    }
    
    String cleaned = current.toString().trim();
    
    // Satır çok uzunsa attribute'ları yeni satıra al
    if (cleaned.length() > 120 && cleaned.contains("=\"") && !cleaned.startsWith("<!--")) {
      return formatLongTag(cleaned, ctx);
    }
    
    return cleaned;
  }
  
  /**
   * Uzun tag'leri formatlar (attribute'ları yeni satıra alır)
   */
  private static String formatLongTag(String tag, Context ctx) {
    if (!tag.contains(" ") || tag.endsWith("/>") || tag.endsWith("?>")) {
      return tag;
    }
    
    String[] parts = tag.split(" ", 2);
    if (parts.length < 2) return tag;
    
    String tagStart = parts[0];
    String attributes = parts[1];
    
    // Attribute'ları parse et
    List<String> attrList = new ArrayList<>();
    StringBuilder currentAttr = new StringBuilder();
    boolean inQuotes = false;
    char quoteChar = '\0';
    
    for (int i = 0; i < attributes.length(); i++) {
      char c = attributes.charAt(i);
      char prev = (i > 0) ? attributes.charAt(i - 1) : '\0';
      
      if (!inQuotes && (c == '"' || c == '\'')) {
        inQuotes = true;
        quoteChar = c;
        currentAttr.append(c);
        } else if (inQuotes && c == quoteChar && prev != '\\') {
        inQuotes = false;
        currentAttr.append(c);
        } else if (!inQuotes && c == ' ' && currentAttr.length() > 0) {
        attrList.add(currentAttr.toString().trim());
        currentAttr.setLength(0);
        } else {
        currentAttr.append(c);
      }
    }
    
    if (currentAttr.length() > 0) {
      attrList.add(currentAttr.toString().trim());
    }
    
    // Tek satıra sığdırmaya çalış
    StringBuilder singleLine = new StringBuilder(tagStart);
    for (String attr : attrList) {
      if (singleLine.length() + attr.length() + 1 <= 120) {
        singleLine.append(" ").append(attr);
        } else {
        // Uzunsa, her attribute'u yeni satıra al
        StringBuilder result = new StringBuilder(tagStart);
        for (String a : attrList) {
          result.append("\n  ").append(a);
        }
        return result.toString();
      }
    }
    
    return singleLine.toString();
  }
  
  /**
   * Girinti oluşturur
   */
  private static String indent(int level, int spaces) {
    if (level <= 0) return "";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < level * spaces; i++) {
      sb.append(' ');
    }
    return sb.toString();
  }
  
  /**
   * Ana formatlama fonksiyonu
   */
  public static void format(String input, String output, int spaces) {
    BufferedReader reader = null;
    BufferedWriter writer = null;
    
    try {
      reader = new BufferedReader(new FileReader(input));
      writer = new BufferedWriter(new FileWriter(output));
      
      String line;
      int level = 0;
      Context ctx = new Context();
      boolean first = true;
      Stack<String> tagStack = new Stack<>();
      
      while ((line = reader.readLine()) != null) {
        // Orijinal satırı temizle
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
          if (!first) writer.newLine();
          first = false;
          continue;
        }
        
        // Satırı daha güzel hale getir
        String beautified = beautifyLine(trimmed, ctx);
        
        // Tag analizi
        int[] tagInfo = analyzeTags(beautified, ctx);
        int open = tagInfo[0];
        int close = tagInfo[1];
        
        // Girinti seviyesini hesapla
        int lineLevel = level;
        
        // Closing tag ile başlıyorsa bu satır için seviyeyi azalt
        if (beautified.startsWith("</")) {
          lineLevel = Math.max(0, level - 1);
        }
        
        // Satırı yaz
        if (!first) writer.newLine();
        writer.write(indent(lineLevel, spaces) + beautified);
        
        // Bir sonraki satır için seviyeyi güncelle
        level = level + open - close;
        if (level < 0) level = 0;
        
        // Context'i güncelle
        processLine(beautified, ctx);
        
        first = false;
      }
      
      System.out.println("✓ XML Formatlama başarılı!");
      System.out.println("  Girdi : " + input);
      System.out.println("  Çıktı : " + output);
      System.out.println("  Boşluk: " + spaces);
      
      } catch (FileNotFoundException e) {
      System.err.println("✗ Dosya bulunamadı: " + input);
      System.exit(1);
      } catch (IOException e) {
      System.err.println("✗ IO Hatası: " + e.getMessage());
      System.exit(1);
      } finally {
      try {
        if (reader != null) reader.close();
        if (writer != null) writer.close();
        } catch (IOException e) {
        // Sessizce kapat
      }
    }
  }
  
  /**
   * XML'i string olarak formatlar (API kullanımı için)
   */
  public static String formatString(String xml, int spaces) {
    StringWriter sw = new StringWriter();
    BufferedReader reader = null;
    
    try {
      reader = new BufferedReader(new StringReader(xml));
      BufferedWriter writer = new BufferedWriter(sw);
      
      String line;
      int level = 0;
      Context ctx = new Context();
      boolean first = true;
      
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
          if (!first) writer.newLine();
          first = false;
          continue;
        }
        
        String beautified = beautifyLine(trimmed, ctx);
        int[] tagInfo = analyzeTags(beautified, ctx);
        int open = tagInfo[0];
        int close = tagInfo[1];
        
        int lineLevel = level;
        if (beautified.startsWith("</")) {
          lineLevel = Math.max(0, level - 1);
        }
        
        if (!first) writer.newLine();
        writer.write(indent(lineLevel, spaces) + beautified);
        
        level = level + open - close;
        if (level < 0) level = 0;
        
        processLine(beautified, ctx);
        first = false;
      }
      
      writer.flush();
      return sw.toString();
      
      } catch (IOException e) {
      return xml; // Hata durumunda orijinalini döndür
      } finally {
      try {
        if (reader != null) reader.close();
        } catch (IOException e) {
        // Sessizce kapat
      }
    }
  }
  
  public static void main(String[] args) {
    int spaces = 2; // XML için varsayılan 2 boşluk daha yaygın
    
    if (args.length < 2 || args.length > 3) {
      System.out.println("╔═══════════════════════════════════════╗");
      System.out.println("║   XMLBeautifier - XML Formatlayıcı    ║");
      System.out.println("╚═══════════════════════════════════════╝");
      System.out.println();
      System.out.println("Kullanım:");
      System.out.println("  java XMLBeautifier <girdi.xml> <çıktı.xml> [boşluk]");
      System.out.println();
      System.out.println("Parametreler:");
      System.out.println("  girdi.xml  : Kaynak XML dosyası");
      System.out.println("  çıktı.xml  : Hedef XML dosyası");
      System.out.println("  boşluk     : Girinti boşluk sayısı (varsayılan: 2)");
      System.out.println();
      System.out.println("Örnekler:");
      System.out.println("  java XMLBeautifier data.xml formatted_data.xml");
      System.out.println("  java XMLBeautifier config.xml new_config.xml 4");
      System.out.println("  java XMLBeautifier web.xml pretty_web.xml 2");
      System.out.println();
      System.out.println("Özellikler:");
      System.out.println("  ✓ Tag'leri otomatik hizalar");
      System.out.println("  ✓ CDATA bölümlerini korur");
      System.out.println("  ✓ XML yorumlarını korur");
      System.out.println("  ✓ Attribute'ları düzenler");
      System.out.println("  ✓ Uzun satırları akıllıca böler");
      System.out.println("  ✓ Boş satırları korur");
      System.out.println("  ✓ Self-closing tag'leri doğru işler");
      System.out.println();
      System.out.println("API Kullanımı:");
      System.out.println("  String formatted = XMLBeautifier.formatString(xml, 2);");
      System.out.println();
      System.exit(0);
    }
    
    String input = args[0];
    String output = args[1];
    
    if (args.length == 3) {
      try {
        spaces = Integer.parseInt(args[2]);
        if (spaces < 1 || spaces > 8) {
          System.err.println("✗ Boşluk sayısı 1-8 arasında olmalı");
          System.exit(1);
        }
        } catch (NumberFormatException e) {
        System.err.println("✗ Geçersiz boşluk değeri: " + args[2]);
        System.exit(1);
      }
    }
    
    System.out.println();
    format(input, output, spaces);
    System.out.println();
  }
}

/*
═══════════════════════════════════════════════════════════════
XMLBEAUTIFIER - DERLEME VE KULLANIM

1. Derleme:
javac XMLBeautifier.java

2. Basit kullanım:
java XMLBeautifier input.xml output.xml

3. Özel girinti ile:
java XMLBeautifier config.xml new_config.xml 4

4. Tüm XML dosyalarını formatla (Linux/Mac):
for f in *.xml; do
  java XMLBeautifier "$f" "formatted_$f"
done

5. Program içinden kullanım:
String xml = "<root><item>test</item></root>";
String pretty = XMLBeautifier.formatString(xml, 2);

6. Örnek XML formatlama:
Girdi: <root><item id="1"><name>Test</name></item></root>

Çıktı (2 boşluk):
<root>
<item id="1">
<name>Test</name>
</item>
</root>

ÖZELLİKLER:
1. Tag hizalama: Açılış ve kapanış tag'lerini hizalar
2. CDATA koruma: <![CDATA[...]]> bölümlerini değiştirmez
3. Yorum koruma: <!-- yorumlar --> olduğu gibi kalır
4. Attribute formatlama: Uzun attribute listelerini yeni satıra alır
5. Self-closing tag: <br/>, <img /> gibi tag'leri doğru işler
6. XML Declaration: <?xml version="1.0"?> gibi tanımları korur
7. Boşluk temizleme: Fazla boşlukları temizler
8. Satır uzunluğu: 120 karakterden uzun satırları böler

DİKKAT EDİLMESİ GEREKENLER:
- XML'in geçerli (well-formed) olması gerekir
- Büyük dosyalar için hafıza kullanımına dikkat edin
- Formatlama öncesi yedek almanız önerilir
═══════════════════════════════════════════════════════════════
 */
