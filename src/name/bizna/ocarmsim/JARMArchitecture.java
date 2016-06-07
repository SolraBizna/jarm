package name.bizna.ocarmsim;

import java.io.IOException;
import java.util.HashMap;
import java.util.TreeMap;

import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Value;

/* FAKE! */

public class JARMArchitecture {
	private SRAMRegion sram;
	private int module0Size = 0;
	private int module1Size = 0;
	private int module2Size = 0;
	private HashMap<Value, Integer> valueToHandle = new HashMap<Value, Integer>();
	private TreeMap<Integer, Value> handleToValue = new TreeMap<Integer, Value>();
	private int nextValueHandle = 1;
	public void setSRAMRegion(SRAMRegion sram) {
		this.sram = sram;
	}
	public void setModule0Size(int size) {
		module0Size = size;
	}
	public void setModule1Size(int size) {
		module1Size = size;
	}
	public void setModule2Size(int size) {
		module1Size = size;
	}
	public boolean flushNVRAM() {
		try {
			sram.flushToNVRAM();
		}
		catch(IOException e) {
			OCARM.logger.error("IOException while flushing NVRAM: %s", e.toString());
		}
		return true;
	}
	public int getCPUCyclesPerTick() {
		return 25000; // pretending to be 500KHz
	}
	public int getMemoryLatency(int unused) {
		return 1; // pretending to have VERY fast memory of all types
	}
	public int getMemorySize(int module_number) {
		switch(module_number) {
		case 0: return module0Size;
		case 1: return module1Size;
		case 2: return module2Size;
		default: return 0;
		}
	}
	public int mapValue(Value value) {
		Integer wat = valueToHandle.get(value);
		if(wat != null)
			throw new RuntimeException("Same Value mapped more than once");
		else {
			// unhardcode this for the main mod
			//if(handleToValue.size() >= 32) throw new RuntimeException("Too many Values");
			while(nextValueHandle == 0 || handleToValue.get(nextValueHandle) != null) ++nextValueHandle;
			valueToHandle.put(value, nextValueHandle);
			handleToValue.put(nextValueHandle, value);
			System.err.println("Mapped value: "+nextValueHandle);
			return nextValueHandle++;
		}
	}
	public Value getValue(int handle) {
		return handleToValue.get(handle);
	}
	public void disposeValue(int handle, Context ctx) {
		Value v = handleToValue.get(handle);
		if(v != null) {
			System.err.println("Disposed value: "+handle);
			v.dispose(ctx);
			handleToValue.remove(handle);
			valueToHandle.remove(v);
		}
		else System.err.println("NONEXISTENT VALUE DISPOSED: "+handle);
	}
	public void disposeAllValues(Context ctx) {
		System.err.println("Disposed all values");
		for(Value v : valueToHandle.keySet()) {
			v.dispose(ctx);
		}
		valueToHandle.clear();
		handleToValue.clear();
	}
}
