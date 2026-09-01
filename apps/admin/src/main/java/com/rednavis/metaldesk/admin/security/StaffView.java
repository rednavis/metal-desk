package com.rednavis.metaldesk.admin.security;

import com.rednavis.metaldesk.share.domain.user.UserRole;

/**
 * Who the back office is acting as.
 *
 * @param login the login name
 * @param email the email address
 * @param role the user's role
 */
public record StaffView(String login, String email, UserRole role) {}
