// Content module "nix-idea.frontend": UI which belongs to the frontend process.
// In split mode, the module is only loaded in the frontend process (see nix-idea.frontend.xml).

plugins {
    id("local.plugin-module")
}

dependencies {
    implementation(project(":core"))
}
