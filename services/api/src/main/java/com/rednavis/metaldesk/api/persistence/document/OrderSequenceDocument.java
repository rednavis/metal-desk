package com.rednavis.metaldesk.api.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The counter of orders placed on one day, from which {@code OrderNumberSequence} allocates.
 *
 * @param id the day, as an ISO date, for example {@code "2022-02-08"}
 * @param value the last sequence number allocated that day
 */
@Document("order_sequences")
public record OrderSequenceDocument(@Id String id, long value) {}
