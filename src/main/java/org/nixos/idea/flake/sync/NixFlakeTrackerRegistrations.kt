package org.nixos.idea.flake.sync

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.nixos.idea.flake.NixFlakeProjectSettings
import java.util.concurrent.ConcurrentHashMap

/**
 * Registers every linked flake with the auto-import tracker, and keeps the registrations in sync with [NixFlakeProjectSettings].
 *
 * Reference: `MavenProjectManagerWatcher.start()` (`projectTracker.register(...)` + `projectTracker.activate(...)`).
 */
@Service(Service.Level.PROJECT)
internal class NixFlakeTrackerRegistrations(private val project: Project) : Disposable {

    private val registrations = ConcurrentHashMap<String, Disposable>()

    fun start() {
        val settings = NixFlakeProjectSettings.getInstance(project)
        settings.linkedFlakes.forEach(::register)
        project.messageBus.connect(this).subscribe(NixFlakeProjectSettings.TOPIC, object : NixFlakeProjectSettings.Listener {
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

    companion object {
        @JvmStatic
        fun getInstance(project: Project): NixFlakeTrackerRegistrations = project.service()
    }
}
