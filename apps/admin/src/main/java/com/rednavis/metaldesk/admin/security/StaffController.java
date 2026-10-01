package com.rednavis.metaldesk.admin.security;

import com.rednavis.metaldesk.admin.persistence.UserRepository;
import com.rednavis.metaldesk.persistence.document.UserDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tells the back office's screens who they are acting as, so a staff member can see it. */
@RestController
@RequestMapping("/api/admin/me")
@RequiredArgsConstructor
public class StaffController {

  private final UserRepository users;

  /**
   * Shows the user the bearer token is for, read from the database so a user who has since been
   * removed or disabled is told they are no longer signed in.
   *
   * @param staff who is acting
   * @return the user
   * @throws UnauthorizedException if the user no longer exists or is disabled
   */
  @GetMapping
  public StaffView me(@AuthenticationPrincipal StaffPrincipal staff) {
    final UserDocument user =
        users
            .findById(staff.id())
            .filter(UserDocument::enabled)
            .orElseThrow(UnauthorizedException::new);
    return new StaffView(user.login(), user.email(), user.role());
  }
}
