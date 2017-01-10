package name.bizna.jarmtool;

import name.bizna.jarm.AlignmentException;
import name.bizna.jarm.BusErrorException;
import name.bizna.jarm.CPU;
import name.bizna.jarm.EscapeCompleteException;
import name.bizna.jarm.EscapeRetryException;
import name.bizna.jarm.SaneCoprocessor;
import name.bizna.jarm.UndefinedException;
import name.bizna.jarm.VirtualMemorySpace;

public class CP3 extends SaneCoprocessor {
	
	public CP3(CPU cpu) { super(cpu); }
	
	@Override
	public void storeCoprocessorRegisterToMemory(boolean unconditional,
			int coproc, boolean D, int base, int CRd, int option, int iword)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		throw new UndefinedException();
	}

	@Override
	public void loadCoprocessorRegisterFromMemory(boolean unconditional,
			int coproc, boolean D, int base, int CRd, int option, int iword)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		if(CRd != 15) throw new UndefinedException();
		VirtualMemorySpace vm = cpu.getVirtualMemorySpace();
		int strlen = 0;
		try {
			int p = base;
			while(vm.readByte(p++) != 0) strlen++;
		}
		catch(BusErrorException e) {}
		byte[] strarray = new byte[strlen];
		boolean be = false;
		try {
			int p = base;
			int n = 0;
			while(n < strlen) strarray[n++] = vm.readByte(p++);
		}
		catch(BusErrorException e) {
			be = true;
		}
		String error = new String(strarray);
		System.err.println("\nPROGRAM CRASHED: "+error);
		if(be) System.err.println("Also, there was a bus error while fetching the error message.");
		throw new ProgramExit(125);
	}

	@Override
	public void moveCoreRegisterToCoprocessorRegister(boolean unconditional,
			int coproc, int opc1, int opc2, int CRn, int CRm, int Rt)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		throw new UndefinedException();
	}

	@Override
	public void moveCoprocessorRegisterToCoreRegister(boolean unconditional,
			int coproc, int opc1, int opc2, int CRn, int CRm, int Rt)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		throw new UndefinedException();
	}

	@Override
	public void coprocessorDataOperation(boolean unconditional, int coproc,
			int opc1, int opc2, int CRn, int CRm, int CRd)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		throw new UndefinedException();
	}

	@Override
	public void moveCoreRegistersToCoprocessorRegister(boolean unconditional,
			int coproc, int opc1, int CRm, int Rt, int Rt2)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		throw new UndefinedException();
	}

	@Override
	public void moveCoprocessorRegisterToCoreRegisters(boolean unconditional,
			int coproc, int opc1, int CRm, int Rt, int Rt2)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		throw new UndefinedException();
	}

	@Override
	public void reset() {
	}

}
