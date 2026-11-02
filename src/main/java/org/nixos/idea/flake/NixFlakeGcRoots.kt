package org.nixos.idea.flake

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path

/**
 * Location of the out-links (`nix build --out-link …`) created by the plugin.
 *
 * An out-link is a symlink into the store which nix registers as an *indirect GC root* (via `/nix/var/nix/gcroots/auto`).
 * As long as the symlink exists, `nix-collect-garbage` keeps the referenced store path. This keeps the formatter and
 * the build results of the flake alive without creating `result` symlinks in the user's project directory.
 *
 * A plain file like the one backing [org.nixos.idea.flake.model.NixFlakeImportState] cannot be a GC root:
 * nix only follows symlinks registered in its `gcroots` directory, it doesn't scan files for store paths.
 *
 * Layout: `<IDE system dir>/nix-idea/gcroots/<project hash>/<flake dir name>-<hash of flake path>/<name>`
 *
 * The directory of a flake is deleted when the flake is unlinked (see [NixFlakeUnlinkedProjectAware.unlinkProject]).
 * TODO: Also clean up directories of projects which were deleted or removed from the IDE. Otherwise their store paths
 *  stay alive forever. Maybe on IDE start, for project hashes which are not in the recent projects list anymore.
 */
object NixFlakeGcRoots {

    /** Returns the path for an out-link named [name] (e.g. `formatter` or `packages`) and creates its parent directory. */
    fun outLink(project: Project, flakeRoot: String, name: String): Path {
        val dir = flakeDir(project, flakeRoot)
        Files.createDirectories(dir)
        return dir.resolve(name)
    }

    fun flakeDir(project: Project, flakeRoot: String): Path {
        val flakePath = Path.of(flakeRoot)
        val flakeId = "${flakePath.fileName}-${Integer.toHexString(flakeRoot.hashCode())}"
        return Path.of(PathManager.getSystemPath(), "nix-idea", "gcroots", project.locationHash, flakeId)
    }
}
