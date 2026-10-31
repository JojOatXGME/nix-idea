package org.nixos.idea.flake.sync

import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import org.nixos.idea.flake.NixFlakeSettings
import java.util.concurrent.ConcurrentHashMap

/**
 * Registers every linked flake with the auto-import tracker, and keeps the registrations in sync with [NixFlakeSettings].
 *
 * Reference: `MavenProjectManagerWatcher.start()` (`projectTracker.register(...)` + `projectTracker.activate(...)`).
 */
internal class NixFlakeStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<NixFlakeTrackerRegistrations>().start()
    }
}

@Service(Service.Level.PROJECT)
internal class NixFlakeTrackerRegistrations(private val project: Project) : Disposable {

    private val registrations = ConcurrentHashMap<String, Disposable>()

    fun start() {
        val settings = NixFlakeSettings.getInstance(project)
        settings.linkedFlakes.forEach(::register)
        project.messageBus.connect(this).subscribe(NixFlakeSettings.TOPIC, object : NixFlakeSettings.Listener {
            override fun onFlakeLinked(flakeRoot: String) = register(flakeRoot)
            override fun onFlakeUnlinked(flakeRoot: String) {
                registrations.remove(flakeRoot)?.let(Disposer::dispose)
            }
        })
    }

    private fun register(flakeRoot: String) {
        val disposable = Disposer.newDisposable(this, "NixFlakeProjectAware($flakeRoot)")
        if (registrations.putIfAbsent(flakeRoot, disposable) != null) {
            Disposer.dispose(disposable)
            return
        }
        val tracker = ExternalSystemProjectTracker.getInstance(project)
        val aware = NixFlakeProjectAware(project, flakeRoot)
        tracker.register(aware, disposable)
        // Activation lets the tracker schedule the first sync if the state is dirty, e.g. after the Workspace Model cache
        // was invalidated, or if `flake.lock` changed while the IDE was closed.
        tracker.activate(aware.projectId)
    }

    override fun dispose() {}
}

/**
 * Re-runs the import when the user trusts the project, since [NixFlakeSyncService] skips untrusted projects.
 * Registered as an application listener in plugin.xml. Reference: `ExternalSystemTrustedListener`.
 */
internal class NixFlakeTrustListener : TrustedProjectsListener {
    override fun onProjectTrusted(project: Project) {
        // TODO(decision): Only re-sync flakes that were never synced, or all of them?
        NixFlakeSyncService.getInstance(project).scheduleSyncAll(explicit = false)
    }
}
