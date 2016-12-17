package name.bizna.jarmtest;

import java.util.HashMap;
import java.util.Map;

import name.bizna.jarm.CPU;

abstract class LValue extends RValue {
	private static class RegisterAccessor extends LValue {
		int regnum;
		public RegisterAccessor(int regnum) {
			this.regnum = regnum;
		}
		@Override
		public int getValue(CPU cpu) {
			return cpu.readRegister(regnum);
		}
		@Override
		public void setValue(CPU cpu, int value) {
			cpu.writeRegister(regnum, value);
		}
		@Override
		public String toString() {
			return "r"+regnum;
		}
	}
	private static class C extends LValue {
		@SuppressWarnings("unused") public C() {}
		@Override public int getValue(CPU cpu) { return cpu.conditionC() ? 1 : 0; }
		@Override public void setValue(CPU cpu, int value) { cpu.setConditionC(value != 0); }
	}
	private static class N extends LValue {
		@SuppressWarnings("unused") public N() {}
		@Override public int getValue(CPU cpu) { return cpu.conditionN() ? 1 : 0; }
		@Override public void setValue(CPU cpu, int value) { cpu.setConditionN(value != 0); }
	}
	private static class Q extends LValue {
		@SuppressWarnings("unused") public Q() {}
		@Override public int getValue(CPU cpu) { return cpu.conditionQ() ? 1 : 0; }
		@Override public void setValue(CPU cpu, int value) { cpu.setConditionQ(value != 0); }
	}
	private static class V extends LValue {
		@SuppressWarnings("unused") public V() {}
		@Override public int getValue(CPU cpu) { return cpu.conditionV() ? 1 : 0; }
		@Override public void setValue(CPU cpu, int value) { cpu.setConditionV(value != 0); }
	}
	private static class Z extends LValue {
		@SuppressWarnings("unused") public Z() {}
		@Override public int getValue(CPU cpu) { return cpu.conditionZ() ? 1 : 0; }
		@Override public void setValue(CPU cpu, int value) { cpu.setConditionZ(value != 0); }
	}
	private static final Map<String, Class<? extends LValue>> simpleValues = new HashMap<String, Class<? extends LValue>>();
	static {
		simpleValues.put("C", C.class);
		simpleValues.put("N", N.class);
		simpleValues.put("Q", Q.class);
		simpleValues.put("V", V.class);
		simpleValues.put("Z", Z.class);
	}
	public static LValue make(String id) {
		try {
			if(simpleValues.containsKey(id)) {
				return simpleValues.get(id).newInstance();
			}
			else if(id.startsWith("r")) {
				int index = Integer.parseInt(id.substring(1));
				if(index >= 0 && index <= 15) return new RegisterAccessor(index);
			}
		}
		catch(NumberFormatException e) {}
		catch(InstantiationException e) { e.printStackTrace(); }
		catch(IllegalAccessException e) { e.printStackTrace(); }
		return null;
	}
	public static LValue make(String id, String index) {
		return null;
	}
	abstract public void setValue(CPU cpu, int value);
}