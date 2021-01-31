package org.nixos.idea.lang;

import com.intellij.lexer.FlexAdapter;

public class NixLexer extends FlexAdapter {
    public NixLexer() {
        super(new _NixLexer(null) {
            @Override
            public void reset(CharSequence buffer, int start, int end, int initialState) {
                super.reset(buffer, start, end, initialState);
                restoreState(initialState);
            }
        });
    }

    @Override
    public int getState() {
        super.getState();
        return ((_NixLexer) getFlex()).getStateIndex();
    }
}
