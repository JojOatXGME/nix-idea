package org.nixos.idea.flake.model

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.legacyBridge.LegacyBridgeJpsEntitySourceFactory
import org.nixos.idea.flake.NixFlakeConstants
import java.nio.file.Path

/**
 * Writes the result of an import into the Workspace Model.
 *
 * Each linked flake becomes one module with the flake directory as content root.
 * The module is mostly needed for two reasons:
 *  - "Build Project" creates one `ModuleBuildTask` per module, which [org.nixos.idea.flake.build.NixFlakeProjectTaskRunner] claims.
 *  - The flake directory becomes proper project content, even if the project was opened from a parent directory.
 *
 * Entity source: modules use [JpsImportedEntitySource] with `externalSystemId = NIX_FLAKE`
 * (created via [LegacyBridgeJpsEntitySourceFactory]), like Maven's `WorkspaceProjectImporter`.
 * They are persisted in `.idea`/`*.iml` (or the external project storage in the system directory),
 * marked as imported, and survive a lost Workspace Model cache.
 *
 * Alternatives considered:
 *  - A custom `data class NixFlakeEntitySource : EntitySource` (what Gradle's new sync and Bazel do) is the cleanest option,
 *    but needs generated metadata classes, which third-party plugins can't produce officially. See [NixFlakeImportState].
 *  - `NonPersistentEntitySource`: never persisted, and shared with other plugins,
 *    so `replaceBySource` would also remove their entities.
 *
 * Flake inputs are *not* written as `LibraryEntity`, see [NixFlakeInputsRootsProvider].
 */
object NixFlakeWorkspaceModel {

    private fun isNixFlakeSource(source: Any): Boolean =
        source is JpsImportedEntitySource && source.externalSystemId == NixFlakeConstants.SYSTEM_ID.id

    /**
     * TODO(decision): Module naming. The directory name is not unique if multiple flakes share a name, and
     *  it may collide with modules created by other build tools or by the user (e.g. the project's own `.iml` module).
     */
    fun moduleName(flakeRoot: String): String = "nix-flake:" + Path.of(flakeRoot).fileName.toString()

    suspend fun applyFlake(project: Project, flake: NixFlakeImportState.FlakeState) {
        val root = flake.root ?: return
        val workspaceModel = project.workspaceModel
        val urlManager = workspaceModel.getVirtualFileUrlManager()
        val rootUrl = urlManager.getOrCreateFromUrl(VfsUtilCore.pathToUrl(root))
        val externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(NixFlakeConstants.SYSTEM_ID.id)
        val entitySource = LegacyBridgeJpsEntitySourceFactory.getInstance(project).createEntitySourceForModule(rootUrl, externalSource)

        workspaceModel.update("Nix flake import: $root") { storage ->
            removeModule(storage, moduleName(root))

            // TODO(decision): What if another module (e.g. the project's own module, or a Gradle module) already has
            //  the flake directory as content root? Two modules must not share a content root.
            //  Options: skip creating the module, attach the flake to the existing module, or replace it.

            storage.addEntity(ModuleEntity(moduleName(root), listOf(InheritedSdkDependency, ModuleSourceDependency), entitySource) {
                // TODO(decision): Module type. Without a type, IDEA shows a "module type unknown" warning in some places.
                //  `GENERAL_MODULE` would fit for non-JVM projects.
                contentRoots = listOf(
                    ContentRootEntity(rootUrl, emptyList(), entitySource) {
                        // `nix build` creates `result*` symlinks into the store. Indexing them would index store paths twice.
                        // TODO: Also exclude `.direnv`? Make excludes configurable?
                        excludedUrls = listOf(
                            ExcludeUrlEntity(urlManager.getOrCreateFromUrl(VfsUtilCore.pathToUrl("$root/result")), entitySource),
                        )
                    }
                )
            })
        }
    }

    suspend fun removeFlake(project: Project, flakeRoot: String) {
        project.workspaceModel.update("Nix flake unlink: $flakeRoot") { storage ->
            removeModule(storage, moduleName(flakeRoot))
        }
        NixFlakeImportState.getInstance(project).remove(flakeRoot)
    }

    private fun removeModule(storage: MutableEntityStorage, name: String) {
        val existing = storage.resolve(ModuleId(name)) ?: return
        // Never remove a module the user or another plugin created with the same name.
        if (isNixFlakeSource(existing.entitySource)) {
            storage.removeEntity(existing)
        }
    }

    /** All modules created by this integration. Used by the project task runner. */
    fun isNixFlakeModule(module: ModuleEntity): Boolean = isNixFlakeSource(module.entitySource)
}
