// Content module "nix-idea.core.frontend": UI which belongs to the frontend process.
// In split mode, the module is only loaded in the frontend process (see nix-idea.core.frontend.xml).

plugins {
    id("local.plugin-module")
}

dependencies {
    implementation(project(":core:shared"))
}
