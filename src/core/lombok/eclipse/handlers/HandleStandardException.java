/*
 * Copyright (C) 2010-2020 The Project Lombok Authors.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.eclipse.handlers;

import lombok.AccessLevel;
import lombok.ConfigurationKeys;
import lombok.StandardException;
import lombok.core.AST.Kind;
import lombok.core.AnnotationValues;
import lombok.eclipse.Eclipse;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseNode;
import lombok.eclipse.handlers.EclipseHandlerUtil.*;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.mangosdk.spi.ProviderFor;

import java.lang.reflect.Modifier;
import java.util.*;

import static lombok.core.handlers.HandlerUtil.handleFlagUsage;
import static lombok.eclipse.Eclipse.ECLIPSE_DO_NOT_TOUCH_FLAG;
import static lombok.eclipse.Eclipse.pos;
import static lombok.eclipse.handlers.EclipseHandlerUtil.*;

@ProviderFor(EclipseAnnotationHandler.class)
public class HandleStandardException extends EclipseAnnotationHandler<StandardException> {
	private static final String NAME = StandardException.class.getSimpleName();

	@Override
	public void handle(AnnotationValues<StandardException> annotation, Annotation ast, EclipseNode annotationNode) {
		handleFlagUsage(annotationNode, ConfigurationKeys.STANDARD_EXCEPTION_FLAG_USAGE, "@StandardException");

		EclipseNode typeNode = annotationNode.up();
		if (!checkLegality(typeNode, annotationNode, NAME)) return;
		StandardException ann = annotation.getInstance();
		SuperField messageField = new SuperField("message", new SingleTypeReference("String".toCharArray(), pos(typeNode.get())));
		SuperField causeField = new SuperField("cause", new SingleTypeReference("Throwable".toCharArray(), pos(typeNode.get())));
		List<Annotation> onConstructor = unboxAndRemoveAnnotationParameter(ast, "onConstructor", "@RequiredArgsConstructor(onConstructor", annotationNode);

		boolean skip = true;
		generateConstructor(
				typeNode, AccessLevel.PUBLIC, Collections.<SuperField>emptyList(), skip,
				onConstructor, annotationNode);
		generateConstructor(
				typeNode, AccessLevel.PUBLIC, Collections.singletonList(messageField), skip,
				onConstructor, annotationNode);
		generateConstructor(
				typeNode, AccessLevel.PUBLIC, Collections.singletonList(causeField), skip,
				onConstructor, annotationNode);
		generateConstructor(
			typeNode, AccessLevel.PUBLIC, Arrays.asList(messageField, causeField), skip,
			onConstructor, annotationNode);
	}

	private static List<EclipseNode> findFields(EclipseNode typeNode, boolean nullMarked) {
		List<EclipseNode> fields = new ArrayList<EclipseNode>();
		for (EclipseNode child : typeNode.down()) {
			if (child.getKind() != Kind.FIELD) continue;
			FieldDeclaration fieldDecl = (FieldDeclaration) child.get();
			if (!filterField(fieldDecl)) continue;
			boolean isFinal = (fieldDecl.modifiers & ClassFileConstants.AccFinal) != 0;
			boolean isNonNull = nullMarked && hasNonNullAnnotations(child);
			if ((isFinal || isNonNull) && fieldDecl.initialization == null) fields.add(child);
		}
		return fields;
	}
	
	static boolean checkLegality(EclipseNode typeNode, EclipseNode errorNode, String name) {
		TypeDeclaration typeDecl = null;
		if (typeNode.get() instanceof TypeDeclaration) typeDecl = (TypeDeclaration) typeNode.get();
		int modifiers = typeDecl == null ? 0 : typeDecl.modifiers;
		boolean notAClass = (modifiers & (ClassFileConstants.AccInterface | ClassFileConstants.AccAnnotation)) != 0;
		
		if (typeDecl == null || notAClass) {
			errorNode.addError(name + " is only supported on a class or an enum.");
			return false;
		}
		
		return true;
	}

	public void generateConstructor(
		EclipseNode typeNode, AccessLevel level, List<SuperField> fieldsToParam, boolean skipIfConstructorExists,
		List<Annotation> onConstructor, EclipseNode sourceNode) {
		
		generate(typeNode, level, fieldsToParam, skipIfConstructorExists, onConstructor, sourceNode);
	}
	
	public void generate(
			EclipseNode typeNode, AccessLevel level, List<SuperField> fieldsToParam, boolean skipIfConstructorExists,
			List<Annotation> onConstructor, EclipseNode sourceNode) {
		if (!(skipIfConstructorExists
				&& constructorExists(typeNode, fieldsToParam) != MemberExistsResult.NOT_EXISTS)) {
			ConstructorDeclaration constr = createConstructor(level, typeNode, fieldsToParam, sourceNode, onConstructor);
			injectMethod(typeNode, constr);
		}
	}

	/**
	 * Checks if there is a (non-default) constructor. In case of multiple constructors (overloading), only
	 * the first constructor decides if EXISTS_BY_USER or EXISTS_BY_LOMBOK is returned.
	 *
	 * @param node Any node that represents the Type (TypeDeclaration) to look in, or any child node thereof.
	 */
	public static MemberExistsResult constructorExists(EclipseNode node, List<SuperField> fields) {
		node = upToTypeNode(node);
		if (node != null && node.get() instanceof TypeDeclaration) {
			TypeDeclaration typeDecl = (TypeDeclaration)node.get();
			if (typeDecl.methods != null) outer: for (AbstractMethodDeclaration def : typeDecl.methods) {
				if (def instanceof ConstructorDeclaration) {
					if (isTolerate(node, def)) continue;

					SuperField[] superFields = fields.toArray(new SuperField[0]);
					if (def.arguments == null) {
						if (superFields.length != 0)
							continue;
					} else if (def.arguments.length != superFields.length) {
						continue;
					} else {
						for (int i = 0; i < superFields.length; i++) {
							String fieldTypeName = Eclipse.toQualifiedName(superFields[i].type.getTypeName());
							String argTypeName = Eclipse.toQualifiedName(def.arguments[i].type.getTypeName());

							if (!typeNamesMatch(node, fieldTypeName, argTypeName))
								continue outer;
						}
					}

					return getGeneratedBy(def) == null ? MemberExistsResult.EXISTS_BY_USER : MemberExistsResult.EXISTS_BY_LOMBOK;
				}
			}
		}

		return MemberExistsResult.NOT_EXISTS;
	}

	private static boolean typeNamesMatch(EclipseNode node, String a, String b) {
		boolean isFqn = node.getImportListAsTypeResolver().typeMatches(node, a, b);
		boolean reverseIsFqn = node.getImportListAsTypeResolver().typeMatches(node, b, a);
		return isFqn || reverseIsFqn;
	}

	private static final char[][] JAVA_BEANS_CONSTRUCTORPROPERTIES = new char[][] { "java".toCharArray(), "beans".toCharArray(), "ConstructorProperties".toCharArray() };
	public static Annotation[] createConstructorProperties(ASTNode source, Collection<SuperField> fields) {
		if (fields.isEmpty()) return null;
		
		int pS = source.sourceStart, pE = source.sourceEnd;
		long p = (long) pS << 32 | pE;
		long[] poss = new long[3];
		Arrays.fill(poss, p);
		QualifiedTypeReference constructorPropertiesType = new QualifiedTypeReference(JAVA_BEANS_CONSTRUCTORPROPERTIES, poss);
		setGeneratedBy(constructorPropertiesType, source);
		SingleMemberAnnotation ann = new SingleMemberAnnotation(constructorPropertiesType, pS);
		ann.declarationSourceEnd = pE;
		
		ArrayInitializer fieldNames = new ArrayInitializer();
		fieldNames.sourceStart = pS;
		fieldNames.sourceEnd = pE;
		fieldNames.expressions = new Expression[fields.size()];
		
		int ctr = 0;
		for (SuperField field : fields) {
			char[] fieldName = field.name.toCharArray();
			fieldNames.expressions[ctr] = new StringLiteral(fieldName, pS, pE, 0);
			setGeneratedBy(fieldNames.expressions[ctr], source);
			ctr++;
		}
		
		ann.memberValue = fieldNames;
		setGeneratedBy(ann, source);
		setGeneratedBy(ann.memberValue, source);
		return new Annotation[] { ann };
	}

	@SuppressWarnings("deprecation") public static ConstructorDeclaration createConstructor(
		AccessLevel level, EclipseNode type, Collection<SuperField> fieldsToParam, EclipseNode sourceNode,
		List<Annotation> onConstructor) {

		ASTNode source = sourceNode.get();
		TypeDeclaration typeDeclaration = ((TypeDeclaration) type.get());

		boolean isEnum = (((TypeDeclaration) type.get()).modifiers & ClassFileConstants.AccEnum) != 0;
		
		if (isEnum) level = AccessLevel.PRIVATE;

		boolean addConstructorProperties;
		if (fieldsToParam.isEmpty()) {
			addConstructorProperties = false;
		} else {
			Boolean v = type.getAst().readConfiguration(ConfigurationKeys.ANY_CONSTRUCTOR_ADD_CONSTRUCTOR_PROPERTIES);
			addConstructorProperties = v != null ? v.booleanValue() :
				Boolean.FALSE.equals(type.getAst().readConfiguration(ConfigurationKeys.ANY_CONSTRUCTOR_SUPPRESS_CONSTRUCTOR_PROPERTIES));
		}
		
		ConstructorDeclaration constructor = new ConstructorDeclaration(((CompilationUnitDeclaration) type.top().get()).compilationResult);
		
		constructor.modifiers = toEclipseModifier(level);
		constructor.selector = typeDeclaration.name;
		constructor.thrownExceptions = null;
		constructor.typeParameters = null;
		constructor.bits |= ECLIPSE_DO_NOT_TOUCH_FLAG;
		constructor.bodyStart = constructor.declarationSourceStart = constructor.sourceStart = source.sourceStart;
		constructor.bodyEnd = constructor.declarationSourceEnd = constructor.sourceEnd = source.sourceEnd;
		constructor.arguments = null;
		
		List<Argument> params = new ArrayList<Argument>();
		List<Expression> superArgs = new ArrayList<Expression>();

		for (SuperField fieldNode : fieldsToParam) {
			char[] fieldName = fieldNode.name.toCharArray();
			long fieldPos = (((long) type.get().sourceStart) << 32) | type.get().sourceEnd;
			Argument parameter = new Argument(fieldName, fieldPos, copyType(fieldNode.type, source), Modifier.FINAL);
			params.add(parameter);
			superArgs.add(new SingleNameReference(fieldName, 0));
		}

		// Super constructor call
		constructor.constructorCall = new ExplicitConstructorCall(ExplicitConstructorCall.Super);
		constructor.constructorCall.arguments = superArgs.toArray(new Expression[0]);
		constructor.constructorCall.sourceStart = source.sourceStart;
		constructor.constructorCall.sourceEnd = source.sourceEnd;

		constructor.arguments = params.isEmpty() ? null : params.toArray(new Argument[0]);
		
		/* Generate annotations that must  be put on the generated method, and attach them. */ {
			Annotation[] constructorProperties = null;
			if (addConstructorProperties && !isLocalType(type)) constructorProperties = createConstructorProperties(source, fieldsToParam);
			
			constructor.annotations = copyAnnotations(source,
				onConstructor.toArray(new Annotation[0]),
				constructorProperties);
		}
		
		constructor.traverse(new SetGeneratedByVisitor(source), typeDeclaration.scope);
		return constructor;
	}

	public static boolean isLocalType(EclipseNode type) {
		Kind kind = type.up().getKind();
		if (kind == Kind.COMPILATION_UNIT) return false;
		if (kind == Kind.TYPE) return isLocalType(type.up());
		return true;
	}

	private static class SuperField {
		private final String name;
		private final TypeReference type;

		private SuperField(String name, TypeReference type) {
			this.name = name;
			this.type = type;
		}
	}
}
