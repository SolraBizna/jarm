package name.bizna.jarmtest;

import name.bizna.jarm.AlignmentException;
import name.bizna.jarm.BusErrorException;
import name.bizna.jarm.CPU;
import name.bizna.jarm.EscapeCompleteException;
import name.bizna.jarm.EscapeRetryException;
import name.bizna.jarm.SaneCoprocessor;
import name.bizna.jarm.UndefinedException;

public class CP7 extends SaneCoprocessor {
	
	private int quitReason = -1;
	
	public CP7(CPU cpu) { super(cpu); }

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
		throw new UndefinedException();
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
		if(opc2 != 0 || CRn != 0 || CRm != 0 || CRd != 0) throw new UndefinedException();
		quitReason = opc1;
		throw new EscapeCompleteException();
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
		quitReason = -1;
	}
	
	public int getQuitReason() {
		return quitReason;
	}

	public void setQuitReason(int i) {
		quitReason = i;
	}

}
