package com.rednavis.metaldesk.api.e2e;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * The real {@code apps/admin} application, started inside the test JVM in a class loader of its own
 * and called over HTTP.
 *
 * <p>The two services cannot share a classpath (admin is MVC on the blocking MongoDB driver, this
 * service is WebFlux on the reactive one), so admin's runtime classpath, resolved by Gradle and
 * passed in the {@code metaldesk.admin.classpath} system property, is loaded by a class loader
 * whose parent is only the platform loader. Nothing of this module's classes is visible to admin
 * and nothing of admin's to this module. It is pointed at the same MongoDB container, on a random
 * port, and with no development identity: every call carries the proxy's identity header.
 */
public final class AdminHarnessTestSupport implements AutoCloseable {

  private static final String STAFF = "accounts.google.com:e2e-staff@example.com";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final URLClassLoader loader;
  private final Closeable context;
  private final int port;
  private final HttpClient http = HttpClient.newHttpClient();

  private AdminHarnessTestSupport(URLClassLoader loader, Closeable context, int port) {
    this.loader = loader;
    this.context = context;
    this.port = port;
  }

  /**
   * Starts admin against a MongoDB.
   *
   * @param mongoUri the connection string admin should use
   * @return the running application
   * @throws ReflectiveOperationException if admin cannot be started
   * @throws IOException if its classpath cannot be read
   */
  public static AdminHarnessTestSupport start(String mongoUri)
      throws ReflectiveOperationException, IOException {
    final String classpath = System.getProperty("metaldesk.admin.classpath");
    if (classpath == null || classpath.isBlank()) {
      throw new IllegalStateException("metaldesk.admin.classpath is not set by the build");
    }
    final URL[] urls =
        Arrays.stream(classpath.split(java.io.File.pathSeparator))
            .map(entry -> toUrl(Path.of(entry)))
            .toArray(URL[]::new);
    final URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
    final Thread thread = Thread.currentThread();
    final ClassLoader previous = thread.getContextClassLoader();
    thread.setContextClassLoader(loader);
    try {
      final Class<?> application =
          loader.loadClass("com.rednavis.metaldesk.admin.AdminApplication");
      final Method run =
          loader
              .loadClass("org.springframework.boot.SpringApplication")
              .getMethod("run", Class.class, String[].class);
      final String[] arguments = {
        "--server.port=0", "--spring.mongodb.uri=" + mongoUri, "--spring.main.banner-mode=off"
      };
      final Object context = unwrap(() -> run.invoke(null, application, arguments));
      final Object environment =
          loader
              .loadClass("org.springframework.context.ConfigurableApplicationContext")
              .getMethod("getEnvironment")
              .invoke(context);
      final String port =
          (String)
              loader
                  .loadClass("org.springframework.core.env.PropertyResolver")
                  .getMethod("getProperty", String.class)
                  .invoke(environment, "local.server.port");
      return new AdminHarnessTestSupport(loader, (Closeable) context, Integer.parseInt(port));
    } finally {
      thread.setContextClassLoader(previous);
    }
  }

  @FunctionalInterface
  private interface Invocation {
    Object call() throws ReflectiveOperationException;
  }

  private static Object unwrap(Invocation invocation) throws ReflectiveOperationException {
    try {
      return invocation.call();
    } catch (InvocationTargetException failure) {
      throw new IllegalStateException("admin failed to start", failure.getCause());
    }
  }

  private static URL toUrl(Path path) {
    try {
      return path.toUri().toURL();
    } catch (java.net.MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Calls admin as the proxy's staff member.
   *
   * @param method the HTTP method
   * @param path the path
   * @param json the body, or null
   * @return the status and the parsed JSON body (empty if there is none)
   * @throws IOException if the call fails
   * @throws InterruptedException if interrupted
   */
  public Answer call(String method, String path, String json)
      throws IOException, InterruptedException {
    final HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("X-Goog-Authenticated-User-Email", STAFF)
            .header("Content-Type", "application/json")
            .method(
                method,
                json == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json));
    final HttpResponse<String> response =
        http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    final String body = response.body();
    @SuppressWarnings("unchecked")
    final Map<String, Object> parsed =
        body == null || body.isBlank() ? Map.of() : JSON.readValue(body, Map.class);
    return new Answer(response.statusCode(), parsed);
  }

  /**
   * What admin answered.
   *
   * @param status the HTTP status
   * @param body the parsed JSON object
   */
  public record Answer(int status, Map<String, Object> body) {

    /** Copies the body, so the record cannot be changed through it. */
    public Answer {
      body = Map.copyOf(body);
    }
  }

  @Override
  public void close() throws IOException {
    try (loader) {
      context.close();
    }
  }
}
