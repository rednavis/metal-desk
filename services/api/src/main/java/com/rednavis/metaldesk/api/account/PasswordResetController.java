package com.rednavis.metaldesk.api.account;

import com.rednavis.metaldesk.api.account.dto.PasswordChanged;
import com.rednavis.metaldesk.api.account.dto.PasswordResetConfirmation;
import com.rednavis.metaldesk.api.account.dto.PasswordResetRequest;
import com.rednavis.metaldesk.api.account.dto.ResetRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Password reset (BRD FR-2.4). Both endpoints are public: the caller has no token yet. */
@RestController
@RequestMapping("/api/account/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

  private static final String REQUESTED =
      "If that address belongs to an account, we have sent it a reset link";

  private final PasswordResetService service;

  /**
   * Asks for a reset link. The response is the same for every address that is a valid email.
   *
   * @param request the email address and mail language
   * @return 202 with a fixed message
   */
  @PostMapping("/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Mono<ResetRequested> request(@RequestBody PasswordResetRequest request) {
    return service.request(request).thenReturn(new ResetRequested(REQUESTED));
  }

  /**
   * Completes a reset from the emailed link.
   *
   * @param confirmation the link's reference and code, and the new password
   * @return 200 on success; 400 {@code verification.invalid} for every kind of link failure
   */
  @PostMapping("/confirm")
  public Mono<PasswordChanged> confirm(
      @RequestBody(required = false) PasswordResetConfirmation confirmation) {
    return service.reset(confirmation).thenReturn(new PasswordChanged(true));
  }
}
