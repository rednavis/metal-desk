package com.rednavis.metaldesk.admin.order.dto;

import java.util.List;

/**
 * One page of a list.
 *
 * @param items the items of this page
 * @param page the zero-based page number
 * @param size the page size asked for
 * @param total how many items there are in all
 * @param <T> the item type
 */
public record PageView<T>(List<T> items, int page, int size, long total) {

  /** Copies the list, so the view cannot be changed through it. */
  public PageView {
    items = List.copyOf(items);
  }
}
