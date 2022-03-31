package com.github.thwak.confix.tree.compiler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.compiler.CompilationProgress;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.compiler.ClassFile;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;

import com.github.thwak.confix.tree.Parser;
import com.github.thwak.confix.util.IOUtils;

public class Compiler {

	private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("confix.debug", "false"));

	public List<ClassFile> classes;
	public List<IProblem> problems;
	public String className;

	public Compiler() {
		problems = new ArrayList<>();
		classes = new ArrayList<>();
	}

	public boolean compile(File f, String targetPath, String classPath, String version) throws Exception {
		URL[] urls = getUrls(classPath);
		CustomClassLoader loader = new CustomClassLoader(urls, getClass().getClassLoader(), new ArrayList<ClassFile>());
		String source = IOUtils.readFile(f);
		Parser parser = new Parser(new String[] {}, new String[] {});
		CompilationUnit cu = parser.parse(source);
		return compile(loader, source, cu, targetPath, true, version);
	}

	private URL[] getUrls(String classPath) throws MalformedURLException {
		String[] classPaths = classPath.split(":");
		List<URL> urlList = new ArrayList<>();
		for (String cp : classPaths) {
			if (cp.toLowerCase().endsWith(".jar")) {
				urlList.add(new URL("jar:file://" + cp + "!/"));
			} else {
				urlList.add(new URL("file://" + cp + "/"));
			}
		}
		URL[] urls = new URL[urlList.size()];
		urlList.toArray(urls);
		return urls;
	}

	public boolean compile(File f, String targetPath, String classPath, String source, String target) {

		// CommandLine command = CommandLine.parse("javac");
		// // command.addArgument("-Xms512m");
		// // command.addArgument("-Xmx2048m");
		// command.addArgument("-classpath");
		// command.addArgument(classPath);
		// // command.addArgument("-Duser.timezone=" + TZ_INFO);
		// // command.addArgument("-Duser.language=en");
		// command.addArgument(" -d ");
		// command.addArgument(targetPath);

		// // command.addArgument(" -extdirs  \"\"");
		// // //command.append(target);
		// // command.addArgument(" ");
		// command.addArgument(f.getAbsolutePath());

		// // command.addArgument(SAMPLE_TEST_RUNNER);
		// // command.addArgument(testClassName);
		// // command.addArgument("sample");
		// // command.addArgument("false");

		// ExecuteWatchdog watchdog = new ExecuteWatchdog(100000);
		// DefaultExecutor executor = new DefaultExecutor();
		// executor.setWatchdog(watchdog);

		// ByteArrayOutputStream out = new ByteArrayOutputStream();

		// executor.setExitValue(0);
		// executor.setStreamHandler(new PumpStreamHandler(out));

		// System.out.println("This is the command:"+command.toString());

		// try {
		// 	executor.execute(command);
		// 	if (DEBUG)
		// 		System.out.println(out.toString());
		// } catch (ExecuteException e) {
		// 	// System.err.println("Exit Value:" + e.getExitValue());
		// 	e.printStackTrace();
		// 	// System.out.println("Error while running test class " + testClassName);
		// 	// System.err.println(out.toString());
		// 	// TestListener testListener = new TestListener();
		// 	// testListener.failedTests.add(testClassName + "#" + "all");
		// 	// listener.update(testListener);
		// } catch (IOException e) {
		// 	e.printStackTrace();
		// 	// TestListener testListener = new TestListener();
		// 	// testListener.failedTests.add(testClassName + "#" + "all");
		// 	// listener.update(testListener);
		// }

		// // TestListener testListener = new TestListener();
		// // testListener = readTestResult();
		// // listener.update(testListener);

		// return true;




		// TE original code below
		// source, target은 자바 버전 정보, 그러나 쓰이지 않는다.

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		PrintWriter outWriter = new PrintWriter(out);
		PrintWriter errWriter = new PrintWriter(err);
		StringBuffer command = new StringBuffer();
		command.append("-classpath ");
		command.append(classPath);
		command.append(" -d ");
		command.append(targetPath);
		command.append(" -source ");
		command.append(source);
		command.append(" -target ");
		command.append(target);
		command.append(" -bootclasspath /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar");
		command.append(" -extdirs  \"\"");
		command.append(" ");
		command.append(f.getAbsolutePath());
		String cmd = command.toString();
		boolean error = !org.eclipse.jdt.core.compiler.batch.BatchCompiler.compile(cmd, outWriter, errWriter, null);
		//if (DEBUG) {
			System.out.println("[Debug.log] line 158 of Compiler.java : cmd = "+cmd);
			System.out.println("[Debug.log] line 159 of Compiler.java : out = "+out.toString());
			System.out.println("[Debug.log] line 160 of Compiler.java : err = "+err.toString());
		//}
		return error;
	}

	public boolean compile(String source, CompilationUnit unit, String path, boolean writeDown, String version)
			throws IOException, FileNotFoundException {
		return compile(getClass().getClassLoader(), source, unit, path, writeDown, version);
	}

	public boolean compile(ClassLoader loader, String source, CompilationUnit unit, String path, boolean writeDown,
			String version) throws IOException, FileNotFoundException {

		CompilationUnitImpl cu = new CompilationUnitImpl(unit);
		org.eclipse.jdt.internal.compiler.batch.CompilationUnit newUnit = new org.eclipse.jdt.internal.compiler.batch.CompilationUnit(
				source.toCharArray(), new String(cu.getFileName()), "UTF8");

		className = CharOperation.toString(cu.getPackageName()) + "." + new String(cu.getMainTypeName());

		CompilationProgress progress = null;
		CompilerRequestorImpl requestor = new CompilerRequestorImpl();
		CompilerOptions options = new CompilerOptions();
		Map<String, String> optionsMap = new HashMap<>();
		optionsMap.put(CompilerOptions.OPTION_Compliance, version);
		optionsMap.put(CompilerOptions.OPTION_Source, version);
		optionsMap.put(CompilerOptions.OPTION_LineNumberAttribute, CompilerOptions.GENERATE);
		optionsMap.put(CompilerOptions.OPTION_SourceFileAttribute, CompilerOptions.GENERATE);

		options.set(optionsMap);

		org.eclipse.jdt.internal.compiler.Compiler compiler = new org.eclipse.jdt.internal.compiler.Compiler(
				new NameEnvironmentImpl(loader, newUnit), DefaultErrorHandlingPolicies.proceedWithAllProblems(),
				options, requestor, new DefaultProblemFactory(Locale.getDefault()), null, progress);
		compiler.compile(new ICompilationUnit[] { newUnit });

		classes = requestor.getClasses();
		problems = requestor.getProblems();

		boolean error = false;
		for (Iterator<IProblem> it = problems.iterator(); it.hasNext();) {
			IProblem problem = it.next();
			if (problem.isError()) {
				error = true;
				break;
			}
		}

		if (writeDown) {
			for (ClassFile cf : classes) {
				String filePath = CharOperation.charToString(cf.fileName());
				String packagePath = path + File.separator + filePath.substring(0, filePath.lastIndexOf("/") + 1);
				File packageDir = new File(packagePath);
				if (!packageDir.exists()) {
					packageDir.mkdirs();
				}
				File f = new File(path + File.separator + filePath + ".class");
				FileOutputStream fos = new FileOutputStream(f);
				fos.write(cf.getBytes());
				fos.flush();
				fos.close();
			}
		}

		return error;
	}
}
