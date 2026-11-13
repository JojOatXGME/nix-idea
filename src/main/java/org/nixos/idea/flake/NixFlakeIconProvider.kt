package org.nixos.idea.flake

import com.intellij.openapi.externalSystem.ui.ExternalSystemIconProvider
import org.nixos.idea.icon.NixIcons
import javax.swing.Icon

/**
 * Icons the External System framework shows for Nix flakes. [reloadIcon] is used by the floating "Load changes" button
 * in the editor of `flake.nix` and `flake.lock`. Reference: `GradleIconProvider`.
 */
class NixFlakeIconProvider : ExternalSystemIconProvider {
    override val reloadIcon: Icon = NixIcons.LOAD_CHANGES
}
