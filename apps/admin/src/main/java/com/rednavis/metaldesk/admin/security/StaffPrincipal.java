package com.rednavis.metaldesk.admin.security;

import com.rednavis.metaldesk.share.domain.user.UserRole;
import java.io.Serializable;

/**
 * The back-office user making a request, as the bearer token states it.
 *
 * @param id the user id
 * @param login the user's login name, which is also who a recorded action is attributed to
 * @param role what the user may do
 */
public record StaffPrincipal(String id, String login, UserRole role) implements Serializable {}
