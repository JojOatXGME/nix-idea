package org.nixos.idea.format;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nixos.idea.lang.NixLanguage;
import org.nixos.idea.settings.NixFlakeSettings;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

/**
 * External formatter backed by {@code nix fmt}.
 */
public final class NixFlakeFormatter extends AsyncDocumentFormattingService {

    @Override
    protected @NotNull String getNotificationGroupId() {
        return NixLanguage.NOTIFICATION_GROUP_ID;
    }

    @Override
    protected @NotNull @NlsSafe String getName() {
        return "nix fmt";
    }

    @Override
    public @NotNull Set<Feature> getFeatures() {
        return EnumSet.noneOf(Feature.class);
    }

    @Override
    public boolean canFormat(@NotNull PsiFile psiFile) {
        NixFlakeSettings settings = NixFlakeSettings.getInstance();
        if (!settings.getUseNixFmt()) return false;

        Path file = FormattingUtil.filePath(psiFile);
        if (file == null) return false;

        return resolveFlake(psiFile.getProject(), file) != null;
    }

    @Override
    protected @Nullable FormattingTask createFormattingTask(@NotNull AsyncFormattingRequest request) {
        FormattingUtil.RequestInfo info = new FormattingUtil.RequestInfo(request);
        Path filePath = info.filePath();
        if (filePath == null) return null;

        Flake flake = resolveFlake(request.getContext().getProject(), filePath);
        if (flake == null) return null;

        if (flake.treefmt()) {
            // treefmt detected: Use equivalent of the following command
            //
            //   nix fmt -- --stdin -- {file-name}
            //
            var commandLine = new GeneralCommandLine(
                    flake.formatterExecutable(),
                    "--stdin",
                    "--",
                    flake.root().relativize(filePath).toString());
            commandLine.withWorkingDirectory(flake.root());
            return new FormattingUtil.NixFormattingTask(info, commandLine, null);
        } else {
            // Unknown formatter implementation: Use the following convention documented by `nix fmt --help`:
            //
            // > Any arguments will be forwarded to the formatter.
            // > Typically these are the files to format.
            //
            var commandLine = new GeneralCommandLine(
                    flake.formatterExecutable(),
                    // Prefix with `./` to escape files starting with a hyphen (`-`).
                    "./" + flake.root().relativize(filePath));
            commandLine.withWorkingDirectory(flake.root());
            return new FormattingUtil.NixFormattingTask(info, commandLine, filePath);
        }
    }

    private static @Nullable Flake resolveFlake(@NotNull Project project, @NotNull Path file) {
        // When using `nix fmt` with `treefmt`,
        // we may ideally support detecting which files are supported by the formatter,
        // but currently we are only formatting `*.nix` files.
        return null; // TODO Implement
    }

    record Flake(
            Path root,
            String formatterExecutable,
            boolean treefmt
    ) {}
}
