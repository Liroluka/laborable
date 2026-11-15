package app.laborable.service;

import app.laborable.domain.BusinessDayCalculator;
import app.laborable.domain.BusinessDayCalculator.AddResult;
import app.laborable.domain.BusinessDayCalculator.BetweenResult;
import app.laborable.domain.Model.HolidayView;
import app.laborable.domain.Model.Place;
import app.laborable.domain.Model.Warning;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BusinessDayService {

  public enum Reason {
    WORKING,
    WEEKEND,
    HOLIDAY
  }

  public record IsWorking(
      LocalDate date,
      boolean working,
      Reason reason,
      HolidayView holiday,
      List<Warning> warnings) {}

  public record Added(AddResult result, List<Warning> warnings) {}

  public record Counted(BetweenResult result, List<Warning> warnings) {}

  private final HolidayService holidays;

  public BusinessDayService(HolidayService holidays) {
    this.holidays = holidays;
  }

  /** Calculator plus the warnings gathered while loading the years it touched. */
  private static final class Session {
    final Map<String, Warning> warnings = new LinkedHashMap<>();
    final Map<LocalDate, HolidayView> views = new LinkedHashMap<>();
    final BusinessDayCalculator calculator;

    Session(HolidayService holidays, Place place) {
      this.calculator =
          new BusinessDayCalculator(
              year -> {
                HolidayService.YearHolidays data = holidays.forYear(place, year);
                data.warnings().forEach(w -> warnings.putIfAbsent(w.code(), w));
                Map<LocalDate, String> names = new LinkedHashMap<>();
                for (HolidayView view : data.holidays()) {
                  names.putIfAbsent(view.date(), view.name());
                  views.putIfAbsent(view.date(), view);
                }
                return names;
              });
    }

    List<Warning> warningList() {
      Collection<Warning> values = warnings.values();
      return List.copyOf(values);
    }
  }

  public IsWorking isWorking(LocalDate date, Place place) {
    Session session = new Session(holidays, place);
    boolean weekend = BusinessDayCalculator.isWeekend(date);
    String holidayName = session.calculator.holidayName(date);
    Reason reason =
        weekend ? Reason.WEEKEND : holidayName != null ? Reason.HOLIDAY : Reason.WORKING;
    return new IsWorking(
        date,
        reason == Reason.WORKING,
        reason,
        holidayName == null ? null : session.views.get(date),
        session.warningList());
  }

  public Added add(LocalDate date, int days, Place place) {
    Session session = new Session(holidays, place);
    try {
      return new Added(session.calculator.add(date, days), session.warningList());
    } catch (IllegalArgumentException e) {
      throw ProblemException.badRequest("invalid-range", "Invalid days", e.getMessage());
    }
  }

  public Counted between(
      LocalDate from, LocalDate to, boolean includeFrom, boolean includeTo, Place place) {
    Session session = new Session(holidays, place);
    try {
      return new Counted(
          session.calculator.between(from, to, includeFrom, includeTo), session.warningList());
    } catch (IllegalArgumentException e) {
      throw ProblemException.badRequest("invalid-range", "Invalid range", e.getMessage());
    }
  }
}
