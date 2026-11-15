package app.laborable.web;

import app.laborable.domain.Model.Place;
import app.laborable.service.HolidayService;
import app.laborable.service.HolidayService.YearHolidays;
import app.laborable.service.PlaceResolver;
import app.laborable.web.Dtos.CoverageDto;
import app.laborable.web.Dtos.HolidaysResponse;
import app.laborable.web.Dtos.PlaceDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HolidayController {

  private final PlaceResolver places;
  private final HolidayService holidays;

  public HolidayController(PlaceResolver places, HolidayService holidays) {
    this.places = places;
    this.holidays = holidays;
  }

  @GetMapping("/holidays")
  public HolidaysResponse holidays(
      @RequestParam int year,
      @RequestParam(required = false) String region,
      @RequestParam(required = false) String municipality) {
    Place place = places.resolve(region, municipality);
    YearHolidays data = holidays.forYear(place, year);
    return new HolidaysResponse(
        year,
        PlaceDto.of(place),
        new CoverageDto(true, data.localLoaded()),
        data.holidays(),
        data.warnings());
  }
}
