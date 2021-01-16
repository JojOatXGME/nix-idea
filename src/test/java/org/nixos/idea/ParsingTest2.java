package org.nixos.idea;

import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.nixos.idea.testutil.IdeaExtension;
import org.nixos.idea.testutil.TestCommons;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.params.ParameterizedTest.INDEX_PLACEHOLDER;

@ExtendWith(IdeaExtension.class)
@TestDataPath("$PROJECT_ROOT/src/test/testData/ParsingTest2")
final class ParsingTest2 {
  @ParameterizedTest(name = "[" + INDEX_PLACEHOLDER + "] {0}")
  @MethodSource("availableFilePairs")
  void test(@TestDataFile String input, @TestDataFile String expectedResult) {
    //
  }

  static Stream<Arguments> availableFilePairs() throws IOException {
    Path directoryPath = TestCommons.getTestDataPath(ParsingTest2.class);
    return findBaseFileNames(directoryPath, Arrays.asList(".nix", ".txt"))
        .map(baseName -> Arguments.of(
            baseName + ".nix",
            baseName + ".txt"));
  }

  private static Stream<String> findBaseFileNames(Path directoryPath, List<String> extensions) throws IOException {
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
}
