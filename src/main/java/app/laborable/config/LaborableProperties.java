package app.laborable.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("laborable")
public record LaborableProperties(Admin admin, Cors cors, Imports imports) {

  public record Admin(String apiKey) {}

  public record Cors(List<String> allowedOrigins) {}

  public record Imports(
      String boeBaseUrl,
      String cataloniaBaseUrl,
      String euskadiBaseUrl,
      long maxResponseBytes,
      Duration timeout) {}
}
