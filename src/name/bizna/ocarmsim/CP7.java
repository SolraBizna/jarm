package name.bizna.ocarmsim;

import java.nio.BufferOverflowException;
import java.nio.CharBuffer;

import name.bizna.jarm.AlignmentException;
import name.bizna.jarm.BusErrorException;
import name.bizna.jarm.CPU;
import name.bizna.jarm.EscapeCompleteException;
import name.bizna.jarm.EscapeRetryException;
import name.bizna.jarm.SaneCoprocessor;
import name.bizna.jarm.UndefinedException;

public class CP7 extends SaneCoprocessor {

	CharBuffer buffer = CharBuffer.allocate(1024);
	
	CP7(CPU cpu) { super(cpu); }
	
	private void flush() {
		buffer.flip();
		if(buffer.limit() > 0) OCARM.logger.info("SerialDebug: "+buffer.toString());
		else OCARM.logger.info("SerialDebug");
		buffer.clear();
	}

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
		if(opc1 == 0 && opc2 == 0 && CRn == 0 && CRm == 0) {
			int codepoint = cpu.readRegister(Rt);
			if(codepoint > 0x10FFFF || codepoint < 0) throw new UndefinedException();
			char[] chars = Character.toChars(codepoint);
			for(char c : chars) {
				try {
					buffer.append(c);
				}
				catch(BufferOverflowException e) {
					flush();
					buffer.append(c);
					/* will not overflow a second time */
				}
			}
			return;
		}
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
		switch(opc1) {
		case 0:
			flush();
			return;
		case 1:
			cpu.dumpState(System.err);
			return;
		}
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
	public void reset() {}

}
