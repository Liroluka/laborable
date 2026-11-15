package app.laborable.persistence;

import app.laborable.domain.Model.HolidayKind;
import app.laborable.domain.Model.HolidayView;
import app.laborable.domain.Model.NewHoliday;
import app.laborable.domain.Model.Scope;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class HolidayRepository {

  private final JdbcClient jdbc;
  private final JdbcTemplate template;

  public HolidayRepository(JdbcClient jdbc, JdbcTemplate template) {
    this.jdbc = jdbc;
    this.template = template;
  }

  /** Holidays of one year that apply to the place; region and ine may be null. */
  @Cacheable(cacheNames = "holidays", key = "#year + '|' + #region + '|' + #ine")
  public List<HolidayView> find(int year, String region, String ine) {
    return jdbc.sql(
            """
            select holiday_date, name, scope, kind from holiday
            where holiday_date between :from and :to
              and (scope = 'NATIONAL'
                   or (scope = 'REGIONAL' and region_code = cast(:region as varchar))
                   or (scope = 'LOCAL' and municipality_ine = cast(:ine as varchar)))
            order by holiday_date,
                     case scope when 'LOCAL' then 0 when 'REGIONAL' then 1 else 2 end
            """)
        .param("from", LocalDate.of(year, 1, 1))
        .param("to", LocalDate.of(year, 12, 31))
        .param("region", region, Types.VARCHAR)
        .param("ine", ine, Types.VARCHAR)
        .query(
            (rs, i) ->
                new HolidayView(
                    rs.getObject("holiday_date", LocalDate.class),
                    rs.getString("name"),
                    Scope.valueOf(rs.getString("scope")),
                    HolidayKind.valueOf(rs.getString("kind"))))
        .list();
  }

  /** Whether coverage exists; a null region means "any region". */
  public boolean hasCoverage(int year, String region, String level) {
    return jdbc.sql(
            """
            select exists(select 1 from coverage
              where year = :year and level = :level
                and (cast(:region as varchar) is null or region_code = cast(:region as varchar)))
            """)
        .param("year", year)
        .param("level", level)
        .param("region", region, Types.VARCHAR)
        .query(Boolean.class)
        .single();
  }

  public long createRun(String source, int year, String sourceRef, boolean dryRun) {
    return jdbc.sql(
            "insert into import_run (source, year, source_ref, dry_run, status)"
                + " values (:s, :y, :r, :d, 'RUNNING') returning id")
        .param("s", source)
        .param("y", year)
        .param("r", sourceRef, Types.VARCHAR)
        .param("d", dryRun)
        .query(Long.class)
        .single();
  }

  public void finishRun(long runId, String status, String statsJson) {
    jdbc.sql(
            "update import_run set status = :st, finished_at = now(),"
                + " stats = cast(:stats as jsonb) where id = :id")
        .param("st", status)
        .param("stats", statsJson)
        .param("id", runId)
        .update();
  }

  public void deleteOfficial(int year) {
    jdbc.sql(
            "delete from holiday where scope in ('NATIONAL', 'REGIONAL')"
                + " and holiday_date between :from and :to")
        .param("from", LocalDate.of(year, 1, 1))
        .param("to", LocalDate.of(year, 12, 31))
        .update();
    jdbc.sql("delete from coverage where year = :y and level = 'REGIONAL'")
        .param("y", year)
        .update();
  }

  public void deleteLocal(int year, String region) {
    jdbc.sql(
            """
            delete from holiday h using municipality m, province p
            where h.scope = 'LOCAL' and h.municipality_ine = m.ine_code
              and m.province_code = p.code and p.region_code = :region
              and h.holiday_date between :from and :to
            """)
        .param("region", region)
        .param("from", LocalDate.of(year, 1, 1))
        .param("to", LocalDate.of(year, 12, 31))
        .update();
    jdbc.sql("delete from coverage where year = :y and region_code = :r and level = 'LOCAL'")
        .param("y", year)
        .param("r", region)
        .update();
  }

  public void insertAll(List<NewHoliday> holidays, long runId) {
    template.batchUpdate(
        "insert into holiday (holiday_date, scope, kind, region_code, municipality_ine, name,"
            + " import_run_id) values (?, ?, ?, ?, ?, ?, ?) on conflict do nothing",
        holidays,
        500,
        (ps, h) -> {
          ps.setObject(1, h.date());
          ps.setString(2, h.scope().name());
          ps.setString(3, h.kind().name());
          ps.setString(4, h.regionCode());
          ps.setString(5, h.municipalityIne());
          ps.setString(6, h.name());
          ps.setLong(7, runId);
        });
  }

  public void upsertCoverage(int year, String region, String level, long runId) {
    jdbc.sql(
            "insert into coverage (year, region_code, level, status, import_run_id)"
                + " values (:y, :r, :l, 'OFFICIAL', :run)"
                + " on conflict (year, region_code, level) do update"
                + " set status = 'OFFICIAL', import_run_id = excluded.import_run_id")
        .param("y", year)
        .param("r", region)
        .param("l", level)
        .param("run", runId)
        .update();
  }
}
