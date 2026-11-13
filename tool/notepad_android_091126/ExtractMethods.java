import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ExtractMethods {

    private static Set<String> results = new LinkedHashSet<String>();
    private static int fileCount = 0;
    private static int memberCount = 0;
    private static final String OUTPUT_FILE = "methods_export.txt";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java ExtractMethods <source-directory>");
            System.exit(1);
        }

        File root = new File(args[0]);
        if (!root.exists() || !root.isDirectory()) {
            System.err.println("Error: Please provide a valid directory.");
            System.exit(1);
        }

        scanDirectory(root);
        writeResults(OUTPUT_FILE);

        System.out.println("Scanning completed.");
        System.out.println("Total files processed: " + fileCount);
        System.out.println("Total unique members found: " + memberCount);
        System.out.println("Results saved to: " + OUTPUT_FILE);
    }

    private static void scanDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                scanDirectory(files[i]);
            } else if (files[i].getName().endsWith(".java")) {
                processJavaFile(files[i]);
            }
        }
    }

    private static void processJavaFile(File javaFile) {
        try {
            String content = readFile(javaFile);
            results.add("\n--- File: " + javaFile.getPath() + " ---");
            extractMembers(content, javaFile.getName());
            fileCount++;
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
    }

    private static String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } finally {
            reader.close();
        }
        return sb.toString();
    }

    private static void extractMembers(String content, String fileName) {
        String className = extractClassName(content, fileName);

        // 1. Fields — capture type and name
        // Matches: public [static] [final] SomeType<X>[] fieldName [= ...];
        Pattern fieldP = Pattern.compile(
            "public\\s+(?:static\\s+)?(?:final\\s+)?([\\w<>\\[\\]]+)\\s+(\\w+)(?:\\s*=.*?)?;"
        );
        Matcher fm = fieldP.matcher(content);
        while (fm.find()) {
            String type  = fm.group(1);
            String name  = fm.group(2);
            // Skip if 'type' looks like a modifier keyword that leaked through
            if (type.equals("class") || type.equals("interface") || type.equals("enum")) continue;
            String entry = "[FIELD] " + type + " " + name;
            if (results.add(entry)) memberCount++;
        }

        // 2. Constructors
        String ctorRegex = "public\\s+" + Pattern.quote(className) + "\\s*\\(([^)]*)\\)\\s*(?:\\{|throws|;)";
        Pattern ctorP = Pattern.compile(ctorRegex);
        Matcher cm = ctorP.matcher(content);
        while (cm.find()) {
            String params = cm.group(1).trim();
            String entry = "[CONSTRUCTOR] " + className + "(" + (params.isEmpty() ? "" : params) + ")";
            if (results.add(entry)) memberCount++;
        }

        // 3. Methods — capture return type
        // Group 1: visibility, Group 2: return type, Group 3: method name, Group 4: params
        Pattern mP = Pattern.compile(
            "(?:@Override\\s+)?" +
            "(public|protected)\\s+" +
            "(?:static\\s+)?(?:abstract\\s+)?(?:final\\s+)?" +
            "([\\w<>\\[\\]]+)\\s+" +       // return type
            "(\\w+)\\s*" +                  // method name
            "\\(([^)]*)\\)\\s*" +           // params
            "(?:\\{|throws|;)"
        );
        Matcher mm = mP.matcher(content);
        while (mm.find()) {
            String visibility = mm.group(1).toUpperCase();
            String returnType = mm.group(2);
            String methodName = mm.group(3);
            String params     = mm.group(4).trim();

            // Skip if what we captured as return type is actually a keyword
            if (returnType.equals("class") || returnType.equals("interface") || returnType.equals("enum")) continue;

            String entry = "[" + visibility + " METHOD] " + returnType + " " + methodName
                         + "(" + (params.isEmpty() ? "" : params) + ")";
            if (results.add(entry)) memberCount++;
        }
    }

    private static String extractClassName(String content, String fileName) {
        Pattern p = Pattern.compile("(?:class|interface|enum)\\s+(\\w+)");
        Matcher m = p.matcher(content);
        return m.find() ? m.group(1) : fileName.replace(".java", "");
    }

    private static void writeResults(String outputFile) {
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outputFile));
            String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
            writer.write("Java Public API & Member Extractor Report\n");
            writer.write("Generated on: " + timestamp + "\n");
            writer.write("------------------------------------------\n");
            for (String line : results) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error writing file: " + e.getMessage());
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException ignored) {}
            }
        }
    }
    
}
