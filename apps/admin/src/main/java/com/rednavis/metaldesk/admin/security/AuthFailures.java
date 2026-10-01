package com.rednavis.metaldesk.admin.security;

import com.rednavis.metaldesk.admin.web.CorrelationId;
import com.rednavis.metaldesk.admin.web.ErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Answers a request the security filter refused with the platform's error envelope, the same body
 * the controllers' failures have: 401 for no valid token, 403 for a token whose role may not do
 * this. The filter runs before any controller, so the exception handler never sees these.
 */
@Component
public class AuthFailures implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final JacksonJsonHttpMessageConverter json;

  /**
   * Creates the failure writer.
   *
   * @param mapper the application's JSON mapper
   */
  public AuthFailures(JsonMapper mapper) {
    this.json = new JacksonJsonHttpMessageConverter(mapper);
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException failure)
      throws IOException {
    write(
        response,
        HttpStatus.UNAUTHORIZED,
        new ErrorEnvelope(
            "auth.unauthorized",
            "A staff identity is required",
            CorrelationId.choose(request.getHeader(CorrelationId.HEADER))));
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException failure)
      throws IOException {
    write(
        response,
        HttpStatus.FORBIDDEN,
        new ErrorEnvelope(
            "auth.forbidden",
            "This role may not do that",
            CorrelationId.choose(request.getHeader(CorrelationId.HEADER))));
  }

  private void write(HttpServletResponse response, HttpStatus status, ErrorEnvelope body)
      throws IOException {
    response.setStatus(status.value());
    json.write(body, MediaType.APPLICATION_JSON, new ServletServerHttpResponse(response));
  }
}
