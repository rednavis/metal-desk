package com.rednavis.metaldesk.admin.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Refuses every {@code /api} request that has no staff identity, before any controller runs, and
 * makes the identity available to the controllers as a request attribute.
 */
@Component
@RequiredArgsConstructor
public class StaffAuthenticationInterceptor implements HandlerInterceptor {

  /** The request attribute holding the {@link StaffPrincipal}. */
  public static final String PRINCIPAL = "metaldesk.staff";

  private final StaffPrincipalResolver resolver;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    final StaffPrincipal staff = resolver.resolve(request).orElseThrow(UnauthorizedException::new);
    request.setAttribute(PRINCIPAL, staff);
    return true;
  }
}
