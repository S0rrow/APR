package com.github.thwak.confix.main;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.Comparator;
import java.util.Collections;
import java.lang.Integer;

import com.github.thwak.confix.coverage.CoverageManager;
import com.github.thwak.confix.coverage.TestResult;
import com.github.thwak.confix.coverage.Tester;
import com.github.thwak.confix.patch.PatchInfo;
import com.github.thwak.confix.patch.PatchStrategy;
import com.github.thwak.confix.patch.PatchUtils;
import com.github.thwak.confix.patch.Patcher;
import com.github.thwak.confix.patch.StrategyFactory;
import com.github.thwak.confix.patch.TargetLocation;
import com.github.thwak.confix.pool.Change;
import com.github.thwak.confix.pool.ChangePool;
import com.github.thwak.confix.pool.ChangePoolGenerator;
import com.github.thwak.confix.tree.compiler.Compiler;
import com.github.thwak.confix.util.IOUtils;

public class ConFix {

	// private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("confix.debug", "false"));
	private static final boolean DEBUG = true;
	public static final int PASS = 0;
	public static final int COMPILE_ERROR = 1;
	public static final int TEST_FAILURE = 2;
	public static final int TEST_TIMEOUT = 3;
	public static final int BREAK_FUNC = 4;
	public static final int TRIGGER_TEST_FAILURE = 5;
	public static final int RELEVANT_TEST_FAILURE = 6;

	public static String testClassPath;
	public static String compileClassPath;
	public static String[] testClassPathEntries;
	public static String[] compileClassPathEntries;
	public static String libClassPath;
	public static List<String> modifiedClasses = new ArrayList<>();
	public static CoverageManager coverage;
	public static String sourceDir;
	public static String targetDir;
	public static String tempDir;
	public static List<String> poolList;
	public static ChangePool pool;
	public static int patchCount = 20;
	public static int maxTrials = 10;
	public static String candidateDir = "candidates";
	public static String patchDir = "patches";
	public static String jvm;
	public static String version;
	public static long timeout;
	public static List<String> triggerTests = new ArrayList<>();
	public static List<String> relTests = new ArrayList<>();
	public static List<String> allTests = new ArrayList<>();
	public static Set<String> brokenTests = new HashSet<>();
	public static long seed;
	public static int numOfTriggers;
	public static long timeBudget;
	public static String pStrategyKey;
	public static String cStrategyKey;
	public static String flMetric;
	public static int maxPoolLoad;
	public static int maxChangeCount;

	// TE
	public static String poolSource;
	public static List<File> cleanFiles = new ArrayList<File>(11);
	public static List<File> buggyFiles = new ArrayList<File>(11);
	public static String projectName;
	public static String bugId;

	public static int pFaultyLine;
	public static String pFaultyClass;

