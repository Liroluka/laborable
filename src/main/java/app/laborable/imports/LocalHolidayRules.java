package app.laborable.imports;

import app.laborable.domain.Model.HolidayKind;
import app.laborable.domain.Model.NewHoliday;
import app.laborable.domain.Model.Scope;
import app.laborable.persistence.MunicipalityRepository.Municipality;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validation shared by every municipal-holiday source (Catalonia, Euskadi, generic upload). A
 * source only reads rows; what is accepted is decided here, identically for all regions.
 *
 * <p>Rules: valid INE code of a province of the declared region, date inside the requested year, a
 * genuine name, not a repeat of a region-wide holiday, and at most two local holidays per
 * municipality (Estatuto de los Trabajadores, art. 37.2). Municipalities above the limit are
 * reported as anomalies and skipped completely rather than guessed.
 */
public final class LocalHolidayRules {

  public static final int MAX_LOCAL_HOLIDAYS = 2;
  public static final Pattern INE = Pattern.compile("(0[1-9]|[1-4]\\d|5[0-2])\\d{3}");
  private static final Pattern TEST_NAME = Pattern.compile("(?i)^(prueba|test|abc|asd)\\b.*");

  private LocalHolidayRules() {}

  public record Parsed(
      List<NewHoliday> holidays,
      List<Municipality> municipalities,
      List<String> anomalies,
      Map<String, Integer> rejected) {}

  /**
   * @param entries rows read by a source adapter
   * @param year the requested year
   * @param region ISO 3166-2:ES code the rows must belong to
   * @param provinces province codes of that region
   * @param regionWideDates national and regional dates of the region (duplicates are dropped)
   * @param rejected rejections already counted by the adapter; the rules add their own
   */
  public static Parsed apply(
      List<LocalEntry> entries,
      int year,
      String region,
      Set<String> provinces,
      Set<LocalDate> regionWideDates,
      Map<String, Integer> rejected) {
    Map<String, Integer> counters = new LinkedHashMap<>(rejected);
    Map<String, Map<LocalDate, String>> byMunicipality = new LinkedHashMap<>();
    Map<String, String> names = new LinkedHashMap<>();

    for (LocalEntry entry : entries) {
      String ine = entry.ine() == null ? "" : entry.ine();
      String name = entry.name() == null ? "" : entry.name().trim();
      String reason = rejection(entry, ine, name, year, provinces, regionWideDates);
      if (reason != null) {
        counters.merge(reason, 1, Integer::sum);
      } else {
        byMunicipality
            .computeIfAbsent(ine, k -> new LinkedHashMap<>())
            .putIfAbsent(entry.date(), name);
        names.putIfAbsent(ine, entry.municipalityName().trim());
      }
    }

    List<NewHoliday> holidays = new ArrayList<>();
    List<Municipality> municipalities = new ArrayList<>();
    List<String> anomalies = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    byMunicipality.forEach(
        (ine, dates) -> {
          if (dates.size() > MAX_LOCAL_HOLIDAYS) {
            anomalies.add(
                ine
                    + " "
                    + names.get(ine)
                    + ": "
                    + dates.size()
                    + " local holidays (max "
                    + MAX_LOCAL_HOLIDAYS
                    + "); skipped");
            return;
          }
          if (seen.add(ine)) {
            municipalities.add(new Municipality(ine, names.get(ine), region));
          }
          dates.forEach(
              (date, name) ->
                  holidays.add(
                      new NewHoliday(date, name, Scope.LOCAL, HolidayKind.LOCAL, null, ine)));
        });
    return new Parsed(holidays, municipalities, anomalies, counters);
  }

  private static String rejection(
      LocalEntry entry,
      String ine,
      String name,
      int year,
      Set<String> provinces,
      Set<LocalDate> regionWideDates) {
    if (!INE.matcher(ine).matches()) {
      return "invalid_ine";
    }
    if (!provinces.contains(ine.substring(0, 2))) {
      return "municipality_of_another_region";
    }
    if (entry.date() == null || entry.date().getYear() != year) {
      return "date_outside_year";
    }
    if (!looksGenuine(name) || blank(entry.municipalityName())) {
      return "test_or_blank_name";
    }
    if (regionWideDates.contains(entry.date())) {
      return "duplicate_of_regional_holiday";
    }
    return null;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static boolean looksGenuine(String name) {
    return name.length() >= 4
        && name.chars().anyMatch(Character::isLetter)
        && !TEST_NAME.matcher(name).matches();
  }
}
