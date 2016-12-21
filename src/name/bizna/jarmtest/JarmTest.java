package name.bizna.jarmtest;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import name.bizna.jarm.CPU;

public class JarmTest {
	
	private static void printUsageString() {
		System.out.println("Usage: JarmTest [options]");
		System.out.println("Options:");
		System.out.println("--baseDir <path...>: Change the base directory. If not specified, the working directory is used.");
		System.out.println("--threads <count>: Number of threads to use. If not specified, one thread is used per CPU.");
	}
	
	private static void recursivelyBuildTestList(List<TestDirectory> tests, File cwd, String canonPath) {
		assert(cwd.isDirectory());
		if(TestDirectory.isValidTestDir(cwd)) tests.add(new TestDirectory(cwd, canonPath));
		for(File child : cwd.listFiles()) {
			if(child.isDirectory())
				recursivelyBuildTestList(tests, child, canonPath == null ? child.getName() : (canonPath + File.separator + child.getName()));
		}
	}
	
	public static void main(String[] args) {
		File baseDirectory = new File(System.getProperty("user.dir"));
		int threadCount = Runtime.getRuntime().availableProcessors();
		int i = 0;
		boolean commandLineValid = true;
		while(i < args.length) {
			String arg = args[i++];
			if(arg.equals("--baseDir")) {
				if(i >= args.length) { System.err.println("--baseDir requires an argument"); commandLineValid = false; }
				else {
					baseDirectory = new File(args[i++]);
					if(!baseDirectory.isDirectory()) {
						System.err.println("--baseDir argument must be a directory");
						commandLineValid = false;
					}
				}
			}
			else if(arg.equals("--threads")) {
				if(i >= args.length) { System.err.println("--threads requires an argument"); commandLineValid = false; }
				else {
					try {
						threadCount = Integer.parseInt(args[i++]);
						if(threadCount < 1 || threadCount > 32)
							throw new NumberFormatException();
					}
					catch(NumberFormatException e) {
						System.err.println("--threads argument must be between 1 and 32");
						commandLineValid = false;
					}
				}
			}
			else {
				System.err.println("Unknown argument");
				commandLineValid = false;
			}
		}
		if(!commandLineValid) { printUsageString(); System.exit(1); }
		List<TestDirectory> tests = new ArrayList<TestDirectory>();
		recursivelyBuildTestList(tests, baseDirectory, null);
		int passed = 0, failed = 0;
		List<String> failures;
		if(threadCount == 1) {
			failures = new ArrayList<String>();
			// Don't bother actually making a separate thread
			CPU cpu = new CPU();
			cpu.mapCoprocessor(7, new CP7(cpu));
			for(TestDirectory test : tests) {
				if(test.runTest(cpu, failures)) ++passed;
				else ++failed;
			}
		}
		else {
			TestThread threads[] = new TestThread[threadCount];
			AtomicInteger semaphore = new AtomicInteger(0);
			for(int n = 0; n < threadCount; ++n)
				threads[n] = new TestThread("TestThread-"+n, tests, semaphore);
			for(int n = 0; n < threadCount; ++n)
				threads[n].start();
			int totalFailCount = 0;
			for(int n = 0; n < threadCount; ++n) {
				while(true) {
					try {
						threads[n].join();
						break;
					}
					catch(InterruptedException e) {
						// try again
					}
				}
				totalFailCount += threads[n].getFailures().size();
				passed += threads[n].getNumPassed();
				failed += threads[n].getNumFailed();
			}
			failures = new ArrayList<String>(totalFailCount);
			for(int n = 0; n < threadCount; ++n) {
				failures.addAll(threads[n].getFailures());
			}
		}
		System.out.println(String.format("%d dir%s passed, %d dir%s failed.", passed, passed == 1 ? "" : "s", failed, failed == 1 ? "" : "s"));
		if(failed > 0) {
			System.out.println("Failed tests:");
			failures.sort(null);
			for(String failure : failures) {
				System.out.println("  "+failure);
			}
			System.exit(1);
		}
	}

}
