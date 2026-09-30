package com.rednavis.metaldesk.admin.security;

/**
 * The staff member making a request.
 *
 * @param email the identity the proxy reported, never blank
 */
public record StaffPrincipal(String email) {}
