package com.rednavis.metaldesk.admin.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.function.ThrowingSupplier;

/**
 * The security filter chain: <strong>default-deny</strong>. Public are the health probe and sign-in
 * itself; every other route, including one that does not exist, needs a valid staff bearer token.
 * There is no session, no CSRF token (nothing rides on a cookie) and no login page: the token is
 * the only credential, and the SPA's own form fetches it.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({JwtProperties.class, ThrottleProperties.class})
public class SecurityConfiguration {

  /** Creates the configuration. */
  public SecurityConfiguration() {
    // Nothing to set up: the bean methods do the work.
  }

  /**
   * Builds the filter chain.
   *
   * @param http the security builder
   * @param decoder validates bearer tokens
   * @param converter turns a validated token into the request's authentication
   * @param failures answers 401 and 403 with the shared error envelope
   * @return the chain
   * @throws IllegalStateException if the chain cannot be built
   */
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtDecoder decoder, StaffTokenConverter converter, AuthFailures failures) {
    http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            handling -> handling.authenticationEntryPoint(failures).accessDeniedHandler(failures))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST, "/api/admin/auth/sign-in", "/api/admin/auth/sign-out")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            resource ->
                resource
                    .authenticationEntryPoint(failures)
                    .accessDeniedHandler(failures)
                    .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)));
    // build() declares `throws Exception`; ThrowingSupplier rethrows a failure unchecked.
    final ThrowingSupplier<SecurityFilterChain> chain = http::build;
    return chain.get();
  }

  /**
   * The password hash. BCrypt is adaptive and needs no extra dependency.
   *
   * @return the encoder
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
