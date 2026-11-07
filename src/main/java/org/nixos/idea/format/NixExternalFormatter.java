package org.nixos.idea.format;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiFile;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nixos.idea.file.NixFile;
import org.nixos.idea.lang.NixLanguage;
import org.nixos.idea.settings.NixExternalFormatterSettings;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * External formatter for {@code *.nix} files configured by the user.
 */
public final class NixExternalFormatter extends AsyncDocumentFormattingService {

    @Override
    protected @NotNull String getNotificationGroupId() {
        return NixLanguage.NOTIFICATION_GROUP_ID;
    }

    @Override
    protected @NotNull @NlsSafe String getName() {
        return "Nix External Formatter";
    }

    @Override
    public @NotNull Set<Feature> getFeatures() {
        return EnumSet.noneOf(Feature.class);
    }

    @Override
    public boolean canFormat(@NotNull PsiFile psiFile) {
        return psiFile instanceof NixFile;
    }

    @Override
    protected @Nullable FormattingTask createFormattingTask(@NotNull AsyncFormattingRequest request) {
        NixExternalFormatterSettings settings = NixExternalFormatterSettings.getInstance();
        if (!settings.isEnabled()) return null;

        FormattingUtil.RequestInfo info = new FormattingUtil.RequestInfo(request);
        boolean inFileDirectory = settings.isFormatCommandRunInFileDirectory();
        // We can run the command inside the directory only if we know the directory
        if (inFileDirectory && info.filePath() == null) return null;

        GeneralCommandLine commandLine;
        Path targetFile;

        switch (settings.getFormatCommandInputMode()) {
            case STDIO -> {
                commandLine = new GeneralCommandLine(parseCommand(settings));
                targetFile = null;
            }
            case CLI_ARGUMENT -> {
                // `getIOFile()` returns the file backing the document when it is a local file,
                // and a temporary copy otherwise.
                File ioFile = request.getIOFile();
                if (ioFile == null) return null;
                var argv = parseCommand(settings, escapePath(inFileDirectory, ioFile.toPath()));
                commandLine = new GeneralCommandLine(argv);
                targetFile = ioFile.toPath();
            }
            default -> throw new IllegalStateException();
        }

        if (inFileDirectory) {
            commandLine.withWorkingDirectory(info.filePath().getParent());
        }

        return new FormattingUtil.NixFormattingTask(info, commandLine, targetFile);
    }

    private static @NotNull List<String> parseCommand(@NotNull NixExternalFormatterSettings settings, @NotNull String @NotNull ... additionalArgs) {
        var command = settings.getFormatCommand();
        List<String> argv = ParametersListUtil.parse(command, false, true);
        if (additionalArgs.length == 0) {
            return argv;
        }
        return Stream.concat(argv.stream(), Arrays.stream(additionalArgs)).toList();
    }

    private static String escapePath(boolean inFileDirectory, Path path) {
        if (inFileDirectory) {
            // Prefix with `./` to escape files starting with a hyphen (`-`).
            return "./" + path.getFileName();
        } else if (path.isAbsolute()) {
            return path.toString();
        } else {
            return "./" + path;
        }
    }
}
