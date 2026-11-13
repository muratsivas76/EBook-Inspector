import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * High-Reliability Java Code Beautifier Engine
 * Implements a strict lexer state-machine to handle escape sequences and multi-line literals.
 */
public class CodeBeautifier {
  
    private static class Context {
        boolean inString = false;
        boolean inChar = false;
        boolean inMultiComment = false;
    }
    
    /**
     * Executes the primary formatting routine with strict character escape evaluation.
     */
    public static void format(String input, String output, int spaces) {
        BufferedReader reader = null;
        BufferedWriter writer = null;
        
        try {
            reader = new BufferedReader(new FileReader(input));
            writer = new BufferedWriter(new FileWriter(output));
            
            String line;
            int level = 0;
            Context ctx = new Context(); // Maintained globally across rows to handle multi-line blocks
            boolean first = true;
            
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                
                // Preserve completely vacant structural rows natively
                if (trimmed.isEmpty()) {
                    if (!first) writer.newLine();
                    first = false;
                    continue;
                }
                
                int open = 0;
                int close = 0;
                boolean startsWithClose = false;
                boolean foundFirstMeaningful = false;
                
                // Track active state at the precise start of this physical text line
                boolean initialInComment = ctx.inMultiComment;
                boolean initialInLiteral = ctx.inString || ctx.inChar;
                
                for (int i = 0; i < trimmed.length(); i++) {
                    char c = trimmed.charAt(i);
                    char next = (i + 1 < trimmed.length()) ? trimmed.charAt(i + 1) : '\0';
                    
                    // 1. Handle Multi-line Comment state mutations
                    if (ctx.inMultiComment) {
                        if (c == '*' && next == '/') {
                            ctx.inMultiComment = false;
                            i++; // Skip the slash token
                        }
                        continue; // Ignore everything inside comments completely
                    }
                    
                    // 2. Handle Single-line Comment circuit breaker instantly
                    if (!ctx.inString && !ctx.inChar && c == '/' && next == '/') {
                        break; // Terminate line loop processing; ignore trailing data
                    }
                    
                    if (!ctx.inString && !ctx.inChar && c == '/' && next == '*') {
                        ctx.inMultiComment = true;
                        i++; // Skip the asterisk token
                        continue;
                    }
                    
                    // 3. Advanced Escape Sequence Counter (Prevents backslash duplication faults)
                    boolean isEscaped = false;
                    if (ctx.inString || ctx.inChar) {
                        int backslashCount = 0;
                        int checkIndex = i - 1;
                        while (checkIndex >= 0 && trimmed.charAt(checkIndex) == '\\') {
                            backslashCount++;
                            checkIndex--;
                        }
                        // An odd number of preceding backslashes means this active character is fully escaped
                        if (backslashCount % 2 != 0) {
                            isEscaped = true;
                        }
                    }
                    
                    // 4. Handle String boundary maps safely
                    if (!ctx.inChar && c == '"' && !isEscaped) {
                        ctx.inString = !ctx.inString;
                        continue;
                    }
                    
                    // 5. Handle Character literal boundary maps safely
                    if (!ctx.inString && c == '\'' && !isEscaped) {
                        ctx.inChar = !ctx.inChar;
                        continue;
                    }
                    
                    // 6. Calculate braces only when structurally exposed outside literals or comments
                    if (!ctx.inString && !ctx.inChar) {
                        if (c == '{') {
                            open++;
                            if (!foundFirstMeaningful) {
                                foundFirstMeaningful = true;
                            }
                        } else if (c == '}') {
                            close++;
                            if (!foundFirstMeaningful) {
                                startsWithClose = true;
                                foundFirstMeaningful = true;
                            }
                        } else if (!Character.isWhitespace(c) && !foundFirstMeaningful) {
                            foundFirstMeaningful = true;
                        }
                    }
                }
                
                // Calculate immediate indentation offset levels based on layout flags
                int lineLevel = level;
                
                // Adjust index only if the row cleanly opens/closes context relative to multi-line states
                if (startsWithClose && !initialInComment && !initialInLiteral) {
                    lineLevel = Math.max(0, level - 1);
                }
                
                // Persist the freshly formatted code string out to disk segments
                if (!first) writer.newLine();
                writer.write(indent(lineLevel, spaces) + trimmed);
                
                // Calibrate structural depth boundaries for the next iteration sequence
                level = level + open - close;
                if (level < 0) level = 0;
                
                first = false;
            }
            
            writer.write("\n");
            
            System.out.println("✓ Formatting completed successfully!");
            System.out.println("  Input  : " + input);
            System.out.println("  Output : " + output);
            System.out.println("  Spaces : " + spaces);
            
        } catch (FileNotFoundException e) {
            System.err.println("✗ File not found: " + input);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("✗ I/O Execution Failure: " + e.getMessage());
            System.exit(1);
        } finally {
            try {
                if (reader != null) reader.close();
                if (output != null && writer != null) writer.close();
            } catch (IOException ignored) {}
        }
    }
  
    /**
     * Synthesizes trailing white space tokens matching target indentation criteria.
     */
    private static String indent(int level, int spaces) {
        if (level <= 0) return "";
        StringBuilder sb = new StringBuilder();
        int totalSpaces = level * spaces;
        for (int i = 0; i < totalSpaces; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }
  
    public static void main(String[] args) {
        int spaces = 4;
        
        if (args.length < 2 || args.length > 3) {
            System.out.println("╔═══════════════════════════════════════╗");
            System.out.println("║   CodeBeautifier - Engine Optimizer   ║");
            System.out.println("╚═══════════════════════════════════════╝");
            System.out.println();
            System.out.println("Usage:");
            System.out.println("  java CodeBeautifier <input> <output> [spaces]");
            System.out.println();
            System.exit(0);
        }
        
        String input = args[0];
        String output = args[1];
        
        if (args.length == 3) {
            try {
                spaces = Integer.parseInt(args[2]);
                if (spaces < 1 || spaces > 16) {
                    System.err.println("✗ Space bounds threshold limitation must fall within 1-16 limits.");
                    System.exit(1);
                }
            } catch (NumberFormatException e) {
                System.err.println("✗ Invalid integer notation token format detected: " + args[2]);
                System.exit(1);
            }
        }
        
        System.out.println();
        format(input, output, spaces);
        System.out.println();
    }
    
}
