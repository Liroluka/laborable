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
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cleans the open dataset "Calendari de festes locals" of the Generalitat de Catalunya (Socrata
 * b4eh-r8up). The dataset contains test rows, region-wide dates and sub-municipal entities, so each
 * row is checked and the legal limit of two local holidays per municipality is enforced.
 */
@Component
public class CataloniaLocalParser {

  public static final String REGION = "CT";
  static final int MAX_LOCAL_HOLIDAYS = 2;

  public record Parsed(
      List<NewHoliday> holidays,
      List<Municipality> municipalities,
      List<String> anomalies,
      Map<String, Integer> rejected) {}

  private static final Pattern INE = Pattern.compile("(0[1-9]|[1-4]\\d|5[0-2])\\d{3}");
  private static final Pattern TEST_NAME = Pattern.compile("(?i)^(prueba|test|abc|asd)\\b.*");
  private static final TypeReference<List<Map<String, Object>>> ROWS = new TypeReference<>() {};

  private final JsonMapper mapper = JsonMapper.builder().build();

  /**
   * @param json rows of the dataset for one year
   * @param year the requested year
   * @param regionWideDates dates that are already national or Catalan holidays (duplicates)
   */
  public Parsed parse(String json, int year, Set<LocalDate> regionWideDates) {
    List<Map<String, Object>> rows = mapper.readValue(json, ROWS);
    Map<String, Integer> rejected = new LinkedHashMap<>();
    Map<String, Map<LocalDate, String>> byMunicipality = new LinkedHashMap<>();
    Map<String, String> names = new LinkedHashMap<>();

    for (Map<String, Object> row : rows) {
      String ine = text(row, "codi_municipi_ine");
      String name = text(row, "festiu");
      String pedania = text(row, "pedania");
      LocalDate date = date(text(row, "data"));
      if (!INE.matcher(ine).matches()) {
        reject(rejected, "invalid_ine");
      } else if (!pedania.isEmpty() && !pedania.equals("000")) {
        reject(rejected, "sub_municipal_entity");
      } else if (date == null || date.getYear() != year) {
        reject(rejected, "date_outside_year");
      } else if (!looksGenuine(name)) {
        reject(rejected, "test_or_blank_name");
      } else if (regionWideDates.contains(date)) {
        reject(rejected, "duplicate_of_regional_holiday");
      } else {
        byMunicipality.computeIfAbsent(ine, k -> new LinkedHashMap<>()).putIfAbsent(date, name);
        names.putIfAbsent(ine, text(row, "ajuntament_o_nucli_municipal"));
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
                    + " local holidays (max 2); skipped");
            return;
          }
          if (seen.add(ine)) {
            municipalities.add(new Municipality(ine, names.get(ine), REGION));
          }
          dates.forEach(
              (date, name) ->
                  holidays.add(
                      new NewHoliday(date, name, Scope.LOCAL, HolidayKind.LOCAL, null, ine)));
        });
    return new Parsed(holidays, municipalities, anomalies, rejected);
  }

  private static boolean looksGenuine(String name) {
    return name.length() >= 4
        && name.chars().anyMatch(Character::isLetter)
        && !TEST_NAME.matcher(name).matches();
  }

  private static void reject(Map<String, Integer> rejected, String reason) {
    rejected.merge(reason, 1, Integer::sum);
  }

  private static String text(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value == null ? "" : value.toString().trim();
  }

  private static LocalDate date(String value) {
    try {
      return value.length() >= 10 ? LocalDate.parse(value.substring(0, 10)) : null;
    } catch (RuntimeException e) {
      return null;
    }
  }
}
