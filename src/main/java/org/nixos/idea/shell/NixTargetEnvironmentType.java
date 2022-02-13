package org.nixos.idea.shell;

import com.intellij.execution.target.LanguageRuntimeType;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentFactory;
import com.intellij.execution.target.TargetEnvironmentType;
import com.intellij.execution.target.local.LocalTargetEnvironmentFactory;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nixos.idea.icon.NixIcons;

import javax.swing.Icon;
import javax.swing.JComponent;

public final class NixTargetEnvironmentType extends TargetEnvironmentType<NixTargetEnvironmentType.Config> {
    private static final String TYPE_ID = "nix-shell";

    public NixTargetEnvironmentType() {
        super(TYPE_ID);
    }

    @Override
    public @NotNull @Nls String getDisplayName() {
        return "nix-shell";
    }

    @Override
    public @NotNull Icon getIcon() {
        return NixIcons.FILE;
    }

    @Override
    public @NotNull Config createDefaultConfig() {
        return new Config();
    }

    @Override
    public @NotNull PersistentStateComponent<?> createSerializer(@NotNull Config config) {
        return config;
    }

    @Override
    public @NotNull Config duplicateConfig(@NotNull Config config) {
        return new Config();
    }

    @Override
    public @NotNull Configurable createConfigurable(@NotNull Project project, @NotNull Config config, @Nullable LanguageRuntimeType<?> languageRuntimeType, @Nullable Configurable configurable) {
        return new NixTargetConfigurable();
    }

    @Override
    public @NotNull TargetEnvironmentFactory createEnvironmentFactory(@NotNull Project project, @NotNull Config config) {
        return new LocalTargetEnvironmentFactory();
    }

    static final class Config extends TargetEnvironmentConfiguration implements PersistentStateComponent<Void> {
        public Config() {
            super(TYPE_ID);
        }

        @Override
        public @NotNull String getProjectRootOnTarget() {
            return "";
        }

        @Override
        public void setProjectRootOnTarget(@NotNull String s) {
            Thread.yield();
        }

        @Override
        public @Nullable Void getState() {
            return null;
        }

        @Override
        public void loadState(@NotNull Void state) {
            throw new UnsupportedOperationException("");
        }
    }

    private static final class NixTargetConfigurable implements Configurable {
        @Override
        public @NlsContexts.ConfigurableName String getDisplayName() {
            return "nix-shell";
        }

        @Override
        public @Nullable JComponent createComponent() {
            return null;
        }

        @Override
        public boolean isModified() {
            return false;
        }

        @Override
        public void apply() throws ConfigurationException {
        }
    }
}
