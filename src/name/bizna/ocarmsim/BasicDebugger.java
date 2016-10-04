package name.bizna.ocarmsim;

import java.awt.Component;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import li.cil.oc.api.machine.ExecutionResult;
import name.bizna.jarm.AlignmentException;
import name.bizna.jarm.BusErrorException;
import name.bizna.jarm.CPU;
import name.bizna.jarm.Debugger;
import name.bizna.jarm.UndefinedException;
import name.bizna.jarm.UnimplementedInstructionException;
import name.bizna.jarm.VirtualMemorySpace;

/**
 *
 * @author Jean-Rémy Buchs <jrb0001@692b8c32.de>
 */
public abstract class BasicDebugger extends Debugger implements Runnable {

	private State state;
	private long sleep;
	private String reason;
	private boolean singleStep;
	private final Set<Breakpoint> breakpoints = new HashSet<>();
	private final Set<Breakpoint> readWatchpoints = new HashSet<>();
	private final Set<Breakpoint> writeWatchpoints = new HashSet<>();

	public synchronized void reset() {
		// Reset CPU.
		cpu.reset(false, true, true);

		// Reset components.
		((FakeMachine) ((CP3) cpu.getCoprocessor(3)).getMachine()).getComponents().forEach((c) -> {
			c.reset();
		});

		// Reset simulator.
		setState(State.PAUSED);
		sleep = 0;
	}

	@Override
	public void run() {
		simulate();
	}

	public void simulate() {
		switch (state) {
			case PAUSED:
			case CRASHED:
			case FAILED:
				return;
			case SLEEPING:
				long wait;
				synchronized (this) {
					while ((wait = sleep - System.nanoTime()) > 0) {
						try {
							wait(wait / 1000000, (int) (wait % 1000000));

						} catch (InterruptedException ex) {
							Logger.getLogger(BasicDebugger.class.getName()).log(Level.SEVERE, null, ex);
						}
					}
					setState(State.RUNNING);
				}
				break;
			case RUNNING:
				try {
					cpu.zeroBudget(false);
					if (!((CP3) cpu.getCoprocessor(3)).mayExecute()) {
						((CP3) cpu.getCoprocessor(3)).runSynchronized();
					} else {
						switch (state) {
							case RUNNING:
								try {
									cpu.execute(1);
								} catch (BreakpointException ignored) {
									setState(State.PAUSED);
								}

								if (singleStep) {
									setState(State.PAUSED);
									singleStep = false;
								}
								break;
							default:
								break;
						}
					}

					ExecutionResult ret = ((CP3) cpu.getCoprocessor(3)).getExecutionResult();
					if (ret != null) {
						if (ret instanceof ExecutionResult.Sleep) {
							sleep(((ExecutionResult.Sleep) ret).ticks * 50000000L);
						} else if (ret instanceof ExecutionResult.Error) {
							crash("CRASH: " + ((ExecutionResult.Error) ret).message);
						} else if (ret instanceof ExecutionResult.Shutdown) {
							crash("Computer is shutting down.");
						} else if (ret instanceof ExecutionResult.SynchronizedCall) {
							((CP3) cpu.getCoprocessor(3)).runSynchronized();
						}
					}
				} catch (UnimplementedInstructionException e) {
					crash("UNIMPLEMENTED INSTRUCTION: " + e.toString());
					cpu.dumpState(System.err);
				} catch (Exception e) {
					crash("Exception in cpu.execute: " + e.toString());
					e.printStackTrace(System.err);
					if (e instanceof BusErrorException) {
						VirtualMemorySpace vm = cpu.getVirtualMemorySpace();
						OCARM.logger.error("While attempting a %d-bit %s at %08X", 8 << vm.getLastAccessWidth(), vm.getLastAccessWasStore() ? "store" : "load", vm.getLastAccessAddress());
					}
					cpu.dumpState(System.err);
					if (!(e instanceof AlignmentException || e instanceof BusErrorException || e instanceof UndefinedException)) {
						fail(e.toString());
					}
				}
				break;
		}
	}

