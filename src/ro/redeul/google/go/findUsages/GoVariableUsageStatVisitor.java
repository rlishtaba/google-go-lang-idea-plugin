package ro.redeul.google.go.findUsages;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import ro.redeul.google.go.inspection.fix.RemoveVariableFix;
import ro.redeul.google.go.lang.parser.GoElementTypes;
import ro.redeul.google.go.lang.psi.GoFile;
import ro.redeul.google.go.lang.psi.GoPsiElement;
import ro.redeul.google.go.lang.psi.declarations.GoConstDeclaration;
import ro.redeul.google.go.lang.psi.declarations.GoConstDeclarations;
import ro.redeul.google.go.lang.psi.declarations.GoVarDeclaration;
import ro.redeul.google.go.lang.psi.declarations.GoVarDeclarations;
import ro.redeul.google.go.lang.psi.expressions.GoExpr;
import ro.redeul.google.go.lang.psi.expressions.literals.GoFunctionLiteral;
import ro.redeul.google.go.lang.psi.expressions.literals.GoIdentifier;
import ro.redeul.google.go.lang.psi.expressions.literals.GoLiteral;
import ro.redeul.google.go.lang.psi.impl.GoPsiElementBase;
import ro.redeul.google.go.lang.psi.impl.expressions.literals.GoLiteralImpl;
import ro.redeul.google.go.lang.psi.statements.GoForWithRangeStatement;
import ro.redeul.google.go.lang.psi.statements.GoShortVarDeclaration;
import ro.redeul.google.go.lang.psi.toplevel.*;
import ro.redeul.google.go.lang.psi.types.GoType;
import ro.redeul.google.go.lang.psi.types.GoTypeName;
import ro.redeul.google.go.lang.psi.types.struct.GoTypeStructField;
import ro.redeul.google.go.lang.psi.visitors.GoRecursiveElementVisitor;

import java.util.*;

import static ro.redeul.google.go.lang.psi.processors.GoNamesUtil.isPredefinedConstant;
import static ro.redeul.google.go.lang.psi.utils.GoPsiUtils.isIotaInConstantDeclaration;
import static ro.redeul.google.go.lang.psi.utils.GoPsiUtils.isNodeOfType;

public class GoVariableUsageStatVisitor extends GoRecursiveElementVisitor {
    private List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    private InspectionManager manager;
    private Context ctx;

    public GoVariableUsageStatVisitor(InspectionManager manager) {
        this.manager = manager;
    }

    public ProblemDescriptor[] getProblems() {
        return problems.toArray(new ProblemDescriptor[problems.size()]);
    }

    @Override
    public void visitFile(GoFile file) {
        HashMap<String, VariableUsage> global = new HashMap<String, VariableUsage>();
        ctx = new Context(problems, manager, global);
        getGlobalVariables(file, global);

        for (GoFunctionDeclaration fd : file.getFunctions()) {
            visitFunctionDeclaration(fd);
        }

        for (VariableUsage v : ctx.popLastScopeLevel().values()) {
            if (!v.isUsed()) {
                ctx.unusedGlobalVariable(v);
            }
        }
    }

    @Override
    public void visitElement(GoPsiElement element) {
        if (!couldOpenNewScope(element)) {
            super.visitElement(element);
            return;
        }

        ctx.addNewScopeLevel();

        super.visitElement(element);

        for (VariableUsage v : ctx.popLastScopeLevel().values()) {
            if (!v.isUsed()) {
                ctx.unusedVariable(v);
            }
        }
    }

    @Override
    public void visitConstDeclaration(GoConstDeclaration gcd) {
        visitIdentifiersAndExpressions(gcd.getIdentifiers(), gcd.getExpressions());
    }

    @Override
    public void visitVarDeclaration(GoVarDeclaration varDeclaration) {
        visitIdentifiersAndExpressions(varDeclaration.getIdentifiers(), varDeclaration.getExpressions());
    }

