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
import java.nio.file.Path

/**
 * Runs `nix` and streams stderr (where nix writes its progress and errors) into a step of the Build tool window.
     *
 * TODO(decision): Path of the `nix` executable. Currently resolved from `PATH`. A setting next to the formatter and
 *  LSP settings would help users of the IDE as a desktop app, where `PATH` often lacks `/nix/var/nix/profiles/default/bin`.
 * TODO: Detect `nix` missing or too old (flakes are experimental before Nix 2.4) and report a `BuildIssue` with a quick fix.
 */
internal object NixCommand {

    private val COMMON_ARGS = listOf(
        "--extra-experimental-features", "nix-command flakes",
        // TODO: `--log-format internal-json` emits structured "start/stop activity" events on stderr
        //  (downloads, builds, evaluation). They could be mapped to nested `startChildProgress` nodes and real progress
        //  bars in the Sync view, instead of forwarding raw text.
    )

    class NixCommandException(val exitCode: Int, message: String) : Exception(message)

    /**
     * Runs `nix <args>` in [workDir] and returns stdout. stderr is forwarded to [step].
     * The process is destroyed when the calling coroutine is cancelled.
     *
     * Note: `BuildProgress.output(String, ProcessOutputType)` only exists in newer platform versions; 2025.2 has the boolean overload.
     */
    suspend fun run(step: BuildProgress<BuildProgressDescriptor>, workDir: Path, vararg args: String): String {
        val commandLine = GeneralCommandLine(listOf("nix") + COMMON_ARGS + args)
            .withWorkingDirectory(workDir)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withCharset(Charsets.UTF_8)
        step.output("$ ${commandLine.commandLineString}\n", true)

        val stdout = StringBuilder()
        val handler = OSProcessHandler(commandLine)
        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                when {
                    ProcessOutputType.isStdout(outputType) -> synchronized(stdout) { stdout.append(event.text) }
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
            synchronized(stdout) { stdout.toString() }
        }
    }
}
