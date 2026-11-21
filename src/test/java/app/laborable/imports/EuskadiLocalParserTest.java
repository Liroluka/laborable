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

/** Fixture rows are synthetic but follow the field layout of the real Open Data Euskadi file. */
class EuskadiLocalParserTest {

  private static EuskadiLocalParser.Result result;

  @BeforeAll
  static void parse() throws IOException {
    String json =
        new ClassPathResource("fixtures/euskadi-calendar-2027.json")
            .getContentAsString(StandardCharsets.UTF_8);
    result =
        new EuskadiLocalParser()
            .parse(json, 2027, Set.of("01", "20", "48"), Set.of(LocalDate.of(2027, 1, 6)));
  }

  @Test
  void buildsIneFromTerritoryAndMunicipalityNumber() {
    assertThat(EuskadiLocalParser.ine("Bizkaia", 1.0)).isEqualTo("48001");
    assertThat(EuskadiLocalParser.ine("Gipuzkoa", 35.0)).isEqualTo("20035");
    assertThat(EuskadiLocalParser.ine("Álava - Araba", 2.0)).isEqualTo("01002");
    assertThat(EuskadiLocalParser.ine("Bizkaia", 911.0)).isEqualTo("48911");
    assertThat(EuskadiLocalParser.ine("Narnia", 5.0)).isNull();
    assertThat(EuskadiLocalParser.ine("Bizkaia", 0.0)).isNull();
    assertThat(EuskadiLocalParser.ine("Bizkaia", 1.5)).isNull();
  }

  @Test
  void keepsMunicipalHolidaysAndTrimsNames() {
    assertThat(result.parsed().holidays())
        .filteredOn(h -> "48001".equals(h.municipalityIne()))
        .extracting(NewHoliday::date)
        .containsExactlyInAnyOrder(LocalDate.of(2027, 5, 15), LocalDate.of(2027, 9, 14));
    assertThat(result.parsed().municipalities())
        .filteredOn(m -> "48001".equals(m.ineCode()))
        .singleElement()
        .satisfies(
            m -> {
              assertThat(m.name()).isEqualTo("Abadiño");
              assertThat(m.regionCode()).isEqualTo("PV");
            });
  }

  @Test
  void dropsDatesAlreadyHolidaysInTheWholeRegion() {
    assertThat(result.parsed().holidays())
        .filteredOn(h -> "48002".equals(h.municipalityIne()))
        .extracting(NewHoliday::date)
        .containsExactly(LocalDate.of(2027, 6, 15));
    assertThat(result.parsed().rejected()).containsEntry("duplicate_of_regional_holiday", 1);
  }

  @Test
  void rejectsWrongYearAndUnknownTerritoryOrCode() {
    assertThat(result.parsed().rejected())
        .containsEntry("date_outside_year", 1)
        .containsEntry("invalid_ine", 2);
    assertThat(result.parsed().holidays())
        .extracting(NewHoliday::municipalityIne)
        .doesNotContain("48003");
  }

  @Test
  void municipalitiesAboveTheLegalLimitAreAnomaliesNotGuesses() {
    assertThat(result.parsed().anomalies()).singleElement().asString().contains("20035");
    assertThat(result.parsed().holidays())
        .extracting(NewHoliday::municipalityIne)
        .doesNotContain("20035");
  }

  @Test
  void reportsTerritorialHolidaysInsteadOfSilentlyDroppingThem() {
    assertThat(result.territorialNotes())
        .hasSize(2)
        .anyMatch(n -> n.contains("2027-07-31") && n.contains("Bizkaia"))
        .anyMatch(n -> n.contains("2027-04-28"));
  }
}
