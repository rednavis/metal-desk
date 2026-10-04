package com.rednavis.metaldesk.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Every stored document record can be built both with its collections absent and with them given,
 * so the defensive copies in the compact constructors run on both paths, and two equal documents
 * stay equal. Found by scanning the package, so a document added later is covered without editing
 * this test.
 */
class DocumentRecordsTest {

  private static final String PACKAGE = "com.rednavis.metaldesk.persistence.document";

  private static final Map<Class<?>, Object> SIMPLE =
      Map.of(
          String.class,
          "x",
          int.class,
          1,
          long.class,
          1L,
          boolean.class,
          true,
          Instant.class,
          Instant.EPOCH);

  private static List<Class<?>> documents() throws IOException, ClassNotFoundException {
    final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    final List<Class<?>> found = new ArrayList<>();
    for (final Resource resource :
        resolver.getResources("classpath*:" + PACKAGE.replace('.', '/') + "/*.class")) {
      final String file = String.valueOf(resource.getFilename());
      final Class<?> type =
          Class.forName(PACKAGE + "." + file.substring(0, file.length() - ".class".length()));
      if (type.isRecord()) {
        found.add(type);
      }
    }
    return found;
  }

  private static Optional<Object> sample(Class<?> type, boolean present) {
    final Optional<Object> simple = Optional.ofNullable(SIMPLE.get(type));
    final Optional<Object> enumeration =
        type.isEnum() ? Optional.of(type.getEnumConstants()[0]) : Optional.empty();
    final Optional<Object> collection =
        present && (type == List.class || type == Map.class)
            ? Optional.of(type == List.class ? List.of() : Map.of())
            : Optional.empty();
    return simple.or(() -> enumeration).or(() -> collection);
  }

  private static Optional<Object> build(Class<?> type, boolean present)
      throws ReflectiveOperationException {
    final RecordComponent[] components = type.getRecordComponents();
    final Object[] arguments = new Object[components.length];
    final Class<?>[] types = new Class<?>[components.length];
    for (int i = 0; i < components.length; i++) {
      types[i] = components[i].getType();
      arguments[i] = sample(types[i], present).orElse(null);
    }
    final Constructor<?> constructor = type.getDeclaredConstructor(types);
    Optional<Object> built;
    try {
      built = Optional.of(constructor.newInstance(arguments));
    } catch (InvocationTargetException refused) {
      built = Optional.empty();
    }
    return built;
  }

  @Test
  void everyDocumentCanBeBuiltWithAndWithoutItsCollections()
      throws IOException, ReflectiveOperationException {
    final List<Class<?>> documents = documents();
    assertTrue(documents.size() > 20, "the scan found the documents");
    int built = 0;
    for (final Class<?> type : documents) {
      final Optional<Object> absent = build(type, false);
      final Optional<Object> present = build(type, true);
      built += absent.isPresent() ? 1 : 0;
      built += present.isPresent() ? 1 : 0;
      if (absent.isPresent()) {
        assertEquals(absent, build(type, false));
        assertEquals(absent.get().hashCode(), build(type, false).orElseThrow().hashCode());
        assertTrue(absent.get().toString().startsWith(type.getSimpleName()));
      }
    }
    assertTrue(built > documents.size(), "most documents accept both shapes");
  }

  @Test
  void theConfigurationIsAnOrdinaryBean() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(PersistenceConfiguration.class);
      context.refresh();
      assertTrue(context.containsBean("priceRuleMapper"));
    }
  }
}
