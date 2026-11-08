package org.nixos.idea.flake.sync

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectAware
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectListener
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectReloadContext
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus
import com.intellij.openapi.project.Project
import org.nixos.idea.flake.NixFlakeConstants
import org.nixos.idea.settings.NixFlakeSettings

/**
 * Connects one linked flake to the platform's auto-import tracker (`ExternalSystemProjectTracker`).
 *
 * The tracker then
 *  - watches [settingsFiles] and shows the floating "Load Nix Flake Changes" button in the editor,
 *  - only reloads automatically if [NixFlakeSettings.autoImport] is enabled, and then
 *    honors the user's auto-reload setting (Settings | Build Tools: "Reload project after changes in the build scripts"),
 *  - requests a reload when the Workspace Model cache was lost (see `AutoImportProjectTracker.isWorkspaceModelCacheAbsentOrInvalid`).
 *
 * Maven uses this API the same way without implementing `ExternalSystemManager`, see `MavenProjectAware`.
 */
class NixFlakeProjectAware(
    private val project: Project,
    private val flakeRoot: String,
) : ExternalSystemProjectAware {

    override val projectId: ExternalSystemProjectId = ExternalSystemProjectId(NixFlakeConstants.SYSTEM_ID, flakeRoot)

    override val settingsFiles: Set<String>
        get() {
            val files = linkedSetOf(
                "$flakeRoot/${NixFlakeConstants.FLAKE_FILE_NAME}",
                "$flakeRoot/${NixFlakeConstants.LOCK_FILE_NAME}",
            )
            // TODO(decision): Also watch local `.nix` files imported by `flake.nix`? Changing them can change the outputs,
            //  but they are not relevant for the inputs. Could be derived from the flake's PSI (`import ./foo.nix`).
            return files
        }

    override fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
        project.messageBus.connect(parentDisposable).subscribe(NixFlakeSyncService.TOPIC, object : NixFlakeSyncService.Listener {
            override fun syncStarted(flakeRoot: String) {
                if (flakeRoot == this@NixFlakeProjectAware.flakeRoot) listener.onProjectReloadStart()
            }

            override fun syncFinished(flakeRoot: String, success: Boolean) {
                if (flakeRoot == this@NixFlakeProjectAware.flakeRoot) {
                    listener.onProjectReloadFinish(if (success) ExternalSystemRefreshStatus.SUCCESS else ExternalSystemRefreshStatus.FAILURE)
                }
            }
        })
    }

    override fun reloadProject(context: ExternalSystemProjectReloadContext) {
        // `context.isExplicitReload` is true when the user clicked the floating button.
        // Without auto-import, flakes are only evaluated on explicit request. Skipping the reload keeps the project
        // dirty for the tracker (the status only changes on `onProjectReloadStart`), so the floating button stays visible.
        if (!context.isExplicitReload && !NixFlakeSettings.getInstance().autoImport) return
        // Must not block.
        NixFlakeSyncService.getInstance(project).scheduleSync(flakeRoot, context.isExplicitReload)
    }
}
