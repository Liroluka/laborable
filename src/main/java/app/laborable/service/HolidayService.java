package app.laborable.service;

import app.laborable.domain.Model.HolidayView;
import app.laborable.domain.Model.Place;
import app.laborable.domain.Model.Warning;
import app.laborable.persistence.HolidayRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HolidayService {

  public static final int MIN_YEAR = 2000;
  public static final int MAX_YEAR = 2100;

  public record YearHolidays(
      int year, List<HolidayView> holidays, boolean localLoaded, List<Warning> warnings) {}

  private final HolidayRepository repository;

  public HolidayService(HolidayRepository repository) {
    this.repository = repository;
  }

  /** Holidays of the year for the place; fails when the year has no official data. */
  public YearHolidays forYear(Place place, int year) {
    if (year < MIN_YEAR || year > MAX_YEAR) {
      throw ProblemException.badRequest(
          "invalid-year", "Invalid year", "Year must be between " + MIN_YEAR + " and " + MAX_YEAR);
    }
    if (!repository.hasCoverage(year, place.regionCode(), "REGIONAL")) {
      throw ProblemException.unprocessable(
          "data-not-available",
          "No holiday data for this year",
          "No official holiday data is loaded for year "
              + year
              + (place.regionCode() == null ? "" : " and region " + place.regionCode())
              + ".");
    }
    List<Warning> warnings = new ArrayList<>();
    boolean localLoaded = false;
    if (place.regionCode() == null) {
      warnings.add(
          new Warning(
              "NO_REGION", "No region given: only holidays valid in all of Spain were applied."));
    }
    if (place.municipalityIne() != null) {
      localLoaded = repository.hasCoverage(year, place.regionCode(), "LOCAL");
      if (!localLoaded) {
        warnings.add(
            new Warning(
                "LOCAL_HOLIDAYS_NOT_LOADED",
                "Municipal holidays are not loaded for region "
                    + place.regionCode()
                    + " and year "
                    + year
                    + "; the result may miss local holidays."));
      }
    }
    if ("CN".equals(place.regionCode())
        || ("CT".equals(place.regionCode())
            && (place.municipalityIne() == null || place.municipalityIne().startsWith("25")))) {
      warnings.add(
          new Warning(
              "SUBREGIONAL_HOLIDAYS_NOT_SUPPORTED",
              "Island (Canarias) and Val d'Aran holidays are not applied."));
    }
    List<HolidayView> holidays = repository.find(year, place.regionCode(), place.municipalityIne());
    return new YearHolidays(year, holidays, localLoaded, List.copyOf(warnings));
  }
}
