package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.payments.provider.PaymentProvider;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Finds the payment provider that serves a method, by <strong>capability</strong>: every {@link
 * PaymentProvider} bean is asked which methods it {@code supports}. There is no {@code switch} on
 * the method and no reference to any adapter class, which is what keeps a vendor out of checkout
 * (BRD FR-6.1).
 *
 * <p>Two providers claiming one method is a <em>startup failure</em>, not something decided by bean
 * order at run time.
 */
@Component
public final class ProviderRegistry {

  private final Map<PaymentMethod, PaymentProvider> byMethod = new ConcurrentHashMap<>();

  /**
   * Builds the registry from every provider bean.
   *
   * @param providers the providers
   * @throws IllegalStateException if two of them support the same method
   */
  public ProviderRegistry(List<PaymentProvider> providers) {
    for (final PaymentProvider provider : providers) {
      for (final PaymentMethod method : PaymentMethod.values()) {
        if (provider.supports(method)) {
          final PaymentProvider previous = byMethod.put(method, provider);
          if (previous != null) {
            throw new IllegalStateException(
                "Payment method "
                    + method
                    + " is supported by both "
                    + previous.capability().providerId()
                    + " and "
                    + provider.capability().providerId());
          }
        }
      }
    }
  }

  /**
   * Finds the provider for a method.
   *
   * @param method the method
   * @return its provider, or empty if none serves it
   */
  public Optional<PaymentProvider> forMethod(PaymentMethod method) {
    return Optional.ofNullable(byMethod.get(method));
  }

  /**
   * Lists the methods some provider serves.
   *
   * @return the methods
   */
  public Set<PaymentMethod> supportedMethods() {
    return Set.copyOf(byMethod.keySet());
  }
}
