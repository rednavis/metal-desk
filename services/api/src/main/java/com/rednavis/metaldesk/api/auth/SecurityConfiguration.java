package com.rednavis.metaldesk.api.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;

/**
 * The reactive security filter chain: <strong>default-deny</strong>, with an explicit allowlist of
 * public routes.
 *
 * <p>Public are the storefront's anonymous reads (BRD FR-1.x: catalog and market data), the health
 * probe, sign-in itself, and the account flows that precede having a token (registration, email
 * verification and password reset). Every other route, including one that does not exist, needs a
 * valid bearer token. There is no session, no CSRF token (nothing rides on a cookie) and no login
 * page: the token is the only credential.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties({JwtProperties.class, ThrottleProperties.class})
public class SecurityConfiguration {

  /** Anonymous reads. */
  private static final String[] PUBLIC_READS = {
    "/api/catalog/**", "/api/market-data/**", "/actuator/health", "/actuator/health/**"
  };

  /** Anonymous writes: sign-in and the account flows a customer has to do before having a token. */
  private static final String[] PUBLIC_POSTS = {
    "/api/auth/sign-in",
    "/api/account/register",
    "/api/account/verify-email",
    "/api/account/password-reset/request",
    "/api/account/password-reset/confirm"
  };

  /**
   * Builds the filter chain.
   *
   * @param http the security builder
   * @param validator turns a validated token into the request's authentication
   * @param decoder validates bearer tokens
   * @param failures answers 401 and 403 with the shared error envelope
   * @return the chain
   */
  @Bean
  public SecurityWebFilterChain securityFilterChain(
      ServerHttpSecurity http,
      JwtValidator validator,
      ReactiveJwtDecoder decoder,
      ApiAuthenticationEntryPoint failures) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
        .exceptionHandling(
            handling -> handling.authenticationEntryPoint(failures).accessDeniedHandler(failures))
        .authorizeExchange(
            exchanges ->
                exchanges
                    .pathMatchers(HttpMethod.GET, PUBLIC_READS)
                    .permitAll()
                    .pathMatchers(HttpMethod.POST, PUBLIC_POSTS)
                    .permitAll()
                    .anyExchange()
                    .authenticated())
        .oauth2ResourceServer(
            resource ->
                resource
                    .authenticationEntryPoint(failures)
                    .accessDeniedHandler(failures)
                    .jwt(
                        jwt ->
                            jwt.jwtDecoder(decoder)
                                .jwtAuthenticationConverter(validator::authentication)))
        .build();
  }

  /**
   * The password hash. BCrypt is adaptive and needs no extra dependency; its 72-byte input limit is
   * enforced by {@link PasswordEncoderAdapter}.
   *
   * @return the encoder
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
