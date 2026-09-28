package com.rednavis.metaldesk.payments.provider;

import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Set;

/**
 * What an adapter can do: which provider it is, and which {@link PaymentMethod}s it serves.
 *
 * <p>Checkout routes a chosen method to the adapter that {@linkplain #supports supports} it, so a
 * new adapter is picked up by declaring its methods, not by editing the checkout. The provider id
 * is the one recorded on the payment afterwards.
 *
 * @param providerId the provider's identifier, trimmed and never blank
 * @param methods the methods the adapter serves, never empty; copied, so the set is unmodifiable
 */
public record ProviderCapability(String providerId, Set<PaymentMethod> methods) {

  /**
   * Validates the fields and copies the method set.
   *
   * @throws ValidationException if the provider id is null or blank, or the methods are null, empty
   *     or hold a null
   */
  public ProviderCapability {
    if (providerId == null || providerId.isBlank()) {
      throw new ValidationException(
          "provider-capability.id-blank", "Provider id must not be null or blank");
    }
    providerId = providerId.strip();
    if (methods == null || methods.isEmpty()) {
      throw new ValidationException(
          "provider-capability.methods-empty", "A provider must serve at least one method");
    }
    methods = Set.copyOf(methods);
  }

  /**
   * Tells whether the adapter serves a method.
   *
   * @param method the method
   * @return {@code true} if the adapter serves it
   */
  public boolean supports(PaymentMethod method) {
    return methods.contains(method);
  }
}
