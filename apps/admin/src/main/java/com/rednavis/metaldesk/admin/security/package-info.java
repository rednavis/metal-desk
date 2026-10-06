/**
 * Who is calling the staff API: back-office users ({@code users} collection) sign in with a login
 * and password and receive a short-lived bearer token (ADR-0006). The token is signed with this
 * service's own key and names this service's own audience, so a customer's token from {@code
 * services/api} is never accepted here, nor a staff token there.
 */
package com.rednavis.metaldesk.admin.security;
