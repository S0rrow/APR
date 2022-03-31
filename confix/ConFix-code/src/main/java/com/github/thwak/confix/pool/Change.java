package com.github.thwak.confix.pool;

import java.io.Serializable;

import org.eclipse.jdt.core.dom.ASTNode;

import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.tree.TreeUtils;

public class Change implements Serializable {

	private static final long serialVersionUID = -244543033789185124L;
	public static final String DELIM = "$$";
	public static final String INSERT = "insert";
	public static final String DELETE = "delete";
	public static final String MOVE = "move";
	public static final String UPDATE = "update";
	public static final String REPLACE = "replace";
	public String id;
	public String type;
	public Node node;
	public Node location;
	public int hashCode;
	public String hash;
	public transient String hashString;
	public String code;
	public String locationCode;
	public Requirements requirements;

	public Change(String id, String type, Node node, Node location) {
		this.id = id;
		this.type = type;
		this.node = node;
		this.location = location;
		hashString = getHash();
		hash = TreeUtils.computeSHA256Hash(hashString);
		hashCode = hash.hashCode();
		requirements = new Requirements();
	}

	public String getHash() {
		StringBuffer sb = new StringBuffer();
		sb.append(type);
		sb.append(DELIM);
		sb.append(TreeUtils.getHashString(node));
		sb.append(DELIM);
		if (type.equals(REPLACE)) {
			sb.append(TreeUtils.getHashString(location));
		} else if (type.equals(UPDATE)) {
			sb.append(location.label);
		}
		return sb.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Change) {
			Change change = (Change) obj;
			return hash.equals(change.hash);
		} else if (obj instanceof String) {
			String hash = (String) obj;
			return this.hash.equals(hash);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer("Change Information: \n");
		System.out.println("[Debug.log] line 75 of Change.java : change type = "+type);
		switch (type) {
			case Change.INSERT:
			case Change.DELETE:
				sb.append("		type: " + type);
				sb.append("\n		code: " + code);
				break;
			case Change.UPDATE:
				sb.append("			type: " + type);
				sb.append("\n");
				sb.append("			location code: " + locationCode);
				sb.append("\n			to\n");
				sb.append("			location value: " + location.value);
				break;
			case Change.REPLACE:
				sb.append("			type: " + type);
				sb.append("\n");
				sb.append("			location code: " + locationCode);
				sb.append("\n			with\n");
				sb.append("			code: " + code);
				break;
			case Change.MOVE:
				sb.append("			type: " + type);
				sb.append("\n");
				sb.append("			code: " + code);
				sb.append("\n		at ");
				if (node.parent.type == ASTNode.BLOCK && node.parent.parent != null) {
					sb.append("			parent parent label: " + node.parent.parent.label);
				} else {
					sb.append("			parent lablel: " + node.parent.label);
				}
				sb.append("[");
				sb.append("node.desc.id : " + node.desc.id);
				sb.append("]");
				sb.append("\n");
				sb.append("			to ");
				if (location.parent.type == ASTNode.BLOCK && location.parent.parent != null) {
					sb.append(location.parent.parent.label);
				} else {
					sb.append(location.parent.label);
				}
				sb.append("[");
				sb.append("location desc id " + location.desc.id);
				sb.append("]");
				break;
		}
		return sb.toString();
	}
}
