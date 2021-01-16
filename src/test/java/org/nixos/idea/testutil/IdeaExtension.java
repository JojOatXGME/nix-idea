package org.nixos.idea.testutil;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

public final class IdeaExtension implements BeforeEachCallback {
  private static final Namespace NAMESPACE = Namespace.create(IdeaExtension.class);

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    IdeaProjectTestFixture projectFixture = factory.createLightFixtureBuilder().getFixture();
    TempDirTestFixture tempDirFixture = new LightTempDirTestFixtureImpl(true);
    CodeInsightTestFixture ideaFixture = factory.createCodeInsightFixture(projectFixture, tempDirFixture);
    ideaFixture.setTestDataPath(TestCommons.getTestDataPath(context.getRequiredTestClass()).toString());
    ideaFixture.setUp();
    context.getStore(NAMESPACE).put(AutoCloseFixture.class, new AutoCloseFixture(ideaFixture));
  }

  private static final class AutoCloseFixture implements ExtensionContext.Store.CloseableResource {
    private final IdeaTestFixture fixture;

    public AutoCloseFixture(IdeaTestFixture fixture) {
      this.fixture = fixture;
    }

    @Override
    public void close() throws Throwable {
      fixture.tearDown();
    }
  }
}