    @Override
    public void visitShortVarDeclaration(GoShortVarDeclaration shortVarDeclaration) {
        visitIdentifiersAndExpressions(shortVarDeclaration.getIdentifiers(), shortVarDeclaration.getExpressions());
    }

    @Override
    public void visitFunctionDeclaration(GoFunctionDeclaration functionDeclaration) {
        getFunctionParameters(functionDeclaration);
        visitElement(functionDeclaration.getBlock());
        afterVisitGoFunctionDeclaration();
    }

    @Override
    public void visitMethodDeclaration(GoMethodDeclaration methodDeclaration) {
        getFunctionParameters(methodDeclaration);
        visitElement(methodDeclaration.getBlock());
        afterVisitGoFunctionDeclaration();
    }

    @Override
    public void visitFunctionLiteral(GoFunctionLiteral functionLiteral) {
        createFunctionParametersMap(functionLiteral.getParameters(), functionLiteral.getResults());
        visitElement(functionLiteral.getBlock());
        afterVisitGoFunctionDeclaration();
    }

    @Override
    public void visitForWithRange(GoForWithRangeStatement forWithRange) {
        ctx.addNewScopeLevel();

        boolean isDeclaration = forWithRange.isDeclaration();
        visitExpressionAsIdentifier(forWithRange.getKey(), isDeclaration);
        visitExpressionAsIdentifier(forWithRange.getValue(), isDeclaration);

        visitElement(forWithRange.getRangeExpression());
        visitElement(forWithRange.getBlock());
    }

    @Override
    public void visitIdentifier(GoIdentifier id) {
        if (needToCollectUsage(id)) {
            ctx.addUsage(id);
        }
    }

    private void visitIdentifiersAndExpressions(GoIdentifier[] identifiers, GoExpr[] exprs) {
        for (GoIdentifier id : identifiers) {
            ctx.addDefinition(id);
        }

        for (GoExpr expr : exprs) {
            visitElement(expr);
        }
    }

    private static void setElementScope(Collection<VariableUsage> variables) {
        GoPsiElementBase[] goElements = getGoElements(variables);
        LocalSearchScope scope = new LocalSearchScope(goElements);
        for (GoPsiElementBase ge : goElements) {
            ge.setUseScope(scope);
        }
    }

    private static GoPsiElementBase[] getGoElements(Collection<VariableUsage> variables) {
        List<GoPsiElementBase> result = new ArrayList<GoPsiElementBase>();
        for (VariableUsage variable : variables) {
            if (variable.element instanceof GoPsiElementBase) {
                result.add((GoPsiElementBase) variable.element);
            }

            for (PsiElement e : variable.usages) {
                if (e instanceof GoPsiElementBase) {
                    result.add((GoPsiElementBase) e);
                }
            }
        }
        return result.toArray(new GoPsiElementBase[result.size()]);
    }


    private static boolean couldOpenNewScope(PsiElement element) {
        if (!(element instanceof GoPsiElementBase)) {
            return false;
        }

        IElementType tt = element.getNode().getElementType();
        return
                tt == GoElementTypes.IF_STATEMENT ||
                        tt == GoElementTypes.FOR_WITH_CLAUSES_STATEMENT ||
                        tt == GoElementTypes.FOR_WITH_CONDITION_STATEMENT ||
                        tt == GoElementTypes.FOR_WITH_RANGE_STATEMENT ||
                        tt == GoElementTypes.BLOCK_STATEMENT;
    }

    private void visitExpressionAsIdentifier(GoExpr expr, boolean declaration) {
        if (!(expr instanceof GoLiteral)) {
            return;
        }

        GoIdentifier id = ((GoLiteral) expr).getIdentifier();
        if (needToCollectUsage(id)) {
            if (declaration) {
                ctx.addDefinition(id);
            } else {
                ctx.addUsage(id);
            }
        }
    }

    private boolean needToCollectUsage(GoIdentifier id) {
        return id != null && !isFunctionOrMethodCall(id) && !isTypeField(id) && !isType(id) &&
                // if there is any dots in the identifier, it could be from other packages.
                // usage collection of other package variables is not implemented yet.
                !id.getText().contains(".");
    }

