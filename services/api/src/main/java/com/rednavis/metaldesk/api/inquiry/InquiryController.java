package com.rednavis.metaldesk.api.inquiry;

import com.rednavis.metaldesk.api.inquiry.dto.InquiryReceipt;
import com.rednavis.metaldesk.api.inquiry.dto.InquiryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Inquiries and messages to staff (BRD FR-9.1, FR-9.2). Open to visitors: no token is needed. */
@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class InquiryController {

  private final InquiryService inquiries;

  /**
   * Sends an inquiry or a message.
   *
   * @param request what the sender wrote; may be absent, which fails validation
   * @return the reference to quote
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<InquiryReceipt> submit(@RequestBody(required = false) InquiryRequest request) {
    return inquiries.submit(request);
  }
}
