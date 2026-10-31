package org.nixos.idea.flake.sync

import com.intellij.build.progress.BuildProgress
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.util.Key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.nixos.idea.util.NixExecutable
import java.nio.file.Path

/**
 * Runs `nix` and streams stderr (where nix writes its progress and errors) into a step of the Build tool window.
 *
 * TODO: Map well-known nix errors (missing `flake.lock`, syntax errors with position, untracked files in git,
 *  `nix` missing or too old for flakes) to BuildIssue/FileMessageEvent, so the Sync view can navigate to
 *  `flake.nix:line:column` and offer quick fixes. Error records of the JSON log (see below) contain `file`, `line`
 *  and `column`. There is intentionally no separate up-front version check.
 */
internal object NixCommand {

    private val COMMON_ARGS = listOf(
        "--extra-experimental-features", "nix-command flakes",
        // TODO: Structured logs. `--log-format internal-json` replaces the human-readable stderr by JSON records
        //  (start/stop of activities like builds and downloads, build log lines per activity, messages with positions).
        //  The format is not formally specified ("internal"), so it may change between Nix versions.
        //  Since Nix 2.30, `--json-log-path <file or unix socket>` writes a *copy* of the log as JSON while stderr
        //  stays human-readable. Plan: keep forwarding stderr to the step node (works with every Nix version), and
        //  additionally parse the JSON copy, if available, to create child nodes per activity. If parsing fails,
        //  stop creating child nodes, but keep the plain log.
    )

    class NixCommandException(val exitCode: Int, message: String) : Exception(message)

    /**
     * Runs `nix <args>` in [workDir] and returns stdout. stderr is forwarded to [step].
     * The process is destroyed when the calling coroutine is cancelled.
     *
     * Note: `BuildProgress.output(String, ProcessOutputType)` only exists in newer platform versions; 2025.2 has the boolean overload.
     */
    suspend fun run(step: BuildProgress<BuildProgressDescriptor>, workDir: Path, vararg args: String): String {
        val commandLine = GeneralCommandLine(listOf(NixExecutable.resolve()) + COMMON_ARGS + args)
            .withWorkingDirectory(workDir)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            // Nix doesn't transcode its output according to the locale: store paths, file names, and JSON are written
            // as UTF-8 bytes. Using the system charset would only make a difference for non-UTF-8 locales, where it
            // would garble these bytes.
            .withCharset(Charsets.UTF_8)
        step.output("$ ${commandLine.commandLineString}\n", true)

        // OSProcessHandler reads stdout and stderr with one reader thread per stream, so `onTextAvailable` is never
        // called concurrently for the same stream, and the order within a stream is preserved.
        // The process handler only reports termination after both readers finished (see `BaseOSProcessHandler`),
        // which makes the content of `stdout` visible after `waitFor` returns.
        val stdout = StringBuilder()
        val handler = OSProcessHandler(commandLine)
        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                when {
                    ProcessOutputType.isStdout(outputType) -> stdout.append(event.text)
                    ProcessOutputType.isStderr(outputType) -> step.output(event.text, false)
                }
            }
        })
        handler.startNotify()

        return withContext(Dispatchers.IO) {
            try {
                while (!handler.waitFor(100)) {
                    ensureActive()
                }
            }
            finally {
                if (!handler.isProcessTerminated) handler.destroyProcess()
            }
            val exitCode = handler.exitCode ?: -1
            if (exitCode != 0) {
                throw NixCommandException(exitCode, "nix ${args.firstOrNull().orEmpty()} failed with exit code $exitCode")
            }
            stdout.toString()
        }
    }
}
