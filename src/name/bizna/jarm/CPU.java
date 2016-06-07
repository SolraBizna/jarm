package name.bizna.jarm;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.TreeSet;

public final class CPU {
	/*** CONSTANTS ***/
	/* processor modes; HYP is PL2, USER is PL0, all others are PL1 (B1-1139) */
	public static enum ProcessorMode {
		USER(16, 0, 0, false, true),
		SYSTEM(31, 0, 0, true, true),
		HYP(26, 1, 0, true, false), /* need Virtualization Extensions */
		FIQ(17, 2, 1, true, true),
		IRQ(18, 3, 2, true, true),
		SUPERVISOR(19, 4, 3, true, true),
		MONITOR(22, 5, 4, true, false), /* need Security Extensions */
		ABORT(23, 6, 5, true, true),
		UNDEFINED(27, 7, 6, true, true);
		private static final HashMap<Integer, ProcessorMode> modeMap = new HashMap<Integer, ProcessorMode>();
		public final int modeRepresentation;
		public final int spIndex, spsrIndex, lrIndex;
		public final boolean privileged, supported;
		ProcessorMode(int modeRepresentation, int spIndex, int lrIndex, boolean privileged, boolean supported) {
			this.modeRepresentation = modeRepresentation;
			this.spIndex = spIndex;
			this.spsrIndex = spIndex-1;
			this.lrIndex = lrIndex;
			this.privileged = privileged;
			this.supported = supported;
		}
		private static final ProcessorMode getModeFromRepresentation(int rep) {
			return modeMap.get(Integer.valueOf(rep));
		}
		static {
			for(ProcessorMode mode : ProcessorMode.values()) {
				modeMap.put(Integer.valueOf(mode.modeRepresentation), mode);
			}
		}
	}
	private static final int EXCEPTION_VECTOR_RESET = 0;
	private static final int EXCEPTION_VECTOR_UNDEFINED = 1;
	private static final int EXCEPTION_VECTOR_SUPERVISOR_CALL = 2;
	private static final int EXCEPTION_VECTOR_PREFETCH_ABORT = 3;
	private static final int EXCEPTION_VECTOR_DATA_ABORT = 4;
	/* vector #5 only used when Virtualization Extensions are present */
	private static final int EXCEPTION_VECTOR_IRQ = 6;
	private static final int EXCEPTION_VECTOR_FIQ = 7;
	/* APSR/CPSR bits (B1-1148) */
	private static final int CPSR_BIT_N = 31;
	private static final int CPSR_BIT_Z = 30;
	private static final int CPSR_BIT_C = 29;
	private static final int CPSR_BIT_V = 28;
	private static final int CPSR_MASK_CLEAR_CONDITIONS = ~0 >>> 4;
	private static final int CPSR_BIT_Q = 27;
	private static final int CPSR_SHIFT_ITLO = 25;
	private static final int CPSR_MASK_ITLO = 3;
	private static final int CPSR_SHIFT_ITHI = 10;
	private static final int CPSR_MASK_ITHI = 63;
	private static final int CPSR_POSTSHIFT_ITHI = 2;
	private static final int CPSR_BIT_J = 24;
	private static final int CPSR_SHIFT_GE = 16;
	private static final int CPSR_MASK_GE = 15;
	private static final int CPSR_BIT_E = 9;
	private static final int CPSR_BIT_A = 8;
	private static final int CPSR_BIT_I = 7;
	private static final int CPSR_BIT_F = 6;
	private static final int CPSR_BIT_T = 5;
	private static final int CPSR_MASK_M = 0x1F;
	private static final int APSR_READ_MASK = 0xF80F0100;
	private static final int APSR_WRITE_MASK = 0xF80F0000;
	private static final int CPSR_READ_MASK = 0xFFFFFFFF;
	/* the ARM ARM ARM says we should be able to write the E bit this way but also deprecates it
	 * we'll let them write it because it costs us nothing
	 * also! don't write the mode here, we'll handle that specially in code that writes CPSR */
	private static final int CPSR_WRITE_MASK = 0xF80F03C0;
	/* exception returns always restore the whole CPSR */
	private static final int SPSR_READ_MASK = 0xFFFFFFFF;
	/* as above, don't write the mode here, we'll handle that specially in code */
	private static final int SPSR_WRITE_MASK = 0xFF0FFFEF;
	/*** REGISTERS ***/
	private int gpr[] = new int[13];
	private int gprFIQ[] = new int[5];
	private int sp[] = new int[8];
	private int lr[] = new int[7];
	private int cur_sp, cur_lr;
	private int pc;
	public int readGPR(int r) {
		if(mode == ProcessorMode.FIQ && r >= 8) return gprFIQ[r-8];
		else return gpr[r];
	}
	public void writeGPR(int r, int new_value) {
		if(mode == ProcessorMode.FIQ && r >= 8) gprFIQ[r-8] = new_value;
		else gpr[r] = new_value;
	}
	public int readSP() { return sp[mode.spIndex]; }
	public void writeSP(int new_value) { sp[mode.spIndex] = new_value; }
	public int readLR() { return lr[mode.lrIndex]; }
	public void writeLR(int new_value) { lr[mode.lrIndex] = new_value; }
	public int readPC() { return isThumb() ? pc + 2 : pc + 4; }
	public void interworkingBranch(int new_pc) {
		if((new_pc & 1) != 0) {
			/* THUMB jump */
			cpsr |= 1<<CPSR_BIT_T;
			pc = new_pc & ~1;
		}
		else {
			/* ARM jump */
			cpsr &= ~(1<<CPSR_BIT_T);
			pc = new_pc & ~3;
		}
	}
	public void branch(int new_pc) {
		if(isThumb()) pc = new_pc & ~1;
		else pc = new_pc & ~3;
	}
	public void loadPC(int new_pc) {
		interworkingBranch(new_pc);
	}
	public void writePC(int new_pc) {
		if(isThumb()) pc = new_pc & ~1;
		else interworkingBranch(new_pc);
	}
	public int readRegister(int r) {
		switch(r) {
		case 13: return readSP();
		case 14: return readLR();
		case 15: return readPC();
		default: return readGPR(r);
		}
	}
	public int readRegisterAlignPC(int r) {
		switch(r) {
		case 13: return readSP();
		case 14: return readLR();
		case 15: return readPC()&~3;
		default: return readGPR(r);
		}
	}
	public void writeRegister(int r, int new_value) {
		switch(r) {
		case 13: writeSP(new_value); break;
		case 14: writeLR(new_value); break;
		case 15: writePC(new_value); break;
		default: writeGPR(r, new_value); break;
		}
	}
	/*** CPSR/APSR ***/
	private int cpsr;
	private int spsr[] = new int[7];
	private ProcessorMode mode;
	public ProcessorMode getProcessorMode() { return mode; }
	/* does not sanity-check the operation! */
	private void setProcessorMode(ProcessorMode newMode) {
		cpsr = (cpsr & ~CPSR_MASK_M) | newMode.modeRepresentation;
		cur_sp = newMode.spIndex;
		cur_lr = newMode.lrIndex;
		mode = newMode;
	}
	private void setProcessorMode(int newModeRep) {
		ProcessorMode newMode = ProcessorMode.getModeFromRepresentation(newModeRep);
		if(newMode == null) throw new NullPointerException();
		setProcessorMode(newMode);
	}
	private void enterProcessorModeByException(ProcessorMode newMode) {
		if(newMode.spsrIndex >= 0)
			spsr[newMode.spsrIndex] = cpsr;
		setProcessorMode(newMode);
	}
	private void returnFromProcessorMode() {
		if(mode.spsrIndex >= 0) {
			ProcessorMode newMode = ProcessorMode.getModeFromRepresentation(spsr[mode.spsrIndex] & CPSR_MASK_M);
			assert(newMode != null);
			cpsr = spsr[mode.spsrIndex];
			cur_sp = newMode.spIndex;
			cur_lr = newMode.lrIndex;
			mode = newMode;
		}
	}
	public boolean conditionN() { return (cpsr & (1<<CPSR_BIT_N)) != 0; }
	public boolean conditionZ() { return (cpsr & (1<<CPSR_BIT_Z)) != 0; }
	public boolean conditionC() { return (cpsr & (1<<CPSR_BIT_C)) != 0; }
	public boolean conditionV() { return (cpsr & (1<<CPSR_BIT_V)) != 0; }
	public boolean conditionQ() { return (cpsr & (1<<CPSR_BIT_Q)) != 0; }
	public void setConditionN(boolean nu) { if(nu) cpsr |= (1<<CPSR_BIT_N); else cpsr &= ~(1<<CPSR_BIT_N); }
	public void setConditionZ(boolean nu) { if(nu) cpsr |= (1<<CPSR_BIT_Z); else cpsr &= ~(1<<CPSR_BIT_Z); }
	public void setConditionC(boolean nu) { if(nu) cpsr |= (1<<CPSR_BIT_C); else cpsr &= ~(1<<CPSR_BIT_C); }
	public void setConditionV(boolean nu) { if(nu) cpsr |= (1<<CPSR_BIT_V); else cpsr &= ~(1<<CPSR_BIT_V); }
	public void setConditionQ(boolean nu) { if(nu) cpsr |= (1<<CPSR_BIT_Q); else cpsr &= ~(1<<CPSR_BIT_Q); }
	public void setConditions(int cc) { cpsr = (cpsr & 0x0FFFFFFF) | (cc<<28); }
	public boolean isThumb() { return (cpsr & (1<<CPSR_BIT_T)) != 0; }
	public boolean isARM() { return (cpsr & (1<<CPSR_BIT_T)) == 0; }
	public boolean isLittleEndian() { return (cpsr & (1<<CPSR_BIT_E)) == 0; }
	public boolean isBigEndian() { return (cpsr & (1<<CPSR_BIT_E)) != 0; }
	public boolean isPrivileged() { return mode.privileged; }
	public int readCPSR() { return cpsr; }
	private void instrWriteCurCPSR(int value, int mask, boolean isExceptionReturn) {
		/* (B1-1153) */
		int writeMask = 0;
		if((mask & 8) != 0) {
			/* N,Z,C,V,Q, and also IT<1:0>/J if exception return */
			if(isExceptionReturn) writeMask |= 0xFF;
			else writeMask |= 0xF8;
		}
		if((mask & 4) != 0) {
			/* GE<3:0> */
			writeMask |= 0x000F0000;
		}
		if((mask & 2) != 0) {
			/* E bit, also A mask if privileged, and IT<7:2> if exception return */
			if(isExceptionReturn) writeMask |= 0x0000FF00;
			else writeMask |= 0x00000300;
		}
		if((mask & 1) != 0) {
			/* I bit, F bit (unless NMFI), M bits, and also T if exception return */
			if(isExceptionReturn) writeMask |= 0x000000BF;
			else writeMask |= 0x0000009F;
			if((cp15.SCTLR&(1<<CP15.SCTLR_BIT_NMFI)) == 0) writeMask |= 0x00000040;
		}
		if(!isPrivileged()) writeMask &= APSR_WRITE_MASK;
		// System.out.printf("Value: %08X, mask:%1X = %08X\n", value, mask, writeMask);
		cpsr = (cpsr & ~writeMask) | (value & writeMask);
		if((writeMask & 31) == 31) setProcessorMode(value & 31);
	}
	private void instrWriteCurSPSR(int value, int mask) throws UndefinedException {
		/* (B1-1154) */
		if(mode.spsrIndex < 0) throw new UndefinedException();
		int writeMask = 0;
		if((mask & 8) != 0) {
			/* N,Z,C,V,Q, IT<1:0>,J */
			writeMask |= 0xFF;
		}
		if((mask & 4) != 0) {
			/* GE<3:0> */
			writeMask |= 0x000F0000;
		}
		if((mask & 2) != 0) {
			/* E,A,IT<7:2> */
			writeMask |= 0x0000FF00;
		}
		if((mask & 1) != 0) {
			/* I,F,T,M */
			writeMask |= 0x000000FF;
		}
		spsr[mode.spsrIndex] = (spsr[mode.spsrIndex] & ~writeMask) | (value & writeMask);
	}
	/*** MEMORY MODEL ***/
	private PhysicalMemorySpace mem = new PhysicalMemorySpace();
	public PhysicalMemorySpace getMemorySpace() { return mem; }
	private VirtualMemorySpace vm = new VirtualMemorySpace(mem);
	public VirtualMemorySpace getVirtualMemorySpace() { return vm; }
	public int instructionReadWord(int address, boolean privileged) throws BusErrorException, AlignmentException, EscapeRetryException, EscapeCompleteException {
		return vm.readInt(address, inStrictAlignMode(), isBigEndian());
	}
	public void instructionWriteWord(int address, int value, boolean privileged) throws BusErrorException, AlignmentException, EscapeRetryException, EscapeCompleteException {
		vm.writeInt(address,value, inStrictAlignMode(), isBigEndian());
	}
	public byte instructionReadByte(int address, boolean privileged) throws BusErrorException, AlignmentException, EscapeRetryException, EscapeCompleteException {
		return vm.readByte(address);
	}
	public void instructionWriteByte(int address, byte value, boolean privileged) throws BusErrorException, AlignmentException, EscapeRetryException, EscapeCompleteException {
		vm.writeByte(address, value);
	}
	public short instructionReadHalfword(int address, boolean privileged) throws BusErrorException, AlignmentException, EscapeRetryException, EscapeCompleteException {
		return vm.readShort(address, inStrictAlignMode(), isBigEndian());
	}
	public void instructionWriteHalfword(int address, short value, boolean privileged) throws BusErrorException, AlignmentException, EscapeRetryException, EscapeCompleteException {
		vm.writeShort(address, value, inStrictAlignMode(), isBigEndian());
	}
	public int instructionReadWord(int address) throws BusErrorException, AlignmentException, EscapeRetryException, EscapeCompleteException {
		return instructionReadWord(address, isPrivileged());
	}
	public void instructionWriteWord(int address, int value) throws BusErrorException, AlignmentException, EscapeRetryException, EscapeCompleteException {
		instructionWriteWord(address, value, isPrivileged());
	}
	public byte instructionReadByte(int address) throws BusErrorException, AlignmentException, EscapeRetryException, EscapeCompleteException {
		return instructionReadByte(address, isPrivileged());
	}
	public void instructionWriteByte(int address, byte value) throws BusErrorException, AlignmentException, EscapeRetryException, EscapeCompleteException {
		instructionWriteByte(address, value, isPrivileged());
	}
	public short instructionReadHalfword(int address) throws BusErrorException, AlignmentException, EscapeRetryException, EscapeCompleteException {
		return instructionReadHalfword(address);
	}
	public void instructionWriteHalfword(int address, short value) throws BusErrorException, AlignmentException, EscapeRetryException, EscapeCompleteException {
		instructionWriteHalfword(address, value, isPrivileged());
	}
	/*** COPROCESSORS ***/
	private Coprocessor[] coprocessors = new Coprocessor[16];
	public void mapCoprocessor(int name, Coprocessor cop) {
		if(name >= 16 || name < 0) throw new ArrayIndexOutOfBoundsException(name);
		if(name >= 8) throw new ReservedCoprocessorNameException();
		coprocessors[name] = cop;
	}
	/*** INTERRUPT VECTORS ***/
	public int getInterruptVector(int interrupt) {
		/* B1-1165 */
		/* TODO: SCTLR.V */
		if((cp15.SCTLR & (1<<CP15.SCTLR_BIT_V)) != 0) return (interrupt * 4) | 0xFFFF0000;
		else return interrupt * 4;
	}
	/*** SYSTEM CONTROL REGISTERS ***/
	/* debug (C6), ThumbEE (A2-95), Jazelle (A2-97) */
	private static class CP14 extends Coprocessor {
		CP14(CPU cpu) {}
		@Override
		public void executeInstruction(boolean unconditional, int iword) throws UndefinedException { throw new UndefinedException(); }
		@Override
		public void reset() {}
	}
	CP14 cp14;
	CP15 cp15;
	public boolean inStrictAlignMode() { return (cp15.SCTLR & (1<<CP15.SCTLR_BIT_A)) != 0; }
	/*** INITIALIZATION ***/
	public CPU() {
		coprocessors[10] = new FPU(this);
		coprocessors[11] = coprocessors[10];
		coprocessors[14] = cp14 = new CP14(this);
		coprocessors[15] = cp15 = new CP15(this);
	}
	/*** EXECUTION ***/
	private boolean haveReset = false;
	private int cycleBudget = 0;
	/**
	 * Returns true if the cycle budget is fully spent, false if there are some unspent cycles left.
	 */
	public boolean budgetFullySpent() { return cycleBudget <= 0; }
	/**
	 * Spends any cycles that still remain in the budget.
	 * @param soft If true, only zero the budget if there are unspent cycles. If false, also forgive debt.
	 */
	public void zeroBudget(boolean soft) {
		if(cycleBudget > 0 || !soft) cycleBudget = 0;
	}
	/**
	 * Fetch and execute enough instructions to meet a given cycle budget. Will go slightly over budget, and compensate exactly for this on the next call.
	 * Throws an exception if exception debug mode is true.
	 * @param budget Number of cycles
	 * @return true if the budget was fully expended
	 */
	public boolean execute(int budget) throws BusErrorException, AlignmentException, UndefinedException {
		// TODO: Define new, user-facing exceptions subclassing RuntimeException.
		// Hack: allow self-tail-call without bursting the whole stack
		while(true) {
			cycleBudget += budget;
			int backupPC = pc;
			if(waitingForInterrupt && (haveIRQ() || haveFIQ())) waitingForInterrupt = false;
			try {
				while(cycleBudget > 0 && !waitingForInterrupt) {
					backupPC = pc;
					execute();
					/* then settle and check the debt */
					cycleBudget -= mem.settleAccessBill();
				}
			}
			catch(BusErrorException e) {
				if(exceptionDebugMode) throw e;
				generateDataAbortException();
				budget = 0;
				cycleBudget -= mem.settleAccessBill();
				continue;
			}
			catch(AlignmentException e) {
				if(exceptionDebugMode) throw e;
				generateDataAbortException();
				budget = 0;
				cycleBudget -= mem.settleAccessBill();
				continue;
			}
			catch(UndefinedException e) {
				if(exceptionDebugMode) throw e;
				generateUndefinedException();
				budget = 0;
				cycleBudget -= mem.settleAccessBill();
				continue;
			}
			catch(EscapeRetryException e) {
				pc = backupPC;
			}
			catch(EscapeCompleteException e) {}
			// don't hoard cycles in a low-power state
			cycleBudget -= mem.settleAccessBill();
			if(waitingForInterrupt && cycleBudget > 0) cycleBudget = 0;
			return cycleBudget <= 0;
		}
	}
	/**
	 * Fetch and execute a single instruction
	 */
	public void execute() throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException {
		if(!haveReset) throw new FatalException("execute() called without first calling reset()");
		if((cpsr & CPSR_BIT_F) == 0 && haveFIQ()) generateFIQException();
		if((cpsr & CPSR_BIT_I) == 0 && haveIRQ()) generateIRQException();
		if(isThumb()) throw new UndefinedException(); // Thumb not implemented
		else executeARM();
	}
	/* Fetch and execute a 32-bit ARM instruction */
	private void executeARM() throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException {
		int iword;
		if((pc&3) != 0) pc=pc&~3;
		try { iword = vm.readInt(pc, true, false); }
		catch(BusErrorException e) {
			// TODO: ugh
			if(exceptionDebugMode) throw e;
			// System.out.println("Prefetch abort! "+Long.toHexString((long)pc & 0xFFFFFFFFL));
			generatePrefetchAbortException();
			try { iword = vm.readInt(pc, true, false); }
			catch(BusErrorException e2) { throw new FatalException("Prefetch abort vector is on invalid address"); }
		}
		// System.out.printf("\t%08X : %08X\n", pc, iword);
		pc += 4;
		try { executeARM(iword); }
		/* if we experience an exception, restore as much state as possible to before the exception */
		catch(BusErrorException e) { pc -= 4; throw e; }
		catch(AlignmentException e) { pc -= 4; throw e; }
		catch(UndefinedException e) { pc -= 4; throw e; }
	}
	/* used by executeDataProcessingOperation for operations that don't provide their own carry logic */
	private boolean shifterCarryOut;
	private int expandARMImmediate(int imm12) {
		int unrotated = imm12 & 255;
		return applyOpShift(unrotated, 3, 2*((imm12>>>8)&15));
	}
	private int applyOpShift(int value, int type, int amount) {
		if(amount == 0) {
			shifterCarryOut = conditionC();
			return value;
		}
		switch(type) {
		case 0: /* logical left */
			if(amount >= 33) shifterCarryOut = false;
			else shifterCarryOut = ((value >>> (32-amount)) & 1) != 0;
			if(amount >= 32) return 0;
			else return value << amount;
		case 1: /* logical right */
			if(amount >= 33) shifterCarryOut = false;
			else shifterCarryOut = ((value >>> amount-1) & 1) != 0;
			if(amount >= 32) return 0;
			else return value >>> amount;
		case 2: /* arithmetic right */
			if(amount > 32) {
				if(value < 0) { shifterCarryOut = true; return 0xFFFFFFFF; }
				else { shifterCarryOut = false; return 0; }
			}
			else if(amount == 32) shifterCarryOut = value < 0;
			else shifterCarryOut = ((value >> (amount-1)) & 1) != 0;
			return value >> amount;
		case 3: /* rotate right */
		{
			amount %= 32;
			int result = (value >>> amount) | (value << (32-amount));
			shifterCarryOut = (result & 0x80000000) != 0;
			return result;
		}
		default:
			throw new InternalError(); /* we technically shouldn't throw these */
		}
	}
	private int applyOpRRX(int value) {
		shifterCarryOut = (value & 1) != 0;
		if(conditionC())
			return (value >>> 1) | 0x80000000;
		else
			return (value >>> 1);
	}
	private int applyIRShift(int src, int type, int imm5) {
		/* A8-291 */
		switch(type) {
		case 0: /* logical left */
			/* optimize this particular case in a way the compiler probably won't
			 * the vast majority of instructions will use this case */
			if(imm5 == 0) { shifterCarryOut = false; return src; }
			else return applyOpShift(src, type, imm5);
		case 1: /* logical right */
		case 2: /* arithmetic right */
			if(imm5 == 0) return applyOpShift(src, type, 32);
			else return applyOpShift(src, type, imm5);
		case 3:
			if(imm5 == 0) return applyOpRRX(src);
			else return applyOpShift(src, type, imm5);
		default:
			throw new InternalError(); /* we technically shouldn't throw these */
		}
	}
	private int applyRRShift(int src, int type, int Rs) {
		return applyOpShift(src, type, readRegister(Rs) & 255);
	}
	private void executeDataProcessingOperation(int opcode, int n, int m, int Rd) throws UndefinedException {
		/* TODO: possibly speed this up by separating WriteFlags version out */
		/* TODO: ensure that all the data processing operation variants are correct */
		boolean writeFlags = (opcode & 1) != 0;
		boolean Cout, Vout;
		int result;
		switch(opcode) {
		case 0: case 1:
			/* AND (immediate at A8-324) */
			writeRegister(Rd, result = (n&m));
			Cout = shifterCarryOut;
			Vout = conditionV();
			break;
		case 2: case 3:
			/* EOR (immediate at A8-382) */
			writeRegister(Rd, result = (n^m));
			Cout = shifterCarryOut;
			Vout = conditionV();
			break;
		case 17:
			/* TST (immediate at A8-744)
			 * same as AND but discard result */
			result = (n&m);
			Cout = shifterCarryOut;
			Vout = conditionV();
			break;
		case 19:
			/* TEQ (immediate at A8-738)
			 * same as EOR but discard result */
			result = (n^m);
			Cout = shifterCarryOut;
			Vout = conditionV();
			break;
		case 24: case 25:
			/* OR (immediate [ORR] at A8-516) */
			writeRegister(Rd, result = (n|m));
			Cout = shifterCarryOut;
			Vout = conditionV();
			break;
		case 26: case 27:
			/* MOV (immediate at A8-484) */
			writeRegister(Rd, result = m);
			Cout = shifterCarryOut;
			Vout = conditionV();
			break;
		case 28: case 29:
			/* BIC (immediate at A8-340) */
			writeRegister(Rd, result = (n&~m));
			Cout = shifterCarryOut;
			Vout = conditionV();
			break;
		case 30: case 31:
			/* MVN (immediate at A8-504) */
			writeRegister(Rd, result = ~m);
			Cout = shifterCarryOut;
			Vout = conditionV();
			break;
		default:
			/* any instruction that is an AddWithCarry at heart */
		{
			int x, y, carry_in;
			boolean writeOut;
			switch(opcode) {
			case 4: case 5:
				/* SUB (immediate at A8-710) */
				x = n; y = ~m; carry_in = 1; writeOut = true;
				break;
			case 6: case 7:
				/* RSB (immediate at A8-574) */
				x = ~n; y = m; carry_in = 1; writeOut = true;
				break;
			case 8: case 9:
				/* ADD (immediate at A8-308) */
				x = n; y = m; carry_in = 0; writeOut = true;
				break;
			case 10: case 11:
				/* ADC (immediate at A8-300) */
				x = n; y = m; carry_in = conditionC() ? 1 : 0; writeOut = true;
				break;
			case 12: case 13:
				/* SBC (immediate, A8-592) */
				x = n; y = ~m; carry_in = conditionC() ? 1 : 0; writeOut = true;
				break;
			case 14: case 15:
				/* RSC (immediate, A8-580) */
				x = ~n; y = m; carry_in = conditionC() ? 1 : 0; writeOut = true;
				break;
			case 21:
				/* CMP (immediate, A8-370) */
				x = n; y = ~m; carry_in = 1; writeOut = false;
				break;
			case 23:
				/* CMN (immediate, A8-364) */
				x = n; y = m; carry_in = 0; writeOut = false;
				break;
			default:
				throw new RuntimeException("unknown op: "+opcode);
			}
			/* this is the way the spec specified it
			 * TODO: work out a more efficient way */
			long signedResult = (long)x + y + carry_in;
			long unsignedResult = (x&0xFFFFFFFFL) + (y&0xFFFFFFFFL) + carry_in;
			result = (int)unsignedResult;
			Cout = unsignedResult != (result&0xFFFFFFFFL);
			Vout = signedResult != result;
			if(writeOut) writeRegister(Rd, result);
		}
		}
		if(writeFlags) {
			if(Rd == 15) {
				/* when the destination register is the PC, the flag-setting versions are for PL1 and above only */
				if(!isPrivileged()) throw new UndefinedException();
			}
			else {
				cpsr &= CPSR_MASK_CLEAR_CONDITIONS;
				if(result < 0) cpsr |= 1<<CPSR_BIT_N;
				if(result == 0) cpsr |= 1<<CPSR_BIT_Z;
				if(Cout) cpsr |= 1<<CPSR_BIT_C;
				if(Vout) cpsr |= 1<<CPSR_BIT_V;
			}
		}
	}
	private void executeARMPage0(int iword) throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException {
		/* (op=0) Data-processing and miscellaneous instructions (A5-196) */
		/* TODO: page 0 decoding is wrong */
		int op1 = (iword >> 20) & 31;
		int op2 = (iword >> 4) & 15;
		if((op1 & 25) != 16) {
			if((op2 & 1) == 0) {
				/* Data-processing (register, A5-197) */
				int Rn = (iword >> 16) & 15;
				int Rd = (iword >> 12) & 15;
				int imm5 = (iword >> 7) & 31;
				int type = (iword >> 5) & 3;
				int Rm = iword & 15;
				int n = readRegister(Rn);
				int m = applyIRShift(readRegister(Rm), type, imm5);
				executeDataProcessingOperation(op1, n, m, Rd);
				/* TODO: if S is set and Rd == 15, copy SPSR to CPSR */
				return;
			}
			else if((op2 & 8) == 0) {
				/* static_assert((op2 & 1) == 1) */
				/* Data-processing (register-shifted register, A5-198) */
				int Rn = (iword >> 16) & 15;
				int Rd = (iword >> 12) & 15;
				int Rs = (iword >> 8) & 15;
				int type = (iword >> 5) & 3;
				int Rm = iword & 15;
				int n = readRegister(Rn);
				int m = applyRRShift(readRegister(Rm), type, Rs);
				executeDataProcessingOperation(op1, n, m, Rd);
				return;
			}
		}
		else {
			if((op2 & 8) == 0) {
				/* Miscellaneous instructions (A5-207) */
				int op = (op1 >> 1) & 3;
				op1 = (iword >> 16) & 15;
				/* op2 is still good */
				switch(op2) {
				case 0:
					if((iword & 512) != 0) {
						if((op & 1) == 0) {
							/* MRS (banked register, B9-1992) */
							throw new UnimplementedInstructionException(iword, "MRS");
						}
						else {
							/* MSR (banked register, B9-1994) */
							throw new UnimplementedInstructionException(iword, "MSR");
						}
					}
					else {
						switch(op) {
						case 0: case 2:
							/* MRS (A8-496, B9-1990) */
							throw new UnimplementedInstructionException(iword, "MRS");
						case 1:
							if((op1 & 3) == 0) {
								/* MSR (register, A8-500) */
								throw new UnimplementedInstructionException(iword, "MSR");
							}
							/* fall through */
						case 3:
							/* MSR (register, B9-1998) */
							throw new UnimplementedInstructionException(iword, "MSR");
						}
					}
					break;
				case 1:
					switch(op) {
					case 1:
						/* BX (A8-352) */
					{
						int Rm = (iword) & 15;
						interworkingBranch(readRegister(Rm));
						return;
					}
					case 3:
						/* CLZ (A8-362) */
						int Rm = (iword) & 15;
						int Rd = (iword >> 12) & 15;
						writeRegister(Rd, Integer.numberOfLeadingZeros(readRegister(Rm)));
						return;
					}
					break;
				case 2:
					switch(op) {
					case 1:
						/* BXJ (A8-354) */
						throw new UnimplementedInstructionException(iword, "BXJ");
					}
					break;
				case 3:
					switch(op) {
					case 1:
						/* BLX (A8-350) */
						int Rm = iword&15;
						writeLR(readPC()-4);
						interworkingBranch(readRegister(Rm));
						return;
					}
					break;
				case 5:
					/* Saturating addition and subtraction (A5-202) */
					throw new UnimplementedInstructionException(iword, "saturating addition / subtraction");
				case 6:
					switch(op) {
					case 3:
						/* ERET (B9-1982) */
						throw new UnimplementedInstructionException(iword, "ERET");
					}
					break;
				case 7:
					switch(op) {
					case 1:
						/* BKPT (A8-346) */
						throw new UnimplementedInstructionException(iword, "BKPT");
					case 2:
						/* HVC (B9-1984) but we do NOT have Virtualization Extensions */
					case 3:
						/* SMC (B9-2002) but we do NOT have Security Extensions */
						break;
					}
				}
				throw new UndefinedException();
			}
			else if((op2 & 1) == 0) {
				/* static_assert((op2 & 8) == 8) */
				/* Halfword multiply and multiply accumulate (A5-203) */
				switch(op1) {
				case 16:
					/* SMLABB/SMLABT/SMLATB/SMLATT (A8-620) */
					throw new UnimplementedInstructionException(iword, "SMLABB/SMLABT/SMLATB/SMLATT");
				case 18:
					if((op2 & 2) == 0)
						/* SMLAWB/SMLAWT (A8-630) */
						throw new UnimplementedInstructionException(iword, "SMLAWB/SMLAWT");
					else
						/* SMULWB/SMULWT (A8-648) */
						throw new UnimplementedInstructionException(iword, "SMULWB/SMULWT");
				case 20:
					/* SMLALBB/SMLALBT/SMLALTB/SMLALTT (A8-626) */
					throw new UnimplementedInstructionException(iword, "SMLALBB/SMLALBT/SMLALTB/SMLALTT");
				case 22:
					/* SMULBB/SMULBT/SMULTB/SMULTT (A8-644) */
					throw new UnimplementedInstructionException(iword, "SMULBB/SMULBT/SMULTB/SMULTT");
				}
				throw new UndefinedException();
			}
		}
		/* still here... ugh, this is the most confusing part of the instruction space */
		if(op2 == 9) {
			switch(op1) {
			/* Multiply and multiply accumulate (A5-202) */
			case 0: case 1: case 2: case 3: {
				/* MUL (A8-502) */
				/* MLA (A8-480) */
				int Rn = iword&15;
				int Rm = (iword>>8)&15;
				int Ra = (iword>>12)&15;
				int Rd = (iword>>16)&15;
				boolean setFlags = (op1&1) != 0;
				boolean accumulate = (op1&2) != 0;
				// result is the same whether signed or unsigned
				int result = readRegister(Rn)*readRegister(Rm);
				if(accumulate) result += readRegister(Ra);
				if(setFlags) {
					setConditionN(result < 0);
					setConditionZ(result == 0);
				}
				writeRegister(Rd, result);
				return;
			}
			case 4:
				/* UMAAL (A8-774) */
				throw new UnimplementedInstructionException(iword, "UMAAL");
			case 6: {
				/* MLS (A8-482) */
				int Rn = iword&15;
				int Rm = (iword>>8)&15;
				int Ra = (iword>>12)&15;
				int Rd = (iword>>16)&15;
				// result is the same whether signed or unsigned
				int result = readRegister(Rn)*readRegister(Rm);
				result = readRegister(Ra) - result;
				writeRegister(Rd, result);
				return;
			}
			case 8: case 9: {
				/* UMULL (A8-778) */
				int Rn = iword&15;
				int Rm = (iword>>8)&15;
				int RdLo = (iword>>12)&15;
				int RdHi = (iword>>16)&15;
				boolean setFlags = (op1&1) != 0;
				long src1 = readRegister(Rn) & 0xFFFFFFFFL;
				long src2 = readRegister(Rm) & 0xFFFFFFFFL;
				long result = src1 * src2;
				writeRegister(RdLo, (int)(result & 0xFFFFFFFFL));
				writeRegister(RdHi, (int)(result >>> 32));
				if(setFlags) {
					setConditionN(result < 0);
					setConditionZ(result == 0);
				}
				return;
			}
			case 10: case 11:
				/* UMLAL (A8-776) */
				throw new UnimplementedInstructionException(iword, "UMLAL");
			case 12: case 13:
				/* SMULL (A8-646) */
				throw new UnimplementedInstructionException(iword, "SMULL");
			case 14: case 15:
				/* SMLAL (A8-624) */
				throw new UnimplementedInstructionException(iword, "SMLAL");
			/* Synchronization primitives (A5-205) */
			case 16: case 20:
				/* SWP/SWPB (A8-722) */
				throw new UnimplementedInstructionException(iword, "SWP/SWPB");
			case 24:
				/* STREX (A8-690) */
				throw new UnimplementedInstructionException(iword, "STREX");
			case 25:
				/* LDREX (A8-432) */
				throw new UnimplementedInstructionException(iword, "LDREX");
			case 26:
				/* STREXD (A8-694) */
				throw new UnimplementedInstructionException(iword, "STREXD");
			case 27:
				/* LDREXD (A8-436) */
				throw new UnimplementedInstructionException(iword, "LDREXD");
			case 28:
				/* STREXB (A8-692) */
				throw new UnimplementedInstructionException(iword, "STREXB");
			case 29:
				/* LDREXB (A8-434) */
				throw new UnimplementedInstructionException(iword, "LDREXB");
			case 30:
				/* STREXH (A8-696) */
				throw new UnimplementedInstructionException(iword, "STREXH");
			case 31:
				/* LDREXH (A8-438) */
				throw new UnimplementedInstructionException(iword, "LDREXH");
			default:
				/* undefined, explicitly throw */
				throw new UndefinedException();
			}
		}
		else if(op2 > 9) {
			boolean argh = (op2 & 4) != 0;
			if((op1 & 16) == 0) {
				if(((op2 == 11) && (op1 & 18) == 2)
					|| (op2 == 13 || op2 == 15) && (op1 & 19) == 3) {
					/* Extra load/store instructions, unprivileged (A5-204) */
					switch(op2) {
					case 11:
						if((op1 & 1) == 0) {
							/* STRHT (A8-704) */
							throw new UnimplementedInstructionException(iword, "STRHT");
						}
						else {
							/* LDRHT (A8-448) */
							throw new UnimplementedInstructionException(iword, "LDRHT");
						}
					case 13:
						/* LDRSBT (A8-456) */
						throw new UnimplementedInstructionException(iword, "LDRSBT");
					case 15:
						/* LDRSHT (A8-464) */
						throw new UnimplementedInstructionException(iword, "LDRSHT");
					}
				}
			}
			/* Extra load/store instructions (A5-203) */
			int Rn = (iword >> 16) & 15;
			switch(op2) {
			case 11:
				switch(op1 & 5) {
				case 0: 
				case 1:
				case 4:
				case 5: {
					/* LDRH (register, A8-446) */
					/* STRH (register, A8-702) */
					/* LDRH (immediate, ARM, A8-442) */
					/* LDRH (literal, A8-444) */
					/* STRH (immediate, ARM, A8-700) */
					boolean P = ((iword >> 24) & 1) != 0;
					boolean U = ((iword >> 23) & 1) != 0;
					boolean W = ((iword >> 21) & 1) != 0;
					boolean isLoad = ((op1 & 1) != 0);
					boolean registerForm = ((op1 & 4) == 0);
					int Rt = (iword >> 12) & 15;
					int offset;
					if(registerForm) offset = readRegister(iword & 15);
					else offset = ((iword >> 4)&240) | (iword&15);
					boolean writeback = !P || W;
					int base_addr = readRegisterAlignPC(Rn);
					int offset_addr;
					if(U) offset_addr = base_addr + offset;
					else offset_addr = base_addr - offset;
					int address = P ? offset_addr : base_addr;
					int value;
					if(isLoad) {
						value = instructionReadHalfword(address, isPrivileged()) & 0xFFFF;
						writeRegister(Rt, value);
					}
					else {
						value = readRegister(Rt);
						instructionWriteHalfword(address, (short)value, isPrivileged());
					}
					if(writeback) writeRegister(Rn, offset_addr);
					return;
				}
				}
			case 13:
				switch(op1 & 5) {
				case 0:
				case 1:
				case 4:
				case 5: {
					/* Load/store word and unsigned byte (A5-208) */
					/* LDRD (register, A8-430) */
					/* LDRSB (register, A8-454) */
					/* LDRD (immediate, A8-426) */
					/* LDRD (literal, A8-428) */
					/* LDRSB (immediate, A8-450) */
					/* LDRSB (literal, A8-452) */
					boolean registerForm = ((iword >> 22) & 1) == 0;
					boolean P = ((iword >> 24) & 1) != 0;
					boolean U = ((iword >> 23) & 1) != 0;
					boolean isByte = ((iword >> 20) & 1) != 0;
					boolean W = ((iword >> 21) & 1) != 0;
					int Rt = (iword >> 12) & 15;
					boolean unprivileged = (!P && W);
					boolean writeback = (!P ^ W);
					int offset;
					if(registerForm) offset = readRegister(iword & 15);
					else offset = (iword & 15) | ((iword >> 4) & 240);
					int base_addr = readRegisterAlignPC(Rn);
					int offset_addr;
					if(U) offset_addr = base_addr + offset;
					else offset_addr = base_addr - offset;
					int address = P ? offset_addr : base_addr;
					int value;
					if(isByte) {
						value = instructionReadByte(address, unprivileged ? false : isPrivileged());
						writeRegister(Rt, value);
					}
					else {
						if((Rt&1)!=0 || Rt==14) throw new UndefinedException();
						writeRegister(Rt, instructionReadWord(address, unprivileged ? false : isPrivileged()));
						writeRegister(Rt+1, instructionReadWord(address+4, unprivileged ? false : isPrivileged()));
					}
					if(writeback) writeRegister(Rn, offset_addr);
					return;
				}
				}
			case 15:
				switch(op1 & 5) {
				case 0:
					/* STRD (register, A8-688) */
					throw new UnimplementedInstructionException(iword, "STRD(688)");
				case 1:
					/* LDRSH (register, A8-462) */
					throw new UnimplementedInstructionException(iword, "LDRSH");
				case 4: {
					/* STRD (immediate, A8-686) */
					boolean P = ((iword >> 24) & 1) != 0;
					boolean U = ((iword >> 23) & 1) != 0;
					boolean W = ((iword >> 21) & 1) != 0;
					int Rt = (iword >> 12) & 15;
					int offset = ((iword >> 4)&240) | (iword&15);
					boolean writeback = !P || W;
					int base_addr = readRegisterAlignPC(Rn);
					int offset_addr;
					if(U) offset_addr = base_addr + offset;
					else offset_addr = base_addr - offset;
					int address = P ? offset_addr : base_addr;
					instructionWriteWord(address, readRegister(Rt));
					instructionWriteWord(address+4, readRegister(Rt+1));
					if(writeback) writeRegister(Rn, offset_addr);
					return;
				}
				case 5: {
					/* LDRSH (immediate, A8-458) */
					/* LDRSH (literal, A8-460) */
					boolean P = ((iword >> 24) & 1) != 0;
					boolean U = ((iword >> 23) & 1) != 0;
					boolean W = ((iword >> 21) & 1) != 0;
					int Rt = (iword >> 12) & 15;
					int offset = ((iword >> 4)&240) | (iword&15);
					boolean writeback = !P || W;
					int base_addr = readRegisterAlignPC(Rn);
					int offset_addr;
					if(U) offset_addr = base_addr + offset;
					else offset_addr = base_addr - offset;
					int address = P ? offset_addr : base_addr;
					int value = instructionReadHalfword(address, isPrivileged());
					writeRegister(Rt, value);
					if(writeback) writeRegister(Rn, offset_addr);
					return;
				}
				}
			}
		}
		throw new UndefinedException();
	}
	private void executeARMPage1(int iword) throws BusErrorException, AlignmentException, UndefinedException {
		/* (op=1) Data-processing and miscellaneous instructions (A5-196) */
		int op1 = (iword >> 20) & 31;
		switch(op1) {
		case 16:
			/* MOVW (A8-484) */
		{
			int Rd = (iword >> 12) & 15;
			int imm16 = ((iword >> 4) & 0xF000) | (iword & 0xFFF);
			writeRegister(Rd, imm16);
			return;
		}
		case 20:
			/* MOVT (high half-word 16 bit immediate, A8-491) */
		{
			int Rd = (iword >> 12) & 15;
			int imm16 = ((iword >> 4) & 0xF000) | (iword & 0xFFF);
			writeRegister(Rd, (readRegister(Rd) & 0xFFFF) | (imm16 << 16));
			return;
		}
		case 18:
		case 22:
			/* MSR/hints (immediate, A5-206) */
		{
			int mask = (iword >> 16) & 15;
			if(((iword >> 22) & 1) == 0 && mask == 0) {
				int hint = iword & 255;
				if(hint >= 240) {
					/* DBG (A8-377) */
					if(debugDumpMode) {
						System.err.println("DBG #"+(hint&15));
						dumpState(System.err);
					}
				}
				switch(hint) {
				case 0: break; // NOP (A8-510)
				case 1: break; // YIELD (A8-1108)
				case 2: /* no SMP or events; TODO events */ break; // WFE (A8-1104)
				case 3: // WFI (A8-1106)
					if(haveFIQ() || haveIRQ()) break;
					waitingForInterrupt = true;
					return;
				case 4: /* no SMP or events; see above */ break; // SEV (A8-606)
				}
				// do nothing
				return;
			}
			else {
				boolean writingSPSR = (op1 & 4) != 0;
				if(writingSPSR || ((mask & 3) == 1) || ((mask & 2) != 0))
					/* MSR (immediate, system-level, B9-1996 */
					if(!isPrivileged()) throw new UndefinedException();
				int imm32 = expandARMImmediate(iword & 4095);
				if(writingSPSR)
					instrWriteCurSPSR(imm32, mask);
				else
					instrWriteCurCPSR(imm32, mask, false);
				return;
			}
		}
		}
		/* Data processing, immediate (A5-199) */
		int Rn = (iword >> 16) & 15;
		int Rd = (iword >> 12) & 15;
		int imm12 = iword & 4095;
		int n = readRegister(Rn);
		executeDataProcessingOperation(op1, n, expandARMImmediate(imm12), Rd);
		if(Rd == 15 && (op1 & 1) == 1) {
			/* non-privileged flag-setting PC writes are already filtered out by eDPO */
			if(Rn == 14) {
				// <op>S PC, LR, #<const>
				returnFromProcessorMode();
			}
			else throw new UndefinedException();
		}
	}
	private void executeARMPage23(int iword) throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException {
		if((iword & (1<<25)) != 0 && (iword & 16) != 0) {
			int op1 = (iword >> 20) & 31;
			int op2 = (iword >> 5) & 7;
			int Rn = iword & 15;
			/* Media instructions (A5-209) */
			if(op1 == 31 && op2 == 7) {
				/* UDF (A8-758) */
				/* ALWAYS undefined. Worth EXPLICITLY throwing. */
				throw new UndefinedException();
			}
			else if(op1 >= 30) {
				if((op2 & 3) == 2) {
					/* UBFX (A8-756) */
					int widthminus1 = (iword >> 16) & 31;
					int Rd = (iword >> 12) & 15;
					int lsbit = (iword >> 7) & 31;
					writeRegister(Rd, (readRegister(Rn) >>> lsbit) & (0xFFFFFFFF >>> (31-widthminus1)));
					return;
				}
			}
			else if(op1 >= 28) {
				if((op2 & 3) == 0) {
					int lsb = (iword >> 7) & 31;
					int msb = (iword >> 16) & 31;
					int Rd = (iword >> 12) & 15;
					int value = readRegister(Rd);
					int mask = (0xFFFFFFFF << lsb) & (0xFFFFFFFF >>> (31-msb));
					if(Rn == 15) {
						/* BFC (A8-336) */
						value = (value & ~mask);
					}
					else {
						/* BFI (A8-338) */
						value = (value & ~mask) | ((readRegister(Rn) << lsb) & mask);
					}
					writeRegister(Rd, value);
					return;
				}
			}
			else if(op1 >= 26) {
				if((op2 & 3) == 2) {
					/* SBFX (A8-598) */
					int widthminus1 = (iword >> 16) & 31;
					int Rd = (iword >> 12) & 15;
					int lsbit = (iword >> 7) & 31;
					int value = (readRegister(Rn) >>> lsbit) & (0xFFFFFFFF >>> (31-widthminus1));
					if((value & (1<<widthminus1))!=0) value |= 0xFFFFFFFF << (widthminus1+1);
					writeRegister(Rd, value);
					return;
				}
			}
			else if(op1 == 24) {
				if(op2 == 0) {
					int Rd = (iword >> 12) & 15;
					if(Rd == 15) {
						/* USADA8 (A8-794) */
						throw new UnimplementedInstructionException(iword, "USADA8");
					}
					else {
						/* USAD8 (A8-792) */
						throw new UnimplementedInstructionException(iword, "USAD8");
					}
				}
			}
			else if(op1 < 24) {
				if((op1 & 16) != 0) {
					if((op1 & 8) == 0) {
						/* Signed multiply, signed and unsigned divide (A5-213) */
						op1 &= 7;
						int A = (iword >> 12) & 15;
						/* high nibble is op1, low op2&~1 */
						switch((op1 << 4) | (op2 & ~1)) {
						case 0x00:
							if(A != 15) {
								/* SMLAD (A8-622) */
								throw new UnimplementedInstructionException(iword, "SMLAD");
							}
							else {
								/* SMUAD (A8-642) */
								throw new UnimplementedInstructionException(iword, "SMUAD");
							}
						case 0x02:
							if(A != 15) {
								/* SMLSD (A8-632) */
								throw new UnimplementedInstructionException(iword, "SMLSD");
							}
							else {
								/* SMUSD (A8-650) */
								throw new UnimplementedInstructionException(iword, "SMUSD");
							}
						case 0x10:
							if(op2 == 0) {
								/* SDIV (A8-600) */
								int Rm = (iword>>8)&15;
								int Rd = (iword>>16)&15;
								int n = readRegister(Rn);
								int m = readRegister(Rm);
								int d;
								if(m == 0) {
									/* TODO: if IntegerZeroDivideTrappingEnabled() then GenerateIntegerZeroDivide(); */
									d = 0;
								}
								else if(m == -1 || n == -2147483648)
									d = -2147483648;
								else
									d = n  / m;
								writeRegister(Rd, d);
								return;
							}
							else break;
						case 0x30:
							if(op2 == 0) {
								/* UDIV (A8-760) */
								int Rm = (iword>>8)&15;
								int Rd = (iword>>16)&15;
								int n = readRegister(Rn);
								int m = readRegister(Rm);
								int d;
								if(m == 0) {
									/* TODO: if IntegerZeroDivideTrappingEnabled() then GenerateIntegerZeroDivide(); */
									d = 0;
								}
								else
									d = (int)((n&0xFFFFFFFFL)  / (m&0xFFFFFFFFL));
								writeRegister(Rd, d);
								return;
							}
							else break;
						case 0x40:
							/* SMLALD (A8-628) */
							throw new UnimplementedInstructionException(iword, "SMLALD");
						case 0x42:
							/* SMLSLD (A8-634) */
							throw new UnimplementedInstructionException(iword, "SMLSLD");
						case 0x50:
							if(A != 15) {
								/* SMMLA (A8-636) */
								throw new UnimplementedInstructionException(iword, "SMMLA");
							}
							else {
								/* SMMUL (A8-640) */
								boolean round = ((iword >> 5) & 1) != 0;
								int Rd = (iword >> 16) & 15;
								int Rm = (iword >> 8) & 15;
								long result = (long)readRegister(Rn) * readRegister(Rm);
								// System.out.printf("\t%08X * %08X = %016X\n", readRegister(Rn), readRegister(Rm), result);
								if(round) result += 0x80000000L;
								writeRegister(Rd, (int)(result >> 32));
								return;
							}
						case 0x56:
							/* SMMLS (A8-638) */
							throw new UnimplementedInstructionException(iword, "SMMLS");
						}
					}
				}
				else if((op1 & 8) != 0) {
					/* Packing, unpacking, saturation, and reversal (A5-212) */
					op1 &= 7;
					int Radd = (iword >> 16) & 15;
					if(op1 == 0) {
						if((op2 & 1) == 0) {
							/* PKH (A8-522) */
							throw new UnimplementedInstructionException(iword, "PKH");
						}
						else if(op2 == 3) {
							if(Radd != 15) {
								/* SXTAB16 (A8-726) */
								throw new UnimplementedInstructionException(iword, "SXTAB16");
							}
							else {
								/* SXTB16 (A8-732) */
								throw new UnimplementedInstructionException(iword, "SXTB16");
							}
						}
						else if(op2 == 5) {
							/* SEL (A8-602) */
							throw new UnimplementedInstructionException(iword, "SEL");
						}
					}
					else if(op1 == 1) {
						/* no valid instructions */
					}
					else {
						boolean unsigned = (op1 & 4) != 0;
						/* high nibble is low bits of op1, low nibble is op2, for ease of reading
						 * wish I were compiling against Java 7 so I could use binary literals... */
						switch(op2 | ((op1 & 3) << 4)) {
						case 0x21:
							/* SSAT16 (A8-654), USAT16 (A8-798) */
							throw new UnimplementedInstructionException(iword, "*SAT16");
						case 0x23: {
							/* SXTAB (A8-724), UXTAB (A8-806) */
							/* SXTB (A8-730), UXTB (A8-812) */
							int Rd = (iword >> 12) & 15;
							int rotation = (iword >> 7) & 24;
							int result = Integer.rotateRight(readRegister(Rn), rotation);
							if(unsigned) result &= 0xFF;
							else result = (int)(byte)result;
							writeRegister(Rd, Radd == 15 ? result : result + readRegister(Radd));
							return;
						}
						case 0x31:
							/* the unsigned/signed symmetry breaks down here */
							if(!unsigned) {
								/* REV (A8-562) */
								throw new UnimplementedInstructionException(iword, "REV");
							}
							else {
								/* RBIT (A8-560) */
								throw new UnimplementedInstructionException(iword, "RBIT");
							}
						case 0x33: {
							/* SXTAH (A8-728), UXTAH (A8-810) */
							/* SXTH (A8-734), UXTH (A8-816) */
							int Rd = (iword >> 12) & 15;
							int rotation = (iword >> 7) & 24;
							int result = Integer.rotateRight(readRegister(Rn), rotation);
							if(unsigned) result &= 0xFFFF;
							else result = (int)(short)result;
							writeRegister(Rd, Radd == 15 ? result : result + readRegister(Radd));
							return;
						}
						case 0x35:
							/* breaks down here too */
							if(!unsigned) {
								/* REV16 (A8-564) */
								throw new UnimplementedInstructionException(iword, "REV16");
							}
							else {
								/* REVSH (A8-566) */
								throw new UnimplementedInstructionException(iword, "REVSH");
							}
						default:
							if((op1 & 6) == 2 && (op2 & 1) == 0) {
								/* SSAT (A8-652), USAT (A8-796) */
								throw new UnimplementedInstructionException(iword, "*SAT");
							}
						}
					}
				}
				else {
					/* Parallel addition and subtraction, signed (A5-210) */
					/* Parallel addition and subtraction, unsigned (A5-211) */
					boolean unsigned = (op1 & 4) != 0;
					boolean saturate = (op1 & 3) == 2;
					boolean half = (op1 & 3) == 3;
					switch(op2) {
					case 0:
						/* SADD16 (A8-586), QADD16 (A8-542), SHADD16 (A5-608), UADD16 (A8-750), UQADD16 (A8-780), UHADD16 (A8-762) */
						throw new UnimplementedInstructionException(iword, "*ADD16");
					case 1:
						/* SASX (A8-590), QASX (A8-546), SHASX (A8-612), UASX (A8-754), UQASX (A8-784), UHASX (A8-766) */
						throw new UnimplementedInstructionException(iword, "*ASX");
					case 2:
						/* SSAX (A8-656), QSAX (A8-552), SHSAX (A8-614), USAX (A8-800), UQSAX (A8-786), UHSAX (A8-768) */
						throw new UnimplementedInstructionException(iword, "*SAX");
					case 3:
						/* SSUB16 (A8-658), QSUB16 (A8-556), SHSUB16 (A8-616), USUB16 (A8-802), UQSUB16 (A8-788), UHSUB16 (A8-770) */
						throw new UnimplementedInstructionException(iword, "*SUB16");
					case 4:
						/* SADD8 (A8-588), QADD8 (A8-544), SHADD8 (A8-610), UADD8 (A8-752), UQADD8 (A8-782), UHADD8 (A8-764) */
						throw new UnimplementedInstructionException(iword, "*ADD8");
					case 7:
						/* SSUB8 (A8-660), QSUB8 (A8-558), SHSUB8 (A8-618), USUB8 (A8-804), UQSUB8 (A8-790), UHSUB8 (A8-772) */
						throw new UnimplementedInstructionException(iword, "*SUB8");
					}
				}
			}
		}
		else {
			/* Load/store word and unsigned byte (A5-208) */
			/* STR (immediate, ARM, A8-674) */
			/* STR (register, A8-676) */
			/* STRT (A8-706) */
			/* LDR (immediate, ARM, A8-408) */
			/* LDR (literal, A8-410) */
			/* LDR (register, ARM, A8-414) */
			/* LDRT (A8-466) */
			/* after hours of banging my head against the wall, the relationship between them becomes clear */
			boolean registerForm = ((iword >> 25) & 1) != 0;
			boolean P = ((iword >> 24) & 1) != 0;
			boolean U = ((iword >> 23) & 1) != 0;
			boolean isByte = ((iword >> 22) & 1) != 0;
			boolean W = ((iword >> 21) & 1) != 0;
			boolean isLoad = ((iword >> 20) & 1) != 0;
			int Rn = (iword >> 16) & 15;
			int Rt = (iword >> 12) & 15;
			boolean unprivileged = (!P && W);
			boolean writeback = (!P ^ W);
			int offset;
			if(registerForm) offset = applyIRShift(readRegister(iword & 15), (iword >> 5) & 3, (iword >> 7) & 31);
			else offset = iword & 4095;
			int base_addr = readRegisterAlignPC(Rn);
			int offset_addr;
			if(U) offset_addr = base_addr + offset;
			else offset_addr = base_addr - offset;
			int address = P ? offset_addr : base_addr;
			int value;
			if(isLoad) {
				if(isByte) value = instructionReadByte(address, unprivileged ? false : isPrivileged()) & 0xFF;
				else value = instructionReadWord(address, unprivileged ? false : isPrivileged());
				writeRegister(Rt, value);
			}
			else {
				value = readRegister(Rt);
				if(isByte) instructionWriteByte(address, (byte)value, unprivileged ? false : isPrivileged());
				else instructionWriteWord(address, value, unprivileged ? false : isPrivileged());
			}
			if(writeback) writeRegister(Rn, offset_addr);
			return;
		}
		throw new UndefinedException();
	}
	private void executeARMPage45(int iword) throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException {
		/* Branch, branch with link, and block data transfer (A5-214) */
		int op = (iword >> 20) & 63;
		if(op >= 32) {
			/* B, BL (A8-334, A8-348) */
			boolean link = (op & 16) != 0;
			int imm32 = iword << 8 >> 6;
			if(link) writeLR(readPC()-4);
			branch(readPC()+imm32);
			return;
		}
        /* LDMDA/LDMFA (A8-400) */
        /* LDM/LDMIA/LDMFD (ARM, A8-398) */
        /* LDMDB/LDMEA (A8-402) */
        /* LDMIB/LDMED (A8-404) */
        /* STMDA/STMED (A8-666) */
        /* STM/STMIA/STMEA (A8-664) */
        /* STMDB/STMFD (A8-668) */
        /* STMIB/STMFA (A8-670) */
        /* POP (ARM, A8-536) */
        /* PUSH (A8-538) */
		boolean isLoad = (op & 1) != 0;
		boolean W = (op & 2) != 0;
		boolean userReg = (op & 4) != 0; // TODO: userReg
		boolean increment = (op & 8) != 0;
		boolean before = (op & 16) != 0;
		int Rn = (iword >> 16) & 15;
		int base_addr = readRegisterAlignPC(Rn);
		int register_list = iword & 65535;
		int register_count = Integer.bitCount(register_list);
		// System.out.printf("%s%s%s r%d%s, {%04X}\n", isLoad?"LDM":"STM", increment?"I":"D", before?"B":"A", Rn, W?"!":"", register_list);
		if(!increment) base_addr -= 4 * register_count;
		int addr = base_addr;
		if(increment == before) addr += 4;
		for(int n = 0; n < 16; n++) {
			if((register_list & (1<<n)) != 0) {
				if(isLoad) writeRegister(n, instructionReadWord(addr));
				else instructionWriteWord(addr, readRegister(n));
				addr += 4;
			}
		}
		if(increment) base_addr += 4 * register_count;
		if(W) writeRegister(Rn, base_addr);
		return;
	}
	private void executeARMPage67(int iword, boolean unconditional) throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException {
		/* Coprocessor instructions, and Supervisor Call (A5-215) */
		int op1 = (iword >> 20) & 63;
		if((op1 & 62) == 0) throw new UndefinedException();
		else if((op1 & 48) == 48) {
			if(unconditional) throw new UndefinedException();
			/* SVC (previously known as SWI, A8-720) */
			performSVC();
			return;
		}
		int coproc = (iword >> 8) & 15;
		/* TODO: throw UndefinedException when a coprocessor is masked out */
		Coprocessor cop = coprocessors[coproc];
		if(cop == null) throw new UndefinedException();
		else cop.executeInstruction(unconditional, iword);
	}
	private void executeARMUnconditional(int iword) throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException {
		/* Unconditional instructions (A5-216) */
		switch((iword >> 26) & 3) {
		case 0: case 1:
			/* TODO: Memory hints, Advanced SIMD instructions, and miscellaneous instructions (A5-217) */
			break;
		case 2:
			if(((iword >> 25) & 1) == 0) {
				int op1ish = (iword >> 20) & 31;
				if((op1ish & 5) == 4)
					/* SRS (ARM, B9-2006) */
					throw new UnimplementedInstructionException(iword, "SRS");
				else if((op1ish & 5) == 1)
					/* RFE (ARM, B9-2000) */
					throw new UnimplementedInstructionException(iword, "RFE");
			}
			else {
				/* BLX (A8-348) */
				int imm32 = (iword << 8 >> 6) | ((iword >> 23) & 2) | 1;
				writeLR(readPC()-4);
				// always switches instruction sets; set the low bit to achieve Thumb enlightenment
				interworkingBranch(((readPC()&~3)+imm32)|1);
				return;
			}
			break;
		case 3:
			executeARMPage67(iword, true);
			return;
		}
		throw new UndefinedException();
	}
	private void executeARM(int iword) throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException {
		int condition = (iword >> 28) & 15;
		if(condition != 15) {
			/* condition codes (A5-288) */
			boolean execute = true;
			switch(condition >> 1) {
			case 0: execute = conditionZ(); break;
			case 1: execute = conditionC(); break;
			case 2: execute = conditionN(); break;
			case 3: execute = conditionV(); break;
			case 4: execute = conditionC() && !conditionZ(); break;
			case 5: execute = conditionN() == conditionV(); break;
			case 6: execute = conditionZ() == false && conditionN() == conditionV(); break;
			/* case 7: execute = true; */
			}
			if((condition & 1) != 0) execute = !execute;
			if(!execute) return;
			switch((iword >> 25) & 7) {
			case 0:
				executeARMPage0(iword);
				break;
			case 1:
				executeARMPage1(iword);
				break;
			case 2: case 3:
				executeARMPage23(iword);
				break;
			case 4: case 5:
				executeARMPage45(iword);
				break;
			case 6:
			case 7:
				executeARMPage67(iword, false);
			}
		}
		else executeARMUnconditional(iword);
	}
	private void performSVC() { generateException(ProcessorMode.SUPERVISOR, (1<<CPSR_BIT_I), EXCEPTION_VECTOR_SUPERVISOR_CALL, isThumb()?pc-2:pc-4); }
	private void generateUndefinedException() { generateException(ProcessorMode.UNDEFINED, (1<<CPSR_BIT_I), EXCEPTION_VECTOR_UNDEFINED, pc); }
	private void generatePrefetchAbortException() { generateException(ProcessorMode.ABORT, (1<<CPSR_BIT_I)|(1<<CPSR_BIT_A), EXCEPTION_VECTOR_PREFETCH_ABORT, pc); }
	private void generateDataAbortException() { generateException(ProcessorMode.ABORT, (1<<CPSR_BIT_I)|(1<<CPSR_BIT_A), EXCEPTION_VECTOR_DATA_ABORT, pc+8); }
	private void generateIRQException() { generateException(ProcessorMode.IRQ, (1<<CPSR_BIT_I)|(1<<CPSR_BIT_A), EXCEPTION_VECTOR_IRQ, pc+4); }
	private void generateFIQException() { generateException(ProcessorMode.FIQ, (1<<CPSR_BIT_I)|(1<<CPSR_BIT_A)|(1<<CPSR_BIT_F), EXCEPTION_VECTOR_FIQ, pc+4); }
	private void generateException(ProcessorMode targetMode, int interruptMask, int vector, int preferredReturn) {
		enterProcessorModeByException(targetMode);
		lr[cur_lr] = preferredReturn;
		cpsr |= interruptMask;
		/* zero J/E/T/IT<7:0> */
		cpsr &= ~((1<<CPSR_BIT_J) | (1<<CPSR_BIT_E) | (1<<CPSR_BIT_T) | (CPSR_MASK_ITLO << CPSR_SHIFT_ITLO) | (CPSR_MASK_ITHI << CPSR_SHIFT_ITHI));
		if((cp15.SCTLR & (1<<CP15.SCTLR_BIT_TE)) != 0) cpsr |= (1<<CPSR_BIT_T);
		else cpsr &= ~(1<<CPSR_BIT_T);
		if((cp15.SCTLR & (1<<CP15.SCTLR_BIT_EE)) != 0) cpsr |= (1<<CPSR_BIT_E);
		else cpsr &= ~(1<<CPSR_BIT_E);
		branch(getInterruptVector(vector));
	}
	/* B1-1206 */
	/**
	 * Reset the CPU. This must be called at least once before any execution occurs.
	 * @param thumb_exceptions Whether the TE bit in SCTLR is initially set. (i.e. whether the exception vectors are Thumb code rather than ARM code)
	 * @param big_endian Whether the EE bit in SCTLR is initially set. (i.e. whether exceptions begin execution in big-endian mode)
	 * @param high_vectors Whether the V bit in SCTLR is initially set. (i.e. whether the exception vectors are at 0xFFFFxxxx instead of 0x0000xxxx)
	 */
	public void reset(boolean thumb_exceptions, boolean big_endian, boolean high_vectors) {
		haveReset = true;
		cpsr = ProcessorMode.SUPERVISOR.modeRepresentation;
		// ResetControlRegisters()
		for(int n = 0; n < 8; ++n) {
			if(coprocessors[n] != null) coprocessors[n].reset();
		}
		cp15.reset(thumb_exceptions, big_endian, high_vectors);
		// TODO: for FP: FPEXC.EN = 0
		generateException(ProcessorMode.SUPERVISOR, (1<<CPSR_BIT_I) | (1<<CPSR_BIT_A) | (1<<CPSR_BIT_F), EXCEPTION_VECTOR_RESET, 0xDEADBEEF);
	}
	/*** INTERRUPTS ***/
	private boolean waitingForInterrupt = false;
	public boolean isWaitingForInterrupt() {
		return waitingForInterrupt;
	}
	private TreeSet<Object> irqs = new TreeSet<Object>();
	private TreeSet<Object> fiqs = new TreeSet<Object>();
	public boolean haveIRQ() { return !irqs.isEmpty(); }
	public boolean haveFIQ() { return !fiqs.isEmpty(); }
	/**
	 * TODO: document the interrupt functions
	 * @param who
	 */
	public void setIRQ(Object who) {
		irqs.add(who);
	}
	/**
	 * 
	 * @param who
	 */
	public void clearIRQ(Object who) {
		irqs.remove(who);
	}
	/**
	 * 
	 * @param who
	 */
	public void setFIQ(Object who) {
		fiqs.add(who);
	}
	/**
	 * 
	 * @param who
	 */
	public void clearFIQ(Object who) {
		fiqs.remove(who);
	}
	/*** DEBUGGING ***/
	private boolean exceptionDebugMode = false;
	private boolean debugDumpMode = false;
	/**
	 * This mode defaults to false. When true, exceptions are thrown to Java rather than processed by the emulated CPU.
	 * @param nu The new mode.
	 */
	public void setExceptionDebugMode(boolean nu) { exceptionDebugMode = nu; }
	/**
	 * This mode defaults to false. When true, DBG #xxx instructions will result in a register dump to System.err.
	 * @param nu The new mode.
	 */
	public void setDebugDumpMode(boolean nu) { debugDumpMode = nu; }
	static final String[] regnames = new String[]{"r0","r1","r2","r3","r4","r5","r6","r7","r8","r9","r10","r11","r12","sp","lr","pc"};
	static final String[] banked_states = new String[]{};
	private String toBinary32(int in) {
		char out[] = new char[32];
		for(int n = 0; n < 32; ++n) {
			out[n] = (((in) >> (31-n)) & 1) != 0 ? '1' : '0';
		}
		return String.copyValueOf(out);
	}
	public void dumpState(PrintStream out) {
		// modified to use only println to be friendlier to Minecraft's logging diversion
		out.println(String.format("The running instruction would be (roughly) %08X", pc));
		out.println("Registers:");
		String line = null;
		for(int n = 0; n < 16; ++n) {
			if(n % 4 == 0) line = " ";
			line = line + String.format("%3s: %08X  ", regnames[n], readRegister(n));
			if(n % 4 == 3) out.println(line);
		}
		out.println("CPSR:");
		out.println("  NZCVQitJ....<ge><.it.>EAIFT<.m.>");
		out.println("  "+toBinary32(readCPSR()));
	}
	public void dumpExtraState(PrintStream out) {
		out.println("Banked Registers:");
		out.println("  <MODE> <..SP..> NZCVQitJ....<ge><.it.>EAIFT<.m.>");
		for(ProcessorMode mode : ProcessorMode.values()) {
			if(mode.spsrIndex >= 0)
				out.printf("  %6.6s %08X %s\n", mode.toString(), sp[mode.spIndex], toBinary32(spsr[mode.spsrIndex]));
			else
				out.printf("  %6.6s %08X xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\n", mode.toString(), sp[mode.spIndex]);
		}
	}
}
/* Note to self: Exception return forms of the data-processing instructions are noted on B4-1613 */