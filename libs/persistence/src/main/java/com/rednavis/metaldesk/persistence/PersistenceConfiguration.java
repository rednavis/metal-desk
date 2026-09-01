package com.rednavis.metaldesk.persistence;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the mappers as beans and the documents as mapped entities. An application imports this
 * class because scanning starts at the application's own package and would never reach this
 * library.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.rednavis.metaldesk.persistence.mapper")
@EntityScan(basePackages = "com.rednavis.metaldesk.persistence.document")
public class PersistenceConfiguration {

  /** Creates the configuration. */
  public PersistenceConfiguration() {
    // Nothing to set up: the annotations do the work.
  }
}
