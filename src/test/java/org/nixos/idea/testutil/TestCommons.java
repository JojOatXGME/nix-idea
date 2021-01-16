package org.nixos.idea.testutil;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class TestCommons {
  private static final Path TEST_DATA_BASE_PATH = Paths.get("src", "test", "testData");

  private TestCommons() {} // This class cannot be instantiated.

  public static @NotNull Path getTestDataPath(@NotNull Class<?> testClass) {
    String binaryClassName = testClass.getName();
    int lastDot = binaryClassName.lastIndexOf('.');
    String fileName = lastDot == -1 ? binaryClassName : binaryClassName.substring(lastDot + 1);
    return TEST_DATA_BASE_PATH.resolve(fileName);
  }
}
