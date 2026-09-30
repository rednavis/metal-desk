package com.rednavis.metaldesk.admin.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tells the back office's screens who they are acting as, so a staff member can see it. */
@RestController
@RequestMapping("/api/admin/me")
public class StaffController {

  /**
   * Shows the identity the proxy reported for this request.
   *
   * @param staff who is acting
   * @return the identity
   */
  @GetMapping
  public StaffView me(
      @RequestAttribute(StaffAuthenticationInterceptor.PRINCIPAL) StaffPrincipal staff) {
    return new StaffView(staff.email());
  }
}