	public static void main(String[] args) {

		String oldLocKey = "";
		String currentLocKey = null;
		Change oldApplied = null;
		String locPoolPath = "";
		StringBuffer sbLoc = new StringBuffer("Pool,CheckedLines,CheckedLoc,CheckedChange,AppliedChange");
		int totalCompileError = 0;
		int totalTestFailure = 0;
		int totalCandidateNum = 0;
		int candidateNum = 1;
		int compileError = 0;
		int testFailure = 0;
		int locNum = 0;
		int changeNum = 0;
		int applied = 0;
		int locChangeCount = 0;
		boolean success = false;
		boolean terminate = false;
		String targetClass = null;
		Patcher patcher = null;

		// ======= STEP 0. Set Properties ======= //
		System.out.println("\n// ======= STEP 0. Set Properties ======= //");
		loadProperties("confix.properties");
		loadTests();
		loadCoverage();
		checkCoverageValidity();
		long startTime = setTimer();
		Random randomSeed = setRandomSeed(startTime);

		// ======= STEP 1. Generate Pool from SimFin Results ======= //
		System.out.println("\n// ======= STEP 1. Generate Pool from SimFin Results ======= //");
		setPoolFromSimFinResults();

		// ======= STEP 1-2. Create Patch Strategy ======= //
		System.out.println("\n// ======= STEP 1-2. Create Patch Strategy ======= //");
		PatchStrategy pStrategy;
		if (flMetric.compareTo("perfect") == 0) {
			pStrategy = StrategyFactory.getPatchStrategy(pStrategyKey, coverage, pool, randomSeed, flMetric,
					cStrategyKey, sourceDir, compileClassPathEntries, pFaultyClass, pFaultyLine);
		} else {
			pStrategy = StrategyFactory.getPatchStrategy(pStrategyKey, coverage, pool, randomSeed, flMetric,
					cStrategyKey, sourceDir, compileClassPathEntries);
		}
		pStrategy.finishUpdate();
		IOUtils.storeContent("coveredlines.txt", pStrategy.getLineInfo());
		System.out.println("[Debug.log] line 136 of ConFix.java : patch strategy update finished");
		//요기서 타겟 코드 안에 있는 모든 variable declaration 찾아서 type과 mapping 시켜놓기
		


		// ======= STEP 2. Generate Patch Candidates ======= //
		System.out.println("\n// ======= STEP 2. Generate Patch Candidates ======= //");
		while (candidateNum <= patchCount) {
			int trial = 0;
			int returnCode = -1;

			// ======= STEP 2-1. Select Location to Fix ======= //
			System.out.println("\n// ======= STEP 2-1. Select Location to Fix ======= //");
			TargetLocation loc = pStrategy.selectLocation();
			targetClass = loc == null ? "" : loc.className;
			currentLocKey = pStrategy.getCurrentLocKey();
			System.out.println("[Debug.log] line 152 of ConFix.java : oldLocKey = "+oldLocKey);
			System.out.println("[Debug.log] line 153 of ConFix.java : currentLocKey = "+currentLocKey);
			if (!oldLocKey.equals(currentLocKey)) {
				oldLocKey = currentLocKey;
				locNum++;
				locChangeCount = 0;
			}

			

			// ======= STEP 2-2. Create Patcher ======= //
			System.out.println("\n// ======= STEP 2-2. Create Patcher ======= //");
			System.out.println("[Debug.log] line 162 of ConFix.java : maven clean package checkout : ok");
			patcher = pStrategy.patcher();
			if (patcher == null){
				System.out.println("[Debug.log] line 164 of ConFix.java : patcher == null");
				// randomSeed = setRandomSeed(setTimer());
				// if (flMetric.compareTo("perfect") == 0) {
				// 	pStrategy = StrategyFactory.getPatchStrategy(pStrategyKey, coverage, pool, randomSeed, flMetric,
				// 			cStrategyKey, sourceDir, compileClassPathEntries, pFaultyClass, pFaultyLine);
				// } else {
				// 	pStrategy = StrategyFactory.getPatchStrategy(pStrategyKey, coverage, pool, randomSeed, flMetric,
				// 			cStrategyKey, sourceDir, compileClassPathEntries);
				// }
				// pStrategy.finishUpdate();
				// IOUtils.storeContent("coveredlines.txt", pStrategy.getLineInfo());
				// continue ;

				break;
			}


			
			// ======= STEP 2-3. Select Change Information ======= //
			System.out.println("\n// ======= STEP 2-3. Select Change Information ======= //");
			Change change = pStrategy.selectChange();
			if (change != null) {
				changeNum++;
				locChangeCount++;
			}
			if (locChangeCount > maxChangeCount) {
				pStrategy.nextLoc();
				continue;
			}

			//이 때 Patcher 안의 cStrategy안의 materials에 우리가 필요로 하는 재료를 더 넣어줘야 한다.
			// if(change.type.equals(Change.UPDATE) && loc.node.parent.astNode.getNodeType() == loc.node.parent.astNode.METHOD_INVOCATION){
			// 	// loc이 어떤 타입의 필드 메소드인지 파악
			// 	// 해당 타입의 모든 필드 메소드를 리스트로 만들어 cStrategy 객체에 넣어준다 + 인덱스 초기화.
			// 	// apply가 실행될 때마다 index 증가, 
			// 	// generate node하기 직전에 copied의 value를 list[index]의 값으로 바꾼다.
			// }	

			// ======= STEP 2-4. Apply Change Information to Create Patch Candidate =======
			// //
			System.out.println("\n// ======= STEP 2-4. Apply Change Information to Create Patch Candidate ======= //");
			Set<String> candidates = new HashSet<>();
			do {
				success = false;
				PatchInfo info = new PatchInfo(targetClass, change, loc);
				try {
					returnCode = patcher.apply(loc, change, info);
					//TE
					System.out.println("[Debug.log] line 215 of ConFix.java : Fix Location : "+loc);
					System.out.println("[Debug.log] line 216 of ConFix.java : Applied Change = "+change);
					System.out.println("[Debug.log] line 218 of ConFix.java : Applied new code");
					if(info.repairs.size() > 0) System.out.println(info.repairs.get(0).newCode);
					System.out.println("[Debug.log] line 220 of ConFix.java : Return Code = "+returnCode);
				} catch (Exception e) {
					System.out.println("[Debug.log] line 221 of ConFix.java : Exception = "+e);
					System.out.println("[Debug.log] line 222 of ConFix.java : Change Application Error.");
					System.out.println("[Debug.log] line 223 of ConFix.java : Fix Location");
					System.out.println("[Debug.log] line 224 of ConFix.java :"+ loc);
					System.out.println("[Debug.log] line 225 of ConFix.java : Applied Change");
					System.out.println("[Debug.log] line 226 of ConFix.java :"+ change);
					returnCode = Patcher.C_NOT_APPLIED;
					// break;
				}
				trial++;

				if (returnCode == Patcher.C_NOT_INST) {
					// break;
				} else {
					if (returnCode == Patcher.C_APPLIED) {
						System.out.println("\n[Debug.log] line 238 of ConFix.java : Patch Candidate-" + candidateNum + " is generated.");
						if (change != null && !change.equals(oldApplied)) {
							oldApplied = change;
							applied++;
						}
						String editText = PatchUtils.getEditText(info, pool);
						//if (DEBUG) {
							System.out.print(
									"\n================= 1. Edit Text (Candidate #: " + candidateNum + ") ========\n");
							System.out.println(editText);

							System.out.print(
									"\n================= 2. New Source (Candidate #: " + candidateNum + ") =======\n");
						//}

						String newSource = patcher.getNewSource();
						String candidateFileName = storeCandidate(newSource, editText, targetClass, change, candidateNum);

						// ======= STEP 2-5. Verify Patch Candidate ======= //
						System.out.println(
								"// ======= STEP 2-5. Verify Patch Candidate(" + candidateNum + ") ======= //");
						int result = verify(candidateFileName);
						if (result == PASS) {
							String patchFileName = storePatch(newSource, editText, targetClass, change);
							System.out.println("A Patch Found! - " + patchFileName);
							System.out.println("Candidate Number:" + candidateNum);
							String elapsedTime = PatchUtils.getElapsedTime(System.currentTimeMillis() - startTime);
							totalCompileError += compileError;
							totalTestFailure += testFailure;
							totalCandidateNum += candidateNum;

							storePatchInfo(totalCandidateNum, totalCompileError, totalTestFailure, elapsedTime,
									patchFileName, info);

							success = true;
							break;
						} else {
							if (result == COMPILE_ERROR) {
								System.out.println("[Debug.log] line 276 of ConFix.java : compile error ++");
								compileError++;
							} else if (result == TEST_FAILURE || result == TEST_TIMEOUT || result == BREAK_FUNC || result == TRIGGER_TEST_FAILURE || result == RELEVANT_TEST_FAILURE) {
								System.out.println("[Debug.log] line 276 of ConFix.java : test error ++");
								testFailure++;
							}
						}
						candidateNum++;
						if (candidateNum > patchCount || result == TEST_TIMEOUT || result == BREAK_FUNC
								|| !candidates.add(editText)){
									System.out.print("\n\n[Debug.log]: condition counting on candidate #"+candidateNum+" ... ");
									if(candidateNum > patchCount) System.out.println("[Debug.log][candidateNum > patchCount]");
									if(result == TEST_TIMEOUT) System.out.println("[Debug.log][result == TEST_TIMEOUT]");
									if(result == BREAK_FUNC) System.out.println("[Debug.log][result == BREAK_FUNC]");
									if(!candidates.add(editText)) System.out.println("[Debug.log][!candidates.add(editText)]");
								}
							// break;
					} else if (returnCode == Patcher.C_NO_FIXLOC) {
						break;
					} else if (returnCode == Patcher.C_NO_CHANGE) {
						break;
					}
				}

				if (isTimeBudgetPassed(startTime)) {
					terminate = true;
					System.out.println("Time Budget is passed.");
					break;
				}


				// if(change == null || change.type.equals("delete") )
				//  	break;

				// String nodeType = change.node.label.split("::")[0];
				// String locationType = change.location.label.split("::")[0];
				// String locType = loc.node.label.split("::")[0];

				// if(change.type.equals(Change.INSERT) || change.type.equals(Change.DELETE)){
				// 	if(nodeType.contains("fixExpression"))
				// 		break;
				// }				
				// else if(change.type.equals(Change.REPLACE) || change.type.equals(Change.UPDATE)){
				// 	if(locType.contains("fixExpression"));
				// 		break;
				// 	// if(locationType.contains("fixExpression"))
				// 	// 	break;
				// }

				
				
				
				

			} while (trial < maxTrials);
			
			// define the current return code
			if(returnCode == Patcher.C_APPLIED) {
				System.out.println("[Debug.log] line 334 of ConFix.java: Patcher.C_APPLIED");
			} else if(returnCode == Patcher.C_NO_FIXLOC) {
				System.out.println("[Debug.log] line 336 of ConFix.java: Patcher.C_NO_FIXLOC");
			} else if(returnCode == Patcher.C_NOT_INST) {
				System.out.println("[Debug.log] line 338 of ConFix.java: Patcher.C_NOT_INST");
			} else if(returnCode == Patcher.C_NOT_APPLIED) {
				System.out.println("[Debug.log] line 340 of ConFix.java: Patcher.C_NOT_APPLIED");
			} else if(returnCode == Patcher.C_NO_CHANGE) {
				System.out.println("[Debug.log] line 342 of ConFix.java: Patcher.C_NO_CHANGE");
			}

			// 여기서 success 없애면 다른 change로도 계속해서 패치 생성 시도해볼 수 있음
			if (success || terminate || returnCode == Patcher.C_NO_FIXLOC)
			//if (terminate || returnCode == Patcher.C_NO_FIXLOC)
				break;
		}

		if (success || terminate) {
			System.out.println("Elapsed Time: " + PatchUtils.getElapsedTime(System.currentTimeMillis() - startTime));
			printLocInfo(pStrategy.getCurrentLineIndex() + 1, locNum, changeNum, applied, locPoolPath, sbLoc);
			System.out.println("Compile Errors:" + compileError);
			System.out.println("Test Failures:" + testFailure);
			IOUtils.storeContent("lines-" + pool.poolName + ".txt", pStrategy.getLocInfo());

		} else {
			System.out.println("No patch found.");
			System.out.println("Elapsed Time: " + PatchUtils.getElapsedTime(System.currentTimeMillis() - startTime));
			System.out.println("Compile Errors:" + compileError);
			System.out.println("Test Failures:" + testFailure);
			totalCompileError += compileError;
			totalTestFailure += testFailure;
			totalCandidateNum += candidateNum;
			printLocInfo(pStrategy.getCurrentLineIndex() + 1, locNum, changeNum, applied, locPoolPath, sbLoc);
			IOUtils.storeContent("lines-" + pool.poolName + ".txt", pStrategy.getLocInfo());
		}

		IOUtils.storeContent("locinfo.csv", sbLoc.toString());
	}

