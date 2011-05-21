package ro.redeul.google.go.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.ProjectAndLibrariesScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import ro.redeul.google.go.lang.lexer.GoTokenTypes;
import ro.redeul.google.go.lang.psi.GoFile;
import ro.redeul.google.go.lang.psi.expressions.GoPsiExpression;
import ro.redeul.google.go.lang.psi.expressions.GoSelectorExpression;
import ro.redeul.google.go.lang.psi.processors.GoExpressionTypeResolver;
import ro.redeul.google.go.lang.psi.toplevel.GoImportSpec;
import ro.redeul.google.go.lang.psi.toplevel.GoPackageDeclaration;
import ro.redeul.google.go.lang.psi.types.GoTypeName;
import ro.redeul.google.go.lang.stubs.GoNamesCache;

import java.util.Collection;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

/**
 * Author: Toader Mihai Claudiu <mtoader@gmail.com>
 * <p/>
 * Date: Aug 19, 2010
 * Time: 9:09:20 PM
 */
public class GoCompletionContributor extends CompletionContributor {


    CompletionProvider<CompletionParameters> packageCompletionProvider = new CompletionProvider<CompletionParameters>() {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {
            result.addElement(LookupElementBuilder.create("package "));
        }
    };

    CompletionProvider<CompletionParameters> importCompletionProvider = new CompletionProvider<CompletionParameters>() {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {
            result.addElement(LookupElementBuilder.create("import "));
        }
    };

    CompletionProvider<CompletionParameters> importPathCompletionProvider = new CompletionProvider<CompletionParameters>() {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {

            String currentPath = parameters.getPosition().getText().replaceAll("Intellij.*", "");

            if (!currentPath.startsWith("\".")) {
                Project project = parameters.getOriginalFile().getProject();

                GoNamesCache packageNamesCache = ContainerUtil.findInstance(project.getExtensions(PsiShortNamesCache.EP_NAME), GoNamesCache.class);

                if ( packageNamesCache != null ) {
                    String packages[] = packageNamesCache.getPackagesByName("a", new ProjectAndLibrariesScope(project));

                    for (String aPackage : packages) {
                        result.addElement(LookupElementBuilder.create(aPackage).setTypeText("via sdk"));
                    }
                }
            }

            PsiElement pos = parameters.getOriginalPosition();
            if ( pos != null && pos.getContainingFile() != null ) {
                Collection<LookupElementBuilder> elements = GoCompletionUtil.resolveLocalPackagesForPath(pos.getProject(), pos.getContainingFile(), currentPath);
                for (LookupElementBuilder lookupElement : elements) {
                    result.addElement(lookupElement);
                }
            }
        }
    };

    CompletionProvider<CompletionParameters> typeNameCompletionProvider = new CompletionProvider<CompletionParameters>() {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {

            PsiElement element = parameters.getOriginalPosition();
            if ( element == null ) {
                return;
            }

            LookupElement elements[];
//            if (currentPath.startsWith("\"./")) {
//                elements = GoCompletionUtil.resolveLocalPackagesForPath(element.getProject(), element.getContainingFile(), currentPath);
//            } else {
//                elements = GoCompletionUtil.resolveSdkPackagesForPath(element.getProject(), element.getContainingFile(), currentPath);
//            }

            elements = GoCompletionUtil.getImportedPackagesNames(element.getContainingFile());
            for (LookupElement lookupElement : elements) {
                result.addElement(lookupElement);
            }

        }
    };

    CompletionProvider<CompletionParameters> packageMethodCompletionProvider = new CompletionProvider<CompletionParameters>() {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {

            PsiElement node = parameters.getOriginalPosition();
            if ( node == null || node.getParent() == null || ! (node.getParent() instanceof GoSelectorExpression) )
                return;

            GoSelectorExpression expression = (GoSelectorExpression) node.getParent();

            if ( expression.getExpressionContext() == null )
                return;

            GoPsiExpression expressionContext = expression.getExpressionContext();

            GoExpressionTypeResolver expressionTypeResolver = new GoExpressionTypeResolver(expressionContext);

            PsiScopesUtil.treeWalkUp(expressionTypeResolver, expressionContext, expressionContext.getContainingFile());

            for (PsiNamedElement psiElement : expressionTypeResolver.getFunctions()) {
                result.addElement(LookupElementBuilder.create(psiElement));
            }
        }
    };

    CompletionProvider<CompletionParameters> debuggingCompletionProvider = new CompletionProvider<CompletionParameters>() {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {
            String originalFile = DebugUtil.psiToString(parameters.getOriginalFile(), false);
            String currentFile = DebugUtil.psiToString(parameters.getPosition().getContainingFile(), false);

            int a = 10;
        }
    };

    public GoCompletionContributor() {

        extend(CompletionType.BASIC,
                psiElement(),
                debuggingCompletionProvider);

        extend(CompletionType.BASIC,
                psiElement(PsiElement.class).withParent(psiElement(GoPackageDeclaration.class).withFirstNonWhitespaceChild(psiElement(PsiErrorElement.class))),
                packageCompletionProvider);

        extend(CompletionType.BASIC,
                psiElement().withParent(psiElement(PsiErrorElement.class).withParent(psiElement(GoFile.class).withFirstNonWhitespaceChild(psiElement(GoPackageDeclaration.class)))),
                importCompletionProvider);

        extend(CompletionType.BASIC,
                psiElement().withElementType(GoTokenTypes.litSTRING).withParent(psiElement(GoImportSpec.class)),
                importPathCompletionProvider);

//        extend(CompletionType.BASIC,
//                psiElement().withElementType(GoTokenTypes.mIDENT)/*.withParent(psiElement(GoSelectorExpression.class))*/,
//                packageMethodCompletionProvider);

//        extend(CompletionType.BASIC,
//                psiElement().withParent(or(psiElement(GoTypeName.class), psiElement().withParent(GoTypeName.class))),
//                typeNameCompletionProvider);
//
//        extend(
//                CompletionType.BASIC,
//                psiElement().withParent(GoFile.class),
//                new CompletionProvider<CompletionParameters>() {
//                    @Override
//                    protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {
//
//                        final PsiElement position = parameters.getPosition();
//                        final PsiElement reference = position.getParent();
//
//                        if (reference == null) return;
//
//                        if (reference.getParent() instanceof GoFile) {
//                            result.addElement(LookupElementBuilder.create("package"));
//                        }
//                    }
//                });
    }

    @Override
    public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
        super.fillCompletionVariants(parameters, result);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public void beforeCompletion(@NotNull CompletionInitializationContext context) {
//        if (context.getCompletionType() != CompletionType.SMART) return;
//    PsiElement lastElement = context.getFile().findElementAt(context.getStartOffset() - 1);
//    if (lastElement != null && lastElement.getText().equals("(")) {
//      final PsiElement parent = lastElement.getParent();
//      if (parent instanceof GrTypeCastExpression) {
//        context.setFileCopyPatcher(new FileCopyPatcher() {
//            @Override
//            public void patchFileCopy(@NotNull PsiFile fileCopy, @NotNull Document document, @NotNull OffsetMap map) {
//
//            }
//        });
//      }
//      else if (parent instanceof GrParenthesizedExpression) {
//        context.setFileCopyPatcher(new DummyIdentifierPatcher("xxx)yyy ")); // to handle type cast
//      }
//    }
    }
}
