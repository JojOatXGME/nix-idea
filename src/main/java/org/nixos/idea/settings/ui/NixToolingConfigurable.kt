package org.nixos.idea.settings.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.emptyText
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import org.nixos.idea.settings.NixToolingSettings
import org.nixos.idea.util.NixExecutable
import java.nio.file.Files
import java.nio.file.InvalidPathException
import kotlin.io.path.Path

/**
 * Settings page "Languages & Frameworks | Nix | Environment".
 */
class NixToolingConfigurable :
    BoundSearchableConfigurable("Environment", "org.nixos.idea.settings.ui.NixToolingConfigurable") {

    override fun createPanel() = panel {
        val settings = NixToolingSettings.getInstance()
        group("Executables", indent = false) {
            row("nix") {
                val descriptor = FileChooserDescriptorFactory.singleFile()
                    .withTitle("Select Executable")
                textFieldWithBrowseButton(descriptor)
                    .bindText(settings::nixExecutable)
                    .applyToComponent { emptyText.text = NixExecutable.resolveDefault().orEmpty() }
                    .align(AlignX.FILL)
                    .comment("Leave empty to search <code>PATH</code> and <code>${NixExecutable.DEFAULT_PROFILE_NIX}</code>.")
                    .validationInfo {
                        val text = it.text
                        if (text.isEmpty() && NixExecutable.resolveDefault() == null)
                            warning("No <code>nix</code> executable found on <code>PATH</code>.")
                        else if (text.isNotEmpty()) {
                            try {
                                val path = Path(text)
                                if (Files.notExists(path))
                                    error("The file does not exist.")
                                else if (Files.isDirectory(path))
                                    error("The path points to a directory.")
                                else if (!Files.isExecutable(path))
                                    error("The file is not executable.")
                                else
                                    null
                            } catch (_: InvalidPathException) {
                                error("The configured value is not a valid path.")
                            }
                        } else {
                            null
                        }
                    }
            }
        }
    }
}
