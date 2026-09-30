package com.rednavis.metaldesk.api.account;

import com.rednavis.metaldesk.api.account.dto.ConfirmEmailRequest;
import com.rednavis.metaldesk.api.account.dto.EmailConfirmed;
import com.rednavis.metaldesk.api.account.dto.RegistrationAccepted;
import com.rednavis.metaldesk.api.account.dto.RegistrationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Registration and email confirmation (BRD FR-2.3, FR-2.6). Both are public. */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class RegistrationController {

  private final RegistrationService service;

  /**
   * Registers a customer and mails a verification code. Does not sign them in.
   *
   * @param request the profile and password
   * @return 202 with the reference to quote when confirming; the same shape for a known address
   */
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Mono<RegistrationAccepted> register(@RequestBody RegistrationRequest request) {
    return service.register(request);
  }

  /**
   * Confirms the emailed code and marks the customer verified.
   *
   * @param request the reference and code
   * @return 200 on success; 400 {@code verification.invalid} for every kind of failure
   */
  @PostMapping("/verify-email")
  public Mono<EmailConfirmed> verifyEmail(
      @RequestBody(required = false) ConfirmEmailRequest request) {
    return service.confirmEmail(request).thenReturn(new EmailConfirmed(true));
  }
}