	private static void checkCoverageValidity() {
		if (coverage == null || coverage.getNegCoveredClasses().size() == 0) {
			System.out.println("No class/coverage information.");
			return;
		} else if (poolList.size() == 0) {
			System.out.println("No change pool is specified.");
			return;
		}
	}

	private static void printLocInfo(int lines, int locNum, int changeNum, int applied, String poolPath,
			StringBuffer sb) {
		System.out.println("Checked Lines:" + lines);
		System.out.println("Checked Fix Locs:" + locNum);
		System.out.println("Checked Changes:" + changeNum);
		System.out.println("Applied Changes:" + applied);
		sb.append("\n");
		sb.append(poolPath);
		sb.append(",");
		sb.append(lines);
		sb.append(",");
		sb.append(locNum);
		sb.append(",");
		sb.append(changeNum);
		sb.append(",");
		sb.append(applied);
	}

	private static long setTimer() {
		long startTime = System.currentTimeMillis();
		System.out.println("Random Seed:" + seed);

		return startTime;
	}

	private static Random setRandomSeed(long startTime) {
		seed = seed == -1 ? new Random(startTime).nextInt(100) : seed;
		Random r = new Random(seed);

		return r;
	}

	private static void setPoolFromSimFinResults() {
		// TE
		// We load new Changepool for each run
		ChangePoolGenerator changePoolGenerator = new ChangePoolGenerator();
		String[] sourcePathEntry = new String[]{sourceDir};
		changePoolGenerator.collect(buggyFiles,cleanFiles, compileClassPathEntries, sourcePathEntry);
		pool = changePoolGenerator.pool;
		pool.poolName = "SimFinPool";
		pool.maxLoadCount = maxPoolLoad;
		System.out.println("[Debug.log] line 426 of ConFix.java : Pool Generation Done.");
	}

