package app.laborable.service;

import java.util.LinkedHashMap;
import java.util.Map;

/** A business-level failure that maps to an RFC 9457 problem response. */
public class ProblemException extends RuntimeException {

  private final int status;
  private final String type;
  private final String title;
  private final transient Map<String, Object> extras;

  public ProblemException(
      int status, String type, String title, String detail, Map<String, Object> extras) {
    super(detail);
    this.status = status;
    this.type = type;
    this.title = title;
    this.extras = extras;
  }

  public static ProblemException badRequest(String type, String title, String detail) {
    return new ProblemException(400, type, title, detail, Map.of());
  }

  public static ProblemException notFound(
      String type, String title, String detail, Map<String, Object> extras) {
    return new ProblemException(404, type, title, detail, extras);
  }

  public static ProblemException conflict(
      String type, String title, String detail, Map<String, Object> extras) {
    return new ProblemException(409, type, title, detail, extras);
  }

  public static ProblemException unprocessable(String type, String title, String detail) {
    return new ProblemException(422, type, title, detail, Map.of());
  }

  public static ProblemException badGateway(String type, String title, String detail) {
    return new ProblemException(502, type, title, detail, Map.of());
  }

  public int status() {
    return status;
  }

  public String type() {
    return type;
  }

  public String title() {
    return title;
  }

  public Map<String, Object> extras() {
    return new LinkedHashMap<>(extras);
  }
}
