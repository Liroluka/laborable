package app.laborable.imports;

import app.laborable.config.LaborableProperties;
import app.laborable.domain.Model.HolidayView;
import app.laborable.imports.ImportModels.ImportRequest;
import app.laborable.imports.ImportModels.ImportResult;
import app.laborable.imports.ImportModels.Source;
import app.laborable.persistence.HolidayRepository;
import app.laborable.persistence.MunicipalityRepository;
import app.laborable.service.ProblemException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ImportService {

  private static final Logger LOG = LoggerFactory.getLogger(ImportService.class);
  private static final int ANOMALY_SAMPLE = 50;

  private final RemoteFetcher fetcher;
  private final BoeAnnexParser boeParser;
  private final CataloniaLocalParser cataloniaParser;
  private final HolidayRepository holidays;
  private final MunicipalityRepository municipalities;
  private final TransactionTemplate tx;
  private final CacheManager caches;
  private final LaborableProperties props;

  public ImportService(
      RemoteFetcher fetcher,
      BoeAnnexParser boeParser,
      CataloniaLocalParser cataloniaParser,
      HolidayRepository holidays,
      MunicipalityRepository municipalities,
      TransactionTemplate tx,
      CacheManager caches,
      LaborableProperties props) {
    this.fetcher = fetcher;
    this.boeParser = boeParser;
    this.cataloniaParser = cataloniaParser;
    this.holidays = holidays;
    this.municipalities = municipalities;
    this.tx = tx;
    this.caches = caches;
    this.props = props;
  }

  public ImportResult run(ImportRequest request) {
    long runId =
        holidays.createRun(
            request.source().name(), request.year(), request.boeId(), request.dryRun());
    try {
      ImportResult result =
          request.source() == Source.BOE
              ? importBoe(request, runId)
              : importCatalonia(request, runId);
      holidays.finishRun(
          runId,
          result.status(),
          "{\"parsed\":" + result.parsed() + ",\"anomalies\":" + result.anomalies().size() + "}");
      return result;
    } catch (ProblemException e) {
      holidays.finishRun(runId, "FAILED", "{}");
      throw e;
    } catch (RuntimeException e) {
      LOG.error("Import {} failed", runId, e);
      holidays.finishRun(runId, "FAILED", "{}");
      throw ProblemException.unprocessable(
          "import-failed",
          "Import failed",
          "The source document could not be processed; see the import run " + runId + ".");
    }
  }

  private ImportResult importBoe(ImportRequest request, long runId) {
    if (request.boeId() == null) {
      throw ProblemException.badRequest(
          "missing-boe-id", "boeId required", "boeId is required for source BOE.");
    }
    URI uri =
        URI.create(props.imports().boeBaseUrl() + "/diario_boe/xml.php?id=" + request.boeId());
    BoeAnnexParser.Parsed parsed = boeParser.parse(fetcher.fetch(uri));
    if (parsed.year() != request.year()) {
      throw ProblemException.unprocessable(
          "year-mismatch",
          "Year mismatch",
          "The document is for year "
              + parsed.year()
              + " but year "
              + request.year()
              + " was requested.");
    }
    if (!request.dryRun()) {
      tx.executeWithoutResult(
          status -> {
            holidays.deleteOfficial(request.year());
            holidays.insertAll(parsed.holidays(), runId);
            parsed
                .regions()
                .forEach(r -> holidays.upsertCoverage(request.year(), r, "REGIONAL", runId));
          });
      clearCache();
    }
    List<String> notes = new ArrayList<>(parsed.notes());
    return new ImportResult(
        runId,
        request.source(),
        request.year(),
        request.dryRun(),
        request.dryRun() ? "DRY_RUN" : "DONE",
        parsed.holidays().size(),
        notes,
        parsed.warnings());
  }

  private ImportResult importCatalonia(ImportRequest request, long runId) {
    int year = request.year();
    if (!holidays.hasCoverage(year, CataloniaLocalParser.REGION, "REGIONAL")) {
      throw ProblemException.conflict(
          "boe-required",
          "Import the BOE resolution first",
          "Regional holidays for " + year + " must be loaded before Catalan local holidays.",
          java.util.Map.of());
    }
    Set<LocalDate> regionWide = new HashSet<>();
    for (HolidayView h : holidays.find(year, CataloniaLocalParser.REGION, null)) {
      regionWide.add(h.date());
    }
    String json = fetcher.fetch(cataloniaUri(year));
    CataloniaLocalParser.Parsed parsed = cataloniaParser.parse(json, year, regionWide);
    if (!request.dryRun()) {
      tx.executeWithoutResult(
          status -> {
            municipalities.upsertAll(parsed.municipalities());
            holidays.deleteLocal(year, CataloniaLocalParser.REGION);
            holidays.insertAll(parsed.holidays(), runId);
            holidays.upsertCoverage(year, CataloniaLocalParser.REGION, "LOCAL", runId);
          });
      clearCache();
    }
    List<String> notes = new ArrayList<>();
    parsed.rejected().forEach((reason, count) -> notes.add("rejected " + reason + ": " + count));
    List<String> anomalies = parsed.anomalies().stream().limit(ANOMALY_SAMPLE).toList();
    return new ImportResult(
        runId,
        request.source(),
        year,
        request.dryRun(),
        request.dryRun() ? "DRY_RUN" : "DONE",
        parsed.holidays().size(),
        notes,
        anomalies);
  }

  private URI cataloniaUri(int year) {
    String query =
        "$select="
            + enc("data,codi_municipi_ine,ajuntament_o_nucli_municipal,pedania,festiu")
            + "&$where="
            + enc("any_calendari='" + year + "'")
            + "&$order="
            + enc("codi_municipi_ine,data")
            + "&$limit=50000";
    return URI.create(props.imports().cataloniaBaseUrl() + "/resource/b4eh-r8up.json?" + query);
  }

  private static String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private void clearCache() {
    Cache cache = caches.getCache("holidays");
    if (cache != null) {
      cache.clear();
    }
  }
}
