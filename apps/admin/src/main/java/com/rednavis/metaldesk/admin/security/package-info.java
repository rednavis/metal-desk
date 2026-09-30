/**
 * Who is calling the staff API. There is no application login: staff are authenticated by the
 * Identity-Aware Proxy in front of the service (Architecture section 7), and this package only
 * reads the identity it passes on. A customer's token from {@code services/api} is never accepted.
 */
package com.rednavis.metaldesk.admin.security;
