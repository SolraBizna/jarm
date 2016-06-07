package name.bizna.ocarm;

import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.machine.Architecture.NoMemoryRequirements;

@Architecture.Name("OC-ARM (ROMDev)")
@NoMemoryRequirements
public class JARMROMDevArchitecture extends JARMArchitecture {

	public JARMROMDevArchitecture(Machine machine) {
		super(machine);
	}
	
	@Override
	protected boolean shouldDumpCore() {
		return OCARM.instance.shouldDumpCore();
	}
	
	@Override
	public boolean initialize() {
		boolean ret = super.initialize();
		if(ret)
			cpu.setExceptionDebugMode(true);
		return ret;
	}

}
