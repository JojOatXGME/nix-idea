package org.nixos.idea.flake

import com.google.gson.JsonObject
import com.intellij.build.AbstractViewManager
import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.EventResult
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.FinishEvent
import com.intellij.build.events.OutputBuildEvent
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.nixos.idea.flake.sync.NixFlakeSyncService
import org.junit.jupiter.api.Assertions.assertEquals
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

/**
 * Sets up a project for tests of the flake import, without running `nix`.
 *
 * - Installs a [FakeNixCommand] ([nix]) for the duration of the test.
 * - Marks the project as trusted, as the import refuses to evaluate flakes of untrusted projects.
 * - Creates flake directories in the project ([createFlake]) and fake store paths in a temporary directory ([storePath]).
 *   The store paths are real directories, so the VFS and the library roots can resolve them.
 *
 * Use it with a [org.nixos.idea._testutil.WithIdeaPlatform.HeavyProject], as the import adds modules to the project.
 */
internal class NixFlakeTestFixture(val project: Project, private val disposable: Disposable) {

    val nix = FakeNixCommand().also { it.install(disposable) }

    private val storeDir: Path = Files.createTempDirectory("nix-idea-fake-store").also { dir ->
        Disposer.register(disposable) { NioFiles.deleteRecursively(dir) }
    }

    init {
        TrustedProjects.setProjectTrusted(project, true)
    }

    /** Creates a directory below the project directory (empty path for the project directory itself) and returns its path. */
    fun createFlake(relativePath: String = ""): String {
        val dir = Path.of(project.basePath!!).resolve(relativePath)
        Files.createDirectories(dir)
        return FileUtil.toSystemIndependentName(dir.toString())
    }

    /** Creates a fake store path with a `default.nix` and returns its path. */
    fun storePath(name: String): String {
        val dir = storeDir.resolve("${"0".repeat(32)}-$name")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("default.nix"), "{ }\n")
        return FileUtil.toSystemIndependentName(dir.toString())
    }

    /** An input as returned by `nix flake archive --json`. */
    data class Input(val path: String, val inputs: Map<String, Input> = emptyMap())

    /** Answers `nix flake metadata` and `nix flake archive` for [flakeRoot], as needed by a sync. */
    fun stubSync(flakeRoot: String, description: String = "Test flake", lastModified: Long = 1_700_000_000, inputs: Map<String, Input> = emptyMap()) {
        val selfPath = storePath("source")
        val metadata = JsonObject().apply {
            addProperty("description", description)
            addProperty("path", selfPath)
            addProperty("lastModified", lastModified)
        }
        nix.on({ it.take(2) == listOf("flake", "metadata") && it.last() == flakeRoot }) { metadata.toString() }
        nix.on({ it.take(2) == listOf("flake", "archive") && it.last() == flakeRoot }) { archive(selfPath, inputs).toString() }
    }

    private fun archive(path: String, inputs: Map<String, Input>): JsonObject = JsonObject().apply {
        addProperty("path", path)
        add("inputs", JsonObject().apply {
            for ((name, input) in inputs) add(name, archive(input.path, input.inputs))
        })
    }

    /** Answers `nix eval … builtins.currentSystem`. */
    fun stubSystem(system: String = "x86_64-linux") {
        nix.onPrefix("eval", "--impure", "--raw", "--expr", "builtins.currentSystem", stdout = system)
    }

    fun sync(flakeRoot: String, explicit: Boolean = true) = runBlocking {
        withTimeout(60.seconds) {
            NixFlakeSyncService.getInstance(project).sync(flakeRoot, explicit)
        }
    }

    /** Records the events of the given view (`BuildViewManager` for builds, `SyncViewManager` for imports). */
    fun recordEvents(viewManager: Class<out AbstractViewManager>): BuildEventRecorder =
        BuildEventRecorder().also { project.getService(viewManager).addListener(it, disposable) }

    fun assertNoUnexpectedNixCalls() {
        assertEquals(emptyList<FakeNixCommand.Call>(), nix.unexpectedCalls, "nix calls without an answer")
    }

    class BuildEventRecorder : BuildProgressListener {
        val events: MutableList<BuildEvent> = CopyOnWriteArrayList()

        override fun onEvent(buildId: Any, event: BuildEvent) {
            events.add(event)
        }

        /** Result of the step (or build) with the given title. */
        fun result(title: String): EventResult? = events.filterIsInstance<FinishEvent>().lastOrNull { it.message == title }?.result

        /** Results of the whole builds, in order. */
        fun buildResults(): List<EventResult> = events.filterIsInstance<FinishBuildEvent>().map { it.result }

        /** All text printed to any step. */
        fun output(): String = events.filterIsInstance<OutputBuildEvent>().joinToString("") { it.message }
    }
}
