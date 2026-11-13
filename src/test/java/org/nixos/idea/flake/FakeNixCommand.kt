package org.nixos.idea.flake

import com.intellij.build.progress.BuildProgress
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.replaceService
import org.nixos.idea.flake.sync.NixCommand
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Replaces [NixCommand] in tests, so no `nix` process is started.
 *
 * Tests register answers for the calls they expect (see [on]). Every call is recorded in [calls].
 * A call without a matching answer fails like a failing `nix` process, and is recorded in [unexpectedCalls],
 * which [NixFlakeTestFixture] checks after each test.
 */
internal class FakeNixCommand : NixCommand {

    data class Call(val workDir: Path, val args: List<String>)

    private class Answer(val matches: (List<String>) -> Boolean, val respond: suspend (Call) -> String)

    private val answers = CopyOnWriteArrayList<Answer>()
    val calls: MutableList<Call> = CopyOnWriteArrayList()
    val unexpectedCalls: MutableList<Call> = CopyOnWriteArrayList()

    fun install(parentDisposable: Disposable) {
        ApplicationManager.getApplication().replaceService(NixCommand::class.java, this, parentDisposable)
    }

    /** Answers calls for which [matches] returns `true`. Later answers take precedence over earlier ones. */
    fun on(matches: (List<String>) -> Boolean, respond: suspend (Call) -> String) {
        answers.add(Answer(matches, respond))
    }

    /** Answers calls whose arguments start with [prefix] with the given stdout. */
    fun onPrefix(vararg prefix: String, stdout: String) {
        on({ it.startsWith(prefix.asList()) }) { stdout }
    }

    /** Lets calls whose arguments start with [prefix] fail like `nix` with a non-zero exit code. */
    fun failOnPrefix(vararg prefix: String, exitCode: Int = 1) {
        on({ it.startsWith(prefix.asList()) }) { throw failure(it, exitCode) }
    }

    /** Recorded calls whose arguments start with [prefix]. */
    fun callsWithPrefix(vararg prefix: String): List<Call> = calls.filter { it.args.startsWith(prefix.asList()) }

    override suspend fun run(step: BuildProgress<BuildProgressDescriptor>, workDir: Path, args: List<String>): String {
        val call = Call(workDir, args)
        calls.add(call)
        step.output("$ nix ${args.joinToString(" ")}\n", true)
        val answer = answers.lastOrNull { it.matches(args) }
        if (answer == null) {
            unexpectedCalls.add(call)
            throw failure(call, 1)
        }
        return answer.respond(call)
    }

    private fun failure(call: Call, exitCode: Int) =
        NixCommand.NixCommandException(exitCode, "fake nix ${call.args.joinToString(" ")} failed with exit code $exitCode")

    private fun List<String>.startsWith(prefix: List<String>): Boolean = size >= prefix.size && subList(0, prefix.size) == prefix
}
