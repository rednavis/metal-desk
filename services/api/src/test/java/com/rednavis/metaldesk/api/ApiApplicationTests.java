package com.rednavis.metaldesk.api;

import com.rednavis.metaldesk.api.persistence.MongoTestSupport;
import org.junit.jupiter.api.Test;

/** The application context starts against a real MongoDB, creating the declared indexes. */
class ApiApplicationTests extends MongoTestSupport {

  @Test
  void contextLoads() {}
}
