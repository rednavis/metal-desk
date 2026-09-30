package com.rednavis.metaldesk.api.checkout;

import com.rednavis.metaldesk.api.cart.CartId;
import com.rednavis.metaldesk.api.checkout.delivery.DeliveryMapping;
import com.rednavis.metaldesk.api.checkout.payment.PaymentMapping;
import com.rednavis.metaldesk.api.checkout.step1.ConsentRecord;
import com.rednavis.metaldesk.api.checkout.step1.ConversionState;
import com.rednavis.metaldesk.api.checkout.step1.CustomerDetails;
import com.rednavis.metaldesk.persistence.document.CheckoutSessionDocument;
import com.rednavis.metaldesk.persistence.document.ConsentDocument;
import com.rednavis.metaldesk.persistence.document.ConversionDocument;
import com.rednavis.metaldesk.persistence.document.CustomerDetailsDocument;
import com.rednavis.metaldesk.persistence.document.DeliveryDocument;
import com.rednavis.metaldesk.persistence.document.Step1Document;
import com.rednavis.metaldesk.persistence.mapper.OrderMapper;
import com.rednavis.metaldesk.persistence.mapper.ValueMapper;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.customer.PhoneNumber;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Maps a {@link CheckoutSession} to its stored form and back. */
@Component
public class CheckoutSessionMapper {

  /**
   * Rebuilds a session.
   *
   * @param document the stored session
   * @return the session
   */
  public CheckoutSession toDomain(CheckoutSessionDocument document) {
    final Step1Document step1 =
        document.step1() == null ? new Step1Document(null, null, null) : document.step1();
    return new CheckoutSession(
        document.id(),
        Optional.ofNullable(document.ownerId()).map(CustomerId::new),
        CheckoutSession.Source.valueOf(document.source()),
        Optional.ofNullable(document.cartId()).map(CartId::new),
        document.lines().stream().map(OrderMapper::toDomain).toList(),
        Optional.ofNullable(step1.details()).map(this::detailsOf),
        Optional.ofNullable(step1.consent()).map(CheckoutSessionMapper::consentOf),
        Optional.ofNullable(step1.conversion()).map(CheckoutSessionMapper::conversionOf),
        Optional.ofNullable(document.delivery()).map(DeliveryMapping::deliveryOf),
        Optional.ofNullable(document.delivery())
            .map(DeliveryDocument::handoff)
            .map(DeliveryMapping::handoffOf),
        Optional.ofNullable(document.payment()).map(PaymentMapping::stateOf),
        new Lifecycle(
            document.version(), document.createdAt(), document.updatedAt(), document.expiresAt()));
  }

  /**
   * Builds the stored form of a session.
   *
   * @param session the session
   * @return the document
   */
  public CheckoutSessionDocument toDocument(CheckoutSession session) {
    return new CheckoutSessionDocument(
        session.id(),
        session.owner().map(CustomerId::value).orElse(null),
        session.source().name(),
        session.cart().map(CartId::value).orElse(null),
        session.lines().stream().map(OrderMapper::toDocument).toList(),
        new Step1Document(
            session.details().map(CheckoutSessionMapper::detailsDocument).orElse(null),
            session.consent().map(CheckoutSessionMapper::consentDocument).orElse(null),
            session.conversion().map(CheckoutSessionMapper::conversionDocument).orElse(null)),
        session
            .delivery()
            .map(state -> DeliveryMapping.deliveryDocument(state, session.handoff()))
            .orElse(null),
        session.payment().map(PaymentMapping::stateDocument).orElse(null),
        session.lifecycle().version(),
        session.lifecycle().createdAt(),
        session.lifecycle().updatedAt(),
        session.lifecycle().expiresAt());
  }

  private CustomerDetails detailsOf(CustomerDetailsDocument document) {
    return new CustomerDetails(
        document.name(),
        new EmailAddress(document.email()),
        new PhoneNumber(document.phone()),
        ValueMapper.addressToDomain(document.address()),
        Optional.ofNullable(document.note()));
  }

  private static CustomerDetailsDocument detailsDocument(CustomerDetails details) {
    return new CustomerDetailsDocument(
        details.name(),
        details.email().value(),
        details.phone().value(),
        ValueMapper.addressToDocument(details.deliveryAddress()),
        details.note().orElse(null));
  }

  private static ConsentRecord consentOf(ConsentDocument document) {
    return new ConsentRecord(
        document.privacyPolicyAccepted(), document.policyVersion(), document.acceptedAt());
  }

  private static ConsentDocument consentDocument(ConsentRecord consent) {
    return new ConsentDocument(
        consent.privacyPolicyAccepted(), consent.policyVersion(), consent.acceptedAt());
  }

  private static ConversionState conversionOf(ConversionDocument document) {
    return new ConversionState(
        document.email(),
        document.reference(),
        Optional.ofNullable(document.customerId()).map(CustomerId::new),
        document.verified());
  }

  private static ConversionDocument conversionDocument(ConversionState state) {
    return new ConversionDocument(
        state.email(),
        state.reference(),
        state.customer().map(CustomerId::value).orElse(null),
        state.verified());
  }
}
