package org.nixos.idea.flake.model

import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar

/**
 * Excludes symlinks into the Nix store from the content of every module, not only from flakes linked by the import.
 *
 * `nix build` creates `result` (or `result-<output>`, or any name passed to `--out-link`), and direnv's `use flake`
 * creates `.direnv/flake-profile-*` and `.direnv/flake-inputs/*`. Without this, the IDE would index store paths
 * as project content. Builds started by this plugin don't create such links, see [org.nixos.idea.flake.NixFlakeGcRoots].
 *
 * The import doesn't give its modules a content root (see [NixFlakeWorkspaceModel]), so the exclusion is registered for
 * the content roots of all modules. Unlike `DirectoryIndexExcludePolicy`, which only takes a fixed list of URLs,
 * [WorkspaceFileSetRegistrar.registerExclusionCondition] also covers links created after the import.
 *
 * TODO: The store directory can be changed (`NIX_STORE_DIR`, `--store`). Ask nix for it (`nix eval --raw --expr builtins.storeDir`).
 */
class NixStoreSymlinkExcluder : WorkspaceFileIndexContributor<ContentRootEntity> {

    override val entityClass: Class<ContentRootEntity>
        get() = ContentRootEntity::class.java

    override fun registerFileSets(entity: ContentRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
        registrar.registerExclusionCondition(entity.url, ::isStoreSymlink, entity)
    }

    private fun isStoreSymlink(file: VirtualFile): Boolean =
        // `is(SYMLINK)` is a cached VFS flag, so this is cheap for regular files. `canonicalPath` resolves the link.
        file.`is`(VFileProperty.SYMLINK) && file.canonicalPath?.startsWith(STORE_DIR) == true

    private companion object {
        const val STORE_DIR = "/nix/store/"
    }
}
