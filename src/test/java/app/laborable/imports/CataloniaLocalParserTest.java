package app.laborable.imports;

import static org.assertj.core.api.Assertions.assertThat;

import app.laborable.domain.Model.NewHoliday;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Fixture rows are synthetic but follow the field layout of the real Socrata dataset. */
class CataloniaLocalParserTest {

  private static LocalHolidayRules.Parsed parsed;

  @BeforeAll
  static void parse() throws IOException {
    String json =
        new ClassPathResource("fixtures/catalonia-local-2027.json")
            .getContentAsString(StandardCharsets.UTF_8);
    parsed =
        new CataloniaLocalParser()
            .parse(json, 2027, Set.of("08", "17", "25", "43"), Set.of(LocalDate.of(2027, 6, 24)));
  }

  @Test
  void keepsGenuineLocalHolidays() {
    assertThat(parsed.holidays())
        .filteredOn(h -> "08305".equals(h.municipalityIne()))
        .extracting(NewHoliday::date)
        .containsExactly(LocalDate.of(2027, 8, 31), LocalDate.of(2027, 9, 1));
  }

  @Test
  void dropsRegionWideDuplicatesAndKeepsTheRest() {
    assertThat(parsed.holidays())
        .filteredOn(h -> "08001".equals(h.municipalityIne()))
        .extracting(NewHoliday::date)
        .containsExactly(LocalDate.of(2027, 6, 29));
    assertThat(parsed.rejected()).containsEntry("duplicate_of_regional_holiday", 1);
  }

  @Test
  void rejectsTestRowsSubMunicipalEntitiesAndWrongYear() {
    assertThat(parsed.rejected())
        .containsEntry("test_or_blank_name", 1)
        .containsEntry("sub_municipal_entity", 1)
        .containsEntry("date_outside_year", 1);
    assertThat(parsed.holidays())
        .extracting(NewHoliday::municipalityIne)
        .doesNotContain("08002", "08004", "08005");
  }

  @Test
  void municipalitiesAboveTheLegalLimitOfTwoAreAnomalies() {
    assertThat(parsed.anomalies()).singleElement().asString().contains("08003");
    assertThat(parsed.holidays()).extracting(NewHoliday::municipalityIne).doesNotContain("08003");
  }

  @Test
  void collectsMunicipalitiesOfStoredHolidaysOnly() {
    assertThat(parsed.municipalities())
        .extracting(m -> m.ineCode())
        .containsExactlyInAnyOrder("08305", "08001", "08901", "17901");
  }
}
