package org.nixos.idea.flake.sync

import com.intellij.build.SyncViewManager
import com.intellij.build.events.FailureResult
import com.intellij.build.events.SkippedResult
import com.intellij.build.events.SuccessResult
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.nixos.idea._testutil.WithIdeaPlatform
import org.nixos.idea.flake.NixFlakeConstants
import org.nixos.idea.flake.NixFlakeTestFixture
import org.nixos.idea.flake.NixFlakeTestFixture.Input
import org.nixos.idea.flake.model.NixFlakeImportState
import org.nixos.idea.flake.model.NixFlakeInputsRootsProvider
import org.nixos.idea.flake.model.NixFlakeWorkspaceModel
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

@WithIdeaPlatform.HeavyProject
internal class NixFlakeSyncServiceTest {

    private lateinit var fixture: NixFlakeTestFixture
    private lateinit var events: NixFlakeTestFixture.BuildEventRecorder
    private val finished = CopyOnWriteArrayList<Pair<String, Boolean>>()

    private val project: Project get() = fixture.project

    @BeforeEach
    fun setUp(project: Project, disposable: Disposable) {
        fixture = NixFlakeTestFixture(project, disposable)
        events = fixture.recordEvents(SyncViewManager::class.java)
        project.messageBus.connect(disposable).subscribe(NixFlakeSyncService.TOPIC, object : NixFlakeSyncService.Listener {
            override fun syncFinished(flakeRoot: String, success: Boolean) {
                finished.add(flakeRoot to success)
            }
        })
    }

    @AfterEach
    fun tearDown() {
        fixture.assertNoUnexpectedNixCalls()
    }

    @Test
    fun `stores the metadata of the flake`() {
        val root = fixture.createFlake("app")
        fixture.stubSync(root, description = "My flake", lastModified = 1_234)

        fixture.sync(root)

        val flake = NixFlakeImportState.getInstance(project).getFlake(root)
        assertNotNull(flake)
        assertEquals("My flake", flake!!.description)
        assertEquals(1_234L, flake.lastModified)
        assertEquals(listOf(root to true), finished)
        for (step in listOf("Evaluate flake", "Fetch inputs", "Update project model")) {
            assertInstanceOf(SuccessResult::class.java, events.result(step), step)
        }
        assertInstanceOf(SuccessResult::class.java, events.buildResults().single())
    }

    @Test
    fun `collects direct and transitive inputs`() {
        val root = fixture.createFlake("app")
        val nixpkgs = fixture.storePath("nixpkgs")
        val utils = fixture.storePath("utils")
        val systems = fixture.storePath("systems")
        fixture.stubSync(
            root, inputs = mapOf(
                "nixpkgs" to Input(nixpkgs),
                // `utils/nixpkgs` follows `nixpkgs`, so it has the same store path and is only listed once.
                "utils" to Input(utils, mapOf("systems" to Input(systems), "nixpkgs" to Input(nixpkgs))),
            )
        )

        fixture.sync(root)

        val inputs = NixFlakeImportState.getInstance(project).getFlake(root)!!.inputs
        assertEquals(listOf("nixpkgs", "utils", "utils/systems"), inputs.map { it.name })
        assertEquals(listOf(nixpkgs, utils, systems), inputs.map { it.storePath })
        assertEquals(listOf(true, true, false), inputs.map { it.direct })
    }

    @Test
    fun `makes inputs available as libraries`() {
        val root = fixture.createFlake("app")
        val nixpkgs = fixture.storePath("nixpkgs")
        fixture.stubSync(root, inputs = mapOf("nixpkgs" to Input(nixpkgs)))

        fixture.sync(root)

        assertEquals(listOf(listOf(nixpkgs)), libraryRoots())
        val file = LocalFileSystem.getInstance().refreshAndFindFileByPath("$nixpkgs/default.nix")!!
        assertTrue(runReadAction { ProjectFileIndex.getInstance(project).isInLibrary(file) })
    }

    @Test
    fun `replaces inputs on the next sync`() {
        val root = fixture.createFlake("app")
        val old = fixture.storePath("old")
        val new = fixture.storePath("new")
        fixture.stubSync(root, inputs = mapOf("dep" to Input(old)))
        fixture.sync(root)

        fixture.stubSync(root, inputs = mapOf("dep" to Input(new)))
        fixture.sync(root)

        assertEquals(listOf(listOf(new)), libraryRoots())
    }

