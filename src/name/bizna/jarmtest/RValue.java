package name.bizna.jarmtest;

import java.util.HashMap;
import java.util.Map;

import name.bizna.jarm.CPU;

abstract class RValue {
	private static class IntConstant extends RValue {
		String origString;
		int n;
		public IntConstant(int n, String origString) {
			this.n = n;
			this.origString = origString;
		}
		@Override
		public int getValue(CPU cpu) { return n; }
		@Override
		public String toString() { return origString; }
	}
	private static class QuitReason extends RValue {
		@SuppressWarnings("unused") public QuitReason() {}
		@Override public int getValue(CPU cpu) { return ((CP7)cpu.getCoprocessor(7)).getQuitReason(); }
	}
	private static class T extends RValue {
		@SuppressWarnings("unused") public T() {}
		@Override public int getValue(CPU cpu) { return cpu.isThumb() ? 1 : 0; }
	}
	private static class A extends RValue {
		@SuppressWarnings("unused") public A() {}
		@Override public int getValue(CPU cpu) { return cpu.usingStrictAlignment() ? 1 : 0; }
	}
	private static class I extends RValue {
		@SuppressWarnings("unused") public I() {}
		@Override public int getValue(CPU cpu) { return cpu.areIRQsEnabled() ? 1 : 0; }
	}
	private static class F extends RValue {
		@SuppressWarnings("unused") public F() {}
		@Override public int getValue(CPU cpu) { return cpu.areFIQsEnabled() ? 1 : 0; }
	}
	private static class M extends RValue {
		@SuppressWarnings("unused") public M() {}
		@Override public int getValue(CPU cpu) { return cpu.getMode(); }
	}
	private static final Map<String, Class<? extends RValue>> simpleValues = new HashMap<String, Class<? extends RValue>>();
	static {
		simpleValues.put("quitReason", QuitReason.class);
		simpleValues.put("T", T.class);
		simpleValues.put("A", A.class);
		simpleValues.put("I", I.class);
		simpleValues.put("F", F.class);
		simpleValues.put("M", M.class);
	}
	public static RValue make(String id) {
		try {
			if(simpleValues.containsKey(id)) {
				return simpleValues.get(id).newInstance();
			}
		}
		catch(NumberFormatException e) { e.printStackTrace(); }
		catch(InstantiationException e) { e.printStackTrace(); }
		catch(IllegalAccessException e) { e.printStackTrace(); }
		return LValue.make(id);
	}
	public static RValue make(String id, String index) {
		return LValue.make(id, index);
	}
	public static RValue makeIntConstant(String text) {
		try {
			if(text.startsWith("0x")) return new IntConstant(Integer.parseUnsignedInt(text.substring(2), 16), text);
			else return new IntConstant(Integer.parseInt(text), text);
		}
		catch(NumberFormatException e) {
			e.printStackTrace();
			return null;
		}
	}
	abstract public int getValue(CPU cpu);
	@Override
	public String toString() {
		return this.getClass().getSimpleName();
	}
}