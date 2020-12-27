/*
 * Copyright (C) 2010-2019 The Project Lombok Authors.
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
package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import lombok.*;
import lombok.core.AST.Kind;
import lombok.core.AnnotationValues;
import lombok.delombok.LombokOptionsFactory;
import lombok.javac.Javac;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import lombok.javac.handlers.JavacHandlerUtil.*;
import org.mangosdk.spi.ProviderFor;

import static lombok.core.handlers.HandlerUtil.handleFlagUsage;
import static lombok.javac.Javac.CTC_VOID;
import static lombok.javac.handlers.JavacHandlerUtil.*;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleStandardException extends JavacAnnotationHandler<StandardException> {
	private static final String NAME = StandardException.class.getSimpleName();

	@Override
	public void handle(AnnotationValues<StandardException> annotation, JCAnnotation ast, JavacNode annotationNode) {
		handleFlagUsage(annotationNode, ConfigurationKeys.STANDARD_EXCEPTION_FLAG_USAGE, "@StandardException");
		deleteAnnotationIfNeccessary(annotationNode, StandardException.class);
		JavacNode typeNode = annotationNode.up();
		if (!checkLegality(typeNode, annotationNode, NAME)) return;
		List<JCAnnotation> onConstructor = unboxAndRemoveAnnotationParameter(ast, "onConstructor", "@NoArgsConstructor(onConstructor", annotationNode);
		StandardException ann = annotation.getInstance();

		SuperField messageField = new SuperField("message", typeNode.getSymbolTable().stringType);
		SuperField causeField = new SuperField("cause", typeNode.getSymbolTable().throwableType);

		SkipIfConstructorExists skip = SkipIfConstructorExists.YES;
		generateConstructor(typeNode, AccessLevel.PUBLIC, onConstructor, List.<SuperField>nil(), skip, annotationNode);
		generateConstructor(typeNode, AccessLevel.PUBLIC, onConstructor, List.of(messageField), skip, annotationNode);
		generateConstructor(typeNode, AccessLevel.PUBLIC, onConstructor, List.of(causeField), skip, annotationNode);
		generateConstructor(typeNode, AccessLevel.PUBLIC, onConstructor, List.of(messageField, causeField), skip, annotationNode);
	}

	public static List<JavacNode> findRequiredFields(JavacNode typeNode) {
		return findFields(typeNode, true);
	}

	public static List<JavacNode> findFields(JavacNode typeNode, boolean nullMarked) {
		ListBuffer<JavacNode> fields = new ListBuffer<JavacNode>();
		for (JavacNode child : typeNode.down()) {
			if (child.getKind() != Kind.FIELD) continue;
			JCVariableDecl fieldDecl = (JCVariableDecl) child.get();
			//Skip fields that start with $
			if (fieldDecl.name.toString().startsWith("$")) continue;
			long fieldFlags = fieldDecl.mods.flags;
			//Skip static fields.
			if ((fieldFlags & Flags.STATIC) != 0) continue;
			boolean isFinal = (fieldFlags & Flags.FINAL) != 0;
			boolean isNonNull = nullMarked && hasNonNullAnnotations(child);
			if ((isFinal || isNonNull) && fieldDecl.init == null) fields.append(child);
		}
		return fields.toList();
	}

	public static List<JavacNode> findAllFields(JavacNode typeNode) {
		return findAllFields(typeNode, false);
	}
	
	public static List<JavacNode> findAllFields(JavacNode typeNode, boolean evenFinalInitialized) {
		ListBuffer<JavacNode> fields = new ListBuffer<JavacNode>();
		for (JavacNode child : typeNode.down()) {
			if (child.getKind() != Kind.FIELD) continue;
			JCVariableDecl fieldDecl = (JCVariableDecl) child.get();
			//Skip fields that start with $
			if (fieldDecl.name.toString().startsWith("$")) continue;
			long fieldFlags = fieldDecl.mods.flags;
			//Skip static fields.
			if ((fieldFlags & Flags.STATIC) != 0) continue;
			//Skip initialized final fields
			boolean isFinal = (fieldFlags & Flags.FINAL) != 0;
			if (evenFinalInitialized || !isFinal || fieldDecl.init == null) fields.append(child);
		}
		return fields.toList();
	}
	
	public static boolean checkLegality(JavacNode typeNode, JavacNode errorNode, String name) {
		JCClassDecl typeDecl = null;
		if (typeNode.get() instanceof JCClassDecl) typeDecl = (JCClassDecl) typeNode.get();
		long modifiers = typeDecl == null ? 0 : typeDecl.mods.flags;
		boolean notAClass = (modifiers & (Flags.INTERFACE | Flags.ANNOTATION)) != 0;
		
		if (typeDecl == null || notAClass) {
			errorNode.addError(name + " is only supported on a class or an enum.");
			return false;
		}
		
		return true;
	}
	
	public enum SkipIfConstructorExists {
		YES, NO, I_AM_BUILDER;
	}

	public void generateConstructor(JavacNode typeNode, AccessLevel level, List<JCAnnotation> onConstructor,
									List<SuperField> fields, SkipIfConstructorExists skipIfConstructorExists, JavacNode source) {
		generate(typeNode, level, onConstructor, fields, skipIfConstructorExists, source);
	}

	private void generate(JavacNode typeNode, AccessLevel level, List<JCAnnotation> onConstructor, List<SuperField> fields,
						  SkipIfConstructorExists skipIfConstructorExists, JavacNode source) {
		ListBuffer<Type> argTypes = new ListBuffer<Type>();
		for (SuperField field : fields) {
			Type mirror = field.type;
			if (mirror == null) {
				argTypes = null;
				break;
			}
			argTypes.append(mirror);
		}
		List<Type> argTypes_ = argTypes == null ? null : argTypes.toList();

		if (!(skipIfConstructorExists != SkipIfConstructorExists.NO && constructorExists(typeNode) != MemberExistsResult.NOT_EXISTS)) {
			JCMethodDecl constr = createConstructor(level, onConstructor, typeNode, fields, source);
			injectMethod(typeNode, constr, argTypes_, Javac.createVoidType(typeNode.getSymbolTable(), CTC_VOID));
		}
	}

	private static boolean noArgsConstructorExists(JavacNode node) {
		node = upToTypeNode(node);
		
		if (node != null && node.get() instanceof JCClassDecl) {
			for (JCTree def : ((JCClassDecl) node.get()).defs) {
				if (def instanceof JCMethodDecl) {
					JCMethodDecl md = (JCMethodDecl) def;
					if (md.name.contentEquals("<init>") && md.params.size() == 0) return true;
				}
			}
		}
		
		for (JavacNode child : node.down()) {
			if (annotationTypeMatches(NoArgsConstructor.class, child)) return true;
			if (annotationTypeMatches(RequiredArgsConstructor.class, child) && findRequiredFields(node).isEmpty()) return true;
			if (annotationTypeMatches(AllArgsConstructor.class, child) && findAllFields(node).isEmpty()) return true;
		}
		
		return false;
	}
	
	public static void addConstructorProperties(JCModifiers mods, JavacNode node, List<SuperField> fields) {
		if (fields.isEmpty()) return;
		JavacTreeMaker maker = node.getTreeMaker();
		JCExpression constructorPropertiesType = chainDots(node, "java", "beans", "ConstructorProperties");
		ListBuffer<JCExpression> fieldNames = new ListBuffer<JCExpression>();
		for (SuperField field : fields) {
			Name fieldName = node.toName(field.name);
			fieldNames.append(maker.Literal(fieldName.toString()));
		}
		JCExpression fieldNamesArray = maker.NewArray(null, List.<JCExpression>nil(), fieldNames.toList());
		JCAnnotation annotation = maker.Annotation(constructorPropertiesType, List.of(fieldNamesArray));
		mods.annotations = mods.annotations.append(annotation);
	}
	
	@SuppressWarnings("deprecation") public static JCMethodDecl createConstructor(AccessLevel level, List<JCAnnotation> onConstructor,
																				  JavacNode typeNode, List<SuperField> fieldsToParam, JavacNode source) {
		JavacTreeMaker maker = typeNode.getTreeMaker();
		
		boolean isEnum = (((JCClassDecl) typeNode.get()).mods.flags & Flags.ENUM) != 0;
		if (isEnum) level = AccessLevel.PRIVATE;
		
		boolean addConstructorProperties;

		if (fieldsToParam.isEmpty()) {
			addConstructorProperties = false;
		} else {
			Boolean v = typeNode.getAst().readConfiguration(ConfigurationKeys.ANY_CONSTRUCTOR_ADD_CONSTRUCTOR_PROPERTIES);
			addConstructorProperties = v != null ? v.booleanValue() :
				Boolean.FALSE.equals(typeNode.getAst().readConfiguration(ConfigurationKeys.ANY_CONSTRUCTOR_SUPPRESS_CONSTRUCTOR_PROPERTIES));
		}
		

		ListBuffer<JCVariableDecl> params = new ListBuffer<JCVariableDecl>();
		ListBuffer<JCExpression> superArgs = new ListBuffer<JCExpression>();
		
		for (SuperField fieldNode : fieldsToParam) {
			Name fieldName = source.toName(fieldNode.name);
			long flags = JavacHandlerUtil.addFinalIfNeeded(Flags.PARAMETER, typeNode.getContext());
			JCExpression pType = maker.getUnderlyingTreeMaker().Ident(fieldNode.type.tsym);
			JCVariableDecl param = maker.VarDef(maker.Modifiers(flags), fieldName, pType, null);
			params.append(param);
//			JCFieldAccess thisX = maker.Select(maker.Ident(source.toName("this")), fieldName);
//			JCExpression assign = maker.Assign(thisX, maker.Ident(fieldName));
			superArgs.append(maker.Ident(fieldName));
		}

		ListBuffer<JCStatement> statements = new ListBuffer<JCStatement>();
		JCMethodInvocation callToSuper = maker.Apply(List.<JCExpression>nil(),
				maker.Ident(typeNode.toName("super")),
				superArgs.toList());
		statements.add(maker.Exec(callToSuper));

		JCModifiers mods = maker.Modifiers(toJavacModifier(level), List.<JCAnnotation>nil());
		if (addConstructorProperties && !isLocalType(typeNode) && LombokOptionsFactory.getDelombokOptions(typeNode.getContext()).getFormatPreferences().generateConstructorProperties()) {
			addConstructorProperties(mods, typeNode, fieldsToParam);
		}
		if (onConstructor != null) mods.annotations = mods.annotations.appendList(copyAnnotations(onConstructor));
		return recursiveSetGeneratedBy(maker.MethodDef(mods, typeNode.toName("<init>"),
			null, List.<JCTypeParameter>nil(), params.toList(), List.<JCExpression>nil(),
			maker.Block(0L, statements.toList()), null), source.get(), typeNode.getContext());
	}

	public static boolean isLocalType(JavacNode type) {
		Kind kind = type.up().getKind();
		if (kind == Kind.COMPILATION_UNIT) return false;
		if (kind == Kind.TYPE) return isLocalType(type.up());
		return true;
	}

	private static class SuperField {
		private final String name;
		private final Type type;

		private SuperField(String name, Type type) {
			this.name = name;
			this.type = type;
		}
	}
}
