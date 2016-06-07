package name.bizna.ocarmsim;

import li.cil.oc.api.machine.Signal;

public class SimSignal implements Signal {

	private String name;
	private Object[] args;
	
	public SimSignal(String name, Object[] args) {
		this.name = name;
		this.args = args;
	}
	
	@Override
	public String name() {
		return name;
	}

	@Override
	public Object[] args() {
		return args;
	}

}
