package ro.redeul.google.go.lang.psi.impl.expressions;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import ro.redeul.google.go.lang.psi.expressions.GoExpr;
import ro.redeul.google.go.lang.psi.impl.GoPsiElementBase;
import ro.redeul.google.go.lang.psi.types.GoType;
import ro.redeul.google.go.lang.psi.visitors.GoElementVisitor;

/**
 * Author: Toader Mihai Claudiu <mtoader@gmail.com>
 * <p/>
 * Date: 5/19/11
 * Time: 10:58 PM
 */
public abstract class GoExpressionBase extends GoPsiElementBase implements GoExpr {

    GoType type;

    protected GoExpressionBase(@NotNull ASTNode node) {
        super(node);
    }

    public String getString() {
        return getText();
    }

    @Override
    public GoType getType() {
        return resolveType();
    }

    protected abstract GoType resolveType();

    public void accept(GoElementVisitor visitor) {
        // TODO: implement this properly
        visitor.visitElement(this);
    }
}
