package com.github.thwak.confix.patch;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import com.github.thwak.confix.pool.Change;
import com.github.thwak.confix.tree.CodeVisitor;
import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.tree.Parser;
import com.github.thwak.confix.tree.TreeUtils;

public class Patcher {

	public static int C_NOT_APPLIED = 0;
	public static int C_APPLIED = 1;
	public static int C_NOT_INST = 2;
	public static int C_NO_FIXLOC = 3;
	public static int C_NO_CHANGE = 4;

	public String className;
	public Document doc;
	private CompilationUnit cu;
	private ASTRewrite rewrite;
	private PatchStrategy pStrategy;
	private ConcretizationStrategy cStrategy;
	private FixLocationIdentifier identifier;
	public String[] classPath;
	public String[] sourcePath;

	public Patcher(String className, String source, String[] classPath, String[] sourcePath, PatchStrategy pStrategy,
			ConcretizationStrategy cStrategy) {
		this(className, source, classPath, sourcePath, pStrategy, cStrategy,
				new FixLocationIdentifier(pStrategy, cStrategy));
	}

	public Patcher(String className, String source, String[] classPath, String[] sourcePath, PatchStrategy pStrategy,
			ConcretizationStrategy cStrategy, FixLocationIdentifier identifier) {
		this.className = className;
		doc = new Document(source);
		this.pStrategy = pStrategy;
		this.cStrategy = cStrategy;
		this.identifier = identifier;
		this.classPath = classPath;
		this.sourcePath = sourcePath;
		updateDocInfo();
	}

	private void updateDocInfo() {
		// TODO: need to pass source/class path for multiple changes.
		Parser parser = new Parser(classPath, sourcePath);
		cu = parser.parse(doc.get());
		Node root = new Node("root");
		rewrite = ASTRewrite.create(cu.getAST());
		CodeVisitor visitor = new CodeVisitor(root);
		cu.accept(visitor);
		cStrategy.collectGlobalMaterials(parser, className, root, cu);
		pStrategy.updateLocations(className, root, identifier);
	}

