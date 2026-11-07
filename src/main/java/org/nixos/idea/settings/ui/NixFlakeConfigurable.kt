package org.nixos.idea.settings.ui

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.nixos.idea.settings.NixFlakeSettings

class NixFlakeConfigurable :
    BoundSearchableConfigurable("Flakes", "org.nixos.idea.settings.ui.NixFlakeConfigurable"),
    Configurable.Beta {

    override fun createPanel() = panel {
        val settings = NixFlakeSettings.getInstance()
        row {
            checkBox("Search and import Flakes in open projects")
                .bindSelected(settings::autoImport)
                .comment("Automatically evaluate <code>flake.nix</code> files found in the project directory")
        }
    }
}
