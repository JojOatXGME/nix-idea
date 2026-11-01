package org.nixos.idea.lang.builtins;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Documentation of the builtins as provided by Nix itself.
 * <p>
 * The documentation is loaded from {@code builtins.json}, which is the output of {@code nix __dump-language}.
 * To update the file, run the following command at the root of the repository.
 * <pre>{@code
 * nix __dump-language | python3 -c "import json,sys;print(json.dumps(json.load(sys.stdin),indent=2,sort_keys=True,ensure_ascii=False))" \
 *     > src/main/resources/org/nixos/idea/lang/builtins/builtins.json
 * }</pre>
 */
public final class NixBuiltinDocumentation {

    private static final Logger LOG = Logger.getInstance(NixBuiltinDocumentation.class);

    private static final String RESOURCE = "builtins.json";
    private static final String DOC_ROOT = "https://nix.dev/manual/nix/stable/";
    private static final URI BUILTINS_PAGE = URI.create(DOC_ROOT + "language/builtins.html");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\]\\(([^)\\s]*)\\)");
    private static final Pattern MARKDOWN_EXTENSION = Pattern.compile("\\.md(?=$|#)");

    private NixBuiltinDocumentation() {} // Cannot be instantiated

    /**
     * Returns the documentation of the given builtin.
     *
     * @param name The name of the builtin, without “builtins.” prefix.
     * @return The documentation, or {@code null} if the documentation is not available.
     */
    public static @Nullable Entry get(@NotNull String name) {
        return Holder.ENTRIES.get(name);
    }

    /**
     * Returns the URL of the given builtin in the Nix manual.
     *
     * @param name The name of the builtin, without “builtins.” prefix.
     * @return URL pointing to the documentation in the Nix manual.
     */
    public static @NotNull String manualUrl(@NotNull String name) {
        return BUILTINS_PAGE + "#builtins-" + name;
    }

    /**
     * Rewrites the links within the given Markdown text so that they point to the online version of the Nix manual.
     *
     * @param markdown The Markdown text from {@code nix __dump-language}.
     * @return The Markdown text with absolute links.
     */
    static @NotNull String rewriteLinks(@NotNull String markdown) {
        Matcher matcher = MARKDOWN_LINK.matcher(markdown);
        StringBuilder result = new StringBuilder(markdown.length() + 256);
        while (matcher.find()) {
            String link = rewriteLink(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement("](" + link + ")"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static @NotNull String rewriteLink(@NotNull String link) {
        String resolved;
        if (link.startsWith("@docroot@/")) {
            resolved = DOC_ROOT + link.substring("@docroot@/".length());
        } else {
            try {
                URI uri = new URI(link);
                if (uri.isAbsolute()) {
                    return link;
                }
                resolved = BUILTINS_PAGE.resolve(uri).toString();
            } catch (URISyntaxException e) {
                return link;
            }
        }
        return MARKDOWN_EXTENSION.matcher(resolved).replaceFirst(".html");
    }

    private static final class Holder {
        private static final Map<String, Entry> ENTRIES = load();

        private static @NotNull Map<String, Entry> load() {
            try (InputStream stream = NixBuiltinDocumentation.class.getResourceAsStream(RESOURCE)) {
                if (stream == null) {
                    LOG.error("Resource not found: " + RESOURCE);
                    return Map.of();
                }
                try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    Map<String, Entry> entries = new Gson().fromJson(reader, new TypeToken<Map<String, Entry>>() {}.getType());
                    return entries == null ? Map.of() : Map.copyOf(entries);
                }
            } catch (IOException | RuntimeException e) {
                LOG.error("Failed to load documentation of builtins", e);
                return Map.of();
            }
        }
    }

    /**
     * Documentation of a single builtin.
     */
    @SuppressWarnings("unused") // Fields are set by Gson
    public static final class Entry {
        private @Nullable List<String> args;
        private @Nullable String doc;
        private @Nullable String type;
        @SerializedName("experimental-feature")
        private @Nullable String experimentalFeature;
        @SerializedName("impure-only")
        private boolean impureOnly;

        private Entry() {} // Only instantiated by Gson

        /**
         * Names of the arguments if the builtin is a function.
         */
        public @NotNull List<String> args() {
            return args == null ? List.of() : args;
        }

        /**
         * Documentation in Markdown, with links pointing to the online version of the Nix manual.
         */
        public @Nullable String markdown() {
            return doc == null ? null : rewriteLinks(doc);
        }

        /**
         * Type of the builtin if it is not a function, e.g. {@code "set"}.
         */
        public @Nullable String type() {
            return type;
        }

        /**
         * Experimental feature which must be enabled to use the builtin.
         */
        public @Nullable String experimentalFeature() {
            return experimentalFeature;
        }

        /**
         * Whether the builtin is only available in impure evaluation mode.
         */
        public boolean impureOnly() {
            return impureOnly;
        }
    }
}