	public String getNewSource() {
		Document copy = new Document(doc.get());
		if (rewrite != null) {
			try {
				TextEdit edit = rewrite.rewriteAST(copy, null);
				edit.apply(copy);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (MalformedTreeException e) {
				e.printStackTrace();
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		}
		rewrite = ASTRewrite.create(cu.getAST());
		return copy.get();
	}

	public CompilationUnit getCompilationUnit() {
		return cu;
	}

	public AST getAST() {
		return cu.getAST();
	}

	public int apply(PatchInfo info) {
		TargetLocation loc = pStrategy.selectLocation();
		Change change = pStrategy.selectChange();
		try {
			int returnCode = apply(loc, change, info);
			return returnCode;
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("[Debug.log] line 112 of Patcher.java: Error occurred while applying change ");
			System.out.println("[Debug.log] line 112 of Patcher.java: change = "+change);
			System.out.println("[Debug.log] line 112 of Patcher.java: to");
			System.out.println("[Debug.log] line 112 of Patcher.java: loc ="+loc);
			return C_NOT_APPLIED;
		}
	}

	public int apply(TargetLocation loc, Change change, PatchInfo info) {
		if (loc == null)
			return C_NO_FIXLOC;
		else if (change == null)
			return C_NO_CHANGE;
		int returnCode = -1;
		switch (change.type) {
			case Change.DELETE:
				//DEBUG
				System.out.println("[Debug.log] line 129 of Patcher.java : switch on case Change.DELETE");
				String editHash = TreeUtils.getTypeHash(change.node);
				String locHash = TreeUtils.getTypeHash(loc.node);
				if (editHash.equals(locHash)) {
					RepairAction repair = new RepairAction(Change.DELETE, loc, getCode(loc.node), "", change);
					delete(loc.node);
					info.repairs.add(repair);
					returnCode = C_APPLIED;
				} else {
					returnCode = C_NOT_APPLIED;
				}
				//DEBUG
				System.out.println("[Debug.log] line 141 of Patcher.java : editHash equals locHash ? : "+editHash.equals(locHash));
				System.out.println("[Debug.log] line 142 of Patcher.java : editHash : "+editHash);
				System.out.println("[Debug.log] line 143 of Patcher.java : locHash : "+locHash);
				System.out.println("[Debug.log] line 144 of Patcher.java : returnCode = "+returnCode);
				break;
			case Change.INSERT:
				if (cStrategy.instCheck(change, loc) && change.node.desc != null) {
					StructuralPropertyDescriptor descriptor = loc.desc;
					ASTNode parent = loc.node.astNode.getParent();
					try {
						if (parent == null || !descriptor.isChildListProperty()
								&& parent.getStructuralProperty(descriptor) != null
								&& parent.getNodeType() != ASTNode.INFIX_EXPRESSION
								&& !(parent.getNodeType() == ASTNode.IF_STATEMENT
										&& descriptor.getId().equals(IfStatement.THEN_STATEMENT_PROPERTY.getId()))
								&& !(parent.getNodeType() == ASTNode.METHOD_INVOCATION
										&& descriptor.getId().equals(MethodInvocation.NAME_PROPERTY.getId())))
							return C_NOT_APPLIED;
					} catch (RuntimeException e) {
						return C_NOT_APPLIED;
					}
					//TE 여기서 instantiate를 통해 실제로 노드를 만들어서 삽입하는 듯
					ASTNode astNode = cStrategy.instantiate(change, loc, info);
					if (astNode == null) {
						System.out.println("[Debug.log] line 165 of Patcher.java : concretization strategy failed to instantiate astNode on designated location");
						return C_NOT_INST;
					}	
					insert(loc, astNode, descriptor);
					RepairAction repair = new RepairAction(Change.INSERT, loc, "", astNode.toString(), change);
					info.repairs.add(repair);
					returnCode = C_APPLIED;
				} else {
					returnCode = C_NOT_APPLIED;
				}
				if(returnCode==C_APPLIED) System.out.println("[Debug.log] line 175 of Patcher.java : return code = C_APPLIED");
				if(returnCode==C_NOT_APPLIED) System.out.println("[Debug.log] line 176 of Patcher.java : return code = C_NOT_APPLIED");
				break;
			case Change.UPDATE:
				System.out.print("[Debug.log] line 179 of Patcher.java : executing UPDATE"); // DEBUG
				System.out.println("instCheck: " + cStrategy.instCheck(change, loc)); // DEBUG
				if (cStrategy.instCheck(change, loc)) {
					System.out.println(" - start instantiating");
					ASTNode astNode = cStrategy.instantiate(change, loc, info);
					if (astNode == null) {
						System.out.println("astNode is null");
						return C_NOT_INST;
					}
					RepairAction repair = new RepairAction(Change.UPDATE, loc, getCode(loc.node), astNode.toString(), change);
					update(loc, astNode); // 2
					info.repairs.add(repair);
					returnCode = C_APPLIED;
					System.out.println("[Debug.log] line 192 of Patcher.java : repair applied on case update"); // DEBUG
				} else {
					returnCode = C_NOT_APPLIED;
					System.out.println("NOT Applied"); // DEBUG
				}
				break;
			case Change.REPLACE:
				//TO DO
				System.out.println("[Debug.log] line 199 of Patcher.java : executing REPLACE"); // DEBUG
				editHash = TreeUtils.getTypeHash(change.node);
				locHash = TreeUtils.getTypeHash(loc.node);
				System.out.println("[Debug.log] line 202 of Patcher.java : 1. Edit Hash: " + editHash);
				System.out.println("[Debug.log] line 203 of Patcher.java : 2. Location Hash: " + locHash);
				if (editHash.equals(locHash) && cStrategy.instCheck(change, loc)) {
					ASTNode astNode = cStrategy.instantiate(change, loc, info);
					System.out.println("[Debug.log] line 210 of Patcher.java : 3. Patch Info(collected methods): " + info.cMethods.size());
					info.cMethods.forEach(method -> {
					System.out.println("[Debug.log] line 212 of Patcher.java : method = " + method);
					});

					if (astNode == null) {
						System.out.println("[Debug.log] line 216 of Patcher.java : result C_NOT_INST due to null ASTNode");
						return C_NOT_INST;
					}
					RepairAction repair = new RepairAction(Change.REPLACE, loc, getCode(loc.node), astNode.toString(),
							change);
					replace(loc, astNode);
					info.repairs.add(repair);
					returnCode = C_APPLIED;
					System.out.println("[Debug.log] line 225 of Patcher.java : repair applied on case replace");
				} else {
					System.out.println("[Debug.log] line 227 of Patcher.java : returncode = C_NOT_APPLIED");
					returnCode = C_NOT_APPLIED;
				}
				break;
		}
		if (returnCode == C_APPLIED) {
			System.out.println("[Debug.log] line 232 of Patcher.java : replace success");
			addImportDecls(change);
		} else if (returnCode == C_NOT_APPLIED) {
			System.out.println("[Debug.log] line 232 of Patcher.java : replace failed, returned with code -1");
		}
		return returnCode;
	}

	private void addImportDecls(Change change) {
		Set<String> importNames = new HashSet<>(change.requirements.imports);
		importNames.addAll(cStrategy.importNames);
		importNames = getImportNames(importNames, cu.imports());
		ListRewrite lrw = rewrite.getListRewrite(cu, CompilationUnit.IMPORTS_PROPERTY);
		AST ast = cu.getAST();
		int counter = 0; // DEBUG
		if(importNames.size() <= 0) System.out.println("[Debug.log] line 248 of Patcher.java : importNames.size() = "+importNames.size());
		for (String importName : importNames) {
			System.out.println("[Debug.log] line 250 of Patcher.java : import name #"+counter+" :: "+importName);
			ImportDeclaration decl = ast.newImportDeclaration();
			decl.setName(ast.newName(importName));
			lrw.insertLast(decl, null);
			counter++;
		}
	}

	private Set<String> getImportNames(Set<String> imports, List<ImportDeclaration> importDecls) {
		HashSet<String> importNames = new HashSet<>(imports);
		int count = 0;
		if(importNames.size() <= 0) System.out.println("[Debug.log] line 261 of Patcher.java : importNames.size() = "+importNames.size());
		for (ImportDeclaration importDecl : importDecls) {
			System.out.println("[Debug.log] line 263 of Patcher.java : getImport name #"+count+" :: "+importDecl.getName());
			importNames.remove(importDecl.getName().getFullyQualifiedName());
			count++;
		}
		return importNames;
	}

	private String getCode(Node node) {
		String code;
		try {
			code = doc.get(node.startPos, node.length);
		} catch (BadLocationException e) {
			code = "";
		}
		return code;
	}

	private void replace(TargetLocation loc, ASTNode astNode) {
		rewrite.replace(loc.node.astNode, astNode, null);
	}

	private void move(TargetLocation loc, TargetLocation moveLoc, StructuralPropertyDescriptor descriptor) {
		ASTNode oldNode = loc.node.astNode;
		if (descriptor.isChildListProperty() && loc.node.isStatement && loc.node.astNode instanceof Expression)
			oldNode = oldNode.getParent();
		ASTNode newNode = ASTNode.copySubtree(moveLoc.node.astNode.getAST(), oldNode);
		delete(loc.node);
		insert(moveLoc, newNode, descriptor);
	}

	private void insert(TargetLocation loc, ASTNode astNode, StructuralPropertyDescriptor descriptor) {
		System.out.println("[Debug.log] line 294 of Patcher.java : inserting astNode on designated location");
		ASTNode parent = loc.node.astNode.getParent();
		if (descriptor.isChildListProperty()) {
			// Handling implicit ExpressionStatement.
			if (loc.node.isStatement && loc.node.astNode instanceof Expression)
				parent = parent.getParent();
			ListRewrite lrw = rewrite.getListRewrite(parent, (ChildListPropertyDescriptor) descriptor);
			if (loc.kind == TargetLocation.INSERT_BEFORE) {
				if (loc.node.astNode.getLocationInParent().equals(descriptor)) {
					lrw.insertBefore(astNode, loc.node.astNode, null);
				} else {
					Node left = loc.node.getLeft();
					if (left != null && left.astNode.getLocationInParent().equals(descriptor)) {
						lrw.insertAfter(astNode, left.astNode, null);
					} else {
						lrw.insertFirst(astNode, null);
					}
				}
			} else if (loc.kind == TargetLocation.INSERT_AFTER) {
				// Default - insert after.
				if (loc.node.astNode.getLocationInParent().equals(descriptor)) {
					lrw.insertAfter(astNode, loc.node.astNode, null);
				} else {
					Node right = loc.node.getRight();
					if (right != null && right.astNode.getLocationInParent().equals(descriptor)) {
						lrw.insertBefore(astNode, right.astNode, null);
					} else {
						lrw.insertFirst(astNode, null);
					}
				}
			} else if (loc.kind == TargetLocation.INSERT_UNDER) {
				lrw = rewrite.getListRewrite(loc.node.astNode, (ChildListPropertyDescriptor) descriptor);
				lrw.insertFirst(astNode, null);
			}
		} else if (parent != null & parent.getNodeType() == ASTNode.INFIX_EXPRESSION) {
			InfixExpression infix = (InfixExpression) parent;
			ListRewrite lrw = rewrite.getListRewrite(parent, InfixExpression.EXTENDED_OPERANDS_PROPERTY);
			if (loc.kind == TargetLocation.INSERT_BEFORE) {
				if (descriptor.getId().equals(InfixExpression.LEFT_OPERAND_PROPERTY.getId())) {
					// n, l, r, e
					lrw.insertFirst(infix.getRightOperand(), null);
					rewrite.set(infix, InfixExpression.RIGHT_OPERAND_PROPERTY, infix.getLeftOperand(), null);
					rewrite.set(infix, InfixExpression.LEFT_OPERAND_PROPERTY, astNode, null);
				} else if (descriptor.getId().equals(InfixExpression.RIGHT_OPERAND_PROPERTY.getId())) {
					// l, n, r, e
					lrw.insertFirst(infix.getRightOperand(), null);
					rewrite.set(infix, InfixExpression.RIGHT_OPERAND_PROPERTY, astNode, null);
				}
			} else if (loc.kind == TargetLocation.INSERT_AFTER) {
				if (descriptor.getId().equals(InfixExpression.LEFT_OPERAND_PROPERTY.getId())) {
					// l, n, r, e
					lrw.insertFirst(infix.getRightOperand(), null);
					rewrite.set(infix, InfixExpression.RIGHT_OPERAND_PROPERTY, astNode, null);
				} else if (descriptor.getId().equals(InfixExpression.RIGHT_OPERAND_PROPERTY.getId())) {
					// l, r, n, e
					lrw.insertFirst(astNode, null);
				}
			}
		} else {
			if (descriptor.getId().equals(IfStatement.THEN_STATEMENT_PROPERTY.getId())
					&& loc.kind == TargetLocation.INSERT_AFTER) {
				descriptor = IfStatement.ELSE_STATEMENT_PROPERTY;
			} else if (descriptor.getId().equals(MethodInvocation.NAME_PROPERTY.getId())
					&& loc.kind == TargetLocation.INSERT_BEFORE) {
				descriptor = MethodInvocation.EXPRESSION_PROPERTY;
			}
			rewrite.set(parent, descriptor, astNode, null);
		}
	}

	private void update(TargetLocation loc, ASTNode astNode) {
		rewrite.replace(loc.node.astNode, astNode, null);
	}

	private void delete(Node node) {
		if (node.isStatement && node.astNode instanceof Expression)
			rewrite.remove(node.astNode.getParent(), null);
		else
			rewrite.remove(node.astNode, null);
	}

}
