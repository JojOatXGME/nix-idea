package org.nixos.idea.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.nixos.idea.settings.SimplePersistentStateComponentHelper.delegate

@State(name = "NixFlakeSettings", storages = [Storage(NixStoragePaths.DEFAULT)])
class NixFlakeSettings : SimplePersistentStateComponent<NixFlakeSettings.State>(State()) {

    class State : BaseState() {
        var autoImport by property(false)
        var useNixFmt by property(true)
    }

    companion object {
        @JvmStatic
        fun getInstance(): NixFlakeSettings {
            return ApplicationManager.getApplication().getService(NixFlakeSettings::class.java)
        }
    }

    var autoImport: Boolean by delegate(State::autoImport)
    var useNixFmt: Boolean by delegate(State::useNixFmt)

}
