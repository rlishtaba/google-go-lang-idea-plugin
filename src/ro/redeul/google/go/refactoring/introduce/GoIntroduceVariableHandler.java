package ro.redeul.google.go.refactoring.introduce;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import ro.redeul.google.go.GoLanguage;
import ro.redeul.google.go.lang.parser.GoElementTypes;
import ro.redeul.google.go.lang.psi.GoFile;
import ro.redeul.google.go.lang.psi.impl.GoPsiElementBase;
import ro.redeul.google.go.refactoring.GoRefactoringException;

public class GoIntroduceVariableHandler extends GoIntroduceHandlerBase {
    @Override
    protected void doIntroduce(Project project, Editor editor, GoFile file, int start, int end) throws GoRefactoringException {
        GoPsiElementBase e = CodeInsightUtilBase.findElementInRange(file, start, end, GoPsiElementBase.class, GoLanguage.INSTANCE);
        if (e == null) {
            throw new GoRefactoringException("It's not a valid expression!");
        }

        if (e.getParent() instanceof GoPsiElementBase) {
            GoPsiElementBase parent = (GoPsiElementBase) e.getParent();
            if (parent.getTokenType() == GoElementTypes.EXPRESSION_PARENTHESIZED) {
                e = parent;
                start = e.getTextOffset();
                end = start + e.getTextLength();
            }
        }

        // Remove redundant parenthesis around declaration.
        boolean needToRemoveParenthesis = e.getTokenType() == GoElementTypes.EXPRESSION_PARENTHESIZED;

        PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
        Document document = manager.getDocument(file);
        if (document == null) {
            return;
        }

        String variable = "value";
        int lineStart = document.getLineStartOffset(document.getLineNumber(start));
        String declaration = e.getText().trim();
        if (needToRemoveParenthesis) {
            declaration = declaration.substring(1, declaration.length() - 1);
        }
        String indent = findIndent(document.getText(new TextRange(lineStart, start)));
        editor.getCaretModel().moveToOffset(end);
        // Replace expression with variable name.
        document.replaceString(start, end, variable);

        // Declare variable.
        document.insertString(lineStart, indent + variable + " := " + declaration + "\n");

        // TODO: trigger an in-place variable rename.
    }
}
