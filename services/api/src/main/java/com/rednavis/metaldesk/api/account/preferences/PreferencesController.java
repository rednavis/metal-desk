package com.rednavis.metaldesk.api.account.preferences;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * The signed-in customer's display preferences (BRD FR-1.6 to FR-1.8). Authenticated: the default
 * deny of the security configuration applies, so an anonymous request is a 401.
 */
@RestController
@RequestMapping("/api/account/preferences")
@RequiredArgsConstructor
public class PreferencesController {

  private final PreferencesService service;

  /**
   * Reads the customer's preferences.
   *
   * @param customer the signed-in customer
   * @return their preferences; empty if they have chosen nothing
   */
  @GetMapping
  public Mono<PreferencesView> read(@AuthenticationPrincipal AuthenticatedCustomer customer) {
    return service.read(customer.id());
  }

  /**
   * Replaces the customer's preferences.
   *
   * @param customer the signed-in customer
   * @param request the new preferences; a field left out is cleared
   * @return what was stored
   */
  @PutMapping
  public Mono<PreferencesView> save(
      @AuthenticationPrincipal AuthenticatedCustomer customer,
      @RequestBody PreferencesView request) {
    return service.save(customer.id(), request);
  }
}
