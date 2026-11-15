package app.laborable.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Pure business-day arithmetic. A business day is not a Saturday, not a Sunday and not a holiday.
 *
 * <p>Holidays are supplied per year (lazily) as a map date to name, so that data gaps surface from
 * the supplier instead of being treated as "no holidays".
 */
public final class BusinessDayCalculator {

  public static final int MAX_DAYS = 3660;

  public record Holiday(LocalDate date, String name) {}

  public record AddResult(LocalDate result, List<Holiday> holidaysSkipped) {}

  public record BetweenResult(long count, List<Holiday> holidaysInRange) {}

  private final IntFunction<Map<LocalDate, String>> holidaysByYear;
  private final Map<Integer, Map<LocalDate, String>> loaded = new HashMap<>();

  public BusinessDayCalculator(IntFunction<Map<LocalDate, String>> holidaysByYear) {
    this.holidaysByYear = holidaysByYear;
  }

  public static boolean isWeekend(LocalDate date) {
    DayOfWeek day = date.getDayOfWeek();
    return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
  }

  private Map<LocalDate, String> holidays(int year) {
    return loaded.computeIfAbsent(year, holidaysByYear::apply);
  }

  public String holidayName(LocalDate date) {
    return holidays(date.getYear()).get(date);
  }

  public boolean isWorking(LocalDate date) {
    return !isWeekend(date) && holidayName(date) == null;
  }

  /** Date reached after {@code days} business days; the start date itself is never counted. */
  public AddResult add(LocalDate start, int days) {
    if (Math.abs((long) days) > MAX_DAYS) {
      throw new IllegalArgumentException("days must be within +/-" + MAX_DAYS);
    }
    int step = Integer.signum(days);
    int remaining = Math.abs(days);
    List<Holiday> skipped = new ArrayList<>();
    LocalDate cursor = start;
    while (remaining > 0) {
      cursor = cursor.plusDays(step);
      if (isWeekend(cursor)) {
        continue;
      }
      String holiday = holidayName(cursor);
      if (holiday != null) {
        skipped.add(new Holiday(cursor, holiday));
      } else {
        remaining--;
      }
    }
    return new AddResult(cursor, skipped);
  }

  /** Number of business days in the range; bounds are configurable. */
  public BetweenResult between(
      LocalDate from, LocalDate to, boolean includeFrom, boolean includeTo) {
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("'to' must not be before 'from'");
    }
    if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
      throw new IllegalArgumentException("range must not exceed " + MAX_DAYS + " days");
    }
    LocalDate first = includeFrom ? from : from.plusDays(1);
    LocalDate last = includeTo ? to : to.minusDays(1);
    long count = 0;
    List<Holiday> inRange = new ArrayList<>();
    for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
      if (isWeekend(d)) {
        continue;
      }
      String holiday = holidayName(d);
      if (holiday != null) {
        inRange.add(new Holiday(d, holiday));
      } else {
        count++;
      }
    }
    return new BetweenResult(count, inRange);
  }
}
