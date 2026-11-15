package app.laborable.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.laborable.imports.RemoteFetcher;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full stack on a real PostgreSQL (embedded, no Docker); official sources are replaced by fixtures.
 */
@SpringBootTest(properties = "laborable.admin.api-key=test-admin-key-0123456789abcdef")
@AutoConfigureMockMvc
@Import(ApiIntegrationTest.FixtureSources.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

  private static final String KEY = "test-admin-key-0123456789abcdef";
  private static final EmbeddedPostgres POSTGRES = startPostgres();

  private static EmbeddedPostgres startPostgres() {
    try {
      return EmbeddedPostgres.builder().start();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "postgres");
  }

  @TestConfiguration
  static class FixtureSources {
    @Bean
    @Primary
    RemoteFetcher fixtureFetcher() {
      return (URI uri) -> {
        String resource =
            uri.toString().contains("xml.php")
                ? "fixtures/boe-fiestas-2027.xml"
                : "fixtures/catalonia-local-2027.json";
        try {
          return new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      };
    }
  }

  @Autowired private MockMvc mvc;

  private static String importBody(String source, boolean dryRun) {
    return "{\"source\":\""
        + source
        + "\",\"year\":2027,\"boeId\":\"BOE-A-2026-21254\",\"dryRun\":"
        + dryRun
        + "}";
  }

  @Test
  @Order(1)
  void withoutImportedDataTheApiSaysSoInsteadOfGuessing() throws Exception {
    mvc.perform(get("/holidays").param("year", "2027").param("region", "CT"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:laborable:problem:data-not-available"));
  }

  @Test
  @Order(2)
  void adminEndpointRequiresTheApiKey() throws Exception {
    mvc.perform(
            post("/admin/holidays/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content(importBody("BOE", true)))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/admin/holidays/import")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "wrong-key-wrong-key-wrong-key")
                .content(importBody("BOE", true)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @Order(3)
  void invalidImportRequestsAreRejected() throws Exception {
    mvc.perform(
            post("/admin/holidays/import")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", KEY)
                .content("{\"source\":\"BOE\",\"year\":2027,\"boeId\":\"../../etc/passwd\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/admin/holidays/import")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", KEY)
                .content("{\"source\":\"CATALONIA_LOCAL\",\"year\":2027}"))
        .andExpect(status().isConflict()); // BOE must be imported first
  }

  @Test
  @Order(4)
  void dryRunStoresNothing() throws Exception {
    mvc.perform(
            post("/admin/holidays/import")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", KEY)
                .content(importBody("BOE", true)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRY_RUN"))
        .andExpect(jsonPath("$.parsed").value(greaterThan(50)));
    mvc.perform(get("/holidays").param("year", "2027").param("region", "CT"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  @Order(5)
  void importsBoeAndThenCatalanLocalHolidays() throws Exception {
    mvc.perform(
            post("/admin/holidays/import")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", KEY)
                .content(importBody("BOE", false)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.notes", hasSize(2)));
    mvc.perform(
            post("/admin/holidays/import")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", KEY)
                .content(importBody("CATALONIA_LOCAL", false)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parsed").value(5))
        .andExpect(jsonPath("$.anomalies", hasSize(1)));
  }

  @Test
  @Order(6)
  void listsHolidaysPerRegionAndNationally() throws Exception {
    mvc.perform(get("/holidays").param("year", "2027").param("region", "ct"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.holidays[*].date", hasItem("2027-03-29")))
        .andExpect(jsonPath("$.holidays[*].date", not(hasItem("2027-03-25"))));
    mvc.perform(get("/holidays").param("year", "2027"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.holidays[*].date", hasItem("2027-01-06")))
        .andExpect(jsonPath("$.warnings[0].code").value("NO_REGION"));
  }

  @Test
  @Order(7)
  void isWorkingDistinguishesRegionsAndWeekends() throws Exception {
    mvc.perform(get("/business-days/is-working").param("date", "2027-03-29").param("region", "CT"))
        .andExpect(jsonPath("$.working").value(false))
        .andExpect(jsonPath("$.reason").value("HOLIDAY"));
    mvc.perform(get("/business-days/is-working").param("date", "2027-03-29").param("region", "MD"))
        .andExpect(jsonPath("$.working").value(true));
    mvc.perform(get("/business-days/is-working").param("date", "2027-03-27").param("region", "MD"))
        .andExpect(jsonPath("$.reason").value("WEEKEND"));
  }

  @Test
  @Order(8)
  void addAndBetweenUseRegionalCalendars() throws Exception {
    mvc.perform(
            get("/business-days/add")
                .param("date", "2027-03-24")
                .param("days", "1")
                .param("region", "CT"))
        .andExpect(jsonPath("$.result").value("2027-03-25"));
    mvc.perform(
            get("/business-days/add")
                .param("date", "2027-03-24")
                .param("days", "1")
                .param("region", "MD"))
        .andExpect(jsonPath("$.result").value("2027-03-29"))
        .andExpect(jsonPath("$.holidaysSkipped", hasSize(2)));
    mvc.perform(
            get("/business-days/between")
                .param("from", "2027-03-22")
                .param("to", "2027-03-26")
                .param("region", "MD"))
        .andExpect(jsonPath("$.count").value(3));
  }

  @Test
  @Order(9)
  void municipalHolidaysAreAppliedAndPlacesAreResolvedStrictly() throws Exception {
    mvc.perform(get("/holidays").param("year", "2027").param("municipality", "08305"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.place.region").value("CT"))
        .andExpect(jsonPath("$.coverage.localLoaded").value(true))
        .andExpect(jsonPath("$.holidays[*].date", hasItem("2027-09-01")));
    mvc.perform(
            get("/business-days/is-working")
                .param("date", "2027-09-01")
                .param("municipality", "vilafranca-del-penedes"))
        .andExpect(jsonPath("$.working").value(false));
    mvc.perform(get("/holidays").param("year", "2027").param("municipality", "vilafranca"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.suggestions[0].ineCode").value("08305"));
    mvc.perform(get("/holidays").param("year", "2027").param("municipality", "castell test"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.candidates", hasSize(2)));
    mvc.perform(
            get("/holidays")
                .param("year", "2027")
                .param("municipality", "08305")
                .param("region", "MD"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(10)
  void unknownLocalDataProducesAWarningNotASilentAnswer() throws Exception {
    mvc.perform(get("/holidays").param("year", "2027").param("municipality", "28079"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.place.region").value("MD"))
        .andExpect(jsonPath("$.warnings[*].code", hasItem("LOCAL_HOLIDAYS_NOT_LOADED")));
  }

  @Test
  @Order(11)
  void validatesInputAndHidesInternals() throws Exception {
    mvc.perform(get("/holidays").param("year", "2030").param("region", "CT"))
        .andExpect(status().isUnprocessableContent());
    mvc.perform(get("/holidays").param("year", "1800")).andExpect(status().isBadRequest());
    mvc.perform(get("/holidays").param("year", "2027").param("region", "XX"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/business-days/add").param("date", "not-a-date").param("days", "1"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/business-days/add").param("date", "2027-03-24").param("days", "99999"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(not(containsString("Exception"))));
    mvc.perform(get("/business-days/between").param("from", "2027-03-26").param("to", "2027-03-22"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(12)
  void healthAndOpenApiAreAvailable() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }
}
