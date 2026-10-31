// Content module "NixIDEA.lsp": Integration of language servers.
// The module is optional and only loaded in IDEs providing the LSP API (see NixIDEA.lsp.xml).
// Common configuration is applied by the root project (see subprojects block in ../build.gradle.kts).

dependencies {
    implementation(project(":core"))
}
