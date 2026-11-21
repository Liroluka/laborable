package app.laborable.imports;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Request and response shapes of the admin import endpoint. */
public final class ImportModels {

  private ImportModels() {}

  public enum Source {
    /** National and regional holidays from the BOE resolution. */
    BOE,
    /** Municipal holidays of Catalonia (open data of the Generalitat). */
    CATALONIA_LOCAL,
    /** Municipal holidays of the Basque Country (Open Data Euskadi). */
    EUSKADI_LOCAL,
    /** Municipal holidays of any region, supplied in the request body. */
    LOCAL_GENERIC
  }

  /** One municipal holiday supplied by the caller for {@link Source#LOCAL_GENERIC}. */
  public record LocalHolidayInput(
      @NotNull @Pattern(regexp = "\\d{5}") String ine,
      @NotBlank @Size(max = 200) String municipality,
      @NotNull LocalDate date,
      @NotBlank @Size(max = 200) String name) {}

  public record ImportRequest(
      @NotNull Source source,
      @Min(2000) @Max(2100) int year,
      @Pattern(regexp = "BOE-A-\\d{4}-\\d{1,6}") String boeId,
      Boolean dryRun,
      @Pattern(regexp = "[A-Z]{2}") String region,
      @Size(max = 20000) List<@Valid @NotNull LocalHolidayInput> entries) {

    /** dryRun is optional in the JSON body and defaults to false. */
    public ImportRequest {
      dryRun = Boolean.TRUE.equals(dryRun);
    }
  }

  public record ImportResult(
      long runId,
      Source source,
      int year,
      boolean dryRun,
      String status,
      int parsed,
      List<String> notes,
      List<String> anomalies) {}
}