	private static void storePatchInfo(int totalCandidateNum, int totalCompileError, int totalTestFailure,
			String elapsedTime, String patchFileName, PatchInfo info) {
		StringBuffer sb = new StringBuffer();
		sb.append("Seed:");
		sb.append(seed);
		sb.append("|Pool:");
		sb.append("beautyPool");
		sb.append("|PatchNum:");
		sb.append(totalCandidateNum);
		sb.append("|Time:");
		sb.append(elapsedTime.trim());
		sb.append("|CompileError:");
		sb.append(totalCompileError);
		sb.append("|TestFailure:");
		sb.append(totalTestFailure);
		sb.append("|Concretize:");
		sb.append(info.getConcretize());
		sb.append("\n");
		sb.append(patchFileName);
		sb.append("\n");
		IOUtils.storeContent("patch_info", sb.toString(), true);
	}

	private static boolean isTimeBudgetPassed(long startTime) {
		if (timeBudget < 0)
			return false;
		long expire = startTime + (timeBudget * 60 * 60 * 1000);
		return System.currentTimeMillis() >= expire;
	}

	private static void loadTests() {
		String trigger = IOUtils.readFile("tests.trigger");
		String relevant = IOUtils.readFile("tests.relevant");
		String all = IOUtils.readFile("tests.all");
		Set<String> testSet = new HashSet<>();
		String[] tests = trigger.split("\n");
		numOfTriggers = tests.length;
		for (String test : tests) {
			// Get the class name only for trigger tests.
			if (!test.startsWith("#"))
				testSet.add(test.split("::")[0]);
		}
		triggerTests.addAll(testSet);
		relTests.addAll(Arrays.asList(relevant.split("\n")));
		allTests.addAll(Arrays.asList(all.split("\n")));
		File f = new File("tests.broken");
		if (f.exists()) {
			String broken = IOUtils.readFile("tests.broken");
			tests = broken.split("\n");
			for (String t : tests)
				brokenTests.add(t.replace("::", "#"));
		}
	}

