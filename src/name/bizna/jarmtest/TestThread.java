package name.bizna.jarmtest;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import name.bizna.jarm.CPU;

public class TestThread extends Thread {
	private final List<TestDirectory> list;
	private final AtomicInteger semaphore;
	private int passed = 0, failed = 0;
	private List<String> failures = new LinkedList<String>();
	public TestThread(String name, List<TestDirectory> list, AtomicInteger semaphore) {
		super(name);
		this.list = list;
		this.semaphore = semaphore;
	}
	@Override
	public void run() {
		int cachedSize = list.size();
		int dirIndex;
		CPU cpu = new CPU();
		cpu.mapCoprocessor(7, new CP7(cpu));
		while((dirIndex = semaphore.getAndAdd(1)) < cachedSize) {
			TestDirectory dir = list.get(dirIndex);
			if(dir.runTest(cpu, failures)) ++passed;
			else {
				++failed;
			}
		}
	}
	public int getNumPassed() { return passed; }
	public int getNumFailed() { return failed; }
	public List<String> getFailures() { return failures; }
}