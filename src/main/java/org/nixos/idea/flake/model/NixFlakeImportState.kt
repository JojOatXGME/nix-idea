package org.nixos.idea.flake.model

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

/**
 * The result of the last successful import of each linked flake.
 *
 * Stored in the project's cache file in the IDE system directory (not in `.idea`), because it is derived data,
 * contains `/nix/store` paths which are machine-specific, and can always be recreated by a re-import.
 * This plays the role of `ExternalProjectsDataStorage` (Gradle's `project.dat`) in the classic External System integration.
 *
 * Why not Workspace Model entities?
 * Custom entity types (e.g. a `NixFlakeInputEntity`) need generated implementation and metadata classes
 * (see the Workspace Model code generator). The IntelliJ Platform SDK docs state that defining custom entities is
 * "not yet available to third-party plugins". Even a custom `EntitySource` needs generated metadata,
 * otherwise saving the Workspace Model cache fails with `MissingMetadataStorage`.
 * The modules created by the import therefore use the platform's own entity sources, see [NixFlakeWorkspaceModel].
 *
 * TODO(decision): Revisit once custom entities are available to third-party plugins. Then this state could move into
 *  `NixFlakeEntity`/`NixFlakeInputEntity`, and the inputs could be registered via `WorkspaceFileIndexContributor`.
 */
@Service(Service.Level.PROJECT)
@State(name = "NixFlakeImportState", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class NixFlakeImportState(private val project: Project) : SimplePersistentStateComponent<NixFlakeImportState.State>(State()) {

    class State : BaseState() {
        @get:XCollection(style = XCollection.Style.v2)
        var flakes by list<FlakeState>()
    }

    @Tag("flake")
    class FlakeState : BaseState() {
        /** System-independent path of the directory containing `flake.nix`. */
        var root by string()
        var description by string()
        /** Store path of the flake itself (`nix flake metadata --json` → `path`). */
        var storePath by string()
        var lastModified by property(0L)
        @get:XCollection(style = XCollection.Style.v2)
        var inputs by list<InputState>()
        /** Out path of `formatter.<system>`, if the flake defines one. */
        var formatter by string()
    }

    @Tag("input")
    class InputState : BaseState() {
        /** Attribute path of the input, e.g. `nixpkgs` or `home-manager/nixpkgs` for transitive inputs. */
        var name by string()
        /** Store path containing the input's source (`nix flake archive --json` → `inputs.<name>.path`). */
        var storePath by string()
        /** Human-readable locked reference, e.g. `github:NixOS/nixpkgs/0123abc`. */
        var lockedRef by string()
        /** `false` for inputs with `flake = false`. */
        var isFlake by property(true)
    }

    val flakes: List<FlakeState>
        get() = state.flakes.toList()

    fun getFlake(root: String): FlakeState? = state.flakes.find { it.root == root }

    fun update(flake: FlakeState) {
        // Assign a new list instead of mutating the old one, so that BaseState notices the modification.
        state.flakes = (state.flakes.filter { it.root != flake.root } + flake).toMutableList()
        project.messageBus.syncPublisher(TOPIC).stateChanged()
    }

    fun remove(root: String) {
        if (getFlake(root) != null) {
            state.flakes = state.flakes.filter { it.root != root }.toMutableList()
            project.messageBus.syncPublisher(TOPIC).stateChanged()
        }
    }

    fun interface Listener {
        fun stateChanged()
    }

    companion object {
        @JvmField
        @Topic.ProjectLevel
        val TOPIC: Topic<Listener> = Topic(Listener::class.java, Topic.BroadcastDirection.NONE)

        @JvmStatic
        fun getInstance(project: Project): NixFlakeImportState = project.service()
    }
}
