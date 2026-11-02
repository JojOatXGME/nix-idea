package org.nixos.idea.util

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import org.nixos.idea.settings.NixToolingSettings
import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves the `nix` executable used by the plugin.
 *
 * If [NixToolingSettings.nixExecutable] is set, only the configured executable is used.
 * Otherwise, `nix` is searched in `PATH`, falling back to the default profile of multi-user installations.
 * The fallback helps when the IDE is started from a desktop environment, where `PATH` sometimes lacks the Nix profile.
 */
object NixExecutable {

    const val DEFAULT_PROFILE_NIX: String = "/nix/var/nix/profiles/default/bin/nix"

    fun resolve(): String {
        val configured = NixToolingSettings.getInstance().nixExecutable
        if (configured.isNotEmpty()) return configured
        resolveDefault()?.let { return it }
        // Let the process creation fail with a meaningful error message.
        return "nix"
    }

    fun resolveDefault(): String? {
        PathEnvironmentVariableUtil.findInPath("nix")?.let { return it.path }
        if (Files.isExecutable(Path.of(DEFAULT_PROFILE_NIX))) return DEFAULT_PROFILE_NIX
        return null
    }
}
