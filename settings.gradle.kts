pluginManagement {
    includeBuild("gradle/plugins")
}

rootProject.name = "NixIDEA"

// Content modules of the plugin (plugin model v2), see src/main/resources/META-INF/plugin.xml.
// The jar of each module is named after the module ("${rootProject.name}.<project path>"),
// i.e. the project `:core` becomes the content module `NixIDEA.core`.
include("core")
include("lsp")

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
