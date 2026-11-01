package org.nixos.idea.lang.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.nixos.idea.lang.NixLanguage;
import org.nixos.idea.lang.references.symbol.NixSymbol;
import org.nixos.idea.lang.references.symbol.NixUserSymbol;
import org.nixos.idea.psi.NixExprVar;
import org.nixos.idea.psi.NixTypes;

import java.util.Map;

/**
 * Completion for variables which are accessible at the current position,
 * i.e. variables declared by {@code let}-expressions, recursive sets, and lambdas, as well as global builtins.
 */
@SuppressWarnings("UnstableApiUsage")
public final class NixCompletionContributor extends CompletionContributor {

    private static final double PRIORITY_USER_SYMBOL = 1;
    private static final double PRIORITY_BUILTIN = 0;

    public NixCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(NixTypes.ID)
                        .withLanguage(NixLanguage.INSTANCE)
                        .withParent(NixExprVar.class),
                new VariableCompletionProvider());
    }

    private static final class VariableCompletionProvider extends CompletionProvider<CompletionParameters> {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            if (!(parameters.getPosition().getParent() instanceof NixExprVar variable)) {
                return;
            }
            for (Map.Entry<String, NixSymbol> entry : variable.getScope().collectVariables().entrySet()) {
                result.addElement(createLookupElement(entry.getKey(), entry.getValue()));
            }
        }
    }

    private static @NotNull LookupElement createLookupElement(@NotNull String name, @NotNull NixSymbol symbol) {
        if (symbol instanceof NixUserSymbol) {
            LookupElementBuilder builder = LookupElementBuilder.create(symbol, name)
                    .withIcon(symbol.presentation().getIcon())
                    .bold();
            return PrioritizedLookupElement.withPriority(builder, PRIORITY_USER_SYMBOL);
        } else {
            LookupElementBuilder builder = LookupElementBuilder.create(symbol, name)
                    .withIcon(AllIcons.Nodes.Padlock)
                    .withTypeText("builtin");
            return PrioritizedLookupElement.withPriority(builder, PRIORITY_BUILTIN);
        }
    }
}
