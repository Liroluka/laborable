package app.laborable.imports;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the open dataset "Calendari de festes locals" of the Generalitat de Catalunya (Socrata
 * b4eh-r8up). The dataset contains test rows, region-wide dates and sub-municipal entities; the
 * shared {@link LocalHolidayRules} clean it up.
 */
@Component
public class CataloniaLocalParser {

  public static final String REGION = "CT";

  private static final TypeReference<List<Map<String, Object>>> ROWS = new TypeReference<>() {};

  private final JsonMapper mapper = JsonMapper.builder().build();

  /**
   * @param json rows of the dataset for one year
   * @param year the requested year
   * @param provinces province codes of Catalonia
   * @param regionWideDates dates that are already national or Catalan holidays (duplicates)
   */
  public LocalHolidayRules.Parsed parse(
      String json, int year, Set<String> provinces, Set<LocalDate> regionWideDates) {
    List<Map<String, Object>> rows = mapper.readValue(json, ROWS);
    Map<String, Integer> rejected = new LinkedHashMap<>();
    List<LocalEntry> entries = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      String ine = text(row, "codi_municipi_ine");
      String pedania = text(row, "pedania");
      if (LocalHolidayRules.INE.matcher(ine).matches()
          && !pedania.isEmpty()
          && !pedania.equals("000")) {
        rejected.merge("sub_municipal_entity", 1, Integer::sum);
        continue;
      }
      entries.add(
          new LocalEntry(
              ine,
              text(row, "ajuntament_o_nucli_municipal"),
              date(text(row, "data")),
              text(row, "festiu")));
    }
    return LocalHolidayRules.apply(entries, year, REGION, provinces, regionWideDates, rejected);
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
