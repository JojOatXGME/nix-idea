// Content module "nix-idea.core.backend": Functionality which needs the project model, the file system or external processes.
// In split mode, the module is only loaded in the backend process (see nix-idea.core.backend.xml).

plugins {
    id("local.plugin-module")
}

dependencies {
    implementation(project(":core:shared"))
}
