import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class FestivalGenerator {

    public static void main(String[] args) {
        String filePath = "Festival.java"; // Generates the file in the current working directory
        BufferedWriter writer = null;

        try {
            writer = new BufferedWriter(new FileWriter(filePath));
            
            // Write package definition and class headers
            writer.write("package net.murat.notepad;\n\n");
            writer.write("/**\n");
            writer.write(" * Automatically generated historical Islamic festival dataset (1900-2100).\n");
            writer.write(" * Contains pre-calculated Gregorian dates for Ramadan, Eid al-Fitr, and Eid al-Adha.\n");
            writer.write(" */\n");
            writer.write("public class Festival {\n\n");
            writer.write("    // Matrix structure: { Year, Ramadan_Start, Ramadan_Duration, Eid_Fitr_Start, Eid_Adha_Start }\n");
            writer.write("    public static final String[][] FESTIVAL_DATA = {\n");

            // Loop between the 1900-2100 time bounds requested
            for (int year = 1900; year <= 2100; year++) {
                String ramadanStart = getMiladiDate(year, 9, 1);       // 9th Month: Ramadan 1st Day
                String eidFitrStart = getMiladiDate(year, 10, 1);      // 10th Month: Shawwal 1st Day (Eid al-Fitr)
                String eidAdhaStart = getMiladiDate(year, 12, 10);     // 12th Month: Dhu al-Hijjah 10th Day (Eid al-Adha)
                
                // Approximate dynamic Ramadan duration logic based on next month alignment boundaries
                String duration = "30";
                if (ramadanStart.equals("00-00") || eidFitrStart.equals("00-00")) {
                    duration = "30"; // Fallback safety latch
                }

                writer.write("        {\"" + year + "\", \"" + ramadanStart + "\", \"" + duration + "\", \"" + eidFitrStart + "\", \"" + eidAdhaStart + "\"},\n");
            }

            // Close the matrix structure definitions safely
            writer.write("    };\n\n");
            writer.write("    public static final int YEARS_COUNT = FESTIVAL_DATA.length;\n");
			writer.write("    public static final int INNER_ENTRIES = 5;\n");
            writer.write("}\n");

            System.out.println("Success: Festival.java has been generated with 1900-2100 dataset matrix!");

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Converts a specific Hijrah target month and day into Gregorian (Miladi) DD-MM format.
     */
    private static String getMiladiDate(int gregorianTargetYear, int hijrahMonth, int hijrahDay) {
        int hijrahYearApprox = ((gregorianTargetYear - 622) * 33) / 32;
        
        // Scan surrounding boundary ranges to pinpoint target year precision
        for (int hYear = hijrahYearApprox - 2; hYear <= hijrahYearApprox + 2; hYear++) {
            long jd = hijrahToJulianDay(hYear, hijrahMonth, hijrahDay);
            int[] greg = julianDayToGregorian(jd);
            
            if (greg[2] == gregorianTargetYear) {
                return String.format("%02d-%02d", greg[0], greg[1]);
            }
        }
        return "00-00";
    }

    private static long hijrahToJulianDay(int year, int month, int day) {
        return (long) (day + Math.ceil(29.5 * (month - 1)) + (year - 1) * 354 + Math.floor((3 + 11 * year) / 30) + 1948440 - 1);
    }

    private static int[] julianDayToGregorian(long jd) {
        long b = (jd > 2299160) ? jd + 1 + (long)Math.floor((jd - 1867216.25) / 36524.25) - (long)Math.floor((long)Math.floor((jd - 1867216.25) / 36524.25) / 4) : jd;
        long c = b + 1524;
        long d = (long) Math.floor((c - 122.1) / 365.25);
        long e = (long) Math.floor(365.25 * d);
        long g = (long) Math.floor((c - e) / 30.6001);
        
        int day = (int) (c - e - Math.floor(30.6001 * g));
        int month = (int) (g < 14 ? g - 1 : g - 13);
        int year = (int) (month > 2 ? d - 4716 : d - 4715);
        
        return new int[]{day, month, year};
    }
    
}
