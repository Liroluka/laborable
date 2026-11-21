package app.laborable.imports;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads "Calendario laboral de Euskadi" from Open Data Euskadi (CC BY 4.0). One JSON array holds
 * Basque-wide rows (municipalityEs "CAE"), territorial rows of Araba, Bizkaia and Gipuzkoa (no
 * municipality code) and one row per municipal holiday. The municipality code is the INE number
 * inside its province, so INE = province prefix + the code padded to three digits.
 */
@Component
public class EuskadiLocalParser {

  public static final String REGION = "PV";

  /** What was read, plus territorial holidays that cannot be attached to a municipality. */
  public record Result(LocalHolidayRules.Parsed parsed, List<String> territorialNotes) {}

  private static final TypeReference<List<Map<String, Object>>> ROWS = new TypeReference<>() {};
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

  private final JsonMapper mapper = JsonMapper.builder().build();

  public Result parse(
      String json, int year, Set<String> provinces, Set<LocalDate> regionWideDates) {
    List<Map<String, Object>> rows = mapper.readValue(json, ROWS);
    Map<String, Integer> rejected = new LinkedHashMap<>();
    Set<String> territorial = new LinkedHashSet<>();
    List<LocalEntry> entries = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      String municipality = text(row, "municipalityEs");
      Object code = row.get("municipalitycode");
      LocalDate date = date(text(row, "date"));
      if (code == null) {
        if (!"CAE".equals(municipality) && date != null && date.getYear() == year) {
          territorial.add(date + " " + text(row, "descripcionEs") + " (" + municipality + ")");
        }
        continue;
      }
      String ine = ine(text(row, "territory"), code);
      if (ine == null) {
        rejected.merge("invalid_ine", 1, Integer::sum);
        continue;
      }
      entries.add(new LocalEntry(ine, municipality, date, text(row, "descripcionEs")));
    }
    List<String> notes =
        territorial.stream()
            .map(t -> "territorial holiday not applied to municipalities: " + t)
            .toList();
    return new Result(
        LocalHolidayRules.apply(entries, year, REGION, provinces, regionWideDates, rejected),
        notes);
  }

  static String ine(String territory, Object code) {
    String province = province(territory);
    if (province == null || !(code instanceof Number number)) {
      return null;
    }
    double value = number.doubleValue();
    if (value < 1 || value > 999 || value != Math.floor(value)) {
      return null;
    }
    return province + String.format("%03d", (int) value);
  }

  private static String province(String territory) {
    String t = territory.toLowerCase(Locale.ROOT);
    if (t.contains("araba") || t.contains("lava")) {
      return "01";
    }
    if (t.contains("gipuzkoa")) {
      return "20";
    }
    if (t.contains("bizkaia")) {
      return "48";
    }
    return null;
  }

  private static String text(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value == null ? "" : value.toString().trim();
  }

  private static LocalDate date(String value) {
    try {
      return LocalDate.parse(value, DATE);
    } catch (RuntimeException e) {
      return null;
    }
  }
}