    private boolean isType(GoIdentifier id) {
        PsiElement parent = id.getParent();
        return isNodeOfType(parent, GoElementTypes.BASE_TYPE_NAME) ||
                isNodeOfType(parent, GoElementTypes.REFERENCE_BASE_TYPE_NAME) || parent instanceof GoTypeName;
    }

    private boolean isTypeField(GoIdentifier id) {
        return id.getParent() instanceof GoTypeStructField || isTypeFieldInitializer(id);
    }

    /**
     * Check whether id is a field name in composite literals
     * @param id
     * @return
     */
    private boolean isTypeFieldInitializer(GoIdentifier id) {
        if (!(id.getParent() instanceof GoLiteral)) {
            return false;
        }

        PsiElement parent = id.getParent().getParent();
        if (parent == null || parent.getNode() == null ||
                parent.getNode().getElementType() != GoElementTypes.COMPOSITE_LITERAL_ELEMENT_KEY) {
            return false;
        }

        PsiElement sibling = parent.getNextSibling();
        return sibling != null && ":".equals(sibling.getText());

    }

    private boolean isFunctionOrMethodCall(GoIdentifier id) {
        if (!(id.getParent() instanceof GoLiteralImpl)) {
            return false;
        }

        PsiElement grandpa = id.getParent().getParent();
        return grandpa.getNode().getElementType() == GoElementTypes.CALL_OR_CONVERSION_EXPRESSION &&
                id.getParent().isEquivalentTo(grandpa.getFirstChild());
    }

    private void addFunctionParametersToMap(GoFunctionParameter[] parameters,
                                            Map<String, VariableUsage> variables,
                                            boolean ignoreProblem) {
        for (GoFunctionParameter p : parameters) {
            for (GoIdentifier id : p.getIdentifiers()) {
                variables.put(id.getName(),
                        new VariableUsage(id, ignoreProblem));
            }
        }
    }

    private GoPsiElementBase getMethodReceiverIdentifier(
            GoMethodDeclaration md) {
        GoPsiElement methodReceiver = md.getMethodReceiver();
        if (methodReceiver == null) {
            return null;
        }

        ASTNode idNode = methodReceiver.getNode()
                .findChildByType(
                        GoElementTypes.IDENTIFIER);
        if (idNode != null && idNode.getPsi() instanceof GoPsiElementBase) {
            return (GoPsiElementBase) idNode.getPsi();
        }
        return null;
    }

    private void getGlobalVariables(GoFile file, HashMap<String, VariableUsage> variables) {
        for (GoConstDeclarations allConsts : file.getConsts()) {
            for (GoConstDeclaration cd : allConsts.getDeclarations()) {
                visitConstDeclaration(cd);
            }
        }

        for (GoVarDeclarations allVariables : file.getGlobalVariables()) {
            for (GoVarDeclaration vd : allVariables.getDeclarations()) {
                visitVarDeclaration(vd);
            }
        }

        for (GoMethodDeclaration md : file.getMethods()) {
            variables.put(md.getFunctionName(), new VariableUsage(md));
        }

        for (GoFunctionDeclaration fd : file.getFunctions()) {
            variables.put(fd.getFunctionName(), new VariableUsage(fd));
        }

        for (GoTypeDeclaration types : file.getTypeDeclarations()) {
            for (GoTypeSpec spec : types.getTypeSpecs()) {
                GoType type = spec.getType();
                if (type != null) {
                    variables.put(type.getName(), new VariableUsage(type));
                }
            }
        }

        setElementScope(variables.values());
    }


    private Map<String, VariableUsage> getFunctionParameters(GoFunctionDeclaration fd) {
        Map<String, VariableUsage> variables = createFunctionParametersMap(fd.getParameters(), fd.getResults());

        if (fd instanceof GoMethodDeclaration) {
            // Add method receiver to parameter list
            GoPsiElementBase receiver = getMethodReceiverIdentifier((GoMethodDeclaration) fd);
            if (receiver != null) {
                variables.put(receiver.getName(), new VariableUsage(receiver));
            }
        }
        return variables;
    }

