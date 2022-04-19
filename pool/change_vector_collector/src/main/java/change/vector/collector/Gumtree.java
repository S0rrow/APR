 package change.vector.collector;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import com.github.gumtreediff.actions.ActionGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.client.Run;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.ITree;

public class Gumtree {

	@SuppressWarnings("deprecation")
	public static void runGumtree(CLIOptions input, ArrayList<BeforeBIC> bbics)
			throws MissingObjectException, IncorrectObjectTypeException, IOException {
		int max_change_length = 100;
		Repository repo = input.repo;
		RevWalk walk = new RevWalk(repo);
		ArrayList<ArrayList<Integer>> gumtree_vectors = new ArrayList<ArrayList<Integer>>();
		ArrayList<BeforeBIC> new_bbics = new ArrayList<BeforeBIC>();
		File file_Y;
		File file_X;
		file_Y = new File(input.outputDir + "Y_" + input.projectName + ".csv");
		file_X = new File(input.outputDir + "X_" + input.projectName + ".csv");

		BufferedWriter writer_Y = Files.newBufferedWriter(Paths.get(file_Y.getAbsolutePath()));
		BufferedWriter writer_X = Files.newBufferedWriter(Paths.get(file_X.getAbsolutePath()));
		CSVPrinter csvprinter_Y = new CSVPrinter(writer_Y, CSVFormat.DEFAULT);
		CSVPrinter csvprinter_X = new CSVPrinter(writer_X, CSVFormat.DEFAULT);

		int spy = 0;
		boolean flag = true;
		int cnt = 0;
		for (BeforeBIC bbic : bbics) {
			//System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@Hello World@@@@@@@@@@@@@@@@@@@@@@@@@@");
			if(flag==true){
				spy++;
			}
			if(flag==false)
				flag=true;

			RevCommit commitBIC = walk.parseCommit(repo.resolve(bbic.shaBIC));
			RevCommit commitBBIC = walk.parseCommit(repo.resolve(bbic.shaBeforeBIC));

			String pathBIC = bbic.pathBIC;
			String pathBBIC = bbic.pathBeforeBIC;

			String srcBlobBIC = Utils.fetchBlob(repo, commitBBIC.getName(), pathBBIC);
			String dstBlobBIC = Utils.fetchBlob(repo, commitBIC.getName(), pathBIC);

			Run.initGenerators();

			// for BIC
			ITree srcBIC = new JdtTreeGenerator().generateFromString(srcBlobBIC).getRoot();
			ITree dstBIC = new JdtTreeGenerator().generateFromString(dstBlobBIC).getRoot();

			Matcher matchBIC = Matchers.getInstance().getMatcher(srcBIC, dstBIC);
			matchBIC.match();

			ActionGenerator gBIC = new ActionGenerator(srcBIC, dstBIC, matchBIC.getMappings());
			gBIC.generate();

			List<Action> actionsBIC = gBIC.getActions();

			ArrayList<Integer> g_vec = new ArrayList<Integer>();
			int check = 0;
			for (Action action : actionsBIC) {
				//System.out.println(check++);
				
				// if regards import, discard
				//if (action.getNode().getType() == 40 || action.getNode().getType() == 26) {
				//	continue;
				//}
				//commented out by TE
				
				if (action.getName().equals("INS")) {
					g_vec.add(action.getNode().getType() + 1);
				} else if (action.getName().equals("DEL")) {
					g_vec.add(action.getNode().getType() + 85 + 1);
				} else if (action.getName().equals("UPD")) {
					g_vec.add(action.getNode().getType() + 85*2 + 1);
				} else if (action.getName().equals("MOV")) {
					g_vec.add(action.getNode().getType() + 85*3 + 1);
				}
				
			}

			// ignore changes greater than max length
			// if (max_change_length < actionsBIC.size()) {
			// 	continue;
			// }
			//commented out by TE
			
			// create AST parser to get lineNum of src and dst codes.
			ASTParser parser_dst = ASTParser.newParser(AST.JLS9);

			parser_dst.setKind(ASTParser.K_COMPILATION_UNIT);

			Hashtable<String, String> pOptions = JavaCore.getOptions();
			pOptions.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_9);
			pOptions.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_9);
			pOptions.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_9);
			pOptions.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, JavaCore.ENABLED);

			parser_dst.setCompilerOptions(pOptions);
			parser_dst.setSource(dstBlobBIC.toCharArray());

			CompilationUnit cu_dst = (CompilationUnit) parser_dst.createAST(null);

			// retrieving the context vector in AST
			ArrayList<Integer> context_vec = new ArrayList<Integer>();
			// create a map to remember context and disregard duplication.
			HashMap<Integer, Boolean> map = new HashMap<Integer, Boolean>();

			// for all the changed nodes:
			int count=0;
			for (Action action : actionsBIC) {
				// if it is INS, look for context in dst
				if (action.getName().equals("INS")) {
					// get the line number of changed node
					int lineNumOfBIC = cu_dst.getLineNumber(action.getNode().getPos());
					// put each changed nodes parent's hashNum without duplication using hashmap
					int parent_hash = action.getNode().getParent().getHash();
					if (map.containsKey(parent_hash)) {
						continue;
					} else {
						map.put(parent_hash, true);
						// get all the descendants of the parent witout duplication using hashmap
						List<ITree> descendants = action.getNode().getParent().getDescendants();
						for (ITree descendant : descendants) {
							if (map.containsKey(descendant.getHash())) {
								continue;
							} else {
								int lineNumOfDescendant = cu_dst.getLineNumber(descendant.getPos());
								if (Math.abs(lineNumOfBIC - lineNumOfDescendant) < 3) {
									map.put(descendant.getHash(), true);
									context_vec.add(descendant.getType() + 85 * 4 + 1);
								}
							}
						}
					}
				}
			}

			// adding the two lists
			g_vec.addAll(context_vec);
			if (g_vec.size() < 1){
				count++;
				//System.out.println("g_vec size zero count :"+count.toString());
				continue;
			}
				
			System.out.println(cnt + ": " + g_vec.size() + "count is: "+count);

			gumtree_vectors.add(g_vec);
			new_bbics.add(bbic);

			csvprinter_Y.printRecord(input.projectName + cnt, bbic.pathBeforeBIC, bbic.pathBIC, bbic.shaBeforeBIC,
					bbic.shaBIC, bbic.pathBeforeBFC, bbic.pathBFC, bbic.shaBeforeBFC, bbic.shaBFC, bbic.key,
					input.projectName, bbic.label);

			csvprinter_Y.flush();

			for (Integer val : g_vec) {
				csvprinter_X.print(val);
			}
			csvprinter_X.println();
			csvprinter_X.flush();

			System.out.println(cnt + "/" + bbics.size());
			cnt++;
			flag=false;
		}

		System.out.println("writing gumvecs complete!");
		//System.out.println("skipped ones are: "+spy);

		csvprinter_Y.close();
		csvprinter_X.close();
		walk.close();
	}

	public static List<CSVRecord> read_record_list(String inPath) throws IOException {
		Reader in = new FileReader(inPath);
		Iterable<CSVRecord> iterable_records = CSVFormat.RFC4180.parse(in);
		Iterator<CSVRecord> iter_record = iterable_records.iterator();
		List<CSVRecord> list_records = new ArrayList<CSVRecord>();

		while (iter_record.hasNext()) {
			list_records.add(iter_record.next());
		}

		return list_records;
	}

	public static void write_record_list(String outPath, List<CSVRecord> list_records, String[] headers)
			throws IOException {
		File file = new File(outPath);
		BufferedWriter bf_writer = Files.newBufferedWriter(Paths.get(file.getAbsolutePath()));
		CSVPrinter csvprinter = new CSVPrinter(bf_writer, CSVFormat.DEFAULT.withHeader(headers));

		for (CSVRecord record : list_records) {
			csvprinter.printRecord(record);
		}

		csvprinter.close();
	}

	public static void write_record_list(String outPath, List<CSVRecord> list_records) throws IOException {
		File file = new File(outPath);
		BufferedWriter bf_writer = Files.newBufferedWriter(Paths.get(file.getAbsolutePath()));
		CSVPrinter csvprinter = new CSVPrinter(bf_writer, CSVFormat.DEFAULT);

		for (CSVRecord record : list_records) {
			csvprinter.printRecord(record);
		}

		csvprinter.close();
	}

	public static void rm_zeros(CLIOptions input) throws IOException {
		final String[] headers = { "index", "path_bbic", "path_bic", "sha_bbic", "sha_bic", "path_bbfc", "path_bfc",
				"sha_bbfc", "sha_bfc", "key", "project", "label" };

		List<CSVRecord> list_x = read_record_list(input.inputDir + "X_" + input.projectName + ".csv");
		List<CSVRecord> list_y = read_record_list(input.inputDir + "Y_" + input.projectName + ".csv");
		List<CSVRecord> list_x_new = new ArrayList<CSVRecord>();
		List<CSVRecord> list_y_new = new ArrayList<CSVRecord>();
		List<CSVRecord> list_log = new ArrayList<CSVRecord>();

		System.out.println(list_x.size());
		System.out.println(list_y.size());

		for (int i = 0; i < list_x.size(); i++) {
			if (list_x.get(i).get(0).equals("")) {
				list_log.add(list_y.get(i));
				continue;
			}
			list_x_new.add(list_x.get(i));
			list_y_new.add(list_y.get(i));
		}
		System.out.println(list_log.size());
		System.out.println(list_x_new.size());
		System.out.println(list_y_new.size());

		write_record_list(input.outputDir + "X_" + input.projectName + ".csv", list_x_new);
		write_record_list(input.outputDir + "Y_" + input.projectName + ".csv", list_y_new);
		write_record_list(input.outputDir + "Log_" + input.projectName + ".csv", list_x_new, headers);

		System.out.println("writing done!");
	}

	public static void rm_dups(CLIOptions arguments) throws IOException {
		List<CSVRecord> list_x = read_record_list(arguments.inputDir + "X_" + arguments.projectName + ".csv");
		List<CSVRecord> list_y = read_record_list(arguments.inputDir + "Y_" + arguments.projectName + ".csv");

		System.out.println(list_x.size());
		System.out.println(list_y.size());

		List<CSVRecord> list_x_new = new ArrayList<CSVRecord>();
		List<CSVRecord> list_y_new = new ArrayList<CSVRecord>();

		Map<String, String> hash = new HashMap<String, String>();

		for (int i = 0; i < list_y.size(); i++) {
			String key = list_y.get(i).get(2) + list_y.get(i).get(4);
			if (!hash.containsKey(key)) {
				list_x_new.add(list_x.get(i));
				list_y_new.add(list_y.get(i));
				hash.put(key, "yo");
			}
		}

		System.out.println(list_x_new.size());
		System.out.println(list_y_new.size());

		write_record_list(arguments.outputDir + "X_" + arguments.projectName + ".csv", list_x_new);
		write_record_list(arguments.outputDir + "Y_" + arguments.projectName + ".csv", list_y_new);

		System.out.println("rm_dups complete!");

	}

	public static void rm_dups_CnB(CLIOptions arguments) throws IOException {
		String header[] = new String[501];
		header[0] = "";
		for (int i = 1; i < 501; i++) {
			header[i] = "f" + i;
		}

		List<CSVRecord> list_x_buggy = read_record_list(
				arguments.inputDir + "X_buggy_" + arguments.projectName + "_encoded.csv");
		List<CSVRecord> list_y_buggy = read_record_list(
				arguments.inputDir + "Y_buggy_" + arguments.projectName + ".csv");
		List<CSVRecord> list_x_clean = read_record_list(
				arguments.inputDir + "X_clean_" + arguments.projectName + "_encoded.csv");
		List<CSVRecord> list_y_clean = read_record_list(
				arguments.inputDir + "Y_clean_" + arguments.projectName + ".csv");

		List<CSVRecord> list_x_buggy_new = new ArrayList<CSVRecord>();
		List<CSVRecord> list_y_buggy_new = new ArrayList<CSVRecord>();
		List<CSVRecord> list_x_clean_new = new ArrayList<CSVRecord>();
		List<CSVRecord> list_y_clean_new = new ArrayList<CSVRecord>();

		System.out.println(list_x_buggy.size());
		System.out.println(list_y_buggy.size());
		System.out.println(list_x_clean.size());
		System.out.println(list_y_clean.size());

		Map<String, String> buggy_hash = new HashMap<String, String>();
		Map<String, String> clean_hash = new HashMap<String, String>();

		for (int i = 0; i < list_y_buggy.size(); i++) {
			// 2 path_bic + 4 sha_bic
			String key = list_y_buggy.get(i).get(2) + list_y_buggy.get(i).get(4);
			if (!buggy_hash.containsKey(key)) {
				list_x_buggy_new.add(list_x_buggy.get(i));
				list_y_buggy_new.add(list_y_buggy.get(i));
				buggy_hash.put(key, "yo");
			}
		}

		for (int i = 0; i < list_y_clean.size(); i++) {
			String key = list_y_clean.get(i).get(2) + list_y_clean.get(i).get(4);
			if (!clean_hash.containsKey(key)) {
				list_x_clean_new.add(list_x_clean.get(i));
				list_y_clean_new.add(list_y_clean.get(i));
				clean_hash.put(key, "yo");
			}
		}

		System.out.println(list_x_buggy_new.size());
		System.out.println(list_y_buggy_new.size());
		System.out.println(list_x_clean_new.size());
		System.out.println(list_y_clean_new.size());

		write_record_list(arguments.outputDir + "X_buggy_" + arguments.projectName + "_encoded.csv", list_x_buggy_new,
				header);
		write_record_list(arguments.outputDir + "Y_buggy_" + arguments.projectName + ".csv", list_y_buggy_new);
		write_record_list(arguments.outputDir + "X_clean_" + arguments.projectName + "_encoded.csv", list_x_clean_new,
				header);
		write_record_list(arguments.outputDir + "Y_clean_" + arguments.projectName + ".csv", list_y_clean_new);

		System.out.println("rm_dups_CnB complete!");
	}

	public static void splitBuggyNClean(CLIOptions arguments) throws IOException {
		List<CSVRecord> list_x = read_record_list(arguments.inputDir + "X_" + arguments.projectName + ".csv");
		List<CSVRecord> list_y = read_record_list(arguments.inputDir + "Y_" + arguments.projectName + ".csv");
		List<CSVRecord> list_x_buggy = new ArrayList<CSVRecord>();
		List<CSVRecord> list_y_buggy = new ArrayList<CSVRecord>();
		List<CSVRecord> list_x_clean = new ArrayList<CSVRecord>();
		List<CSVRecord> list_y_clean = new ArrayList<CSVRecord>();

		System.out.println(list_x.size());
		System.out.println(list_y.size());

		for (int i = 0; i < list_x.size(); i++) {
			if (list_y.get(i).get(11).equals("1")) {
				list_x_buggy.add(list_x.get(i));
				list_y_buggy.add(list_y.get(i));
			} else if (list_y.get(i).get(11).equals("0")) {
				list_x_clean.add(list_x.get(i));
				list_y_clean.add(list_y.get(i));
			}
		}
		System.out.println(list_x_buggy.size());
		System.out.println(list_y_buggy.size());
		System.out.println(list_x_clean.size());
		System.out.println(list_y_clean.size());

		write_record_list(arguments.outputDir + "X_buggy_" + arguments.projectName + ".csv", list_x_buggy);
		write_record_list(arguments.outputDir + "Y_buggy_" + arguments.projectName + ".csv", list_y_buggy);
		write_record_list(arguments.outputDir + "X_clean_" + arguments.projectName + ".csv", list_x_clean);
		write_record_list(arguments.outputDir + "Y_clean_" + arguments.projectName + ".csv", list_y_clean);

		System.out.println("split complete!");
	}

	public static void writeGumVecs(CLIOptions input, ArrayList<ArrayList<Integer>> gumtree_vectors)
			throws IOException {
		File fileP = new File(input.outputDir + "X_" + input.projectName + ".csv");
		BufferedWriter writer = Files.newBufferedWriter(Paths.get(fileP.getAbsolutePath()));
		CSVPrinter csvprinter = new CSVPrinter(writer, CSVFormat.DEFAULT);

		int index = 0;
		for (ArrayList<Integer> g_vec : gumtree_vectors) {
			csvprinter.print(input.projectName + index);
			for (Integer val : g_vec) {
				csvprinter.print(val);
			}
			csvprinter.println();
			csvprinter.flush();
			index++;
		}

		csvprinter.close();
		System.out.println("wrting X_gumvecs complete!");
		return;
	}

	public static int getMaxASTLenth(ArrayList<BeforeBIC> bbics, CLIOptions input) throws RevisionSyntaxException,
			MissingObjectException, IncorrectObjectTypeException, AmbiguousObjectException, IOException {
		Repository repo = input.repo;
		RevWalk walk = new RevWalk(repo);
		int max_size = 0;
		// get the max length of changes
		for (BeforeBIC bbic : bbics) {
			RevCommit commitBIC = walk.parseCommit(repo.resolve(bbic.shaBIC));
			RevCommit commitBBIC = walk.parseCommit(repo.resolve(bbic.shaBeforeBIC));

			String pathBIC = bbic.pathBIC;
			String pathBBIC = bbic.pathBeforeBIC;
			String srcBlobBIC = Utils.fetchBlob(repo, commitBBIC.getName(), pathBBIC);
			String dstBlobBIC = Utils.fetchBlob(repo, commitBIC.getName(), pathBIC);
			Run.initGenerators();
			ITree srcBIC;
			ITree dstBIC;
			try {
				srcBIC = new JdtTreeGenerator().generateFromString(srcBlobBIC).getRoot();
				dstBIC = new JdtTreeGenerator().generateFromString(dstBlobBIC).getRoot();
			} catch (Exception e) {
				continue;
			}
			Matcher matchBIC = Matchers.getInstance().getMatcher(srcBIC, dstBIC);
			matchBIC.match();

			ActionGenerator gBIC = new ActionGenerator(srcBIC, dstBIC, matchBIC.getMappings());
			gBIC.generate();

			List<Action> actionsBIC = gBIC.getActions();
			int max = 0;
			for (Action action : actionsBIC) {
				//if (action.getNode().getType() == 40 || action.getNode().getType() == 26) {
				//	continue;
				//}
				//commented out by TE
				
				if (action.getName().equals("INS")) {
					max++;
				} else if (action.getName().equals("DEL")) {
					max++;
				} else if (action.getName().equals("UPD")) {
					max++;
				} else if (action.getName().equals("MOV")) {
					max++;
				}
			}

			if (max > max_size) {
				max_size = max;
			}

		}
		walk.close();
		return max_size;
	}

	public static void runD4j3(CLIOptions input)
			throws IOException, RevisionSyntaxException, NoHeadException, GitAPIException {

		ArrayList<String> bfcList = new ArrayList<String>();
		Reader d4j_reader = new FileReader(input.inputDir + "d4j_" + input.projectName + ".csv");
		Iterable<CSVRecord> d4j_records = CSVFormat.RFC4180.parse(d4j_reader);

		for (CSVRecord record : d4j_records) {
			bfcList.add(record.get(2));
		}

		ArrayList<BICInfo> bicLists = BICInfo.collectFrom(input, bfcList);

		// writing BIC_.csv for d4j instance.
		final String[] headers = BICInfo.headers;
		File file_BIC = new File(input.outputDir + "BIC_d4j_" + input.projectName + ".csv");
		BufferedWriter writer_BIC = Files.newBufferedWriter(Paths.get(file_BIC.getAbsolutePath()));
		CSVPrinter csvprinter_BIC = new CSVPrinter(writer_BIC, CSVFormat.DEFAULT.withHeader(headers));

		for (BICInfo bic : bicLists) {
			csvprinter_BIC.printRecord(bic.getBISha1(), bic.getBIPath(), bic.getPath(), bic.getFixSha1(),
					bic.getBIDate(), bic.getFixDate(), bic.getLineNum(), bic.getLineNumInPrevFixRev(),
					bic.getIsAddedLine(), bic.getLine());
			csvprinter_BIC.flush();
		}
		csvprinter_BIC.close();
	}

	public static void runD4j2(CLIOptions input) throws IOException {
		ArrayList<BeforeBIC> new_bbics = new ArrayList<BeforeBIC>();
		Reader d4j_reader = new FileReader(input.inputDir + "d4j_" + input.projectName + ".csv");
		Reader bbic_reader = new FileReader(input.inputDir + "BBIC_" + input.projectName + ".csv");
		Iterable<CSVRecord> d4j_records = CSVFormat.RFC4180.parse(d4j_reader);
		Iterable<CSVRecord> bbic_records = CSVFormat.RFC4180.parse(bbic_reader);

		ArrayList<String> sha_d4js = new ArrayList<String>();
		ArrayList<BeforeBIC> bbics = new ArrayList<BeforeBIC>();
		for (CSVRecord record : d4j_records) {
			sha_d4js.add(record.get(2));
		}
		for (CSVRecord record : bbic_records) {
			BeforeBIC bbic = new BeforeBIC(record.get(1), record.get(2), record.get(3), record.get(4), record.get(5),
					record.get(6), record.get(7), record.get(8), record.get(9), record.get(10), record.get(11));
			bbics.add(bbic);
		}

		int match_cnt = 0;
		for (int i = 0; i < sha_d4js.size(); i++) {
			for (int j = 0; j < bbics.size(); j++) {
				if (sha_d4js.get(i).equals(bbics.get(j).shaBFC)) {
					new_bbics.add(bbics.get(j));
					match_cnt++;
				}
			}
		}

		runGumtree(input, new_bbics);
		System.out.println("match_cnt: " + match_cnt);
		System.out.println("d4j: " + sha_d4js.size());
		System.out.println("bbic: " + bbics.size());
	}

	public static void runD4j(CLIOptions input) throws IOException, GitAPIException {
		int MAX_SIZE = 2000;
		Reader in = new FileReader(input.inputDir + "d4j_" + input.projectName + ".csv");
		Iterable<CSVRecord> records = CSVFormat.RFC4180.parse(in);
		File file_Y = new File(input.outputDir + "Y_defects4j.csv");
		File file_X = new File(input.outputDir + "X_defects4j.csv");
		BufferedWriter writer_Y = Files.newBufferedWriter(Paths.get(file_Y.getAbsolutePath()));
		BufferedWriter writer_X = Files.newBufferedWriter(Paths.get(file_X.getAbsolutePath()));
		CSVPrinter csvprinter_Y = new CSVPrinter(writer_Y, CSVFormat.DEFAULT);
		CSVPrinter csvprinter_X = new CSVPrinter(writer_X, CSVFormat.DEFAULT);
		RevWalk walk = new RevWalk(input.repo);

		// for each line of records (e.g. 40)
		int all_cnt = 0;
		int record_idx = 0;
		for (CSVRecord record : records) {

			String shaBeforeBFC = record.get(1);
			String shaBFC = record.get(2);
			String pathBeforeBFC = "";
			String pathBFC = "";

			// skip header
			if (shaBFC.equals("revision.id.fixed"))
				continue;

			ObjectId beforeBFC = input.repo.resolve(shaBeforeBFC);
			ObjectId bfc = input.repo.resolve(shaBFC);
			RevCommit commitBBFC = walk.parseCommit(beforeBFC);
			RevCommit commitBFC = walk.parseCommit(bfc);
			RevTree treeBBFC = walk.parseTree(beforeBFC);
			RevTree treeBFC = walk.parseTree(bfc);

			ObjectReader reader = input.repo.newObjectReader();
			CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
			oldTreeIter.reset(reader, treeBBFC);
			CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
			newTreeIter.reset(reader, treeBFC);

			List<DiffEntry> diffs = input.git.diff().setNewTree(newTreeIter).setOldTree(oldTreeIter).call();

			// for each files in a commit (1 ~ 3)
			for (DiffEntry entry : diffs) {
				ArrayList<Integer> g_vec = new ArrayList<Integer>();
				pathBeforeBFC = entry.getOldPath();
				pathBFC = entry.getNewPath();

				String srcBlobBFC = Utils.fetchBlob(input.repo, commitBBFC.getName(), pathBeforeBFC);
				String dstBlobBFC = Utils.fetchBlob(input.repo, commitBFC.getName(), pathBFC);

				Run.initGenerators();

				ITree srcBFC;
				ITree dstBFC;

				try {
					srcBFC = new JdtTreeGenerator().generateFromString(srcBlobBFC).getRoot();
					dstBFC = new JdtTreeGenerator().generateFromString(dstBlobBFC).getRoot();
				} catch (Exception e) {
					System.out.println("exception: " + e);
					continue;
				}
				Matcher matchBFC = Matchers.getInstance().getMatcher(srcBFC, dstBFC);
				matchBFC.match();

				ActionGenerator gBFC = new ActionGenerator(srcBFC, dstBFC, matchBFC.getMappings());
				gBFC.generate();

				List<Action> actionsBFC = gBFC.getActions();

				if (actionsBFC.size() <= 0) {
					//System.out.println("actionsFIX size < 0");
					continue;
					//commented out by TE
				}

				// for each action node in a change
				for (Action action : actionsBFC) {
					//if (action.getNode().getType() == 40 || action.getNode().getType() == 26) {
					//	continue;
					//}
					//commented out by TE
					if (action.getName().equals("INS")) {
						g_vec.add(action.getNode().getType() + 1);
					} else if (action.getName().equals("DEL")) {
						g_vec.add(action.getNode().getType() + 85 + 1);
					} else if (action.getName().equals("UPD")) {
					g_vec.add(action.getNode().getType() + 85*2 + 1);
					} else if (action.getName().equals("MOV")) {
						g_vec.add(action.getNode().getType() + 85*3 + 1);
					}


				}

				if (g_vec.size() > MAX_SIZE || g_vec.size() <= 0)

					continue;

				if (actionsBFC.size() < MAX_SIZE) {
					for (int i = 0; i < MAX_SIZE - actionsBFC.size(); i++) {
						g_vec.add(0);
					}
				}

				@SuppressWarnings("deprecation")
				ASTParser parser = ASTParser.newParser(AST.JLS9);
				parser.setKind(ASTParser.K_COMPILATION_UNIT);
				Hashtable<String, String> pOptions = JavaCore.getOptions();
				pOptions.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_9);
				pOptions.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_9);
				pOptions.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_9);
				pOptions.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, JavaCore.ENABLED);
				parser.setCompilerOptions(pOptions);
				parser.setSource(dstBlobBFC.toCharArray());
				CompilationUnit cu = (CompilationUnit) parser.createAST(null);

				ArrayList<Integer> context_vec = new ArrayList<Integer>();
				HashMap<Integer, Boolean> map = new HashMap<Integer, Boolean>();

				// adding context vectors
				for (Action action : actionsBFC) {
					int lineNumOfBIC = cu.getLineNumber(action.getNode().getPos());
					int parent_hash = action.getNode().getParent().getHash();
					if (map.containsKey(parent_hash)) {
						continue;
					} else {
						map.put(parent_hash, true);
						List<ITree> descendants = action.getNode().getParent().getDescendants();
						for (ITree descendant : descendants) {
							if (map.containsKey(descendant.getHash())) {
								continue;
							} else {
								int lineNumOfDescendant = cu.getLineNumber(descendant.getPos());
								if (Math.abs(lineNumOfBIC - lineNumOfDescendant) < 3) {
									map.put(descendant.getHash(), true);
									context_vec.add(descendant.getType());
								}
							}
						}
					}
				}
				g_vec.addAll(context_vec);
				String key = pathBeforeBFC + pathBFC + shaBeforeBFC + shaBFC;
				csvprinter_Y.printRecord(input.projectName + all_cnt, "-", "-", "-", "-", pathBeforeBFC, pathBFC,
						shaBeforeBFC, shaBFC, key, input.projectName);

				csvprinter_Y.flush();

				for (Integer val : g_vec) {
					csvprinter_X.print(val);
				}
				csvprinter_X.println();
				csvprinter_X.flush();
				all_cnt++;

			} // end of diff for
			record_idx++;
		} // end of record for

		csvprinter_Y.close();
		csvprinter_X.close();
		walk.close();
		System.out.println("record_cnt: " + record_idx);
		System.out.println("all_cnt: " + all_cnt);

		System.out.println("writing gumvecs complete!");
	}
}