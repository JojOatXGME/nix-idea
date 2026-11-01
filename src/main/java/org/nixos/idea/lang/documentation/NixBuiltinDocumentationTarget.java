package org.nixos.idea.lang.documentation;

import com.intellij.icons.AllIcons;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter;
import com.intellij.model.Pointer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nixos.idea.lang.NixLanguage;
import org.nixos.idea.lang.builtins.NixBuiltin;
import org.nixos.idea.lang.builtins.NixBuiltinDocumentation;
import org.nixos.idea.util.NixVersion;

import java.util.Objects;

/**
 * Quick documentation for builtins of Nix.
 */
@SuppressWarnings("UnstableApiUsage")
final class NixBuiltinDocumentationTarget implements DocumentationTarget {

    private final @NotNull Project myProject;
    private final @NotNull NixBuiltin myBuiltin;

    NixBuiltinDocumentationTarget(@NotNull Project project, @NotNull NixBuiltin builtin) {
        myProject = project;
        myBuiltin = builtin;
    }

    @Override
    public @NotNull Pointer<NixBuiltinDocumentationTarget> createPointer() {
        return () -> myProject.isDisposed() ? null : this;
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(myBuiltin.name())
                .icon(AllIcons.Nodes.Padlock)
                .presentation();
    }

    @Override
    public @Nullable String computeDocumentationHint() {
        NixBuiltinDocumentation.Entry entry = NixBuiltinDocumentation.get(myBuiltin.name());
        return HtmlChunk.tag("code").addText(signature(entry)).toString();
    }

    @Override
    public @NotNull DocumentationResult computeDocumentation() {
        NixBuiltinDocumentation.Entry entry = NixBuiltinDocumentation.get(myBuiltin.name());
        StringBuilder html = new StringBuilder();
        html.append(DocumentationMarkup.DEFINITION_START)
                .append(StringUtil.escapeXmlEntities(signature(entry)))
                .append(DocumentationMarkup.DEFINITION_END);

        String markdown = entry == null ? null : entry.markdown();
        if (markdown != null) {
            html.append(DocumentationMarkup.CONTENT_START)
                    .append(DocMarkdownToHtmlConverter.convert(myProject, markdown, NixLanguage.INSTANCE))
                    .append(DocumentationMarkup.CONTENT_END);
        }

        StringBuilder sections = new StringBuilder();
        if (myBuiltin.global()) {
            appendSection(sections, "Scope:", "Also available without <code>builtins.</code> prefix");
        }
        if (entry != null && entry.type() != null) {
            appendSection(sections, "Type:", StringUtil.escapeXmlEntities(entry.type()));
        }
        NixVersion since = myBuiltin.since();
        if (since != null) {
            appendSection(sections, "Since:", "Nix " + formatVersion(since));
        }
        String experimentalFeature = entry != null && entry.experimentalFeature() != null
                ? entry.experimentalFeature()
                : myBuiltin.featureFlag();
        if (experimentalFeature != null) {
            appendSection(sections, "Experimental feature:",
                    "<code>" + StringUtil.escapeXmlEntities(experimentalFeature) + "</code>");
        }
        if (entry != null && entry.impureOnly()) {
            appendSection(sections, "Impure:", "Not available in pure evaluation mode");
        }
        if (!sections.isEmpty()) {
            html.append(DocumentationMarkup.SECTIONS_START)
                    .append(sections)
                    .append(DocumentationMarkup.SECTIONS_END);
        }

        return DocumentationResult.documentation(html.toString())
                .externalUrl(NixBuiltinDocumentation.manualUrl(myBuiltin.name()));
    }

    private @NotNull String signature(@Nullable NixBuiltinDocumentation.Entry entry) {
        StringBuilder signature = new StringBuilder("builtins.").append(myBuiltin.name());
        if (entry != null) {
            for (String arg : entry.args()) {
                signature.append(' ').append(arg);
            }
        }
        return signature.toString();
    }

    private static void appendSection(@NotNull StringBuilder builder, @NotNull String title, @NotNull String contentHtml) {
        builder.append(DocumentationMarkup.SECTION_HEADER_START).append(title)
                .append(DocumentationMarkup.SECTION_SEPARATOR).append(contentHtml)
                .append(DocumentationMarkup.SECTION_END);
    }

    private static @NotNull String formatVersion(@NotNull NixVersion version) {
        // Enum constants follow the pattern V<major>_<minor>, e.g. V2_04
        String[] parts = version.name().substring(1).split("_");
        return Integer.parseInt(parts[0]) + "." + Integer.parseInt(parts[1]);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NixBuiltinDocumentationTarget that)) return false;
        return myProject.equals(that.myProject) && myBuiltin.equals(that.myBuiltin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myProject, myBuiltin);
    }
}
