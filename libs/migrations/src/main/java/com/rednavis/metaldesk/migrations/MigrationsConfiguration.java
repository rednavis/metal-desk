package com.rednavis.metaldesk.migrations;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs the migrations when an application starts. An application imports this class, as it imports
 * the persistence configuration.
 *
 * <p>The run happens once every singleton exists and before the web server starts taking requests,
 * so nothing is served against a database that is not migrated yet. If a migration fails, the
 * application does not start. It can be switched off with {@code
 * metaldesk.migrations.enabled=false} for a context that must not touch a database.
 */
@Configuration(proxyBeanMethods = false)
public class MigrationsConfiguration {

  /** Creates the configuration. */
  public MigrationsConfiguration() {
    // Nothing to set up: the bean method does the work.
  }

  /**
   * The step that applies the migrations.
   *
   * @param uri the application's MongoDB connection string
   * @param enabled whether to run at all
   * @return the startup step
   */
  @Bean
  public SmartInitializingSingleton mongoMigrationsRunner(
      @Value("${spring.mongodb.uri}") String uri,
      @Value("${metaldesk.migrations.enabled:true}") boolean enabled) {
    return () -> {
      if (enabled) {
        MongoMigrations.run(uri);
      }
    };
  }
}
