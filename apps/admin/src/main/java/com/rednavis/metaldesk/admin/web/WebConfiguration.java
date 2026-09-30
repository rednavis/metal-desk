package com.rednavis.metaldesk.admin.web;

import com.rednavis.metaldesk.admin.security.StaffAuthenticationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Puts the staff check in front of every {@code /api} route. Actuator endpoints are not under
 * {@code /api}, so {@code /actuator/health} stays open for the platform's health checks.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class WebConfiguration implements WebMvcConfigurer {

  private final StaffAuthenticationInterceptor staff;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(staff).addPathPatterns("/api/**");
  }
}
