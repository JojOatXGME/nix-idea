package org.nixos.idea.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.nixos.idea.settings.SimplePersistentStateComponentHelper.delegate
import java.util.ArrayDeque
import java.util.Collections
import java.util.Deque

@State(
    name = "NixExternalFormatterSettings",
    storages = [Storage(value = NixStoragePaths.TOOLS, roamingType = RoamingType.LOCAL)]
)
class NixExternalFormatterSettings : SimplePersistentStateComponent<NixExternalFormatterSettings.State>(State()) {

    class State : BaseState() {
        var enabled by property(false)
        var command by string()
        var history: Deque<String> by property(ArrayDeque()) { it.isEmpty() }
        var inputMode by enum(InputMode.STDIO)
        var runInFileDirectory by property(false)
    }

    var isEnabled: Boolean by delegate(State::enabled)
    var formatCommand: String by delegate(State::command, State::history)
    val formatCommandHistory: Collection<String>
        get() = Collections.unmodifiableCollection(state.history)
    var formatCommandInputMode: InputMode by delegate(State::inputMode)
    var isFormatCommandRunInFileDirectory: Boolean by delegate(State::runInFileDirectory)

    override fun noStateLoaded() {
        super.noStateLoaded()
        // New default for `runInFileDirectory` in new installations,
        // without changing the behavior of configurations written by older versions.
        state.runInFileDirectory = true
    }

    companion object {
        @JvmStatic
        fun getInstance(): NixExternalFormatterSettings {
            return ApplicationManager.getApplication().getService(NixExternalFormatterSettings::class.java)
        }
    }

    enum class InputMode {
        STDIO,
        CLI_ARGUMENT,
    }
}
