package com.rednavis.metaldesk.api.payments;

import com.rednavis.metaldesk.api.persistence.mapper.OrderMapper;
import com.rednavis.metaldesk.api.persistence.repository.OrderRepository;
import com.rednavis.metaldesk.payments.gateway.GatewayConfiguration;
import com.rednavis.metaldesk.payments.gateway.GatewayProvider;
import com.rednavis.metaldesk.payments.invoice.InvoiceOrders;
import com.rednavis.metaldesk.payments.invoice.InvoiceProvider;
import com.rednavis.metaldesk.payments.invoice.InvoiceRenderer;
import com.rednavis.metaldesk.payments.invoice.InvoiceSink;
import com.rednavis.metaldesk.payments.invoice.MinimalPdfInvoiceRenderer;
import com.rednavis.metaldesk.payments.provider.PaymentProvider;
import com.rednavis.metaldesk.payments.wallet.WalletConfiguration;
import com.rednavis.metaldesk.payments.wallet.WalletProvider;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Registers the three payment adapters as {@link PaymentProvider} beans. Checkout depends on the
 * interface only; see the package description.
 *
 * <p>The invoice adapter makes no outbound call: it reads the order, renders the documents and
 * hands them to an {@link InvoiceSink}. The default sink archives them; confirmation (T-038) reads
 * the archive to attach them to the mails, and a bean of that type replaces this default.
 */
@Configuration
@EnableConfigurationProperties(PaymentsProperties.class)
public class PaymentProvidersConfiguration {

  /**
   * The card and bank gateway.
   *
   * @param properties where it is
   * @return the provider
   */
  @Bean
  public PaymentProvider gatewayProvider(PaymentsProperties properties) {
    final PaymentsProperties.Endpoint endpoint = properties.gateway();
    return new GatewayProvider(
        new GatewayConfiguration(
            URI.create(endpoint.baseUrl()), endpoint.timeout(), endpoint.retryBudget()));
  }

  /**
   * The wallet provider.
   *
   * @param properties where it is
   * @return the provider
   */
  @Bean
  public PaymentProvider walletProvider(PaymentsProperties properties) {
    final PaymentsProperties.Endpoint endpoint = properties.wallet();
    return new WalletProvider(
        new WalletConfiguration(
            URI.create(endpoint.baseUrl()), endpoint.timeout(), endpoint.retryBudget()));
  }

  /**
   * The invoice provider.
   *
   * @param orders finds the order to invoice
   * @param renderer renders the documents
   * @param sink receives them
   * @return the provider
   */
  @Bean
  public PaymentProvider invoiceProvider(
      InvoiceOrders orders, InvoiceRenderer renderer, InvoiceSink sink) {
    return new InvoiceProvider(orders, renderer, sink);
  }

  /**
   * Finds orders for the invoice provider.
   *
   * @param repository the order store
   * @param mapper maps stored orders
   * @return the lookup
   */
  @Bean
  public InvoiceOrders invoiceOrders(OrderRepository repository, OrderMapper mapper) {
    return id -> repository.findById(id.value()).map(mapper::toDomain).switchIfEmpty(Mono.empty());
  }

  /**
   * The invoice renderer.
   *
   * @return the built-in PDF renderer
   */
  @Bean
  @ConditionalOnMissingBean
  public InvoiceRenderer invoiceRenderer() {
    return new MinimalPdfInvoiceRenderer();
  }

  /**
   * The default invoice sink, which archives the documents for confirmation to attach.
   *
   * @param archive where the documents are kept
   * @return the sink
   */
  @Bean
  @ConditionalOnMissingBean
  public InvoiceSink invoiceSink(InvoiceArchive archive) {
    return new ArchivingInvoiceSink(archive);
  }
}
