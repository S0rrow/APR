package com.github.thwak.confix.patch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.github.thwak.confix.pool.Change;

public class PatchInfo {

	public TargetLocation loc;
	public Change change;
	public String className;
	public Set<String> cMethods;
	public List<RepairAction> repairs;

	public PatchInfo(String targetClass, Change change, TargetLocation loc) {
		this.className = targetClass;
		this.loc = loc;
		this.change = change;
		this.repairs = new ArrayList<>();
		this.cMethods = new HashSet<>();
	}

	public String getConcretize() {
		System.out.println("[Debug.log]: line 27 of PatchInfo, getConcretize()");
		StringBuffer sb = new StringBuffer();
		//DEBUG
		if(cMethods.size() <= 0) return "ERROR:empty_hashset";
		for(String str : cMethods) {
			sb.append(",");
			sb.append(str);
		}
		try {
			System.out.println("[Debug.log] : sb.substring(1) = "+sb.substring(1));
		} catch(Exception e){
			System.out.println("[Debug.log] Exception e = "+e);
		}
		return sb.substring(1);
	}
}