	private static int verify(String patchFileName) {
		// Compile a given patch candidate.
		if (!compileCheck(patchFileName)) {
			return COMPILE_ERROR;
		} else {
			return testCheck();
		}
	}

	public static boolean compileCheck(String patchFileName) {
		System.out.print("\n================= 3. Compilation ===================\n");
		System.out.print("\n ================= 3-1. Compile Info ============\n");
		System.out.print("\n 1. patchFileName : " + patchFileName);
		System.out.print("\n 2. tempDir : " + tempDir);
		System.out.print("\n 3. compileClassPath : " + compileClassPath);
		System.out.print("\n 4. Java version : " + version);
		System.out.print("\n 5. JVM : " + jvm);
		System.out.print("\n 6. System JVM : " + System.getProperty("java.version"));
		System.out.print("\n ================================================\n");

		File patchFile = new File(patchFileName);
		Compiler compiler = new Compiler();
		try {
			boolean error = compiler.compile(patchFile, tempDir, compileClassPath, version, version);
			if (error) {
				System.out.println("[Debug.log] line 507 of ConFix.java : no exception Compile error.");
				return false;
			}
		} catch (Exception e) {
			// TE
			e.printStackTrace(System.out);
			System.out.println("[Debug.log] line 513 of ConFix.java : Compile error.");
			return false;
		}
		return true;

	}

