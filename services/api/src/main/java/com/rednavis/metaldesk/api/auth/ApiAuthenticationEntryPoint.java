package com.rednavis.metaldesk.api.auth;

import com.rednavis.metaldesk.api.web.ApiErrorEnvelope;
import com.rednavis.metaldesk.api.web.CorrelationId;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Answers an unauthenticated or forbidden request with the same {@link ApiErrorEnvelope} every
 * other error uses, instead of an empty body.
 *
 * <p>A missing, expired, wrongly signed or wrongly addressed token all get the same 401: the caller
 * is told the token is not accepted, never why.
 */
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint
    implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

  private final JsonMapper json;

  @Override
  public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException failure) {
    exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    return write(
        exchange, HttpStatus.UNAUTHORIZED, "auth.unauthorized", "Authentication is required");
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException failure) {
    return write(exchange, HttpStatus.FORBIDDEN, "auth.forbidden", "Access is denied");
  }

  private Mono<Void> write(
      ServerWebExchange exchange, HttpStatus status, String code, String message) {
    final String supplied = exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER);
    final ApiErrorEnvelope body =
        new ApiErrorEnvelope(code, message, CorrelationId.choose(supplied));
    final ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    final DataBuffer buffer = response.bufferFactory().wrap(json.writeValueAsBytes(body));
    return response.writeWith(Mono.just(buffer));
  }
}
