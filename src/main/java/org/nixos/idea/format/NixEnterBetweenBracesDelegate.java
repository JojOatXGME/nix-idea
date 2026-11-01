package org.nixos.idea.format;

import com.intellij.codeInsight.editorActions.enter.EnterBetweenBracesDelegate;

/**
 * Splits empty lists ({@code [<caret>]}) over three lines when pressing <kbd>Enter</kbd>,
 * the same way the platform already does for curly braces and parentheses.
 */
public final class NixEnterBetweenBracesDelegate extends EnterBetweenBracesDelegate {
    @Override
    protected boolean isBracePair(char lBrace, char rBrace) {
        return super.isBracePair(lBrace, rBrace) || (lBrace == '[' && rBrace == ']');
    }
}