	private static void removeBrokenTests(TestResult result) {
		if (result != null) {
			Set<String> falseAlarm = new HashSet<>();
			for (String test : result.failedTests) {
				if (brokenTests.contains(test))
					falseAlarm.add(test);
			}
			result.failedTests.removeAll(falseAlarm);
			result.failCnt = result.failedTests.size();
		}
	}

	public static int testCheck() {
		Tester tester = new Tester(jvm, timeout);
		// Run failed tests.
		TestResult result = null;
		String classPath = tempDir + File.pathSeparator + testClassPath;

		// System.out.print("\n================= 4. Test ===================\n");
		// System.out.print("\n ================= 4-1. Test Info ============\n");
		// System.out.print("\n 1. tempDir : " + tempDir);
		// System.out.print("\n 2. ClassPath : " + classPath);
		// System.out.print("\n 3. JVM : " + jvm);

		// System.out.print("\n ================================================\n");

		try {
			result = tester.runTestsWithJUnitCore(triggerTests, classPath);

			// System.out.print("\n ============ 4-2 trigger Test results ==============");

			removeBrokenTests(result);
			if (result != null && result.failCnt > 0) {
				System.out.println("Trigger tests - " + result.failCnt + " Tests Failed.");
				if (result.failCnt > numOfTriggers)
					return BREAK_FUNC;
				return TRIGGER_TEST_FAILURE;
			} else if (result == null || result.runCnt == 0) {
				System.out.println("An error occurs while running trigger tests - no records.");
				return TEST_TIMEOUT;
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("An error occurs while running trigger tests.");
			return TRIGGER_TEST_FAILURE;
		}

		// If passed, run relevant tests.
		if (result != null && result.runCnt > 0 && result.failCnt == 0) {
			try {
				result = tester.runTestsWithJUnitCore(relTests, classPath);
				removeBrokenTests(result);
				if (result != null && result.failCnt > 0) {
					System.out.println("Relevant tests - " + result.failCnt + " Tests Failed.");
					return RELEVANT_TEST_FAILURE;
				} else if (result == null || result.runCnt == 0) {
					System.out.println("An error occurs while running relevant tests. - no records.");
					return TEST_TIMEOUT;
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("An error occurs while running relevant tests.");
				return RELEVANT_TEST_FAILURE;
			}
		}

		// If passed, run all tests.
		if (result != null && result.runCnt > 0 && result.failCnt == 0) {
			try {
				result = tester.runTestsWithJUnitCore(allTests, classPath);
				removeBrokenTests(result);
				if (result != null && result.failCnt > 0) {
					System.out.println("All tests - " + result.failCnt + " Tests Failed.");
					return TEST_FAILURE;
				} else if (result == null || result.runCnt == 0) {
					System.out.println("An error occurs while running all tests. - no records.");
					return TEST_TIMEOUT;
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("An error occurs while running all tests.");
				return TEST_FAILURE;
			}
		}

		// If passed, return true.
		if (result != null && result.runCnt > 0 && result.failCnt == 0) {
			return PASS;
		} else {
			return TEST_FAILURE;
		}
	}

	private static String storePatch(String newSource, String patch, String targetClass, Change change) {
		int lastDotIndex = targetClass.lastIndexOf('.');
		String packageName = targetClass.substring(0, lastDotIndex);
		String fileName = targetClass.substring(lastDotIndex + 1) + ".java";
		File dir = new File(patchDir);
		File[] dirs = dir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File f) {
				return f.isDirectory() && f.getName().matches("[0-9]+");
			}
		});
		int patchId = dirs == null ? 0 : dirs.length;
		String patchPath = patchDir + File.separator + patchId + File.separator;
		String packagePath = patchPath + packageName.replaceAll("\\.", File.separator + File.separator);
		File packageDir = new File(packagePath);
		if (!packageDir.exists())
			packageDir.mkdirs();
		String filePath = Paths.get(packagePath + File.separator + fileName).toString();
		IOUtils.storeContent(filePath, newSource);

		File easyDir = new File(patchPath + sourceDir + File.separator + packageName.replaceAll("\\.", File.separator + File.separator));
		if (!easyDir.exists())
			easyDir.mkdirs();
		String easyPath = Paths.get(patchPath + sourceDir + File.separator + packageName.replaceAll("\\.", File.separator + File.separator) + File.separator + fileName).toString();
		IOUtils.storeContent(easyPath, newSource);
		
		IOUtils.storeContent(patchPath + "edit", patch);
		IOUtils.storeObject(patchPath + "change.obj", change);

		return filePath;
	}

