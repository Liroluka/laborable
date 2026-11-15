package app.laborable.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.laborable.domain.Model.HolidayKind;
import app.laborable.domain.Model.NewHoliday;
import app.laborable.domain.Model.Scope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Runs against the real BOE-A-2026-21254 resolution (holidays of 2027), saved as a fixture. */
class BoeAnnexParserTest {

  private static BoeAnnexParser.Parsed parsed;

  @BeforeAll
  static void parse() throws IOException {
    String xml =
        new ClassPathResource("fixtures/boe-fiestas-2027.xml")
            .getContentAsString(StandardCharsets.UTF_8);
    parsed = new BoeAnnexParser().parse(xml);
  }

  private static Set<LocalDate> datesOf(String region) {
    return parsed.holidays().stream()
        .filter(h -> h.scope() == Scope.NATIONAL || region.equals(h.regionCode()))
        .map(NewHoliday::date)
        .collect(Collectors.toSet());
  }

  @Test
  void readsYearAndAll19Communities() {
    assertThat(parsed.year()).isEqualTo(2027);
    assertThat(parsed.regions()).hasSize(19).contains("CT", "MD", "CN", "CE", "ML");
  }

  @Test
  void nationalHolidaysAreMarkedInEveryCommunity() {
    List<NewHoliday> national =
        parsed.holidays().stream().filter(h -> h.scope() == Scope.NATIONAL).toList();
    assertThat(national)
        .extracting(NewHoliday::date)
        .containsExactlyInAnyOrder(
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2027, 1, 6),
            LocalDate.of(2027, 3, 26),
            LocalDate.of(2027, 5, 1),
            LocalDate.of(2027, 10, 12),
            LocalDate.of(2027, 11, 1),
            LocalDate.of(2027, 12, 6),
            LocalDate.of(2027, 12, 8),
            LocalDate.of(2027, 12, 25));
    assertThat(national)
        .filteredOn(h -> h.date().equals(LocalDate.of(2027, 1, 6)))
        .singleElement()
        .extracting(NewHoliday::kind)
        .isEqualTo(HolidayKind.NATIONAL_SUBSTITUTABLE);
  }

  @Test
  void catalunyaHasPascuaFloridaAndSantJoanButNoJuevesSanto() {
    Set<LocalDate> ct = datesOf("CT");
    assertThat(ct)
        .contains(LocalDate.of(2027, 3, 29), LocalDate.of(2027, 6, 24), LocalDate.of(2027, 9, 11));
    assertThat(ct).doesNotContain(LocalDate.of(2027, 3, 25));
  }

  @Test
  void madridKeepsJuevesSantoAndSanJose() {
    Set<LocalDate> md = datesOf("MD");
    assertThat(md).contains(LocalDate.of(2027, 3, 25), LocalDate.of(2027, 3, 19));
    assertThat(md).doesNotContain(LocalDate.of(2027, 3, 29));
  }

  @Test
  void ownHolidaysBelongToTheirCommunityOnly() {
    assertThat(datesOf("AN")).contains(LocalDate.of(2027, 3, 1));
    assertThat(datesOf("MD")).doesNotContain(LocalDate.of(2027, 3, 1));
    assertThat(datesOf("EX")).contains(LocalDate.of(2027, 10, 11), LocalDate.of(2027, 9, 8));
  }

  @Test
  void noCommunityExceedsFourteenDates() {
    assertThat(parsed.warnings()).isEmpty();
    parsed.regions().forEach(r -> assertThat(datesOf(r).size()).isLessThanOrEqualTo(14));
  }

  @Test
  void subregionalFootnotesAreReportedNotImported() {
    assertThat(parsed.notes()).hasSize(2);
    assertThat(parsed.notes().get(0)).contains("Canarias");
    assertThat(parsed.notes().get(1)).contains("Cataluña");
  }

  @Test
  void rejectsDocumentsWithoutTheAnnexTable() {
    String xml = "<documento><texto><p class=\"anexo_tit\">Año 2027</p></texto></documento>";
    assertThatThrownBy(() -> new BoeAnnexParser().parse(xml))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsXmlWithDoctype() {
    String xml = "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><documento/>";
    assertThatThrownBy(() -> new BoeAnnexParser().parse(xml))
        .isInstanceOf(IllegalStateException.class);
  }
}
