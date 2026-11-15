package app.laborable.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BusinessDayCalculatorTest {

  // 2027-03-25 Thu and 2027-03-26 Fri are holidays in this synthetic calendar.
  private static final Map<LocalDate, String> HOLIDAYS =
      Map.of(
          LocalDate.of(2027, 3, 25), "Jueves Santo",
          LocalDate.of(2027, 3, 26), "Viernes Santo",
          LocalDate.of(2027, 12, 25), "Navidad",
          LocalDate.of(2028, 1, 6), "Reyes");

  private final BusinessDayCalculator calc = new BusinessDayCalculator(year -> HOLIDAYS);

  @Test
  void weekendsAndHolidaysAreNotWorking() {
    assertThat(calc.isWorking(LocalDate.of(2027, 3, 27))).isFalse(); // Saturday
    assertThat(calc.isWorking(LocalDate.of(2027, 3, 25))).isFalse(); // holiday
    assertThat(calc.isWorking(LocalDate.of(2027, 3, 24))).isTrue();
  }

  @Test
  void addSkipsWeekendsAndHolidaysAndExcludesStartDate() {
    BusinessDayCalculator.AddResult r = calc.add(LocalDate.of(2027, 3, 24), 1);
    assertThat(r.result()).isEqualTo(LocalDate.of(2027, 3, 29)); // Thu, Fri holidays; weekend
    assertThat(r.holidaysSkipped())
        .extracting(BusinessDayCalculator.Holiday::name)
        .containsExactly("Jueves Santo", "Viernes Santo");
  }

  @Test
  void addZeroReturnsStartEvenOnWeekend() {
    LocalDate saturday = LocalDate.of(2027, 3, 27);
    assertThat(calc.add(saturday, 0).result()).isEqualTo(saturday);
  }

  @Test
  void addNegativeGoesBackwards() {
    assertThat(calc.add(LocalDate.of(2027, 3, 29), -1).result())
        .isEqualTo(LocalDate.of(2027, 3, 24));
  }

  @Test
  void addCrossesYearBoundary() {
    // Fri 2027-12-24, then Dec 25 holiday(Sat), Mon 27, Tue 28 ... Thu Jan 6 holiday
    assertThat(calc.add(LocalDate.of(2027, 12, 31), 3).result())
        .isEqualTo(LocalDate.of(2028, 1, 5)); // Mon 3, Tue 4, Wed 5
    assertThat(calc.add(LocalDate.of(2027, 12, 31), 4).result())
        .isEqualTo(LocalDate.of(2028, 1, 7)); // Thu 6 is a holiday
  }

  @Test
  void betweenCountsWithConfigurableBounds() {
    LocalDate mon = LocalDate.of(2027, 3, 22);
    LocalDate fri = LocalDate.of(2027, 3, 26);
    assertThat(calc.between(mon, fri, true, false).count()).isEqualTo(3); // Mon, Tue, Wed
    assertThat(calc.between(mon, fri, true, true).count()).isEqualTo(3);
    assertThat(calc.between(mon, fri, false, true).count()).isEqualTo(2);
    assertThat(calc.between(mon, mon, true, false).count()).isZero();
  }

  @Test
  void betweenRejectsReversedAndHugeRanges() {
    LocalDate d = LocalDate.of(2027, 3, 22);
    assertThatThrownBy(() -> calc.between(d.plusDays(1), d, true, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> calc.between(d, d.plusDays(BusinessDayCalculator.MAX_DAYS + 1), true, false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void addRejectsHugeDayCounts() {
    assertThatThrownBy(() -> calc.add(LocalDate.of(2027, 1, 1), BusinessDayCalculator.MAX_DAYS + 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void addAndBetweenAreConsistent() {
    Random random = new Random(42);
    for (int i = 0; i < 300; i++) {
      LocalDate start = LocalDate.of(2027, 1, 1).plusDays(random.nextInt(300));
      int n = 1 + random.nextInt(60);
      LocalDate end = calc.add(start, n).result();
      assertThat(calc.between(start, end, false, true).count())
          .as("add(%s, %d) = %s", start, n, end)
          .isEqualTo(n);
      assertThat(calc.isWorking(end)).isTrue();
    }
  }

  @Test
  void missingYearDataPropagatesInsteadOfBeingTreatedAsNoHolidays() {
    BusinessDayCalculator strict =
        new BusinessDayCalculator(
            year -> {
              if (year != 2027) {
                throw new IllegalStateException("no data for " + year);
              }
              return HOLIDAYS;
            });
    assertThatThrownBy(() -> strict.add(LocalDate.of(2027, 12, 30), 5))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("2028");
  }
}
