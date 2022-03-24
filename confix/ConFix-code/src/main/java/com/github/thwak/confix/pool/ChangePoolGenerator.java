package com.github.thwak.confix.pool;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays ;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import com.github.thwak.confix.tree.Node;
import com.github.thwak.confix.util.IOUtils;

import script.ScriptGenerator;
import script.model.EditOp;
import script.model.EditScript;
import tree.Tree;
import tree.TreeBuilder;

public class ChangePoolGenerator {
	public ChangePool pool;
	public List<Integer> changeList = new ArrayList<Integer>();
	public List<String> postfixList = new ArrayList<String>(Arrays.asList("++", "--"));
	public List<String> infixList = new ArrayList<String>(Arrays.asList("==", "!=", "<", "<=", ">", ">=", "&&", "||", "+", "-", "*", "%", "/", "+=", "-=")) ;
	public List<String> prefixList = new ArrayList<String>(Arrays.asList("!", "++", "--"));
	public List<String> fixList;

	public ChangePoolGenerator() {
		pool = new ChangePool();
	}

	public void collect(Script script) {
		// System.out.println(script.toString()) ;
		// TODO: hard coding된 fix list들을 제거하고 실행해보기
		Integer newChangeHash;
		Change revChange;

		for (Change c : script.changes.keySet()) {
			ContextIdentifier identifier = pool.getIdentifier();
			List<EditOp> ops = script.changes.get(c);
			for(EditOp op : ops) {
				Context context = identifier.getContext(op);
				updateMethod(c);
				pool.add(context, c);
			}
		}
	}

	private void updateMethod(Change c) {
		Node n = c.node;
		while (n.parent != null && n.parent.type != ASTNode.METHOD_DECLARATION) {
			n = n.parent;
		}
		StringBuffer sb = new StringBuffer(c.id);
		if (n.parent == null)
			sb.append(":");
		else {
			sb.append(":");
			if (n.parent.astNode != null) {
				MethodDeclaration md = (MethodDeclaration) n.parent.astNode;
				sb.append(md.getName().toString());
			}
			sb.append(":");
			sb.append(n.parent.startPos);
		}
		c.id = sb.toString();
	}

	public void collect(List<File> bugFiles, List<File> cleanFiles, String[] compileClassPathEntries, String[] sourcePath) {

		System.out.println("[Debug.log] line 72 of ChangePoolGenerator.java: bugFiles size: "+bugFiles.size());
		System.out.println("[Debug.log] line 73 of ChangePoolGenerator.java: cleanFiles size: "+cleanFiles.size());
		try {
			for (int i = 0; i < bugFiles.size(); i++) {

				if(bugFiles.get(i) == null || cleanFiles.get(i) == null)
					continue;

				System.out.println("[Debug.log] line 80 of ChangePoolGenerator.java: buggy file : "+bugFiles.get(i).getName());
				System.out.println("[Debug.log] line 81 of ChangePoolGenerator.java: clean file : "+cleanFiles.get(i).getName());

				// Generate EditScript from before and after.
				String oldCode = IOUtils.readFile(bugFiles.get(i));
				String newCode = IOUtils.readFile(cleanFiles.get(i));

				// System.out.println("First letter of old code: "+oldCode.charAt(0));
				// System.out.println("First letter of new code: "+newCode.charAt(0));
				//String[] srcPathList = new String [1];
				//srcPathList[0] = sourcePathEntries;

				Tree before = TreeBuilder.buildTreeFromFile(bugFiles.get(i),compileClassPathEntries,sourcePath);
				Tree after = TreeBuilder.buildTreeFromFile(cleanFiles.get(i),compileClassPathEntries,sourcePath);


				if(before == null || after == null)
					System.out.println("Tree is null");

				EditScript editScript = ScriptGenerator.generateScript(before, after);
				// Convert EditScript to Script.
				editScript = Converter.filter(editScript);
				EditScript combined = Converter.combineEditOps(editScript);
				Script script = Converter.convert("0", combined, oldCode, newCode);
				collect(script);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

