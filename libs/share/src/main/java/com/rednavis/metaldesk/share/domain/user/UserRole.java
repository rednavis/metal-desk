package com.rednavis.metaldesk.share.domain.user;

/**
 * What a back-office user may do. A user has exactly one role; a new role is a new constant here.
 *
 * <p>The name is what is stored and what a token carries, so a constant is never renamed.
 */
public enum UserRole {

  /** Runs the back office: the full set of staff actions. */
  ADMIN,

  /** Works the order and quote queues day to day. */
  MANAGER
}
