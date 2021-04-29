package com.github.thwak.confix.diff;

import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

public class StatementVisitor extends ASTVisitor {
    private final Stack<Node> stack;
    private final List<Node> statements;

    public StatementVisitor(Node root) {
        stack = new Stack<>();
        stack.push(root);
        statements = new ArrayList<Node>();
    }

    public List<Node> statements() {
        return Collections.unmodifiableList(statements);
    }

    @Override
    public boolean preVisit2(ASTNode node) {
        String value = DiffUtils.getValue(node);
        String label = node.getClass().getSimpleName() + Node.DELIM + value;
        Node n = new Node(label, node, value);
        if (!stack.isEmpty()) {
            stack.peek().addChild(n);
        }

        if (node instanceof Statement
                && !(node instanceof Block)) {
            statements.add(n);
        } else if (node instanceof Name) {
            return false;
        }
        stack.push(n);

        return super.preVisit2(node);
    }

    @Override
    public void postVisit(ASTNode node) {
        if (!(node instanceof Name)) {
            stack.pop();
        }
        super.postVisit(node);
    }
}