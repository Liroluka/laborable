package app.laborable.service;

import app.laborable.domain.Model.Place;
import app.laborable.persistence.MunicipalityRepository;
import app.laborable.persistence.MunicipalityRepository.Municipality;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Turns the optional region / municipality request parameters into a validated {@link Place}. */
@Service
public class PlaceResolver {

  private static final Pattern INE = Pattern.compile("\\d{5}");
  private static final int MAX_SUGGESTIONS = 5;

  private final MunicipalityRepository municipalities;

  public PlaceResolver(MunicipalityRepository municipalities) {
    this.municipalities = municipalities;
  }

  public Place resolve(String region, String municipality) {
    String regionCode = blankToNull(region) == null ? null : region.trim().toUpperCase(Locale.ROOT);
    if (regionCode != null && !municipalities.regionExists(regionCode)) {
      throw ProblemException.badRequest(
          "invalid-region", "Unknown region", "Region must be an ISO 3166-2:ES code such as CT.");
    }
    String query = blankToNull(municipality);
    if (query == null) {
      return new Place(regionCode, null, null);
    }
    Municipality found = INE.matcher(query.trim()).matches() ? byIne(query.trim()) : byName(query);
    if (regionCode != null && !regionCode.equals(found.regionCode())) {
      throw ProblemException.badRequest(
          "region-mismatch",
          "Region does not match municipality",
          "Municipality " + found.ineCode() + " belongs to region " + found.regionCode() + ".");
    }
    return new Place(found.regionCode(), found.ineCode(), found.name());
  }

  private Municipality byIne(String ine) {
    Optional<Municipality> known = municipalities.findByIne(ine);
    if (known.isPresent()) {
      return known.get();
    }
    // Unknown to the directory, but the province prefix still tells us the region.
    String region =
        municipalities
            .regionOfProvince(ine.substring(0, 2))
            .orElseThrow(
                () ->
                    ProblemException.notFound(
                        "unknown-municipality",
                        "Unknown municipality",
                        "INE code " + ine + " does not belong to any Spanish province.",
                        Map.of()));
    return new Municipality(ine, null, region);
  }

  private Municipality byName(String name) {
    String normalized = MunicipalityRepository.normalize(name);
    List<Municipality> exact = municipalities.findByNormalizedName(normalized);
    if (exact.size() == 1) {
      return exact.get(0);
    }
    if (exact.size() > 1) {
      throw ProblemException.conflict(
          "ambiguous-municipality",
          "Ambiguous municipality",
          "Several municipalities share this name; use the INE code.",
          Map.of("candidates", exact));
    }
    throw ProblemException.notFound(
        "unknown-municipality",
        "Unknown municipality",
        "No municipality with this exact name. Use the INE code or one of the suggestions.",
        Map.of("suggestions", municipalities.suggest(normalized, MAX_SUGGESTIONS)));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
