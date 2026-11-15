package app.laborable.persistence;

import java.sql.Types;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MunicipalityRepository {

  public record Municipality(String ineCode, String name, String regionCode) {}

  private static final String SELECT =
      "select m.ine_code, m.name, p.region_code from municipality m"
          + " join province p on p.code = m.province_code ";

  private final JdbcClient jdbc;
  private final JdbcTemplate template;

  public MunicipalityRepository(JdbcClient jdbc, JdbcTemplate template) {
    this.jdbc = jdbc;
    this.template = template;
  }

  /** Lower-case, accent-free, words separated by single spaces ("Vilafranca-del Penedès"). */
  public static String normalize(String text) {
    String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
    return decomposed
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .trim();
  }

  public Optional<Municipality> findByIne(String ine) {
    return jdbc.sql(SELECT + "where m.ine_code = :ine")
        .param("ine", ine)
        .query((rs, i) -> new Municipality(rs.getString(1), rs.getString(2), rs.getString(3)))
        .optional();
  }

  public List<Municipality> findByNormalizedName(String normalized) {
    return jdbc.sql(SELECT + "where m.name_normalized = :n order by m.ine_code")
        .param("n", normalized)
        .query((rs, i) -> new Municipality(rs.getString(1), rs.getString(2), rs.getString(3)))
        .list();
  }

  public List<Municipality> suggest(String normalizedFragment, int limit) {
    return jdbc.sql(
            SELECT + "where m.name_normalized like :p order by m.name_normalized limit :limit")
        .param("p", "%" + normalizedFragment.replace("%", "").replace("_", "") + "%")
        .param("limit", limit)
        .query((rs, i) -> new Municipality(rs.getString(1), rs.getString(2), rs.getString(3)))
        .list();
  }

  public Optional<String> regionOfProvince(String provinceCode) {
    return jdbc.sql("select region_code from province where code = :c")
        .param("c", provinceCode)
        .query(String.class)
        .optional();
  }

  public boolean regionExists(String code) {
    return jdbc.sql("select exists(select 1 from region where code = :c)")
        .param("c", code, Types.VARCHAR)
        .query(Boolean.class)
        .single();
  }

  public void upsertAll(List<Municipality> municipalities) {
    template.batchUpdate(
        "insert into municipality (ine_code, name, name_normalized, province_code)"
            + " values (?, ?, ?, ?) on conflict (ine_code) do update"
            + " set name = excluded.name, name_normalized = excluded.name_normalized",
        municipalities,
        500,
        (ps, m) -> {
          ps.setString(1, m.ineCode());
          ps.setString(2, m.name());
          ps.setString(3, normalize(m.name()));
          ps.setString(4, m.ineCode().substring(0, 2));
        });
  }
}
