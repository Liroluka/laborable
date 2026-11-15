package app.laborable.domain;

import java.time.LocalDate;

/** Plain domain types shared across layers. */
public final class Model {

  private Model() {}

  public enum Scope {
    NATIONAL,
    REGIONAL,
    LOCAL
  }

  public enum HolidayKind {
    NATIONAL_FIXED,
    NATIONAL_SUBSTITUTABLE,
    REGIONAL,
    LOCAL
  }

  /** A holiday as exposed by the API. */
  public record HolidayView(LocalDate date, String name, Scope scope, HolidayKind kind) {}

  /** A holiday to be stored; exactly one of regionCode / municipalityIne matches the scope. */
  public record NewHoliday(
      LocalDate date,
      String name,
      Scope scope,
      HolidayKind kind,
      String regionCode,
      String municipalityIne) {}

  /** A place used for lookups. Region and municipality are optional. */
  public record Place(String regionCode, String municipalityIne, String municipalityName) {}

  public record Warning(String code, String message) {}
}
