package org.nixos.idea.shell;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class NixProgramRunner implements ProgramRunner<RunnerSettings> {
    @Override
    public @NotNull @NonNls String getRunnerId() {
        return "nix-shell-wrapper";
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return true;
    }

    @Override
    public void execute(@NotNull ExecutionEnvironment environment) throws ExecutionException {
        ProgramRunner<?> runner = ProgramRunner.PROGRAM_RUNNER_EP.findFirstSafe(r -> matches(environment, r));
        runner.execute(environment);
    }

    private boolean matches(@NotNull ExecutionEnvironment environment, @NotNull ProgramRunner<?> runner) {
        String executorId = environment.getExecutor().getId();
        return runner != this && runner.canRun(executorId, environment.getRunProfile());
    }
}
