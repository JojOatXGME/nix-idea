pluginManagement {
    includeBuild("gradle/plugins")
}

rootProject.name = "NixIDEA"

// Content modules of the plugin (plugin model v2), see src/main/resources/META-INF/plugin.xml.
// The jar of each module is named after the module ("nix-idea.<project path>", see local.plugin-module),
// i.e. the project `:core:shared` becomes the content module `nix-idea.core.shared`.
include("core:shared")
include("core:backend")
include("core:frontend")
include("spellchecker")
include("lsp")

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
