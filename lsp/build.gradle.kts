// Content module "nix-idea.lsp": Integration of language servers.
// The module is optional and only loaded in IDEs providing the LSP API (see nix-idea.lsp.xml).

plugins {
    id("local.plugin-module")
}

dependencies {
    implementation(project(":core"))
}
