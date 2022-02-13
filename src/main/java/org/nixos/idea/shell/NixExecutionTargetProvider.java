package org.nixos.idea.shell;

import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.ExecutionTargetProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.List;

public final class NixExecutionTargetProvider extends ExecutionTargetProvider {
    private static final NixExecutionTarget TARGET = new NixExecutionTarget();

    @Override
    public List<ExecutionTarget> getTargets(@NotNull Project project, @NotNull RunConfiguration configuration) {
        return List.of(TARGET);
    }

    private static final class NixExecutionTarget extends ExecutionTarget {
        @Override
        public @NotNull @NonNls String getId() {
            return "nix-shell";
        }

        @Override
        public @NotNull @Nls String getDisplayName() {
            return "Nix Shell"; // TODO: 06.02.2022 Translate
        }

        @Override
        public @Nullable Icon getIcon() {
            return null;
        }

        @Override
        public boolean canRun(@NotNull RunConfiguration configuration) {
            return true;
        }
    }
}
