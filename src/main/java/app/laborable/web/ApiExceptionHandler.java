package app.laborable.web;

import app.laborable.service.ProblemException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Renders every failure as application/problem+json without leaking internals. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private static final String TYPE_PREFIX = "urn:laborable:problem:";

  @ExceptionHandler(ProblemException.class)
  public ResponseEntity<ProblemDetail> problem(ProblemException e) {
    ProblemDetail body =
        ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(e.status()), e.getMessage());
    body.setType(URI.create(TYPE_PREFIX + e.type()));
    body.setTitle(e.title());
    e.extras().forEach(body::setProperty);
    return ResponseEntity.status(e.status()).body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> unexpected(Exception e) {
    LOG.error("Unhandled error", e);
    ProblemDetail body =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error.");
    body.setType(URI.create(TYPE_PREFIX + "internal"));
    body.setTitle("Internal error");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
