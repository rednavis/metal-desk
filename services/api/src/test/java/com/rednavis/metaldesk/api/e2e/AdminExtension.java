package com.rednavis.metaldesk.api.e2e;

import com.rednavis.metaldesk.persistence.testing.SharedMongo;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Starts the real {@code apps/admin} once for the whole test run, on first need, and stops it when
 * the run ends: the application is held in the root context's store as a closeable resource, so the
 * engine closes it, and no test has to.
 */
public class AdminExtension implements BeforeAllCallback {

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(AdminExtension.class);
  private static final AtomicReference<AdminHarnessTestSupport> RUNNING = new AtomicReference<>();

  /** Creates the extension. */
  public AdminExtension() {
    // Registered by annotation; nothing to set up.
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    context.getRoot().getStore(NAMESPACE).computeIfAbsent(Holder.class, key -> new Holder(start()));
  }

  /**
   * The running application.
   *
   * @return admin, started by {@link #beforeAll}
   */
  public static AdminHarnessTestSupport admin() {
    final AdminHarnessTestSupport current = RUNNING.get();
    if (current == null) {
      throw new IllegalStateException("admin is not running: the test must use AdminExtension");
    }
    return current;
  }

  private static AdminHarnessTestSupport start() {
    try {
      final AdminHarnessTestSupport started =
          AdminHarnessTestSupport.start(SharedMongo.connectionString());
      RUNNING.set(started);
      return started;
    } catch (ReflectiveOperationException | IOException e) {
      throw new IllegalStateException("could not start apps/admin", e);
    }
  }

  /** Stops admin when the root context is closed. */
  private static final class Holder implements AutoCloseable {

    private final AdminHarnessTestSupport admin;

    private Holder(AdminHarnessTestSupport admin) {
      this.admin = admin;
    }

    @Override
    public void close() throws IOException {
      RUNNING.compareAndSet(admin, null);
      admin.close();
    }
  }
}
