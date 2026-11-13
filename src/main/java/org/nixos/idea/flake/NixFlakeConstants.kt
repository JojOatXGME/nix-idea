package org.nixos.idea.flake

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import org.nixos.idea.flake.build.NixFlakeProjectTaskRunner
import org.nixos.idea.flake.model.NixFlakeInputsRootsProvider
import org.nixos.idea.flake.model.NixFlakeWorkspaceModel
import org.nixos.idea.flake.sync.NixFlakeProjectAware
import org.nixos.idea.flake.sync.NixFlakeSyncService
import org.nixos.idea.flake.ui.NixFlakeToolWindowFactory

/**
 * Constants shared by the Nix Flake integration.
 *
 * Design note: the integration does *not* implement `ExternalSystemManager`.
 * It follows the approach of the Maven and Bazel plugins:
 * an own sync service which plugs into standalone platform hooks:
 *
 *  - [NixFlakeUnlinkedProjectAware]: detects `flake.nix`, offers or auto-runs the import
 *  - [NixFlakeProjectAware]: re-import on changes, floating "Load changes" button
 *  - [NixFlakeSyncService]: runs the import, reports to the Sync tab of the Build tool window
 *  - [NixFlakeWorkspaceModel]: writes modules into the Workspace Model
 *  - [NixFlakeInputsRootsProvider]: makes flake inputs browsable as libraries
 *  - [NixFlakeToolWindowFactory]: tool window on the right
 *  - [NixFlakeProjectTaskRunner]: "Build Project" runs `nix build`
 *
 * The [SYSTEM_ID] is only used as an identifier for these APIs.
 */
object NixFlakeConstants {

    /**
     * Identifier used for [ExternalSystemUnlinkedProjectAware], [ExternalSystemProjectTracker] and as
     * `externalSystemId` of the [JpsImportedEntitySource] of imported modules.
     *
     * TODO(decision): The id ends up in `.iml` files (`external.system.id`) and in the auto-import settings.
     *  Changing it later would orphan modules of existing users. Decide on the final id before the first release.
     */
    @JvmField
    val SYSTEM_ID: ProjectSystemId = ProjectSystemId("NIX_FLAKE", "Nix Flake")

    const val FLAKE_FILE_NAME: String = "flake.nix"
    const val LOCK_FILE_NAME: String = "flake.lock"

    const val TOOL_WINDOW_ID: String = "Nix Flake"
}
