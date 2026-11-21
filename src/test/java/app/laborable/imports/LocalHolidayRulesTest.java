package app.laborable.imports;

import static org.assertj.core.api.Assertions.assertThat;

import app.laborable.domain.Model.NewHoliday;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The rules are region-agnostic; Andalucía (provinces 04, 11, 14, 18, 21, 23, 29, 41) is used. */
class LocalHolidayRulesTest {

  private static final Set<String> ANDALUCIA =
      Set.of("04", "11", "14", "18", "21", "23", "29", "41");

  private static LocalEntry entry(String ine, String date, String name) {
    return new LocalEntry(ine, "Municipio " + ine, LocalDate.parse(date), name);
  }

  private static LocalHolidayRules.Parsed apply(List<LocalEntry> entries) {
    return LocalHolidayRules.apply(
        entries, 2027, "AN", ANDALUCIA, Set.of(LocalDate.of(2027, 2, 28)), Map.of());
  }

  @Test
  void acceptsValidMunicipalHolidaysOfTheRegion() {
    var parsed =
        apply(
            List.of(
                entry("41091", "2027-05-30", "Fiesta local uno"),
                entry("41091", "2027-09-08", "Fiesta local dos"),
                entry("14021", "2027-05-24", "Fiesta local tres")));
    assertThat(parsed.holidays()).hasSize(3);
    assertThat(parsed.municipalities())
        .extracting(m -> m.ineCode() + ":" + m.regionCode())
        .containsExactlyInAnyOrder("41091:AN", "14021:AN");
    assertThat(parsed.rejected()).isEmpty();
  }

  @Test
  void rejectsMunicipalitiesOfAnotherRegion() {
    var parsed = apply(List.of(entry("08019", "2027-05-24", "Fiesta de Barcelona")));
    assertThat(parsed.holidays()).isEmpty();
    assertThat(parsed.rejected()).containsEntry("municipality_of_another_region", 1);
  }

  @Test
  void rejectsInvalidIneBlankNamesAndWrongYears() {
    var parsed =
        apply(
            List.of(
                entry("99999", "2027-05-24", "Fiesta rara"),
                entry("41091", "2027-05-24", "x"),
                entry("41091", "2026-05-24", "Fiesta del año pasado"),
                new LocalEntry("41091", " ", LocalDate.of(2027, 5, 24), "Sin nombre de pueblo")));
    assertThat(parsed.holidays()).isEmpty();
    assertThat(parsed.rejected())
        .containsEntry("invalid_ine", 1)
        .containsEntry("date_outside_year", 1)
        .containsEntry("test_or_blank_name", 2);
  }

  @Test
  void dropsRegionWideDuplicatesAndRepeatedDates() {
    var parsed =
        apply(
            List.of(
                entry("41091", "2027-02-28", "Día de Andalucía"),
                entry("41091", "2027-05-30", "Fiesta local uno"),
                entry("41091", "2027-05-30", "Fiesta local uno otra vez")));
    assertThat(parsed.holidays())
        .extracting(NewHoliday::date)
        .containsExactly(LocalDate.of(2027, 5, 30));
    assertThat(parsed.rejected()).containsEntry("duplicate_of_regional_holiday", 1);
  }

  @Test
  void skipsMunicipalitiesWithMoreThanTwoLocalHolidays() {
    var parsed =
        apply(
            List.of(
                entry("41091", "2027-05-30", "Fiesta local uno"),
                entry("41091", "2027-06-01", "Fiesta local dos"),
                entry("41091", "2027-06-02", "Fiesta local tres"),
                entry("14021", "2027-05-24", "Fiesta local cuatro")));
    assertThat(parsed.anomalies()).singleElement().asString().contains("41091");
    assertThat(parsed.holidays()).extracting(NewHoliday::municipalityIne).containsOnly("14021");
  }

  @Test
  void keepsCountsFromTheSourceAdapter() {
    var parsed =
        LocalHolidayRules.apply(
            List.of(), 2027, "AN", ANDALUCIA, Set.of(), Map.of("sub_municipal_entity", 3));
    assertThat(parsed.rejected()).containsEntry("sub_municipal_entity", 3);
  }
}
