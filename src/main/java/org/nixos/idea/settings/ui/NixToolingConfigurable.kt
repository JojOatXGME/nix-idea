package org.nixos.idea.settings.ui

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import org.nixos.idea.settings.NixToolingSettings
import org.nixos.idea.util.NixExecutable

/**
 * Settings page "Languages & Frameworks | Nix | Environment".
 *
 * TODO(decision): Final name of the page ("Environment" or "Tooling").
 * TODO: Use a text field with a browse button and validate that the configured file is executable.
 */
class NixToolingConfigurable :
    BoundSearchableConfigurable("Environment", "org.nixos.idea.settings.ui.NixToolingConfigurable") {

    override fun createPanel() = panel {
        val settings = NixToolingSettings.getInstance()
        row("Nix executable:") {
            textField()
                .bindText(settings::nixExecutable)
                .align(AlignX.FILL)
                .comment("Leave empty to search <code>PATH</code> and <code>${NixExecutable.DEFAULT_PROFILE_NIX}</code>.")
        }
    }
}
