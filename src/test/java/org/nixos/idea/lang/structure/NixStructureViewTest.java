package org.nixos.idea.lang.structure;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.nixos.idea._testutil.WithIdeaPlatform;
import org.nixos.idea.file.NixFileType;
import org.nixos.idea.psi.NixBindAttr;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithIdeaPlatform.OnEdt
@WithIdeaPlatform.CodeInsight
final class NixStructureViewTest {

    private final CodeInsightTestFixture myFixture;

    NixStructureViewTest(CodeInsightTestFixture fixture) {
        myFixture = fixture;
    }

    @Test
    void nestedAttributeSets() {
        assertStructure("""
                { a = 1; b.c = { d = 2; e = { f = 3; }; }; "g h" = 4; }
                """, """
                attr a
                attr b.c
                  attr d
                  attr e
                    attr f
                attr "g h"
                """);
    }

    @Test
    void letBindings() {
        assertStructure("""
                let x = 1; y = { z = 2; }; in { inherit x; a = x; }
                """, """
                var x
                var y
                  attr z
                attr x (inherited)
                attr a
                """);
    }

    @Test
    void inheritFromSource() {
        assertStructure("""
                { inherit (lib) mkIf mkOption; }
                """, """
                attr mkIf (inherited from lib)
                attr mkOption (inherited from lib)
                """);
    }

    @Test
    void moduleLikeFile() {
        assertStructure("""
                { config, lib, pkgs, ... }:
                with lib;
                {
                  options.foo = mkOption { type = types.bool; };
                  config = mkIf config.foo.enable ({ a = 1; } // { b = 2; });
                }
                """, """
                attr options.foo
                  attr type
                attr config
                  attr a
                  attr b
                """);
    }

    @Test
    void ignoresOtherExpressions() {
        assertStructure("""
                { a = [ { b = 1; } ]; c = if true then { d = 1; } else { }; }
                """, """
                attr a
                attr c
                """);
    }

    @Test
    void currentEditorElement() {
        myFixture.configureByText(NixFileType.INSTANCE, "{ a = { b = <caret>1; }; }");
        var model = new NixStructureViewModel(myFixture.getFile(), myFixture.getEditor());
        try {
            Object current = model.getCurrentEditorElement();
            assertTrue(current instanceof NixBindAttr, String.valueOf(current));
            assertEquals("b", ((NixBindAttr) current).getAttrPath().getText());
        } finally {
            model.dispose();
        }
    }

    @Test
    void breadcrumbs() {
        myFixture.configureByText(NixFileType.INSTANCE, "{ a.b = { c = { d = <caret>1; }; }; }");
        PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        var provider = new NixBreadcrumbsProvider();
        List<String> crumbs = new ArrayList<>();
        for (PsiElement e = element; e != null; e = e.getParent()) {
            if (provider.acceptElement(e)) {
                crumbs.add(0, provider.getElementInfo(e));
            }
        }
        assertEquals(List.of("a.b", "c", "d"), crumbs);
    }

    private void assertStructure(@Language("Nix") String code, String expected) {
        myFixture.configureByText(NixFileType.INSTANCE, code);
        var model = new NixStructureViewModel(myFixture.getFile(), null);
        try {
            StringBuilder actual = new StringBuilder();
            for (TreeElement child : model.getRoot().getChildren()) {
                print(child, 0, actual);
            }
            assertEquals(expected, actual.toString());
        } finally {
            model.dispose();
        }
    }

    private static void print(TreeElement element, int depth, StringBuilder out) {
        var presentation = element.getPresentation();
        out.append("  ".repeat(depth));
        out.append(presentation.getIcon(false) == AllIcons.Nodes.Variable ? "var " : "attr ");
        out.append(presentation.getPresentableText());
        if (presentation.getLocationString() != null) {
            out.append(" (").append(presentation.getLocationString()).append(")");
        }
        out.append('\n');
        for (TreeElement child : element.getChildren()) {
            print(child, depth + 1, out);
        }
    }
}
