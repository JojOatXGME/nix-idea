package org.nixos.idea.flake

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autolink.ExternalSystemProjectLinkListener
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.util.io.NioFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nixos.idea.flake.model.NixFlakeWorkspaceModel
import org.nixos.idea.flake.sync.NixFlakeSyncService
import java.nio.file.Path

/**
 * Detects unlinked flakes and lets the platform suggest (or automatically run) the import.
 *
 * The platform's `UnlinkedProjectStartupActivity` uses this extension to
 *  - automatically call [linkAndLoadProjectAsync] when the project is opened with an empty model and
 *    `flake.nix` is the only build file in the project root, and
 *  - otherwise show a "Nix Flake build script found → Load" notification while there is an unlinked `flake.nix`.
 *
 * Reference: `GradleUnlinkedProjectAware`, `MavenUnlinkedProjectAware`, `BazelUnlinkedProjectAware`.
 */
class NixFlakeUnlinkedProjectAware : ExternalSystemUnlinkedProjectAware {

    override val systemId: ProjectSystemId = NixFlakeConstants.SYSTEM_ID

    override fun isBuildFile(project: Project, buildFile: VirtualFile): Boolean =
        buildFile.name == NixFlakeConstants.FLAKE_FILE_NAME

    override fun isLinkedProject(project: Project, externalProjectPath: String): Boolean =
        NixFlakeSettings.getInstance(project).isLinked(externalProjectPath)

    override fun subscribe(project: Project, listener: ExternalSystemProjectLinkListener, parentDisposable: Disposable) {
        project.messageBus.connect(parentDisposable).subscribe(NixFlakeSettings.TOPIC, object : NixFlakeSettings.Listener {
            override fun onFlakeLinked(flakeRoot: String) = listener.onProjectLinked(flakeRoot)
            override fun onFlakeUnlinked(flakeRoot: String) = listener.onProjectUnlinked(flakeRoot)
        })
    }

    override suspend fun linkAndLoadProjectAsync(project: Project, externalProjectPath: String) {
        // The platform calls this method without checking the trust state of the project (see `UnlinkedProjectStartupActivity`).
        // Evaluating a flake may run arbitrary code (IFD, `nixConfig`, and building the formatter), so ask the user first.
        // Same approach as `GradleOpenProjectProvider.linkProject`.
        if (!ExternalSystemTrustedProjectDialog.confirmLinkingUntrustedProjectAsync(project, systemId, Path.of(externalProjectPath))) {
            return
        }
        NixFlakeSettings.getInstance(project).link(externalProjectPath)
        NixFlakeSyncService.getInstance(project).sync(externalProjectPath, explicit = true)
    }

    override suspend fun unlinkProject(project: Project, externalProjectPath: String) {
        NixFlakeSettings.getInstance(project).unlink(externalProjectPath)
        NixFlakeWorkspaceModel.removeFlake(project, externalProjectPath)
        // Drop the GC roots, so `nix-collect-garbage` can delete the formatter and build results of the flake.
        withContext(Dispatchers.IO) {
            NioFiles.deleteRecursively(NixFlakeGcRoots.flakeDir(project, externalProjectPath))
        }
    }
}