    @Test
    fun `creates a module for the flake`() {
        val root = fixture.createFlake("app")
        fixture.stubSync(root)

        fixture.sync(root)

        val module = NixFlakeWorkspaceModel.findModule(project, root)
        assertNotNull(module)
        assertEquals("nix-flake.app", module!!.name)
        assertEquals(root, NixFlakeWorkspaceModel.getFlakeRoot(module))
        assertEquals(0, ModuleRootManager.getInstance(module).contentRoots.size)

        // Marked as imported, and the `.iml` file (if any) goes to `.idea/modules`, not into the flake directory.
        val source = project.workspaceModel.currentSnapshot.resolve(ModuleId(module.name))!!.entitySource
        assertInstanceOf(JpsImportedEntitySource::class.java, source)
        source as JpsImportedEntitySource
        assertEquals(NixFlakeConstants.SYSTEM_ID.id, source.externalSystemId)
        val directory = (source.internalFile as JpsProjectFileEntitySource.FileInDirectory).directory.url
        assertTrue(directory.endsWith("/.idea/modules"), directory)
    }

    @Test
    fun `names the module of a flake in the project directory nix-flake`() {
        val root = fixture.createFlake()
        fixture.stubSync(root)

        fixture.sync(root)

        assertEquals("nix-flake", NixFlakeWorkspaceModel.findModule(project, root)?.name)
    }

    @Test
    fun `keeps the module on the next sync`() {
        val root = fixture.createFlake("app")
        fixture.stubSync(root)

        fixture.sync(root)
        fixture.sync(root)

        val flakeModules = ModuleManager.getInstance(project).modules.filter { NixFlakeWorkspaceModel.getFlakeRoot(it) != null }
        assertEquals(listOf("nix-flake.app"), flakeModules.map { it.name })
    }

    @Test
    fun `uses another module name if the name is taken`() {
        val root = fixture.createFlake("app")
        fixture.stubSync(root)
        runBlocking {
            project.workspaceModel.update("Add module of another build system") { storage ->
                storage.addEntity(ModuleEntity("nix-flake.app", emptyList(), NonPersistentEntitySource))
            }
        }

        fixture.sync(root)
        fixture.sync(root)

        assertEquals("nix-flake.app-2", NixFlakeWorkspaceModel.findModule(project, root)?.name)
        val other = ModuleManager.getInstance(project).findModuleByName("nix-flake.app")
        assertNotNull(other)
        assertNull(NixFlakeWorkspaceModel.getFlakeRoot(other!!))
    }

    @Test
    fun `removes module and state when the flake is removed`() {
        val root = fixture.createFlake("app")
        val nixpkgs = fixture.storePath("nixpkgs")
        fixture.stubSync(root, inputs = mapOf("nixpkgs" to Input(nixpkgs)))
        fixture.sync(root)

        runBlocking { NixFlakeWorkspaceModel.removeFlake(project, root) }

        assertNull(NixFlakeWorkspaceModel.findModule(project, root))
        assertNull(NixFlakeImportState.getInstance(project).getFlake(root))
        assertEquals(emptyList<Any>(), libraryRoots())
    }

    @Test
    fun `reports a failing nix call and keeps the previous result`() {
        val root = fixture.createFlake("app")
        fixture.stubSync(root, description = "Before")
        fixture.sync(root)

        fixture.nix.failOnPrefix("flake", "metadata")
        fixture.sync(root)

        assertEquals(listOf(root to true, root to false), finished)
        assertInstanceOf(FailureResult::class.java, events.result("Evaluate flake"))
        assertInstanceOf(FailureResult::class.java, events.buildResults().last())
        assertEquals("Before", NixFlakeImportState.getInstance(project).getFlake(root)?.description)
        assertNotNull(NixFlakeWorkspaceModel.findModule(project, root))
    }

    @Test
    fun `does not run nix for untrusted projects`() {
        val root = fixture.createFlake("app")
        TrustedProjects.setProjectTrusted(project, false)

        fixture.sync(root)

        assertEquals(emptyList<Any>(), fixture.nix.calls)
        assertNull(NixFlakeImportState.getInstance(project).getFlake(root))
        assertNull(NixFlakeWorkspaceModel.findModule(project, root))
    }

    @Test
    fun `cancels the sync`() {
        val root = fixture.createFlake("app")
        val started = CompletableDeferred<Unit>()
        fixture.nix.on({ it.take(2) == listOf("flake", "metadata") }) {
            started.complete(Unit)
            awaitCancellation()
        }

        runBlocking {
            val job = launch(Dispatchers.Default) { NixFlakeSyncService.getInstance(project).sync(root, true) }
            withTimeout(30.seconds) { started.await() }
            job.cancelAndJoin()
        }

        assertEquals(listOf(root to false), finished)
        assertInstanceOf(SkippedResult::class.java, events.result("Evaluate flake"))
        assertNull(NixFlakeImportState.getInstance(project).getFlake(root))
    }

    private fun libraryRoots(): List<List<String>> =
        NixFlakeInputsRootsProvider().getAdditionalProjectLibraries(project).map { library -> library.sourceRoots.map { it.path } }
}
