package app.laborable.web;

import app.laborable.domain.Model.Place;
import app.laborable.service.BusinessDayService;
import app.laborable.service.BusinessDayService.Added;
import app.laborable.service.BusinessDayService.Counted;
import app.laborable.service.BusinessDayService.IsWorking;
import app.laborable.service.PlaceResolver;
import app.laborable.web.Dtos.AddResponse;
import app.laborable.web.Dtos.BetweenResponse;
import app.laborable.web.Dtos.IsWorkingResponse;
import app.laborable.web.Dtos.PlaceDto;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/business-days")
public class BusinessDayController {

  private final PlaceResolver places;
  private final BusinessDayService service;

  public BusinessDayController(PlaceResolver places, BusinessDayService service) {
    this.places = places;
    this.service = service;
  }

  @GetMapping("/is-working")
  public IsWorkingResponse isWorking(
      @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate date,
      @RequestParam(required = false) String region,
      @RequestParam(required = false) String municipality) {
    Place place = places.resolve(region, municipality);
    IsWorking r = service.isWorking(date, place);
    return new IsWorkingResponse(
        r.date(), r.working(), r.reason(), r.holiday(), PlaceDto.of(place), r.warnings());
  }

  @GetMapping("/add")
  public AddResponse add(
      @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate date,
      @RequestParam int days,
      @RequestParam(required = false) String region,
      @RequestParam(required = false) String municipality) {
    Place place = places.resolve(region, municipality);
    Added r = service.add(date, days, place);
    return new AddResponse(
        date,
        days,
        r.result().result(),
        r.result().holidaysSkipped(),
        PlaceDto.of(place),
        r.warnings());
  }

  @GetMapping("/between")
  public BetweenResponse between(
      @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "true") boolean includeFrom,
      @RequestParam(defaultValue = "false") boolean includeTo,
      @RequestParam(required = false) String region,
      @RequestParam(required = false) String municipality) {
    Place place = places.resolve(region, municipality);
    Counted r = service.between(from, to, includeFrom, includeTo, place);
    return new BetweenResponse(
        from,
        to,
        includeFrom,
        includeTo,
        r.result().count(),
        r.result().holidaysInRange(),
        PlaceDto.of(place),
        r.warnings());
  }
}
