package name.bizna.jarm;

/**
 *
 * @author Jean-Rémy Buchs <jrb0001@692b8c32.de>
 */
public abstract class Debugger {

	protected final CPU cpu;

	public Debugger() {
		cpu = new CPU(this);
	}

	public CPU getCpu() {
		return cpu;
	}

	public abstract void onReadMemory(int addr, int size, boolean bigEndian);

	public abstract void onWriteMemory(int addr, int size, boolean bigEndian, long value);

	public abstract void onInstruction(CPU cpu, int addr);

	public abstract void onSignal(String name, Object[] args);
}
