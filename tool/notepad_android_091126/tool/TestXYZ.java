import java.io.BufferedReader;
import java.io.StringReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestXYZ {

    // Pre-compiled patterns matching your configuration
    private static final Pattern UNI_PATTERN = Pattern.compile("(?u)\\b(\\p{L}+)(\\d+)\\b");
    private static final Pattern FOOTNOTE_PATTERN = Pattern.compile("^(\\d+)\\.\\s*([^:]+):\\s*(.+)$");
    private static final Pattern DIYEZ_PATTERN = Pattern.compile("^\\d+[\\.\\-:]\\s*.*");
    private static final Pattern Y_PATTERN = Pattern.compile("[\\.\\-:]");
    
    private static final String START_FN = "#################@";
    private static final String END_FN = "#################&";

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("       STARTING STANDALONE V2UTILS TEST           ");
        System.out.println("==================================================");

        // --- TEST 1: Mock input for getDiyezString ---
        String sampleInput1 = "Bu bir normal satir.\n" +
                              "1. Dipnot icerigi buraya gelecek:\n" +
                              "2- Ikinci dipnot metni testi.\n" +
                              "Diger normal bir satir.";

        System.out.println("\n[TEST 1] Running getDiyezString...");
        try {
            String result1 = getDiyezString(sampleInput1);
            System.out.println("-> SUCCESS! Output length: " + result1.length());
            System.out.println("--- Output Content ---");
            System.out.print(result1);
            System.out.println("----------------------");
        } catch (Exception e) {
            System.out.println("-> CRASH DETECTED IN TEST 1!");
            e.printStackTrace();
        }

        // --- TEST 2: Mock input for processBlocksSeparately ---
        String sampleInput2 = "Kitap metni burasi Kitap1 ve Madde2 referanslari barindirir.\n" +
                              START_FN + ".\n" +
                              "1. Kitap: Bu birinci referans aciklamasidir.\n" +
                              "2. Madde: Bu ikinci referans aciklamasidir.\n" +
                              END_FN + ".\n" +
                              "Son normal satir.";

        System.out.println("\n[TEST 2] Running processBlocksSeparately...");
        try {
            String result2 = processBlocksSeparately(sampleInput2);
            System.out.println("-> SUCCESS! Output length: " + result2.length());
            System.out.println("--- Output Content ---");
            System.out.print(result2);
            System.out.println("----------------------");
        } catch (Exception e) {
            System.out.println("-> CRASH DETECTED IN TEST 2!");
            e.printStackTrace();
        }

        System.out.println("\n==================================================");
        System.out.println("            TEST EXECUTION COMPLETED              ");
        System.out.println("==================================================");
    }

    // =========================================================================
    // V2Utils Engine Methods under diagnosis
    // =========================================================================

    public static String getDiyezString(final String text) {
        if (text == null || text.isEmpty()) return "";

        StringBuilder result = new StringBuilder(text.length() + 512); 
        Set<Integer> footnoteLines = new HashSet<Integer>(); 
        java.util.List<String> lines = new ArrayList<String>();

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new StringReader(text));
            String line;
            int lineIndex = 0;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (DIYEZ_PATTERN.matcher(line.trim()).matches()) {
                    footnoteLines.add(lineIndex);
                }
                lineIndex++;
            }
        } catch (IOException e) {
            return text; 
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException ignored) {}
            }
        }

        boolean inFootnoteBlock = false;
        int totalLines = lines.size();

        for (int i = 0; i < totalLines; i++) {
            String line = lines.get(i);

            if (footnoteLines.contains(i)) { 
                if (!inFootnoteBlock) {
                    result.append("\n").append(START_FN).append(".\n");
                    inFootnoteBlock = true;
                }

                String[] parts = Y_PATTERN.split(line.trim(), -2);

                if (parts != null && parts.length >= 2) {
                    String number = parts[0];
                    String description = (parts[1] != null) ? parts[1].trim() : "";
                    if (!description.isEmpty() && !description.endsWith(".")) {
                        description = description + ".";
                    }
                    result.append(number).append(". ").append(description).append("\n");
                } else {
                    result.append(line).append("\n");
                }

                if (i + 1 >= totalLines || !footnoteLines.contains(i + 1)) {
                    result.append(END_FN).append(".\n");
                    inFootnoteBlock = false;
                }
            } else {
                result.append(line).append("\n");
            }
        }

        return result.toString();
    }

    public static String processBlocksSeparately(final String text) {
        if (text == null || text.isEmpty()) return "";

        StringBuilder result = new StringBuilder(text.length());
        StringBuilder currentTextBuffer = new StringBuilder(512);
        StringBuilder currentFootnoteBuffer = new StringBuilder(256);
        
        boolean insideFootnoteBlock = false;
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new StringReader(text));
            String line;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.startsWith(START_FN + ".")) {
                    insideFootnoteBlock = true;
                    currentFootnoteBuffer.setLength(0); 
                    currentFootnoteBuffer.append(line).append("\n");
                } 
                else if (trimmed.startsWith(END_FN + ".")) {
                    currentFootnoteBuffer.append(line).append("\n");

                    Map<String, String> footnoteMap = parseFootnotesFromBlock(currentFootnoteBuffer.toString());
                    String processedText = applyFootnotesToText(currentTextBuffer.toString(), footnoteMap);
                    result.append(processedText);

                    currentTextBuffer.setLength(0);
                    insideFootnoteBlock = false;
                } 
                else if (insideFootnoteBlock) {
                    currentFootnoteBuffer.append(line).append("\n");
                } 
                else {
                    currentTextBuffer.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            return text; 
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException ignored) {}
            }
        }

        if (currentTextBuffer.length() > 0) {
            result.append(currentTextBuffer.toString());
        }

        return result.toString();
    }
    
    public static Map<String, String> parseFootnotesFromBlock(String footnoteBlock) {
        Map<String, String> footnoteMap = new LinkedHashMap<String, String>();
        
        if (footnoteBlock == null || footnoteBlock.isEmpty()) {
            return footnoteMap;
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new StringReader(footnoteBlock));
            String line;
            
            while ((line = reader.readLine()) != null) {
                Matcher matcher = FOOTNOTE_PATTERN.matcher(line.trim());
                
                if (matcher.matches() && matcher.groupCount() >= 3) {
                    String number = matcher.group(1);
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
        } catch (IOException e) {
            return footnoteMap;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException ignored) {}
            }
        }
        
        return footnoteMap;
    }
    
    public static String applyFootnotesToText(String text, Map<String, String> footnoteMap) {
        if (text == null || text.isEmpty() || footnoteMap == null || footnoteMap.isEmpty()) {
            return text;
        }
        
        Matcher matcher = UNI_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer(text.length() + 256);
        
        while (matcher.find()) {
            if (matcher.groupCount() >= 2) {
                String word = matcher.group(1);
                String number = matcher.group(2);
                
                if (word != null && number != null) {
                    String definition = footnoteMap.get(number);
                    if (definition != null) {
                        matcher.appendReplacement(result, Matcher.quoteReplacement(word + " [" + definition + "]"));
                    } else {
                        String fullMatch = matcher.group(0);
                        matcher.appendReplacement(result, Matcher.quoteReplacement(fullMatch != null ? fullMatch : ""));
                    }
                }
            }
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
}
