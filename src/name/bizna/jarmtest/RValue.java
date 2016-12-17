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
	private static final Map<String, Class<? extends RValue>> simpleValues = new HashMap<String, Class<? extends RValue>>();
	static {
		simpleValues.put("quitReason", QuitReason.class);
	}
	public static RValue make(String id) {
		try {
			if(simpleValues.containsKey(id)) {
				return simpleValues.get(id).newInstance();
			}
		}
		catch(NumberFormatException e) {}
		catch(InstantiationException e) { e.printStackTrace(); }
		catch(IllegalAccessException e) { e.printStackTrace(); }
		return LValue.make(id);
	}
	public static RValue make(String id, String index) {
		return LValue.make(id, index);
	}
	public static RValue makeIntConstant(String text) {
		try {
			return new IntConstant(Integer.parseInt(text), text);
		}
		catch(NumberFormatException e) {
			return null;
		}
	}
	abstract public int getValue(CPU cpu);
	@Override
	public String toString() {
		return this.getClass().getSimpleName();
	}
}