package com.rednavis.metaldesk.admin.tier;

import com.rednavis.metaldesk.admin.tier.dto.TierRemoval;
import com.rednavis.metaldesk.admin.tier.dto.TierRequest;
import com.rednavis.metaldesk.admin.tier.dto.TierResult;
import com.rednavis.metaldesk.admin.tier.dto.TierView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fulfillment-tier configuration for staff (BRD FR-5.1).
 *
 * <p>Several tiers per region are allowed; checkout picks the cheapest one that covers the order,
 * then the faster, then the lower id. Saving a tier that can never be chosen, and removing or
 * narrowing the last tier of a region (after which every order there goes to manager handoff), are
 * both applied and reported in the {@code warnings} of the response body.
 */
@RestController
@RequestMapping("/api/admin/tiers")
@RequiredArgsConstructor
public class TierController {

  private final TierService service;

  /**
   * Lists the tiers.
   *
   * @param region a region code to list one region, or absent for all
   * @return the tiers
   */
  @GetMapping
  public List<TierView> list(@RequestParam(required = false) String region) {
    return service.list(region);
  }

  /**
   * Reads one tier.
   *
   * @param id the tier id
   * @return the tier
   */
  @GetMapping("/{id}")
  public TierView get(@PathVariable String id) {
    return service.get(id);
  }

  /**
   * Creates a tier.
   *
   * @param request the tier
   * @return the tier and any warnings
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TierResult create(@RequestBody TierRequest request) {
    return service.create(request);
  }

  /**
   * Replaces a tier.
   *
   * @param id the tier id
   * @param request the new description
   * @return the tier and any warnings
   */
  @PutMapping("/{id}")
  public TierResult update(@PathVariable String id, @RequestBody TierRequest request) {
    return service.update(id, request);
  }

  /**
   * Removes a tier. The body, not just the status, says whether the region lost its last tier.
   *
   * @param id the tier id
   * @return the removed id and any warnings
   */
  @DeleteMapping("/{id}")
  public TierRemoval delete(@PathVariable String id) {
    return service.delete(id);
  }
}
