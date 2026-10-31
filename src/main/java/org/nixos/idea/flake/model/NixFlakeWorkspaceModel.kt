package org.nixos.idea.flake.model

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
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
     * Module names must be unique within the project (`ModuleId` is the symbolic ID of a module), even for modules in
     * different directories. The name is therefore derived from the path relative to the project directory, like
     * Gradle does for subprojects: `nix-flake` for a flake in the project directory, `nix-flake.sub.dir` for
     * `<project>/sub/dir`. Flakes outside the project directory get their directory name and a hash.
     * `/` and `:` are avoided because the name may be used as `.iml` file name.
     *
     * TODO: A module created by the user or another plugin may already use the name. Then pick another name
     *  (e.g. append a number) and remember it in [NixFlakeImportState.FlakeState], instead of recomputing it here.
     * TODO(decision): Renamed or moved directories. The linked flake is identified by its absolute path, so after a
     *  rename the old flake is gone (the sync fails) and `flake.nix` at the new location shows up as unlinked.
     *  Options: detect it via a VFS listener (`VFileMoveEvent`/`VFilePropertyChangeEvent` of a linked directory) and
     *  update settings, state and module; or just drop flakes which don't exist anymore on the next sync.
     */
    fun moduleName(project: Project, flakeRoot: String): String {
        val flakePath = Path.of(flakeRoot)
        val basePath = project.basePath?.let { Path.of(it) }
        if (basePath == null || !flakePath.startsWith(basePath)) {
            return "nix-flake.${flakePath.fileName}-${Integer.toHexString(flakeRoot.hashCode())}"
        }
        return (listOf("nix-flake") + basePath.relativize(flakePath).map { it.toString() }.filter { it.isNotEmpty() })
            .joinToString(".")
    }

    suspend fun applyFlake(project: Project, flake: NixFlakeImportState.FlakeState) {
        val root = flake.root ?: return
        val workspaceModel = project.workspaceModel
        val urlManager = workspaceModel.getVirtualFileUrlManager()
        val rootUrl = urlManager.getOrCreateFromUrl(VfsUtilCore.pathToUrl(root))
        val externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(NixFlakeConstants.SYSTEM_ID.id)
        val entitySource = LegacyBridgeJpsEntitySourceFactory.getInstance(project).createEntitySourceForModule(rootUrl, externalSource)

        workspaceModel.update("Nix flake import: $root") { storage ->
            removeModule(storage, moduleName(project, root))

            // TODO(decision): Another module (e.g. the project's own module, or a Gradle module) may already have the
            //  flake directory as content root. The Workspace Model can store that, but the platform doesn't support it:
            //  `ProjectFileIndex` maps every file to exactly one module, and Project Structure reports "Content root is
            //  defined for multiple modules". Checking for an existing module first would depend on the import order.
            //  Proposal: Don't give the module a content root at all. The module is then only a hook for "Build Project"
            //  (`ModuleBuildTask` doesn't need content roots), and the runner looks up the flake via the module name in
            //  NixFlakeImportState. Excludes would move to a `DirectoryIndexExcludePolicy`, which works for any module.

            storage.addEntity(ModuleEntity(moduleName(project, root), listOf(InheritedSdkDependency, ModuleSourceDependency), entitySource) {
                // TODO(decision): Module type. A custom type is possible via the `moduleType` extension point (own icon and
                //  name in Project Structure), but IDEA then expects a `ModuleBuilder` for the New Module wizard, and
                //  projects show "unknown module type" when the plugin is disabled. Proposal: no custom type for now, but
                //  set `GeneralModuleType.TYPE_ID` (`GENERAL_MODULE`), the platform's type for modules without
                //  language-specific setup (needs a check that it is registered in all IDEs).
                contentRoots = listOf(
                    // `nix build` creates `result` (or `result-<output>`) symlinks into the store. Indexing them would
                    // index store paths as project content. Builds started by this plugin don't create them,
                    // see NixFlakeGcRoots. Patterns match file names anywhere below the content root.
                    // TODO: Users can also choose other names (`--out-link`). Ideally exclude every symlink into
                    //  `/nix/store`, or everything that is not tracked by git (nix ignores it anyway). Possible via
                    //  a `DirectoryIndexExcludePolicy` which checks symlinks / asks the git integration.
                    // TODO: Also exclude `.direnv`? Make excludes configurable?
                    ContentRootEntity(rootUrl, listOf("result", "result-*"), entitySource),
                )
            })
        }
    }

    suspend fun removeFlake(project: Project, flakeRoot: String) {
        project.workspaceModel.update("Nix flake unlink: $flakeRoot") { storage ->
            removeModule(storage, moduleName(project, flakeRoot))
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
