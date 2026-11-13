package org.nixos.idea.flake.build

import com.intellij.build.BuildViewManager
import com.intellij.build.events.FailureResult
import com.intellij.build.events.SuccessResult
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskManager
import com.intellij.task.ProjectTaskRunner
import com.intellij.task.TaskRunnerResults
import org.jetbrains.concurrency.AsyncPromise
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.nixos.idea._testutil.WithIdeaPlatform
import org.nixos.idea.flake.NixFlakeGcRoots
import org.nixos.idea.flake.NixFlakeTestFixture
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@WithIdeaPlatform.HeavyProject
internal class NixFlakeBuildServiceTest {

    private lateinit var fixture: NixFlakeTestFixture
    private lateinit var events: NixFlakeTestFixture.BuildEventRecorder

    @BeforeEach
    fun setUp(project: Project, disposable: Disposable) {
        fixture = NixFlakeTestFixture(project, disposable)
        events = fixture.recordEvents(BuildViewManager::class.java)
    }

    @AfterEach
    fun tearDown() {
        fixture.assertNoUnexpectedNixCalls()
    }

    @Test
    fun `builds all packages of the current system`() {
        val root = fixture.createFlake("app")
        fixture.stubSystem("x86_64-linux")
        fixture.nix.onPrefix("eval", "--json", "$root#packages.x86_64-linux", stdout = """["hello","my.tool"]""")
        fixture.nix.onPrefix("build", stdout = "")

        val result = build(root)

        assertSame(TaskRunnerResults.SUCCESS, result)
        val outLink = NixFlakeGcRoots.outLink(fixture.project, root, "packages")
        assertEquals(
            listOf(
                "build", "--print-build-logs", "--out-link", outLink.toString(),
                "$root#packages.x86_64-linux.\"hello\"",
                "$root#packages.x86_64-linux.\"my.tool\"",
            ),
            fixture.nix.callsWithPrefix("build").single().args,
        )
        assertEquals(Path.of(root), fixture.nix.callsWithPrefix("build").single().workDir)
        assertInstanceOf(SuccessResult::class.java, events.result("nix build app"))
        assertInstanceOf(SuccessResult::class.java, events.buildResults().single())
    }

    @Test
    fun `skips the build if the flake has no packages`() {
        val root = fixture.createFlake("app")
        fixture.stubSystem("aarch64-darwin")
        fixture.nix.onPrefix("eval", "--json", stdout = "[]")

        val result = build(root)

        assertSame(TaskRunnerResults.SUCCESS, result)
        assertEquals(emptyList<Any>(), fixture.nix.callsWithPrefix("build"))
        assertTrue(events.output().contains("Flake has no packages for aarch64-darwin"), events.output())
    }

    @Test
    fun `builds each flake`() {
        val first = fixture.createFlake("first")
        val second = fixture.createFlake("second")
        fixture.stubSystem()
        fixture.nix.onPrefix("eval", "--json", stdout = """["default"]""")
        fixture.nix.onPrefix("build", stdout = "")

        val result = build(first, second)

        assertSame(TaskRunnerResults.SUCCESS, result)
        assertEquals(listOf(Path.of(first), Path.of(second)), fixture.nix.callsWithPrefix("build").map { it.workDir })
        assertInstanceOf(SuccessResult::class.java, events.result("nix build first"))
        assertInstanceOf(SuccessResult::class.java, events.result("nix build second"))
    }

    @Test
    fun `stops at the first failing flake`() {
        val first = fixture.createFlake("first")
        val second = fixture.createFlake("second")
        fixture.stubSystem()
        fixture.nix.onPrefix("eval", "--json", stdout = """["default"]""")
        fixture.nix.failOnPrefix("build")

        val result = build(first, second)

        assertSame(TaskRunnerResults.FAILURE, result)
        assertEquals(listOf(Path.of(first)), fixture.nix.calls.map { it.workDir }.distinct())
        assertInstanceOf(FailureResult::class.java, events.result("nix build first"))
        assertInstanceOf(FailureResult::class.java, events.buildResults().single())
    }

    @Test
    fun `Build Project builds the linked flakes`() {
        val root = fixture.createFlake("app")
        fixture.stubSync(root)
        fixture.sync(root)
        fixture.stubSystem()
        fixture.nix.onPrefix("eval", "--json", stdout = """["default"]""")
        fixture.nix.onPrefix("build", stdout = "")

        val result = ProjectTaskManager.getInstance(fixture.project).buildAllModules().blockingGet(60, TimeUnit.SECONDS)

        assertNotNull(result)
        assertFalse(result!!.hasErrors())
        assertFalse(result.isAborted)
        assertEquals(Path.of(root), fixture.nix.callsWithPrefix("build").single().workDir)
    }

    private fun build(vararg flakeRoots: String): ProjectTaskRunner.Result? {
        val promise = AsyncPromise<ProjectTaskRunner.Result>()
        NixFlakeBuildService.getInstance(fixture.project).build(flakeRoots.toList(), promise)
        return promise.blockingGet(60, TimeUnit.SECONDS)
    }
}
