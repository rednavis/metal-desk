package com.rednavis.metaldesk.api.persistence.document;

import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.catalog.StockStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A catalog product as stored.
 *
 * <p>The product holds its category's <em>id</em>, not a copy of the category: a copy would drift
 * when staff rename or reclassify the category. {@code ProductMapper} is therefore given the
 * category when it rebuilds the domain product. {@code categoryId} is indexed for browsing, and
 * {@code name} carries the collection's text index for search.
 *
 * @param id the product id
 * @param name the product's name, text-indexed
 * @param categoryId the id of its category, indexed
 * @param metal the metal it is made of
 * @param purity the purity in parts per thousand, as decimal text
 * @param weight the weight
 * @param dimensions the free-text dimensions, or null
 * @param stock its stock status
 * @param price its fixed price, or null when it is priced on request
 */
@Document("products")
public record ProductDocument(
    @Id String id,
    @TextIndexed String name,
    @Indexed String categoryId,
    Metal metal,
    String purity,
    WeightDocument weight,
    String dimensions,
    StockStatus stock,
    MoneyDocument price) {}
