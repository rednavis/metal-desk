package com.rednavis.metaldesk.api.catalog.dto;

import java.util.List;

/**
 * One page of a listing.
 *
 * @param items the entries on this page
 * @param page the zero-based page number
 * @param size the page size that was asked for
 * @param total the number of entries across all pages
 * @param <T> the entry type
 */
public record PageView<T>(List<T> items, int page, int size, long total) {

  /** Copies the list, so a page cannot be changed through it. */
  public PageView {
    items = List.copyOf(items);
  }
}
