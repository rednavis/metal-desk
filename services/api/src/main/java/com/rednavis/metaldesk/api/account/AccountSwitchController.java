package com.rednavis.metaldesk.api.account;

import com.rednavis.metaldesk.api.account.dto.SwitchRequest;
import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.auth.JwtIssuer;
import com.rednavis.metaldesk.api.auth.SignInResponse;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.api.web.ApiErrorEnvelope;
import com.rednavis.metaldesk.api.web.CorrelationId;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Switching to another account without signing in again (BRD FR-2.5).
 *
 * <p>Switching <em>re-issues</em> a token; it never changes the one presented. The current token is
 * validated by the security chain like any other, the target is checked against {@link
 * AccountAccessService}, and a new token is minted for the target. Nothing is stored on the server
 * (Architecture section 5). A target the caller may not act as, or one that does not exist, gets
 * the same 403, so the endpoint cannot be used to probe customer ids.
 */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountSwitchController {

  /** The error code of a refused switch. */
  public static final String DENIED = "account.switch-denied";

  private static final String BEARER = "Bearer";

  private final AccountAccessService access;
  private final CustomerRepository customers;
  private final JwtIssuer issuer;

  /**
   * Switches to another account the caller may act as.
   *
   * @param current the authenticated caller
   * @param request the target customer id
   * @param correlation the caller's correlation id, if any
   * @return 200 with a new token for the target, or 403
   */
  @PostMapping("/switch")
  public Mono<ResponseEntity<Object>> switchTo(
      @AuthenticationPrincipal AuthenticatedCustomer current,
      @RequestBody(required = false) SwitchRequest request,
      @RequestHeader(name = CorrelationId.HEADER, required = false) String correlation) {
    final String target = request == null ? null : request.targetCustomerId();
    final ResponseEntity<Object> denied =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(
                new ApiErrorEnvelope(
                    DENIED, "That account is not available", CorrelationId.choose(correlation)));
    return target == null
        ? Mono.just(denied)
        : access
            .mayActAs(current.id().value(), target)
            .filter(allowed -> allowed)
            .flatMap(allowed -> customers.findById(target))
            .map(
                customer ->
                    issuer.issue(
                        new AuthenticatedCustomer(
                            new CustomerId(customer.id()), customer.verification())))
            .<ResponseEntity<Object>>map(
                token ->
                    ResponseEntity.ok(
                        new SignInResponse(token.value(), BEARER, token.lifetime().toSeconds())))
            .defaultIfEmpty(denied);
  }
}
