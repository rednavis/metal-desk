package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.share.domain.order.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Binds the payment step's settings and supplies the default, do-nothing {@link OrderSettlement}.
 */
@Configuration
@EnableConfigurationProperties(PaymentMethodPolicy.class)
public class PaymentConfiguration {

  /**
   * The default hand-off after payment, which does nothing until confirmation (T-038) replaces it.
   *
   * @return the settlement
   */
  @Bean
  @ConditionalOnMissingBean
  public OrderSettlement orderSettlement() {
    return new OrderSettlement() {
      @Override
      public Mono<Void> paid(Order order) {
        return Mono.empty();
      }

      @Override
      public Mono<Void> invoiceIssued(Order order) {
        return Mono.empty();
      }
    };
  }
}
