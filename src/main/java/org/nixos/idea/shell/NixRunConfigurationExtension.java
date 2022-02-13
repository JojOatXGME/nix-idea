package org.nixos.idea.shell;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import org.jetbrains.annotations.NotNull;

public final class NixRunConfigurationExtension extends RunConfigurationExtension {
    @Override
    public boolean isApplicableFor(@NotNull RunConfigurationBase<?> configuration) {
        return true;
    }

    @Override
    protected void patchCommandLine(@NotNull RunConfigurationBase configuration, RunnerSettings runnerSettings, @NotNull GeneralCommandLine cmdLine, @NotNull String runnerId) throws ExecutionException {
        cmdLine.withEnvironment("NIX_TEST_ENV", "42");
    }

    @Override
    public <T extends RunConfigurationBase<?>> void updateJavaParameters(@NotNull T configuration, @NotNull JavaParameters params, RunnerSettings runnerSettings) throws ExecutionException {
        params.addEnv("NIX_TEST_ENV", "43");
    }
}
