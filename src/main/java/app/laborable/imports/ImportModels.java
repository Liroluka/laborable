package app.laborable.imports;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/** Request and response shapes of the admin import endpoint. */
public final class ImportModels {

  private ImportModels() {}

  public enum Source {
    BOE,
    CATALONIA_LOCAL
  }

  public record ImportRequest(
      @NotNull Source source,
      @Min(2000) @Max(2100) int year,
      @Pattern(regexp = "BOE-A-\\d{4}-\\d{1,6}") String boeId,
      Boolean dryRun) {

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
