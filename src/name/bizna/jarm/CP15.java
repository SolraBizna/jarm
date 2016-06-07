package name.bizna.jarm;

/* VMSA system control registers (B3-1444, B3-1469, B3-1491) */
class CP15 extends SaneCoprocessor {
	/** CPUID REGISTERS **/
	/* Main ID Register */
	/* 24-31: Implementer ('j' | 0x80)
	   We break the rules by providing our own implementer bits. */
	/* 20-23: Variant (TODO: Allow the user to provide these bits) */
	/* 16-19: Architecture (0xF / "defined by CPUID scheme") */
	/* 4-15: Primary part number (TODO: Allow the user to provide these bits) */
	/* 0-3: Revision number (TODO: Allow the user to provide these bits) */
	static final int MIDR = 0xEA0F0000;
	/* Cache Type Register (B4-1556) */
	/* 29-31: 0b100, ARMv7 format */
	/* 24-27: CWG / Cache Write-back Granule. 0b0000 -> no info provided. */
	/* 20-23: ERG / Exclusives Reservation Granule. TODO: Pick a value for this */
	/* 16-19: DminLine. 0. */
	/* 14-15: L1Ip. Break the rules, RAZ. */
	/* 4-13,28: RAZ. */
	/* 0-3: IminLine. 0. */
	static final int CTR = 0x80000000;
	/* TCM Type Register (B4-1716) */
	/* 29-31: 0b100, ARMv7 format */
	/* 0-28: Implementation defined */
	static final int TCMTR = 0x80000000;
	/* TLB Type Register (B4-1721) */
	/* 1-31: Implementation defined */
	/* 0: nU; 0 -> unified TLB */
	static final int TLBTR = 0;
	/* Multiprocessor Affinity Register (RAZ in this implementation) */
	static final int MPIDR = 0;
	/* Revision ID Register (B4-1703) */
	/* TODO: Allow the user to provide these bits */
	static final int REVIDR = 0;
	/* Processor Feature Register 0 (B4-1633) */
	/* 16-31: Reserved */
	/* 12-15: State3 (0 -> no ThumbEE support) */
	/* 8-11: State2 (1 -> trivial Jazelle support) */
	/* 7-4: Thumb support (0 -> no Thumb support) TODO: change to 3 when we support Thumb */
	/* 0-3: ARM support (1 -> supported) */
	static final int ID_PFR0 = 0x00000101;
	/* Processor Feature Register 1 (B4-1635) */
	/* 20-31: Reserved */
	/* 16-19: Generic Timer support (0 -> none) */
	/* 12-15: Virtualization Extensions support (0 -> none) */
	/* 8-11: M profile programmer's model support (0 -> none) */
	/* 4-7: Security Extensions support (0 -> none) */
	/* 0-3: Standard programmer's model support (1 -> supported) */
	static final int ID_PFR1 = 1;
	/* Debug Feature Register 0 (B4-1605) */
	/* 28-31: Reserved */
	/* 24-27: Performance Monitors Extension (0 -> not supported) */
	/* 20-23: M debug model (0 -> not supported) */
	/* 16-19: Memory-mapped trace model (0 -> not supported) */
	/* 12-15: Coprocessor trace model (0 -> not supported) */
	/* 8-11: A/R debug model (0 -> not supported) */
	/* 4-7: Coprocessor Secure debug model (0 -> not supported) */
	/* 0-3: Coprocessor debug model (0 -> not supported) */
	static final int ID_DFR0 = 0;
	/* Auxiliary Feature Register 0 (B4-1604, RAZ in this implementation) */
	static final int ID_AFR0 = 0;
	/* Memory Model Feature Register 0 (B4-1621) */
	/* 28-31: Innermost shareability (UNK) */
	/* 24-27: FCSE support (TODO) */
	/* 20-23: Auxiliary registers (TODO) */
	/* 16-19: TCM/DMA support (TODO) */
	/* 12-15: Shareability levels (0?) */
	/* 8-11: Outermost shareability (15?) */
	/* 4-7: PMSA support (0) */
	/* 0-3: VMSA support (4 -> VMSAv7 + PXN bit) TODO: Long descriptor translation table? */
	static final int ID_MMFR0 = 0xF0000F04;
	/* Memory Model Feature Register 1 (B4-1623) */
	/* 28-31: Branch predictor (0 -> no branch predictor) TODO: Change this when we add JIT */
    /* 0-27: Required 0 in v7 */
	static final int ID_MMFR1 = 0x00000000;
	/* Memory Model Feature Register 2 (B4-1627) */
	/* 28-31: HW Access (1 -> supported) */
	/* 24-27: WFI (1 -> supported) */
	/* 20-23: CP15 memory barriers (2 -> ISB/DMB) */
	/* 16-19: TLB maintenance operations (3 -> ALL AAAHHHHH except Secure/Virt) */
	/* 0-15: Harvard things (0 -> we are not Harvard) */
	static final int ID_MMFR2 = 0x11230000;
	/* Memory Model Feature Register 3 (B4-1630) */
	/* 28-31: Supersections (0 -> supported) */
	/* 24-27: Cached memory size (0 -> 4GB) */
	/* 20-23: Coherent walk (1 -> coherent?) */
	/* 16-19: UNK */
	/* 12-15: Maintenance broadcast (0 -> irrelevant) */
	/* 8-11: BP maintain operations (2 -> all) */
	/* 4-7: Cache maintain set/way (1 -> all) */
	/* 0-3: Cache maintain MVA (1 -> all) */
	static final int ID_MMFR3 = 0x00100211;
	/* ISA Feature Register 0 (B4-1608) */
	/* 28-31: Reserved */
	/* 24-27: Divide instructions (2 -> both ARM and Thumb) */
	/* 20-23: Debug instructions (0 -> none) TODO: Do we support BKPT? */
	/* 16-19: Coprocessor instructions (4 -> all generics implemented) */
	/* 12-15: CmpBranch instructions (1 -> CBNZ/CBZ supported) TODO: this is a lie while we don't support Thumb */
	/* 8-11: Bitfield instructions (1 -> supported) */
	/* 4-7: BitCount instructions (1 -> supported) */
	/* 0-3: Swap instrutions (1 -> supported) */
	static final int ID_ISAR0 = 0x02041111;
	/* ISA Feature Register 1 (B4-1610) */
	/* 28-31: Jazelle instructions (1 -> BXJ supported) */
	/* 24-27: Interworking instructions (3 -> BX/BLX implemented, and all PC-altering operations are interworking branches) */
	/* 20-23: Immediate instructions (1 -> MOVT/MOV/ADD[i]/SUB[i]/etc instructions) */
	/* 16-19: IfThen instructions (1 -> supported) TODO: another Thumb lie */
	/* 12-15: Extend instructions (2 -> supported) */
	/* 8-11: Exception-handling instructions (A/R profile; 1 -> supported) */
	/* 4-7: Exception-handling instructions (generic, 1 -> supported) */
	/* 0-3: Endian instructions (1 -> supported) */
	static final int ID_ISAR1 = 0x13112111;
	/* ISA Feature Register 2 (B4-1613) */
	/* 28-31: Reversal instructions (2 -> REV/REV16/REVSH/RBIT supported) */
	/* 24-27: PSR register instructions (1 -> MRS/MSR supported) */
	/* 20-23: Unsigned Multiply instructions (2 -> UMULL, UMLAL, UMAAL supported) */
	/* 16-19: Signed Multiply instructions (3 -> many, many supported) */
	/* 12-15: Multiply instructions (2 -> all supported) */
	/* 8-11: LDM/STM interruptability (1 -> restartable) */
	/* 4-7: Memory Hint instructions (3 -> PLD/PLI/PLDW supported) TODO: Don't support these? */
	/* 0-3  Additional load/store instructions (1 -> LDRD/STRD) */
	static final int ID_ISAR2 = 0x21232131;
	/* ISA Feature Register 3 (B4-1615) */
	/* 28-31: ThumbEE support (0 -> not supported) */
	/* 24-27: True NOPs (1 -> yes) */
	/* 20-23: Thumb non-flagging low MOV (1 -> yes) */
	/* 16-19: Thumb table branch (1 -> yes) */
	/* 12-15: Sync primitives (2 -> all) */
	/* 8-11: SVC instruction (1 -> yes) */
	/* 4-7: Crummy SIMD instructions (3 -> all) */
	/* 0-3: Saturate instructions (1 -> yes) */
	static final int ID_ISAR3 = 0x01112131;
	/* ISA Feature Register 4 (B4-1618) */
	/* 28-31: SWP/SWPB memory locking (1 -> sort of) */
	/* 24-27: M profile forms of PSR modifications (1 -> yes) TODO: do we actually implement this? */
	/* 20-23: Sync primitives fraction (0) */
	/* 16-19: Barrier operations (1 -> yes) */
	/* 12-15: SMC instruction (1 -> yes) TODO: do we actually have this? */
	/* 8-11: Writeback addressing support (1 -> all instructions that have writeback encodings) */
	/* 4-7: Barrel shifter (4 -> all instructions that have shifted encodings) */
	/* 0-3: Unprivileged instruction variants (2 -> all) */
	static final int ID_ISAR4 = 0x11011142;
	/* ISA Feature Register 5 (B4-1620) */
	/* 0-31: Reserved */
	static final int ID_ISAR5 = 0;
	/* Auxiliary ID Register (implementation defined) TODO: allow user to provide these bits */
	static final int AIDR = 0x9C0FFEE5;
	/** CACHE REGISTERS **/
	/* (RO) Cache Size ID Registers TODO: Revisit when JIT */
	/* 31: Write-through (0 -> no) */
	/* 30: Write-back (0 -> no) */
	/* 29: Read-allocation (0 -> no?) */
	/* 28: Write-allocation (0 -> no?) */
	/* 13-27: Number of sets (0 -> one?) */
	/* 3-12: Associativity (0 -> one?) */
	/* 0-2: Line size (0 -> four words?) */
	static final int CCSIDR = 0x00000000;
	/* (RO) Cache Level ID Register (B4-1530) */
	/* 30-31: UNK */
	/* 24-26: Level of Coherence (TODO: B2-1275) */
	/* 21-23: Level of Unification Inner Shareable (TODO: B2-1275) */
	/* 3-20: Ctype2-7 (0 -> no cache) */
	/* 0-2: Ctype1 (0 -> no cache) TODO: change to 1 when JIT is in use */
	static final int CLIDR = 0x00000000;
	/* (RW) Cache Size Selection Register (B4-1555) */
	/* Implemented as RAZ/WI */
	int CSSELR = 0;
	/** SYSTEM CONTROL REGISTERS **/
	/* System Control Register, the big kahuna (B4-1707) */
	/* reset() sets this, see there for initial values */
	/* 31: RAZ */
	/* 30: TE */
	/* 29: AFE */
	/* 28: TRE */
	/* 27: NMFI */
	/* 26: RAZ */
	/* 25: EE */
	/* 24: IVE (RAZ) */
	/* 23: RAO */
	/* 22: U (RAO) */
	/* 21: FI (RAO) */
	/* 20: UWXN (RAZ) */
	/* 19: WXN (RAZ) */
	/* 18: RAO */
	/* 17: HA */
	/* 16: RAO */
	/* 15: RAZ */
	/* 14: RR (RAZ) */
	/* 13: V */
	/* 12: I (RAZ) TODO: Make me RW and allow me to enable JIT */
	/* 11: Z (RAZ) TODO: "" */
	/* 10: SW */
	/* 8-9: RAZ */
	/* 7: B (RAZ) */
	/* 6: RAO */
	/* 5: CP15BEN */
	/* 3-4: RAO */
	/* 2: C */
	/* 1: A */
	/* 0: M */
	static final int SCTLR_BIT_TE = 30;
	static final int SCTLR_BIT_AFE = 29;
	static final int SCTLR_BIT_TRE = 28;
	static final int SCTLR_BIT_NMFI = 27;
	static final int SCTLR_BIT_EE = 25;
	static final int SCTLR_BIT_HA = 17;
	static final int SCTLR_BIT_V = 13;
	static final int SCTLR_BIT_I = 12;
	static final int SCTLR_BIT_Z = 11;
	static final int SCTLR_BIT_SW = 10;
	static final int SCTLR_BIT_CP15BEN = 5;
	static final int SCTLR_BIT_C = 2;
	static final int SCTLR_BIT_A = 1;
	static final int SCTLR_BIT_M = 0;
	static final int SCTLR_READ_OR = 0x00E50058;
	static final int SCTLR_READ_MASK = 0x7A022427;
	int SCTLR = 0xDEADBEEF;
	/* Auxiliary Control Register */
	/* Implemented as a fully read/write register that is not affected by resets but is 0 on power-on */
	int ACTLR = 0;
	/* Coprocessor Access Control Register (TODO: implement) */
	int CPACR = 0;
	/** IMPLEMENTATION **/
	CP15(CPU cpu) { super(cpu); }
	/* B3-1446: all CDP, LDC, and STC operations to ... CP15 [are undefined] */
	@Override
	public void coprocessorDataOperation(boolean unconditional, int coproc, int opc1, int opc2, int CRn, int CRm, int CRd) throws BusErrorException, AlignmentException, UndefinedException {
		throw new UndefinedException();
	}
	@Override
	public void loadCoprocessorRegisterFromMemory(boolean unconditional, int coproc, boolean D, int base, int CRd, int option, int iword) throws BusErrorException, AlignmentException, UndefinedException {
		throw new UndefinedException();
	}
	@Override
	public void storeCoprocessorRegisterToMemory(boolean unconditional, int coproc, boolean D, int base, int CRd, int option, int iword) throws BusErrorException, AlignmentException, UndefinedException {
		throw new UndefinedException();
	}
	@Override
	public void moveCoprocessorRegisterToCoreRegister(boolean unconditional, int coproc, int opc1, int opc2, int CRn, int CRm, int Rt) throws BusErrorException, AlignmentException, UndefinedException {
		int readValue = 0xdeadbeef;
		boolean privileged = true;
		boolean valid = false;
		switch(CRn) {
		case 0:
			/* ID registers (B3-1471) */
			switch(opc1) {
			case 0:
				valid = true;
				switch(CRm) {
				case 0:
					switch(opc2) {
					case 0: case 4: case 7: readValue = MIDR; break;
					case 1: readValue = CTR; break;
					case 2: readValue = TCMTR; break;
					case 3: readValue = TLBTR; break;
					case 5: readValue = MPIDR; break;
					case 6: readValue = REVIDR; break;
					}
					break;
				case 1:
					switch(opc2) {
					case 0: readValue = ID_PFR0; break;
					case 1: readValue = ID_PFR1; break;
					case 2: readValue = ID_DFR0; break;
					case 3: readValue = ID_AFR0; break;
					case 4: readValue = ID_MMFR0; break;
					case 5: readValue = ID_MMFR1; break;
					case 6: readValue = ID_MMFR2; break;
					case 7: readValue = ID_MMFR3; break;
					}
					break;
				case 2:
					switch(opc2) {
					case 0: readValue = ID_ISAR0; break;
					case 1: readValue = ID_ISAR1; break;
					case 2: readValue = ID_ISAR2; break;
					case 3: readValue = ID_ISAR3; break;
					case 4: readValue = ID_ISAR4; break;
					case 5: readValue = ID_ISAR5; break;
					default: readValue = 0;
					}
					break;
				default:
					readValue = 0; /* RAZ */
				}
				break;
			case 1:
				if(CRm == 0) {
					switch(opc2) {
					case 0: valid = true; readValue = CCSIDR; break;
					case 1: valid = true; readValue = CLIDR; break;
					case 7: valid = true; readValue = AIDR; break;
					}
				}
				break;
			case 2:
				if(CRm == 0 && opc2 == 0) {
					valid = true;
					readValue = CSSELR & 15;
					break;
				}
				break;
			}
			break;
		case 1:
			/* System control registers (B3-1472) */
			if(opc1 == 0 && CRm == 0) {
				switch(opc2) {
				case 0: valid = true; readValue = (SCTLR & SCTLR_READ_MASK) | SCTLR_READ_OR; break;
				case 1: valid = true; readValue = ACTLR; break;
				case 2: valid = true; readValue = CPACR; break;
				}
			}
			break;
		case 2:
			/* Memory protection and control registers (B3-1473) */
			break;
		case 3:
			/* Memory protection and control registers, continued (B3-1473) */
			break;
		case 4:
			/* Not used */
			break;
		case 5:
			/* Memory system fault registers (B3-1474) */
			break;
		case 6:
			/* Memory system fault registers, continued (B3-1474) */
			break;
		case 7:
			/* Cache maintenance, address translation, and other functions (B3-1475) */
			break;
		case 8:
			/* TLB maintenance operations (B3-1476) */
			/* All write-only */
			break;
		case 9:
			/* Cache and TCM control and performance monitors (B3-1477) */
			break;
		case 10:
			/* Memory remapping and TLB control registers (B3-1478) */
			break;
		case 11:
			/* Reserved for TCM DMA registers (B3-1478) */
			break;
		case 12:
			/* Reserved for Security Extensions (B3-1479) */
			/* We do not implement the Security Extensions */
			break;
		case 13:
			/* Process, context, and thread ID registers (B3-1479) */
			break;
		case 14:
			/* Generic Timer Extension (B3-1480) */
			break;
		case 15:
			/* Implementation defined */
			break;
		}
		if(!valid) throw new UndefinedException();
		if(privileged && !cpu.isPrivileged()) throw new UndefinedException();
		cpu.writeGPR(Rt, readValue);
	}
	@Override
	public void moveCoreRegisterToCoprocessorRegister(boolean unconditional, int coproc, int opc1, int opc2, int CRn, int CRm, int Rt) throws BusErrorException, AlignmentException, UndefinedException {
		/* TODO: implement write access */
		throw new UndefinedException();
	}
	@Override
	public void moveCoreRegistersToCoprocessorRegister(boolean unconditional, int coproc, int opc1, int CRm, int Rt, int Rt2) throws BusErrorException, AlignmentException, UndefinedException {
		throw new UndefinedException();
	}
	@Override
	public void moveCoprocessorRegisterToCoreRegisters(boolean unconditional, int coproc, int opc1, int CRm, int Rt, int Rt2) throws BusErrorException, AlignmentException, UndefinedException {
		throw new UndefinedException();
	}
	@Override
	public void reset() {} /* never called; we have special reset logic */
	/* TODO: make me not have special reset logic, and do the post-reset configuration in CPU.reset */
	void reset(boolean thumb_exceptions, boolean big_endian, boolean high_vectors) {
		/* SCTLR (B4-1707) */
		/* 31: RAZ */
		/* 30: TE (thumb_exceptions) */
		/* 29: AFE (0->???) */
		/* 28: TRE (0->???) */
		/* 27: NMFI (0->maskable, RO) */
		/* 26: RAZ */
		/* 25: EE (big_endian) */
		/* 24: IVE (RAZ) */
		/* 23: RAO */
		/* 22: U (RAO) */
		/* 21: FI (RAO) */
		/* 20: UWXN (RAZ) */
		/* 19: WXN (RAZ) */
		/* 18: RAO */
		/* 17: HA (0->disabled) */
		/* 16: RAO */
		/* 15: RAZ */
		/* 14: RR (RAZ) */
		/* 13: V (high_vectors) */
		/* 12: I (RAZ) TODO: Make me RW and allow me to enable JIT */
		/* 11: Z (RAZ) TODO: "" */
		/* 10: SW (0->disabled) */
		/* 8-9: RAZ */
		/* 7: B (RAZ) */
		/* 6: RAO */
		/* 5: CP15BEN (1->enabled) */
		/* 3-4: RAO */
		/* 2: C (0->data/unified caches disabled) */
		/* 1: A (0->alignment checking disabled) */
		/* 0: M (0->MMU disabled) */
		SCTLR = 0x00E50078;
		if(thumb_exceptions) SCTLR |= 1<<SCTLR_BIT_TE;
		if(big_endian) SCTLR |= 1<<SCTLR_BIT_EE;
		if(high_vectors) SCTLR |= 1<<SCTLR_BIT_V;
	}
}
