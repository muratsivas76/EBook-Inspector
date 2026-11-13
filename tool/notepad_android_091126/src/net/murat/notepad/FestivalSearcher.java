package net.murat.notepad;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
* Historical query engine that parses pre-calculated festival datasets
* to pinpoint exact matching years for a given target day and month.
*/
public class FestivalSearcher {

  /**
  * Iterates over the pre-compiled matrix array to locate overlapping
  * dates for Ramadan, Eid al-Fitr, or Eid al-Adha.
  *
  * @param targetDay   User-selected day threshold (1-31)
  * @param targetMonth User-selected month index boundary (1-12)
  * @return List of aggregated result logs indicating matching contexts
  */
  public java.util.List<String> findYearsForDate(int targetDay, int targetMonth) {
    java.util.List<String> results = new ArrayList<String>();
    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
    Calendar cal = Calendar.getInstance();

    // Safe perimeter loops leveraging your provided array boundaries
    for (int i = 0; i < Festival.YEARS_COUNT; i++) {
      String[] row = Festival.FESTIVAL_DATA[i];

      String year = row[0];
      String ramadanStartStr = row[1];
      int ramadanDuration = Integer.parseInt(row[2]);
      String eidFitrStartStr = row[3];
      String eidAdhaStartStr = row[4];

      try {
        // 1. EVALUATE RAMADAN DURATION LIFESPAN (29 or 30 Days)
        Date ramadanStart = sdf.parse(ramadanStartStr + "-" + year);
        cal.setTime(ramadanStart);
        for (int d = 0; d < ramadanDuration; d++) {
          if (cal.get(Calendar.DAY_OF_MONTH) == targetDay && (cal.get(Calendar.MONTH) + 1) == targetMonth) {
            results.add(year + " - Ramadan (Day " + (d + 1) + ")");
            break;
          }
          cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        // 2. EVALUATE EID AL-FITR HOLIDAY WINDOW (3 Days Fixed)
        Date eidFitrStart = sdf.parse(eidFitrStartStr + "-" + year);
        cal.setTime(eidFitrStart);
        for (int d = 0; d < 3; d++) {
          if (cal.get(Calendar.DAY_OF_MONTH) == targetDay && (cal.get(Calendar.MONTH) + 1) == targetMonth) {
            results.add(year + " - Eid al-Fitr (Day " + (d + 1) + ")");
            break;
          }
          cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        // 3. EVALUATE EID AL-ADHA HOLIDAY WINDOW (4 Days Fixed)
        Date eidAdhaStart = sdf.parse(eidAdhaStartStr + "-" + year);
        cal.setTime(eidAdhaStart);
        for (int d = 0; d < 4; d++) {
          if (cal.get(Calendar.DAY_OF_MONTH) == targetDay && (cal.get(Calendar.MONTH) + 1) == targetMonth) {
            results.add(year + " - Eid al-Adha (Day " + (d + 1) + ")");
            break;
          }
          cal.add(Calendar.DAY_OF_YEAR, 1);
        }

      } catch (ParseException e) {
        // Ignore corrupt calendar formats safely inside pipeline bounds
        e.printStackTrace();
      }
    }
    return results;
  }

}