	protected synchronized void sleep(long nanos) {
		System.out.println((nanos / 1000000) + "ms");
		sleep = System.nanoTime() + nanos;
		if (state == State.RUNNING) {
			setState(State.SLEEPING);
		}
	}

	protected void crash(String reason) {
		OCARM.logger.error("CRASH: " + reason);
		this.reason = reason;
		setState(State.CRASHED);
	}

	protected void fail(String reason) {
		OCARM.logger.error("FAIL: " + reason);
		this.reason = reason;
		setState(State.FAILED);
	}

	protected void step() {
		if (state == State.PAUSED) {
			setState(State.SLEEPING);
		}

		singleStep = true;
		simulate();
	}

	protected void go() {
		if (state == State.PAUSED) {
			setState(State.SLEEPING);
		}

		singleStep = false;
		simulate();
	}

	protected void pause() {
		if (state == State.RUNNING || state == State.SLEEPING) {
			setState(State.PAUSED);
		}
	}

	protected void addBreakpoint(Breakpoint point) {
		breakpoints.add(point);
	}

	protected void addReadWatchpoint(Breakpoint point) {
		readWatchpoints.add(point);
	}

	protected void addWriteWatchpoint(Breakpoint point) {
		writeWatchpoints.add(point);
	}

	protected void removeBreakpoint(Breakpoint point) {
		breakpoints.remove(point);
	}

	protected void removeReadWatchpoint(Breakpoint point) {
		readWatchpoints.remove(point);
	}

	protected void removeWriteWatchpoint(Breakpoint point) {
		writeWatchpoints.remove(point);
	}

	public Set<Breakpoint> getBreakpoints() {
		return Collections.unmodifiableSet(breakpoints);
	}

	public Set<Breakpoint> getReadWatchpoints() {
		return Collections.unmodifiableSet(readWatchpoints);
	}

	public Set<Breakpoint> getWriteWatchpoints() {
		return Collections.unmodifiableSet(writeWatchpoints);
	}

	public State getState() {
		return state;
	}

	private synchronized void setState(State state) {
		this.state = state;
		notifyAll();
	}

	@Override
	public void onReadMemory(int addr, int size, boolean bigEndian) {
		if (readWatchpoints.stream().filter(point -> (point.getAddress() & 0xFFFFFFFFL) < (addr & 0xFFFFFFFFL) + size && (addr & 0xFFFFFFFFL) < (point.getAddress() & 0xFFFFFFFFL) + (point.getLength() & 0xFFFFFFFFL)).count() > 0) {
			throw new BreakpointException();
		}
	}

	@Override
	public void onWriteMemory(int addr, int size, boolean bigEndian, long value) {
		if (writeWatchpoints.stream().filter(point -> (point.getAddress() & 0xFFFFFFFFL) < (addr & 0xFFFFFFFFL) + size && (addr & 0xFFFFFFFFL) < (point.getAddress() & 0xFFFFFFFFL) + (point.getLength() & 0xFFFFFFFFL)).count() > 0) {
			throw new BreakpointException();
		}
	}

	@Override
	public void onInstruction(CPU cpu, int addr) {
		final int size = 4;
		if (breakpoints.stream().filter(point -> (point.getAddress() & 0xFFFFFFFFL) < (addr & 0xFFFFFFFFL) + size && (addr & 0xFFFFFFFFL) < (point.getAddress() & 0xFFFFFFFFL) + (point.getLength() & 0xFFFFFFFFL)).count() > 0) {
			throw new BreakpointException();
		}
	}

	@Override
	public synchronized void onSignal(String name, Object[] args) {
		sleep = System.nanoTime();
		notifyAll();
	}

	public abstract Component getComponent();

	public static enum State {
		RUNNING, SLEEPING, PAUSED, CRASHED, FAILED
	}
}
