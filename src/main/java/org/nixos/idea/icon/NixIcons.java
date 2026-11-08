package org.nixos.idea.icon;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class NixIcons {

    // Documentation
    // -> https://plugins.jetbrains.com/docs/intellij/icons.html
    // Design Guide
    // -> https://jetbrains.design/intellij/principles/icons/

    private static final Icon SNOWFLAKE = IconLoader.getIcon("/icons/nixSnowflake.svg", NixIcons.class);
    public static final Icon FILE = SNOWFLAKE;

    // Monochrome icons in the colors of the new UI icon palette. They are used in the classic UI as well.
    public static final Icon TOOL_WINDOW = IconLoader.getIcon("/icons/nixFlakeToolWindow.svg", NixIcons.class);
    public static final Icon LOAD_CHANGES = IconLoader.getIcon("/icons/nixFlakeLoadChanges.svg", NixIcons.class);
}