    private Map<String, VariableUsage> createFunctionParametersMap(GoFunctionParameter[] parameters,
                                                                   GoFunctionParameter[] results) {
        Map<String, VariableUsage> variables = ctx.addNewScopeLevel();
        addFunctionParametersToMap(parameters, variables, false);

        // Don't detect usage problem on function result
        addFunctionParametersToMap(results, variables, true);
        return variables;
    }


    public void afterVisitGoFunctionDeclaration() {
        for (VariableUsage v : ctx.popLastScopeLevel().values()) {
            if (!v.isUsed()) {
                ctx.unusedParameter(v);
            }
        }
    }

    private static class Context {
        public final List<ProblemDescriptor> problems;
        public final InspectionManager manager;
        public final List<Map<String, VariableUsage>> variables = new ArrayList<Map<String, VariableUsage>>();

        Context(List<ProblemDescriptor> problems, InspectionManager manager,
                Map<String, VariableUsage> global) {
            this.problems = problems;
            this.manager = manager;
            this.variables.add(global);
        }

        public Map<String, VariableUsage> addNewScopeLevel() {
            Map<String, VariableUsage> variables = new HashMap<String, VariableUsage>();
            this.variables.add(variables);
            return variables;
        }

        public Map<String, VariableUsage> popLastScopeLevel() {
            Map<String, VariableUsage> map = variables.remove(variables.size() - 1);
            setElementScope(map.values());
            return map;
        }

        public void unusedVariable(VariableUsage variableUsage) {
            if (variableUsage.isBlank()) {
                return;
            }

            addProblem(variableUsage, "Unused variable",
                       ProblemHighlightType.ERROR, new RemoveVariableFix());
        }

        public void unusedParameter(VariableUsage variableUsage) {
            if (!variableUsage.isBlank()) {
                addProblem(variableUsage, "Unused parameter",
                           ProblemHighlightType.LIKE_UNUSED_SYMBOL, null);
            }
        }

        public void unusedGlobalVariable(VariableUsage variableUsage) {
            if (variableUsage.element instanceof GoFunctionDeclaration ||
                variableUsage.element instanceof GoType) {
                return;
            }

            addProblem(variableUsage, "Unused global",
                       ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                       new RemoveVariableFix());
        }

        public void undefinedVariable(VariableUsage variableUsage) {
            if (isPredefinedConstant(variableUsage.element.getText())) {
                return;
            }

            // It's valid to use "iota" in const declaration expression list.
            if (isIotaInConstantDeclaration(variableUsage.element)) {
                return;
            }

            addProblem(variableUsage, "Undefined variable",
                       ProblemHighlightType.ERROR, null);
        }

        private void addProblem(VariableUsage variableUsage, String desc,
                                ProblemHighlightType highlightType,
                                @Nullable LocalQuickFix fix) {
            if (variableUsage.ignoreAnyProblem) {
                return;
            }
            problems.add(
                manager.createProblemDescriptor(variableUsage.element, desc,
                                                fix, highlightType, true));
        }

        public void addDefinition(GoPsiElement element) {
            Map<String, VariableUsage> map = variables.get(variables.size() - 1);
            VariableUsage variableUsage = map.get(element.getText());
            if (variableUsage != null) {
                variableUsage.addUsage(element);
            } else {
                map.put(element.getText(), new VariableUsage(element));
            }
        }

        public void addUsage(GoPsiElement element) {
            for (int i = variables.size() - 1; i >= 0; i--) {
                VariableUsage variableUsage = variables.get(i)
                                                       .get(element.getText());
                if (variableUsage != null) {
                    variableUsage.addUsage(element);
                    return;
                }
            }

            undefinedVariable(new VariableUsage(element));
        }
    }
}
