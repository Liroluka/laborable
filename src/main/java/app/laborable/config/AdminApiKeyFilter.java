package app.laborable.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates /admin requests that carry the configured X-API-Key; disabled without a key. */
public class AdminApiKeyFilter extends OncePerRequestFilter {

  static final String HEADER = "X-API-Key";
  static final int MIN_KEY_LENGTH = 24;

  private final byte[] expectedDigest;

  public AdminApiKeyFilter(String configuredKey) {
    if (configuredKey == null || configuredKey.isBlank()) {
      this.expectedDigest = null;
    } else if (configuredKey.length() < MIN_KEY_LENGTH) {
      throw new IllegalStateException(
          "LABORABLE_ADMIN_API_KEY must be at least " + MIN_KEY_LENGTH + " characters long");
    } else {
      this.expectedDigest = sha256(configuredKey);
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/admin/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String provided = request.getHeader(HEADER);
    if (expectedDigest != null
        && provided != null
        && MessageDigest.isEqual(expectedDigest, sha256(provided))) {
      SecurityContextHolder.getContext()
          .setAuthentication(
              UsernamePasswordAuthenticationToken.authenticated(
                  "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
    chain.doFilter(request, response);
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
