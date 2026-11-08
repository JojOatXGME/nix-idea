package org.nixos.idea.flake.model

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.nixos.idea.icon.NixIcons
import javax.swing.Icon

/**
 * Makes the sources of flake inputs part of the workspace, so they are indexed, searchable ("Project and Libraries" scope),
 * navigable, and shown under "External Libraries" in the Project view.
 *
 * Options considered (see the PR description for details):
 *
 *  1. `WorkspaceFileIndexContributor<NixFlakeInputEntity>` + `ExternalLibrariesWorkspaceModelNodesProvider<NixFlakeInputEntity>`:
 *     The modern approach, but both need a custom entity type, which third-party plugins can't define yet.
 *     The nodes provider only adds a Project view node for entities whose files are *already* registered by a
 *     file index contributor, so it adds presentation, not indexing.
 *  2. `LibraryEntity` with SOURCES roots + `LibraryDependency` from the flake module:
 *     Persisted and incremental, but JVM-shaped ("Classes"/"Sources"), editable in Project Structure,
 *     and libraries without class roots look broken in some IDEA UIs.
 *  3. [AdditionalLibraryRootsProvider] (this class): Simple, works in every IDE, shown under "External Libraries"
 *     automatically, with an own icon and name per input. The platform wraps it into the Workspace File Index internally.
 *     Downsides: not part of the Workspace Model (state comes from [NixFlakeImportState]); changes must be announced via
 *     [AdditionalLibraryRootsListener.fireAdditionalLibraryChanged], see [notifyInputsChanged].
 */
class NixFlakeInputsRootsProvider : AdditionalLibraryRootsProvider() {

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        // Inputs are already deduplicated per flake (see NixFlakeSyncService.collectInputs). The same store path can
        // still be used by multiple linked flakes, so deduplicate across flakes as well. The first flake wins the name.
        val result = LinkedHashMap<String, SyntheticLibrary>()
        for (flake in NixFlakeImportState.getInstance(project).flakes) {
            for (input in flake.inputs) {
                val storePath = input.storePath ?: continue
                if (storePath in result) continue
                val root = LocalFileSystem.getInstance().findFileByPath(storePath) ?: continue
                result[storePath] = NixFlakeInputLibrary(input.name ?: root.name, input.lockedRef, root)
            }
        }
        // TODO(decision): Index everything, or only `.nix` files? Large inputs like nixpkgs (~40k files) make indexing slow.
        //  `SyntheticLibrary.getExcludeFileCondition()` can skip directories, or the root could be registered as
        //  non-indexable once `WorkspaceFileKind.EXTERNAL_NON_INDEXABLE` can be used (needs option 1).
        return result.values
    }

    // `/nix/store` is immutable: never return store paths from `getRootsToWatch`.

    private class NixFlakeInputLibrary(
        private val inputName: String,
        private val lockedRef: String?,
        private val root: VirtualFile,
    ) : SyntheticLibrary("nix-flake-input:${root.path}", null), ItemPresentation {

        override fun getSourceRoots(): Collection<VirtualFile> = listOf(root)

        override fun getPresentableText(): String = inputName

        override fun getLocationString(): String? = lockedRef

        override fun getIcon(unused: Boolean): Icon = NixIcons.FILE

        override fun equals(other: Any?): Boolean = other is NixFlakeInputLibrary && other.root == root

        override fun hashCode(): Int = root.hashCode()
    }

    companion object {
        /**
         * Must be called after [NixFlakeImportState] changed the inputs of a flake.
         * The listener requires a write action. Resolve the roots before calling this method, outside the write action,
         * as `refreshAndFindFileByPath` must not run inside a write action.
         */
        suspend fun notifyInputsChanged(project: Project, oldRoots: Collection<VirtualFile>, newRoots: Collection<VirtualFile>) {
            edtWriteAction {
                AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(
                    project, "Nix flake inputs", oldRoots, newRoots, "NixFlakeInputs",
                )
            }
        }
    }
}
