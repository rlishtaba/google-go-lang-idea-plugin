package ro.redeul.google.go.lang.psi.processors;

import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.BaseScopeProcessor;
import ro.redeul.google.go.lang.psi.statements.GoShortVarDeclaration;
import ro.redeul.google.go.lang.psi.expressions.GoExpr;
import ro.redeul.google.go.lang.psi.expressions.literals.GoIdentifier;
import ro.redeul.google.go.lang.psi.toplevel.GoFunctionParameter;
import ro.redeul.google.go.lang.psi.types.GoType;

/**
 * Author: Toader Mihai Claudiu <mtoader@gmail.com>
 * <p/>
 * Date: 5/26/11
 * Time: 7:38 PM
 */
public class VariableTypeResolver extends BaseScopeProcessor {

    private GoType type;
    private GoIdentifier identifier;

    public VariableTypeResolver(GoIdentifier identifier) {
        this.identifier = identifier;
    }

    @Override
    public boolean execute(PsiElement element, ResolveState state) {

        if ( element instanceof GoFunctionParameter ) {
            return checkFunctionParameters((GoFunctionParameter) element);
        }

        if ( element instanceof GoShortVarDeclaration) {
            GoShortVarDeclaration shortVarDeclaration = (GoShortVarDeclaration) element;

            GoIdentifier identifiers[] = shortVarDeclaration.getIdentifiers();
            GoExpr expressions[] = shortVarDeclaration.getExpressions();

            for (int i = 0, identifiersLength = identifiers.length; i < identifiersLength; i++) {

                GoIdentifier identifier = identifiers[i];

                if (identifier.getName().equalsIgnoreCase(this.identifier.getName())) {
                    if ( expressions != null && expressions.length > i ) {
                        type = expressions[i].getType();
                    }
                    return false;
                }
            }
        }

        return true;
    }

    private boolean checkFunctionParameters(GoFunctionParameter parameter) {

        GoIdentifier []identifiers = parameter.getIdentifiers();

        for (GoIdentifier identifier : identifiers) {
            if ( identifier.getText().equalsIgnoreCase(this.identifier.getText()) ) {
                type = parameter.getType();
                return false;
            }
        }

        return true;
    }

    public GoType getResolvedType() {
        return type;
    }
}
