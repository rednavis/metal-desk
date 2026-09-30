package com.rednavis.metaldesk.admin.quote;

import com.rednavis.metaldesk.admin.order.dto.OrderDetailView;
import com.rednavis.metaldesk.admin.order.dto.OrderSummaryView;
import com.rednavis.metaldesk.admin.order.dto.PageView;
import com.rednavis.metaldesk.admin.quote.dto.QuoteOutcome;
import com.rednavis.metaldesk.admin.security.StaffAuthenticationInterceptor;
import com.rednavis.metaldesk.admin.security.StaffPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The manager-quote queue (BRD FR-5.3): list, set terms, decline. */
@RestController
@RequestMapping("/api/admin/quotes")
@RequiredArgsConstructor
public class QuoteController {

  private final QuoteService service;

  /**
   * Lists the orders awaiting a quote, newest first.
   *
   * @param page the zero-based page, default 0
   * @param size the page size, 1 to 100, default 20
   * @return the page
   */
  @GetMapping
  public PageView<OrderSummaryView> awaiting(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return service.awaiting(page, size);
  }

  /**
   * Shows one order in full.
   *
   * @param orderId the order id
   * @return the order
   */
  @GetMapping("/{orderId}")
  public OrderDetailView detail(@PathVariable String orderId) {
    return service.detail(orderId);
  }

  /**
   * Sets the terms; the order returns to awaiting payment.
   *
   * @param orderId the order id
   * @param request the price, terms and validity
   * @param staff who is acting
   * @return the outcome
   */
  @PostMapping("/{orderId}/terms")
  public QuoteOutcome applyTerms(
      @PathVariable String orderId,
      @RequestBody ManagerQuoteRequest request,
      @RequestAttribute(StaffAuthenticationInterceptor.PRINCIPAL) StaffPrincipal staff) {
    return service.applyTerms(orderId, request, staff);
  }

  /**
   * Declines the quote; the order is cancelled.
   *
   * @param orderId the order id
   * @param request why the quote is declined; the back office always sends one, but the call still
   *     works without a body
   * @param staff who is acting
   * @return the outcome
   */
  @PostMapping("/{orderId}/decline")
  public QuoteOutcome decline(
      @PathVariable String orderId,
      @RequestBody(required = false) DeclineRequest request,
      @RequestAttribute(StaffAuthenticationInterceptor.PRINCIPAL) StaffPrincipal staff) {
    return service.decline(orderId, request == null ? null : request.reason(), staff);
  }
}
