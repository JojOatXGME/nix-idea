package org.nixos.idea.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Machine-specific settings of how the plugin runs the `nix` CLI.
 */
@State(
    name = "NixToolingSettings",
    storages = [Storage(value = NixStoragePaths.TOOLS, roamingType = RoamingType.LOCAL)]
)
class NixToolingSettings : SimplePersistentStateComponent<NixToolingSettings.State>(State()) {

    class State : BaseState() {
        var nixExecutable by string()
    }

    /** Path of the `nix` executable. Empty means auto-detection, see [org.nixos.idea.util.NixExecutable]. */
    var nixExecutable: String
        get() = state.nixExecutable.orEmpty()
        set(value) {
            state.nixExecutable = value.trim().ifEmpty { null }
        }

    companion object {
        @JvmStatic
        fun getInstance(): NixToolingSettings {
            return ApplicationManager.getApplication().getService(NixToolingSettings::class.java)
        }
    }
}
