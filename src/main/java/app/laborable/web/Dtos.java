package app.laborable.web;

import app.laborable.domain.BusinessDayCalculator.Holiday;
import app.laborable.domain.Model.HolidayView;
import app.laborable.domain.Model.Place;
import app.laborable.domain.Model.Warning;
import app.laborable.service.BusinessDayService.Reason;
import java.time.LocalDate;
import java.util.List;

/** Response shapes of the public API. */
public final class Dtos {

  private Dtos() {}

  public record PlaceDto(String region, String municipalityIne, String municipality) {
    static PlaceDto of(Place place) {
      return new PlaceDto(place.regionCode(), place.municipalityIne(), place.municipalityName());
    }
  }

  public record CoverageDto(boolean regionalOfficial, boolean localLoaded) {}

  public record HolidaysResponse(
      int year,
      PlaceDto place,
      CoverageDto coverage,
      List<HolidayView> holidays,
      List<Warning> warnings) {}

  public record IsWorkingResponse(
      LocalDate date,
      boolean working,
      Reason reason,
      HolidayView holiday,
      PlaceDto place,
      List<Warning> warnings) {}

  public record AddResponse(
      LocalDate start,
      int days,
      LocalDate result,
      List<Holiday> holidaysSkipped,
      PlaceDto place,
      List<Warning> warnings) {}

  public record BetweenResponse(
      LocalDate from,
      LocalDate to,
      boolean includeFrom,
      boolean includeTo,
      long count,
      List<Holiday> holidaysInRange,
      PlaceDto place,
      List<Warning> warnings) {}
}
