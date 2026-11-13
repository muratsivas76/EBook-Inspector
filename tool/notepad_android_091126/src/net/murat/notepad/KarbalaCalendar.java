package net.murat.notepad;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
* High-performance calendar engine utility for calculating and retrieving
* Islamic historical events and Ehli Beyt metrics using standardized US Locale formats.
*
* @author Murat iNAN
*/
public class KarbalaCalendar {

  // Enforcing strict US locale constraints to prevent local device formatting corruption
  private static final Locale strictLocale = Locale.US;

  /**
  * Map of Islamic events based on Hijri calendar structure.
  * Array signature: { Hijri Month, Hijri Day, Raw Event Description String }
  */
  private static final Object[][] KARBALA_EVENTS = {
    {1, 1,  "Hijri New Year"},
    {1, 10, "Day of Ashura (Martyrdom of Imam Husayn, 10th October 680)"},
    {2, 20, "Arbaeen (40th Day of Karbala Martyrs)"},
    {3, 11, "Mawlid al-Nabi (Birth of the Prophet)"},
    {6, 20, "Birth Anniversary of Lady Fatimah al-Zahra"},
    {7, 13, "Birth Anniversary of Amir al-Mu'minin Imam Ali"},
    {7, 26, "Laylat al-Mi'raj (The Ascension)"},
    {8, 3,  "Birth Anniversary of Imam Husayn al-Shahid"},
    {8, 14, "Laylat al-Bara'ah (Night of Salvation)"},
    {9, 1,  "First Day of Holy Ramadan"},
    {9, 15, "Birth Anniversary of Imam Hasan al-Mujtaba"},
    {9, 26, "Laylat al-Qadr (Night of Power)"},
    {10, 1, "Eid al-Fitr (Ramadan Feast)"},
    {12, 10, "Eid al-Adha (Feast of Sacrifice)"},
    {12, 18, "Eid al-Ghadir (Declaration of Imamate)"}
  };

  /**
  * Private constructor to enforce utility pattern constraint bounds.
  */
  private KarbalaCalendar() {}

  /**
  * Core processing engine that computes all historical events chronologically
  * for the target year and formats the comprehensive results string.
  */
  public static String getCalendarResults(String inputDateText) {
    StringBuilder sb = new StringBuilder();
    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", strictLocale);
    sdf.setLenient(false);

    try {
      Date date = sdf.parse(inputDateText);
      Calendar cal = Calendar.getInstance();
      cal.setTime(date);
      int year = cal.get(Calendar.YEAR);

      // Strict boundary alignment validation tracking Gregorian shift limits
      if (year < 1583 || year > 2500) {
        return "Error: Year must be between 1583 and 2500!";
      }

      // Selected Date info tracking day name metrics natively via strict US Locale
      SimpleDateFormat df = new SimpleDateFormat("dd MMMM yyyy, EEEE", strictLocale);
      sb.append("Selected Date: ").append(df.format(date)).append("\n\n");

      List<CalendarEvent> eventList = new ArrayList<CalendarEvent>();

      // Setup a scanning iterator boundary initialized at the first second of the targeted year
      Calendar iterator = Calendar.getInstance();
      iterator.setTime(date);
      iterator.set(Calendar.DAY_OF_YEAR, 1);
      int targetYear = iterator.get(Calendar.YEAR);

      // Scan through every single day of the year chronologically
      while (iterator.get(Calendar.YEAR) == targetYear) {
        int miladiMonth = iterator.get(Calendar.MONTH) + 1; // January is 0, add 1 for standard human bounds
        int miladiDay = iterator.get(Calendar.DAY_OF_MONTH);

        // Inject dynamically generated Gregorian milestones chronologically inside the loop matrix
        if (miladiMonth == 4 && miladiDay == 20) {
          Calendar eventCal = (Calendar) iterator.clone();
          eventList.add(new CalendarEvent(eventCal, "Mawlid al-Nabi (Prophet's Birth - Gregorian 20 April)"));
        }
        if (miladiMonth == 6 && miladiDay == 8) {
          Calendar eventCal = (Calendar) iterator.clone();
          eventList.add(new CalendarEvent(eventCal, "Wafat al-Nabi (Prophet's Death - Gregorian 8 June)"));
        }

        // Check and fetch corresponding Hijri calendar parameters
        int[] hData = getHijriDate(iterator);
        int hMonth = hData[1];
        int hDay = hData[0];

        // Cross-reference data tracking our historical metrics rows
        for (int r = 0; r < KARBALA_EVENTS.length; r++) {
          Object[] row = KARBALA_EVENTS[r];
          if ((Integer) row[0] == hMonth && (Integer) row[1] == hDay) {
            Calendar eventCal = (Calendar) iterator.clone();
            eventList.add(new CalendarEvent(eventCal, (String) row[2]));
          }
        }

        iterator.add(Calendar.DAY_OF_YEAR, 1);
      }

      // Sort all collected milestones into absolute chronological sorting balance
      Collections.sort(eventList);

      sb.append("Islamic & Historical Calendar Milestones for Year ").append(year).append(":\n");
      SimpleDateFormat mdf = new SimpleDateFormat("dd MMMM", strictLocale);

      for (int i = 0; i < eventList.size(); i++) {
        CalendarEvent e = eventList.get(i);
        sb.append("* ").append(mdf.format(e.calendarDate.getTime())).append(": ").append(e.desc).append("\n");
      }

      return sb.toString();

    } catch (Exception e) {
      return "Error: Invalid date format! Please use dd.MM.yyyy pattern (e.g. 15.08.2024).";
    }
  }

  /**
  * Converts Gregorian calendar parameters into precise Hijri dates based on algorithmic julian day steps.
  */
  private static int[] getHijriDate(Calendar cal) {
    int day = cal.get(Calendar.DAY_OF_MONTH);
    int month = cal.get(Calendar.MONTH) + 1;
    int year = cal.get(Calendar.YEAR);

    if (year < 1583) return new int[]{0, 0, 0};

    int jd = (1461 * (year + 4800 + (month - 14) / 12)) / 4 +
    (367 * (month - 2 - 12 * ((month - 14) / 12))) / 12 -
    (3 * ((year + 4900 + (month - 14) / 12) / 100)) / 4 + day - 32075;

    int l = jd - 1948440 + 10632;
    int n = (l - 1) / 10631;
    l = l - 10631 * n + 354;
    int j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238);
    l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29;
    int m = (24 * l) / 709;
    int d = l - (709 * m) / 24;
    int y = 30 * n + j - 30;

    return new int[]{d, m, y};
  }

  /**
  * Internal structure designed to maintain localized chronological milestone ordering mappings.
  */
  private static class CalendarEvent implements Comparable<CalendarEvent> {
    Calendar calendarDate;
    String desc;

    CalendarEvent(Calendar d, String s) {
      this.calendarDate = d;
      this.desc = s;
    }

    @Override
    public int compareTo(CalendarEvent o) {
      return this.calendarDate.compareTo(o.calendarDate);
    }
  }

}