	private static String storeCandidate(String newSource, String patch, String targetClass, Change change, int candidateNum) {
		int lastDotIndex = targetClass.lastIndexOf('.');
		String packageName = targetClass.substring(0, lastDotIndex);
		String fileName = targetClass.substring(lastDotIndex + 1) + ".java";
		//String candidatePath = candidateDir + File.separator + "candidate_"+candidateNum + File.separator;
		String candidatePath = candidateDir + File.separator + "candidate"+ File.separator;
		String packagePath = candidatePath + packageName.replaceAll("\\.", File.separator + File.separator);
		File dir = new File(packagePath);
		if (!dir.exists())
			dir.mkdirs();
		String filePath = Paths.get(packagePath + File.separator + fileName).toString();
		IOUtils.storeContent(filePath, newSource);
		IOUtils.storeContent(candidatePath + "edit", patch);
		IOUtils.storeObject(candidatePath + "change.obj", change);

		return filePath;
	}

	private static void loadProperties(String fileName) {
		System.out.println("[Debug.log] line 667 of ConFix.java : loading confix.properties ...");
		Properties props = new Properties();
		File f = new File(fileName);
		try {
			FileInputStream fis = new FileInputStream(f);
			props.load(fis);
			fis.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		libClassPath = PatchUtils.getStringProperty(props, "cp.lib", "");
		testClassPath = PatchUtils.getStringProperty(props, "cp.test", "");
		compileClassPath = PatchUtils.getStringProperty(props, "cp.compile", "");
		String priority = PatchUtils.getStringProperty(props, "cp.test.priority", "local");
		if (libClassPath.length() > 0) {
			if ("cfix".equals(priority)) {
				testClassPath = libClassPath + File.pathSeparatorChar + testClassPath;
			} else {
				testClassPath = testClassPath + File.pathSeparatorChar + libClassPath;
			}
		}
		testClassPathEntries = testClassPath.split(File.pathSeparator);
		compileClassPathEntries = compileClassPath.split(File.pathSeparator);
		sourceDir = PatchUtils.getStringProperty(props, "src.dir", "src/main/java");
		targetDir = PatchUtils.getStringProperty(props, "target.dir", "target/classes");
		modifiedClasses = PatchUtils.getListProperty(props, "classes.modified", ",");
		poolList = PatchUtils.getListProperty(props, "pool.path", ",");
		jvm = PatchUtils.getStringProperty(props, "jvm", "/usr/bin/java");
		version = PatchUtils.getStringProperty(props, "version", "1.7");
		timeout = Long.parseLong(PatchUtils.getStringProperty(props, "timeout", "10")) * 1000;
		patchCount = Integer.parseInt(PatchUtils.getStringProperty(props, "patch.count", "20"));
		maxTrials = Integer.parseInt(PatchUtils.getStringProperty(props, "max.trials", "10"));
		maxChangeCount = Integer.parseInt(PatchUtils.getStringProperty(props, "max.change.count", "25"));
		maxPoolLoad = Integer.parseInt(PatchUtils.getStringProperty(props, "max.pool.load", "1000"));
		seed = Long.parseLong(PatchUtils.getStringProperty(props, "seed", "-1"));
		tempDir = new File("tmp").getAbsolutePath();
		timeBudget = Long.parseLong(PatchUtils.getStringProperty(props, "time.budget", "-1"));
		pStrategyKey = PatchUtils.getStringProperty(props, "patch.strategy", "flfreq");
		cStrategyKey = PatchUtils.getStringProperty(props, "concretize.strategy", "tc");
		flMetric = PatchUtils.getStringProperty(props, "fl.metric", "ochiai");
		System.out.println("[Debug.log] line 709 of ConFix.java : fl.metric = " + flMetric);
		poolSource = PatchUtils.getStringProperty(props, "pool.source", "default");
		projectName = PatchUtils.getStringProperty(props, "projectName", "Closure");
		bugId = PatchUtils.getStringProperty(props, "bugId", "3");
		pFaultyLine = Integer.parseInt(PatchUtils.getStringProperty(props, "pFaultyLine", "1"));
		pFaultyClass = PatchUtils.getStringProperty(props, "pFaultyClass", "");
		System.out.println("[Debug.log] line 715 of ConFix.java : original pFaultyClass = " + pFaultyClass);
		pFaultyClass = pFaultyClass.replace(sourceDir.replaceAll("/", ".") + ".", "");
		System.out.println("[Debug.log] line 717 of ConFix.java : replaced pFaultyClass = " + pFaultyClass);
		createFileLists(projectName);
	}

	public static void createFileLists(String projectName) {
		File dir = new File(poolSource);
		// File dir = new File("/home/aprweb/APR_Projects/APR/pool/las/data");
		File[] directoryListing = dir.listFiles();
		//System.out.println("File list size: "+directoryListing.length()) ;

		for(int i = 0; i<11 ; i++){
			cleanFiles.add(null);
			buggyFiles.add(null);
		}

		if (directoryListing != null) {
			for (File child : directoryListing) {
					int start = child.getName().lastIndexOf('-') + 1;
                    int end = child.getName().lastIndexOf('_');
                    int number = Integer.parseInt(child.getName().substring(start, end));
				try {
					//System.out.println("Child path: " + child.getCanonicalPath()) ;
					String[] childPath = child.getCanonicalPath().split("source/"); // "source/"" 뒤에는 파일 이름이 붙는다.
					if (childPath[1].indexOf("new") > 0) {
						cleanFiles.set(number, child);
					} else {
						buggyFiles.set(number, child);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
		}

	}

	private static void loadCoverage() {
		System.out.print("Loading Coverage Information....");
		coverage = (CoverageManager) IOUtils.readObject("coverage-info.obj");
		System.out.println("Done.");
	}


}

