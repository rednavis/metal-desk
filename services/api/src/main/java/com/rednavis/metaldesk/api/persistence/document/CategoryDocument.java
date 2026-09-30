package com.rednavis.metaldesk.api.persistence.document;

import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A catalog category as stored.
 *
 * @param id the category id
 * @param name the category's name
 * @param parentId the parent category's id, or null for a root; indexed to list children
 * @param taxCategory the tax category products in it fall under
 */
@Document("categories")
public record CategoryDocument(
    @Id String id, String name, @Indexed(sparse = true) String parentId, TaxCategory taxCategory) {}
