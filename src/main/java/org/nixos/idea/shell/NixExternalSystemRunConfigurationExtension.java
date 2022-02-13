package org.nixos.idea.shell;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemProcessHandler;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfigurationExtension;
import com.intellij.openapi.options.SettingsEditorGroup;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NixExternalSystemRunConfigurationExtension implements ExternalSystemRunConfigurationExtension {
    @Override
    public void readExternal(@NotNull ExternalSystemRunConfiguration configuration, @NotNull Element element) {}

    @Override
    public void writeExternal(@NotNull ExternalSystemRunConfiguration configuration, @NotNull Element element) {}

    @Override
    public void appendEditors(@NotNull ExternalSystemRunConfiguration configuration, @NotNull SettingsEditorGroup<ExternalSystemRunConfiguration> group) {}

    @Override
    public void attachToProcess(@NotNull ExternalSystemRunConfiguration configuration, @NotNull ExternalSystemProcessHandler processHandler, @Nullable RunnerSettings settings) {}

    @Override
    public void updateVMParameters(@NotNull ExternalSystemRunConfiguration configuration, @NotNull SimpleJavaParameters javaParameters, @Nullable RunnerSettings settings, @NotNull Executor executor) throws ExecutionException {
        javaParameters.addEnv("NIX_TEST_ENV", "42");
    }
}
