package org.nixos.idea.flake

import com.intellij.platform.backend.observation.ActivityKey
import org.jetbrains.annotations.Nls

/**
 * Lets `Observation.awaitConfiguration(project)` (used by tests, headless warm-up and Qodana) wait for running imports.
 * See [com.intellij.platform.backend.observation.trackActivity].
 */
object NixFlakeActivityKey : ActivityKey {
    override val presentableName: @Nls String
        get() = "Nix flake import"
}
