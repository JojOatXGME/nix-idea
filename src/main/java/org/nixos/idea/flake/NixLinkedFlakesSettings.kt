package org.nixos.idea.flake

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.nixos.idea.settings.NixStoragePaths

/**
 * Project-level, VCS-shareable settings: which flakes are linked to the project.
 *
 * This is the equivalent of `GradleSettings` (`.idea/gradle.xml`).
 * The *result* of an import is not stored here, see [org.nixos.idea.flake.model.NixFlakeImportState].
 * Whether flakes are detected and imported automatically is an application-level setting,
 * see [org.nixos.idea.settings.NixFlakeSettings].
 */
@Service(Service.Level.PROJECT)
@State(name = "NixLinkedFlakes", storages = [Storage(NixStoragePaths.DEFAULT)])
class NixLinkedFlakesSettings(private val project: Project) : SimplePersistentStateComponent<NixLinkedFlakesSettings.State>(State()) {

    class State : BaseState() {
        /** System-independent paths of directories containing a linked `flake.nix`. */
        var linkedFlakes by list<String>()

        // TODO(decision): Per-flake options, e.g. extra `nix` arguments, `--impure`, or which outputs to build on
        //  "Build Project". Probably a list of `LinkedFlakeState : BaseState` instead of plain strings.
    }

    val linkedFlakes: List<String>
        get() = state.linkedFlakes.toList()

    // TODO(decision): Paths are stored absolute. Use `$PROJECT_DIR$`-relative paths if the file should be shared via VCS.
    //  (Using `Storage(StoragePathMacros.WORKSPACE_FILE)` would avoid the question by keeping the setting local.)

    fun isLinked(flakeRoot: String): Boolean = flakeRoot in state.linkedFlakes

    fun link(flakeRoot: String) {
        if (isLinked(flakeRoot)) return
        // Assign a new list instead of mutating the old one, so that BaseState notices the modification.
        state.linkedFlakes = (state.linkedFlakes + flakeRoot).toMutableList()
        project.messageBus.syncPublisher(TOPIC).onFlakeLinked(flakeRoot)
    }

    fun unlink(flakeRoot: String) {
        if (isLinked(flakeRoot)) {
            state.linkedFlakes = (state.linkedFlakes - flakeRoot).toMutableList()
            project.messageBus.syncPublisher(TOPIC).onFlakeUnlinked(flakeRoot)
        }
    }

    interface Listener {
        fun onFlakeLinked(flakeRoot: String) {}
        fun onFlakeUnlinked(flakeRoot: String) {}
    }

    companion object {
        @JvmField
        @Topic.ProjectLevel
        val TOPIC: Topic<Listener> = Topic(Listener::class.java, Topic.BroadcastDirection.NONE)

        @JvmStatic
        fun getInstance(project: Project): NixLinkedFlakesSettings = project.service()
    }
}
