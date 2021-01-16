package org.nixos.idea.testutil;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.platform.commons.support.AnnotationSupport;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class FileBasedTestExtension implements TestTemplateInvocationContextProvider {
  private static final Namespace NAMESPACE = Namespace.create(FileBasedTestExtension.class);
  private static final String KEY_DIRECTORY_PATH = "directoryPath";

  @Override
  public boolean supportsTestTemplate(ExtensionContext context) {
    Set<String> values = context.getRequiredTestInstances().getAllInstances().stream()
        .map(Object::getClass)
        .map(clazz -> AnnotationSupport.findAnnotation(clazz, FileBasedTest.class))
        .flatMap(optional -> optional.map(Stream::of).orElseGet(Stream::empty))
        .map(FileBasedTest::value)
        .collect(Collectors.toSet());
    if (values.isEmpty()) {
      return false;
    }
    else if (values.size() == 1) {
      context.getStore(NAMESPACE).put(KEY_DIRECTORY_PATH, values.iterator().next());
      return true;
    }
    else {
      throw new IllegalStateException("Multiple conflicting declarations of @FileBasedTest: " + values);
    }
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
    String directoryPath = context.getStore(NAMESPACE).get(KEY_DIRECTORY_PATH, String.class);
    try {
      // TODO: 06.01.2021 Fix second argument.
      return findBaseFileNames(Paths.get(directoryPath), Collections.emptyList())
          .map(Context::new);
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private Stream<String> findBaseFileNames(Path directoryPath, List<String> extensions) throws IOException {
    Set<String> baseNames = new LinkedHashSet<>();
    try (Stream<Path> files = Files.list(directoryPath)) {
      files.forEach(path -> {
        String fileName = path.getFileName().toString();
        Optional<String> extension = extensions.stream().filter(fileName::endsWith).findAny();
        extension.ifPresent(s -> baseNames.add(fileName.substring(0, fileName.length() - s.length())));
      });
    }
    catch (UncheckedIOException e) {
      throw e.getCause();
    }
    return baseNames.stream();
  }

  private static final class Context implements TestTemplateInvocationContext {
    private final String baseFileName;

    private Context(String baseFileName) {
      this.baseFileName = baseFileName;
    }

    @Override
    public String getDisplayName(int invocationIndex) {
      return "[" + invocationIndex + "] " + baseFileName;
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
      return Collections.singletonList(new ContextExtension(baseFileName));
    }
  }

  private static final class ContextExtension implements BeforeEachCallback, ParameterResolver {
    private final String baseFileName;

    private ContextExtension(String baseFileName) {
      this.baseFileName = baseFileName;
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
      for (Object instance : context.getRequiredTestInstances().getAllInstances()) {
        for (Field field : AnnotationSupport.findAnnotatedFields(instance.getClass(), FileBasedTest.File.class)) {
          FileBasedTest.File annotation = AnnotationSupport.findAnnotation(field, FileBasedTest.File.class).get();
          Object newValue = getFile(context, annotation, field.getType());
          field.setAccessible(true);
          field.set(instance, newValue);
        }
      }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
      return parameterContext.isAnnotated(FileBasedTest.File.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
      FileBasedTest.File annotation = parameterContext.findAnnotation(FileBasedTest.File.class).get();
      return getFile(extensionContext, annotation, parameterContext.getParameter().getType());
    }

    private Object getFile(ExtensionContext context, FileBasedTest.File annotation, Class<?> type) {
      String directoryPath = context.getStore(NAMESPACE).get(KEY_DIRECTORY_PATH, String.class);
      Path file = Paths.get(directoryPath, baseFileName + annotation.value());
      if (!Files.isRegularFile(file)) {
        throw new IllegalStateException("Not a regular file: " + file);
      }
      else if (type.isAssignableFrom(Path.class)) {
        return file;
      }
      else if (type.isAssignableFrom(File.class)) {
        return file.toFile();
      }
      else {
        throw new IllegalStateException("Unsupported file type: " + type);
      }
    }
  }
}
