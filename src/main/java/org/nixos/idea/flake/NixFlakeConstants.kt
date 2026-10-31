package org.nixos.idea.flake

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.platform.backend.observation.ActivityKey
import org.jetbrains.annotations.Nls

/**
 * Constants shared by the Nix Flake integration.
 *
 * Design note: the integration does *not* implement `ExternalSystemManager`.
 * It follows the approach of the Maven and Bazel plugins:
 * an own sync service which plugs into standalone platform hooks:
 *
 *  - [NixFlakeUnlinkedProjectAware]: detects `flake.nix`, offers or auto-runs the import
 *  - [org.nixos.idea.flake.sync.NixFlakeProjectAware]: re-import on changes, floating "Load changes" button
 *  - [org.nixos.idea.flake.sync.NixFlakeSyncService]: runs the import, reports to the Sync tab of the Build tool window
 *  - [org.nixos.idea.flake.model.NixFlakeWorkspaceModel]: writes modules into the Workspace Model
 *  - [org.nixos.idea.flake.model.NixFlakeInputsRootsProvider]: makes flake inputs browsable as libraries
 *  - [org.nixos.idea.flake.ui.NixFlakeToolWindowFactory]: tool window on the right
 *  - [org.nixos.idea.flake.build.NixFlakeProjectTaskRunner]: "Build Project" runs `nix build`
 *
 * The [SYSTEM_ID] is only used as an identifier for these APIs.
 */
object NixFlakeConstants {

    /**
     * Identifier used for [ExternalSystemUnlinkedProjectAware], [ExternalSystemProjectTracker] and as
     * `externalSystemId` of the [com.intellij.platform.workspace.jps.JpsImportedEntitySource] of imported modules.
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

/**
 * Lets `Observation.awaitConfiguration(project)` (used by tests, headless warm-up and Qodana) wait for running imports.
 * See [com.intellij.platform.backend.observation.trackActivity].
 */
object NixFlakeActivityKey : ActivityKey {
    override val presentableName: @Nls String
        get() = "Nix flake import"
}
