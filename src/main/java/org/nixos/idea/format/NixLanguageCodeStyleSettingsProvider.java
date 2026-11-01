package org.nixos.idea.format;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nixos.idea.lang.NixLanguage;

/**
 * Registers code style settings for Nix, currently only the indentation options.
 * The defaults follow the conventions of the Nix community (two spaces).
 */
public final class NixLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {

    private static final String CODE_SAMPLE = """
            { lib, stdenv, fetchurl }:
            let
              version = "1.0";
            in
            stdenv.mkDerivation {
              pname = "example";
              inherit version;
              src = fetchurl {
                url = "https://example.org/example-${version}.tar.gz";
                hash = lib.fakeHash;
              };
              buildInputs = [
                stdenv.cc
              ];
              installPhase = ''
                mkdir -p $out
              '';
            }
            """;

    @Override
    public @NotNull Language getLanguage() {
        return NixLanguage.INSTANCE;
    }

    @Override
    public @Nullable String getCodeSample(@NotNull SettingsType settingsType) {
        return CODE_SAMPLE;
    }

    @Override
    public @Nullable IndentOptionsEditor getIndentOptionsEditor() {
        return new IndentOptionsEditor();
    }

    @Override
    protected void customizeDefaults(@NotNull CommonCodeStyleSettings commonSettings,
                                     @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
        indentOptions.INDENT_SIZE = 2;
        indentOptions.CONTINUATION_INDENT_SIZE = 2;
        indentOptions.TAB_SIZE = 2;
    }

    @Override
    public @NotNull CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings baseSettings,
                                                             @NotNull CodeStyleSettings modelSettings) {
        return new CodeStyleAbstractConfigurable(baseSettings, modelSettings, getConfigurableDisplayName()) {
            @Override
            protected @NotNull CodeStyleAbstractPanel createPanel(@NotNull CodeStyleSettings settings) {
                return new TabbedLanguageCodeStylePanel(NixLanguage.INSTANCE, getCurrentSettings(), settings) {
                    @Override
                    protected void initTabs(CodeStyleSettings settings) {
                        addIndentOptionsTab(settings);
                    }
                };
            }
        };
    }
}
