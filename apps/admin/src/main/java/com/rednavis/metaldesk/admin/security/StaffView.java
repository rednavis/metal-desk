package com.rednavis.metaldesk.admin.security;

/**
 * Who the back office is acting as.
 *
 * @param email the identity the proxy reported
 */
public record StaffView(String email) {}
