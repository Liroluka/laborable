package app.laborable.imports;

import app.laborable.config.LaborableProperties;
import app.laborable.service.ProblemException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * HTTPS-only fetcher restricted to the configured official hosts. Redirects are not followed, the
 * body size is capped and timeouts apply, so a request can never be steered elsewhere (SSRF).
 */
@Component
public class HttpRemoteFetcher implements RemoteFetcher {

  private final Set<String> allowedHosts;
  private final long maxBytes;
  private final java.time.Duration timeout;
  private final HttpClient client;

  public HttpRemoteFetcher(LaborableProperties props) {
    LaborableProperties.Imports imports = props.imports();
    this.allowedHosts =
        Set.of(
            URI.create(imports.boeBaseUrl()).getHost(),
            URI.create(imports.cataloniaBaseUrl()).getHost());
    this.maxBytes = imports.maxResponseBytes();
    this.timeout = imports.timeout();
    this.client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(timeout)
            .build();
  }

  @Override
  public String fetch(URI uri) {
    if (!"https".equals(uri.getScheme()) || !allowedHosts.contains(uri.getHost())) {
      throw ProblemException.badGateway(
          "source-not-allowed",
          "Source not allowed",
          "Only official HTTPS sources can be fetched.");
    }
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("Accept", "application/xml, application/json")
            .GET()
            .build();
    try {
      HttpResponse<InputStream> response =
          client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() != 200) {
        throw ProblemException.badGateway(
            "source-error",
            "Source returned an error",
            "The official source answered HTTP " + response.statusCode() + ".");
      }
      return readLimited(response.body());
    } catch (IOException e) {
      throw ProblemException.badGateway(
          "source-unreachable", "Source unreachable", "Could not download the official source.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw ProblemException.badGateway(
          "source-unreachable", "Source unreachable", "Download was interrupted.");
    }
  }

  private String readLimited(InputStream in) throws IOException {
    try (in) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[16 * 1024];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
        if (out.size() > maxBytes) {
          throw ProblemException.badGateway(
              "source-too-large",
              "Source too large",
              "The official source exceeded the size limit.");
        }
      }
      return out.toString(StandardCharsets.UTF_8);
    }
  }
}
