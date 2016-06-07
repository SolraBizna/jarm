package name.bizna.jarm;

/**
 * Implements a coprocessor that behaves in a sane manner. It only implements the standard coprocessor instructions,
 * and only uses the standard instruction encodings.
 */
public abstract class SaneCoprocessor extends Coprocessor {
	protected final CPU cpu;
	protected SaneCoprocessor(CPU cpu) { this.cpu = cpu; }
	/**
	 * STC instruction; store a coprocessor register in memory.
	 * Coprocessor registers stored this way may be of any size and so access arbitrary memory starting at base.
	 * @param unconditional true for STC2 instruction, false for STC instruction. Conventionally ignored.
	 * @param coproc The coprocessor number being used. (Only needed for "fat coprocessors".)
	 * @param D bit 21 of instruction word. Conventionally ignored.
	 * @param CRd The coprocessor register number to store.
	 * @param base The address to begin storing at.
	 * @param option An implementation-defined option from 0 to 255, or -1 if an offset was applied
	 * @param iword The raw instruction word (useful for things like VFP's weird store multiple encodings)
	 */
	public abstract void storeCoprocessorRegisterToMemory(boolean unconditional, int coproc, boolean D, int base, int CRd, int option, int iword) throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException;
	/**
	 * LDC instruction; load a coprocessor register from memory.
	 * Coprocessor registers stored this way may be of any size and so access arbitrary memory starting at base.
	 * @param unconditional true for LDC2 instruction, false for LDC instruction. Conventionally ignored.
	 * @param coproc The coprocessor number being used. (Only needed for "fat coprocessors".)
	 * @param D bit 21 of instruction word. Conventionally ignored.
	 * @param CRd The coprocessor register number to load.
	 * @param base The address to begin loading at.
	 * @param option An implementation-defined option from 0 to 255, or -1 if an offset was applied
	 * @param iword The raw instruction word (useful for things like VFP's weird store multiple encodings)
	 */
	public abstract void loadCoprocessorRegisterFromMemory(boolean unconditional, int coproc, boolean D, int base, int CRd, int option, int iword) throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException;
	/**
	 * MCR instruction; move to a coprocessor register from an ARM core register.
	 * opc1, opc2, CRn, and CRm are interpreted in coprocessor-specific ways.
	 * @param unconditional true for MCR2 instruction, false for MCR instruction. Conventionally ignored.
	 * @param coproc The coprocessor number being used. (Only needed for "fat coprocessors".)
	 * @param opc1 A coprocessor-specific opcode in the range 0 to 7.
	 * @param opc2 A coprocessor-specific opcode in the range 0 to 7.
	 * @param CRn A coprocessor register number.
	 * @param CRm Another coprocessor register number.
	 * @param Rt The core register number to move from.
	 */
	public abstract void moveCoreRegisterToCoprocessorRegister(boolean unconditional, int coproc, int opc1, int opc2, int CRn, int CRm, int Rt) throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException;
	/**
	 * MRC instruction; move to an ARM core register from a coprocessor register.
	 * opc1, opc2, CRn, and CRm are interpreted in coprocessor-specific ways.
	 * @param unconditional true for MRC2 instruction, false for MRC instruction. Conventionally ignored.
	 * @param coproc The coprocessor number being used. (Only needed for "fat coprocessors".)
	 * @param opc1 A coprocessor-specific opcode in the range 0 to 7.
	 * @param opc2 A coprocessor-specific opcode in the range 0 to 7.
	 * @param CRn A coprocessor register number.
	 * @param CRm Another coprocessor register number.
	 * @param Rt The core register number to move to.
	 */
	public abstract void moveCoprocessorRegisterToCoreRegister(boolean unconditional, int coproc, int opc1, int opc2, int CRn, int CRm, int Rt) throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException;
	/**
	 * CDP instruction; perform coprocessor operation that does not act on ARM core registers or memory.
	 * opc1, opc2, CRn, CRd, and CRm are interpreted in coprocessor-specific ways.
	 * @param unconditional true for CDP2 instruction, false for CDP instruction. Conventionally ignored.
	 * @param coproc The coprocessor number being used. (Only needed for "fat coprocessors".)
	 * @param opc1 A coprocessor-specific opcode in the range 0 to 15.
	 * @param opc2 A coprocessor-specific opcode in the range 0 to 7.
	 * @param CRn A coprocessor register number. Normally the first source operand.
	 * @param CRm Another coprocessor register number. Normally the second source operand.
	 * @param CRd Another coprocessor register number. Normally the destination.
	 */
	public abstract void coprocessorDataOperation(boolean unconditional, int coproc, int opc1, int opc2, int CRn, int CRm, int CRd) throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException;
	/**
	 * MCRR instruction; move to a coprocessor register from two ARM core registers.
	 * opc1 and CRm are interpreted in coprocessor-specific ways.
	 * @param unconditional true for MCRR2 instruction, false for MCRR instruction. Conventionally ignored.
	 * @param coproc The coprocessor number being used. (Only needed for "fat coprocessors".)
	 * @param opc1 A coprocessor-specific opcode in the range 0 to 15.
	 * @param CRm The coprocessor register number.
	 * @param Rt The first core register to move from. Conventionally, this register provides the low-order 32 bits.
	 * @param Rt2 The second core register to move from. Conventionally, this register provides the high-order 32 bits.
	 */
	public abstract void moveCoreRegistersToCoprocessorRegister(boolean unconditional, int coproc, int opc1, int CRm, int Rt, int Rt2) throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException;
	/**
	 * MRRC instruction; move to two ARM core registers from a coprocessor register.
	 * opc1 and CRm are interpreted in coprocessor-specific ways.
	 * @param unconditional true for MRRC2 instruction, false for MRRC instruction. Conventionally ignored.
	 * @param coproc The coprocessor number being used. (Only needed for "fat coprocessors".)
	 * @param opc1 A coprocessor-specific opcode in the range 0 to 15.
	 * @param CRm The coprocessor register number.
	 * @param Rt The first core register to move to. Conventionally, this register accepts the low-order 32 bits.
	 * @param Rt2 The second core register to move to. Conventionally, this register accepts the high-order 32 bits.
	 */
	public abstract void moveCoprocessorRegisterToCoreRegisters(boolean unconditional, int coproc, int opc1, int CRm, int Rt, int Rt2) throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException;
	@Override
	public void executeInstruction(boolean unconditional, int iword) throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException {
		int Rn = (iword >> 16) & 15;
		boolean op = (iword & 16) != 0;
		int op1 = (iword >> 20) & 63;
		int coproc = (iword >> 8) & 15;
		if((op1 & 62) == 4) {
			int Rt = (iword>>12)&15;
			int Rt2 = (iword>>16)&15;
			int opc1 = (iword>>4)&15;
			int CRm = iword&15;
			if(op1 == 4)
				/* MCRR/MCRR2 (A8-478) */
				moveCoreRegistersToCoprocessorRegister(unconditional, coproc, opc1, CRm, Rt, Rt2);
			else
				/* MRRC/MRRC2 (A8-494) */
				moveCoprocessorRegisterToCoreRegisters(unconditional, coproc, opc1, CRm, Rt, Rt2);
			return;
		}
		else {
			switch(op1 & 49) {
			case 0:	case 16:
				/* STC/STC2 (A8-662) */
			{
				/* one of P/U/D/W must be set */
				if(((iword>>21)&15) == 0) throw new UndefinedException();
				boolean P = (iword & (1<<24)) != 0;
				boolean U = (iword & (1<<23)) != 0;
				boolean D = (iword & (1<<22)) != 0;
				boolean W = (iword & (1<<21)) != 0;
				int CRd = (iword >>> 12) & 15;
				int imm8 = iword & 255;
				int imm32 = imm8 << 2;
				int baseAddr = cpu.readRegister(Rn);
				int offsetAddr;
				if(U) offsetAddr = baseAddr + imm32;
				else offsetAddr = baseAddr - imm32;
				storeCoprocessorRegisterToMemory(unconditional, coproc, D, P?offsetAddr:baseAddr, CRd, !P && !W && U ? imm8 : -1, iword);
				if(W) cpu.writeRegister(Rn, offsetAddr);
				return;
			}
			case 1: case 17:
			{
				/* LDC/LDC2 (immediate, A8-392) */
				/* LDC/LDC2 (literal, A8-394) */
				/* one of P/U/D/W must be set */
				if(((iword>>21)&15) == 0) throw new UndefinedException();
				boolean P = (iword & (1<<24)) != 0;
				boolean U = (iword & (1<<23)) != 0;
				boolean D = (iword & (1<<22)) != 0;
				boolean W = (iword & (1<<21)) != 0;
				if(Rn == 15 && W) throw new UndefinedException();
				int CRd = (iword >>> 12) & 15;
				int imm8 = iword & 255;
				int imm32 = imm8 << 2;
				int baseAddr = cpu.readRegisterAlignPC(Rn);
				int offsetAddr;
				if(U) offsetAddr = baseAddr + imm32;
				else offsetAddr = baseAddr - imm32;
				loadCoprocessorRegisterFromMemory(unconditional, coproc, D, P?offsetAddr:baseAddr, CRd, !P && !W && U ? imm8 : -1, iword);
				if(W) cpu.writeRegister(Rn, offsetAddr);
				return;
			}
			case 32:
			case 33:
				if(!op) {
					/* CDP/CDP2 (A8-358) */
					int opc1 = (iword>>20)&15;
					int CRn = (iword>>16)&15;
					int CRd = (iword>>12)&15;
					int opc2 = (iword>>5)&7;
					int CRm = iword&15;
					coprocessorDataOperation(unconditional, coproc, opc1, opc2, CRn, CRm, CRd);
				}
				else {
					/* MCR/MCR2 (A8-476), MRC/MRC2 (A8-492) */
					int opc1 = (iword>>21)&7;
					int CRn = (iword>>16)&15;
					int Rt = (iword>>12)&15;
					int opc2 = (iword>>5)&7;
					int CRm = iword&15;
					if((op1 & 1) == 0)
						moveCoreRegisterToCoprocessorRegister(unconditional, coproc, opc1, opc2, CRn, CRm, Rt);
					else
						moveCoprocessorRegisterToCoreRegister(unconditional, coproc, opc1, opc2, CRn, CRm, Rt);
				}
				return;
			}
		}
		throw new UndefinedException();
	}
}
