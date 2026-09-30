package com.rednavis.metaldesk.api.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;

/**
 * A catalog category.
 *
 * @param id the category id
 * @param name the category's name
 * @param parentId the parent category's id; absent for a root category
 * @param taxCategory the tax classification of everything listed under it (BRD BR-4)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CategoryView(String id, String name, String parentId, TaxCategory taxCategory) {}
