package name.bizna.jarm;

public strictfp class FPU extends SaneCoprocessor {
	
	/* TODO: How to copy FPU status bits to APSR? */
	
	/* 31: Special exception handling (RAZ/WI) */
	static final int FPEXC_BIT_EX = 31;
	/* 30: Floating-point enabled (1=enabled) */
	static final int FPEXC_BIT_EN = 30;
	/* 0-29: RAZ/WI */
	static final int FPEXC_READ_MASK = 0x40000000;
	static final int FPEXC_WRITE_MASK = 0x40000000;
	
	/* TODO: documentation
	 * DN=1,Z=0,RMode=0,Stride=0,Len=0 or all floating-point data operations are undefined so that software can implement them
	 * exceptions bits are ignored by the "hardware" because of Java limitations
	 */
	
	/* condition flags used by floating point compares */
	static final int FPSCR_BIT_N = 31;
	static final int FPSCR_BIT_Z = 30;
	static final int FPSCR_BIT_C = 29;
	static final int FPSCR_BIT_V = 28;
	/* (advanced SIMD only) */
	static final int FPSCR_BIT_QC = 27;
	/* UNK/SBZP as we don't implement half-precision */
	static final int FPSCR_BIT_AHP = 26;
	/* Default NaN mode (see A2-69) (all floating-point data operations are unimplemented if not set to 1) */
	static final int FPSCR_BIT_DN = 25;
	/* Flush-to-zero mode (see A2-68) (all floating-point data operations are unimplemented if not set to 0) */
	static final int FPSCR_BIT_FZ = 24;
	/* Floating-point rounding mode (all floating-point data operations are unimplemented if not set to 00) */
	static final int FPSCR_SHIFT_RMode = 22;
	static final int FPSCR_MASK_RMode = 3;
	/* Floating-point Stride (nonzero is unimplemented) */
	static final int FPSCR_SHIFT_Stride = 20;
	static final int FPSCR_MASK_Stride = 3;
	// 19 is reserved
	/* Floating-point Len (nonzero is unimplemented) */
	static final int FPSCR_SHIFT_Len = 16;
	static final int FPSCR_MASK_Len = 7;
	/* exceptions aren't implemented in hardware, in ANY form, because of Java limitations */
	/* all of the exception enable bits are ignored */
	static final int FPSCR_BIT_IDE = 15; // Input Denormal
	// 14/13 are reserved
	static final int FPSCR_BIT_IXE = 12; // Inexact
	static final int FPSCR_BIT_UFE = 11; // Underflow
	static final int FPSCR_BIT_OFE = 10; // Overflow
	static final int FPSCR_BIT_DZE = 9; // Division by Zero
	static final int FPSCR_BIT_IOE = 8; // Invalid Operation
	/* all of the cumulative exception bits are ignored */
	static final int FPSCR_BIT_IDC = 7;
	// 6/5 are reserved
	static final int FPSCR_BIT_IXC = 4;
	static final int FPSCR_BIT_UFC = 3;
	static final int FPSCR_BIT_OFC = 2;
	static final int FPSCR_BIT_DZC = 1;
	static final int FPSCR_BIT_IOC = 0;
	
	static final int FPSCR_READ_MASK = 0xF3F79F9F;
	static final int FPSCR_WRITE_MASK = 0xF3F79F9F;
	static final int FPSCR_IMPLEMENTED_MASK = 0x03FF0000;
	static final int FPSCR_IMPLEMENTED_SUBSET = 0x02000000;

	/* 24-31: Implementer ('j' | 0x80)
	   We break the rules by providing our own implementer bits. */
	/* 23: SW (0 -> hardware support for floating-point) */
	/* 22-16: Sub-architecture (4 -> VFPv3 + Common VFP... close enough) */
	/* 8-15: Part number (implementation defined) */
	/* 4-7: Variant (implementation defined) */
	/* 0-3: Revision (implementation defined) */
	static private final int FPSID = 0xEA040000;
	
	/* 28-31: VFP rounding modes (0 -> Round To Nearest only) */
	/* 24-27: Short vectors (0 -> no) */
	/* 20-23: Square root (1 -> yes) */
	/* 16-19: Divide (1 -> supported) */
	/* 12-15: Exception trapping (0 -> Not supported) */
	/* 8-11: Double precision (2 -> VFPv3) */
	/* 4-7: Single precisoin (2 -> VFPv3) */
	/* 0-3: A_SIMD registers (2 -> 32 64-bit registers) */
	static private final int MVFR0 = 0x00110222;
	
	/* 28-31: A_SIMD FMAC (0 -> lie, no fused multiply accumulate instructions) */
	/* 24-27: VFP HPFP (0 -> no half-precision) */
	/* 8-23: A_SIMD stuff (0 -> none!) */
	/* 4-7: NAN propagation stuff (0 -> ??? Default NaN, yeah, that's what we do, yeah...) */
	/* 0-3: Denormalized arithmetic (1 -> no flush-to-zero? does Java guarantee this?) */
	static private final int MVFR1 = 0x00000001;
	
	private int[] registerBits = new int[64];
	private int FPSCR, FPEXC;
	
	private static int expandVFPImmediateSingleRawBits(int imm8) {
		int bits = ((imm8&128)<<24) | ((imm8&63)<<19);
		if((imm8&64)!=0)
			bits |= 0x3E000000;
		else
			bits |= 0x40000000;
		return bits;
	}
	private static int expandVFPImmediateDoubleHighRawBits(int imm8) {
		/* low bits are always 0 */
		int bits = ((imm8&128)<<24) | ((imm8&63)<<16);
		if((imm8&64)!=0)
			bits |= 0x3FC00000;
		else
			bits |= 0x40000000;
		return bits;
	}
	private static int singleToInt(float v, boolean unsigned) {
		if(unsigned) {
			if(v >= 4294967295.0F) return -1;
			else if(v >= 2147483648.0F) return (int)(v - 4294967296.0F);
			else if(v <= 0.0F) return 0;
			else return (int)v;
		}
		else {
			if(v >= 2147483647.0F) return 2147483647;
			else if(v <= -2147483648.0F) return -2147483648;
			else return (int)v;
		}
	}
	private static int doubleToInt(double v, boolean unsigned) {
		if(unsigned) {
			if(v >= 4294967295.0) return -1;
			else if(v >= 2147483648.0) return (int)(v - 4294967296.0);
			else if(v <= 0.0) return 0;
			else return (int)v;
		}
		else {
			if(v >= 2147483647.0) return 2147483647;
			else if(v <= -2147483648) return -2147483648;
			else return (int)v;
		}
	}
	
	FPU(CPU cpu) {
		super(cpu);
	}
	
	private void loadSingle(int address, int d) throws AlignmentException, BusErrorException, EscapeCompleteException, EscapeRetryException {
		registerBits[d] = cpu.instructionReadWord(address);
	}
	private void loadDouble(int address, int d) throws AlignmentException, BusErrorException, EscapeCompleteException, EscapeRetryException {
		if(cpu.isBigEndian()) {
			registerBits[d+1] = cpu.instructionReadWord(address);
			registerBits[d] = cpu.instructionReadWord(address+4);
		}
		else {
			registerBits[d] = cpu.instructionReadWord(address);
			registerBits[d+1] = cpu.instructionReadWord(address+4);
		}
	}
	private void storeSingle(int address, int d) throws AlignmentException, BusErrorException, EscapeCompleteException, EscapeRetryException {
		cpu.instructionWriteWord(address, registerBits[d]);
	}
	private void storeDouble(int address, int d) throws AlignmentException, BusErrorException, EscapeCompleteException, EscapeRetryException {
		if(cpu.isBigEndian()) {
			cpu.instructionWriteWord(address, registerBits[d+1]);
			cpu.instructionWriteWord(address+4, registerBits[d]);
		}
		else {
			cpu.instructionWriteWord(address, registerBits[d]);
			cpu.instructionWriteWord(address+4, registerBits[d+1]);
		}
	}
	private float getSingle(int i) { return Float.intBitsToFloat(registerBits[i]); }
	private int getSingleRawBits(int i) { return registerBits[i]; }
	private double getDouble(int i) { return Double.longBitsToDouble((registerBits[i]&0xFFFFFFFFL)|((long)registerBits[i+1]<<32L)); }
	private long getDoubleRawBits(int i) { return (registerBits[i]&0xFFFFFFFFL)|((long)registerBits[i+1]<<32L); }
	private void putSingle(int i, float v) { registerBits[i] = Float.floatToIntBits(v); }
	private void putSingle(int i, int v) { registerBits[i] = v; }
	private void putDouble(int i, double v) { long l = Double.doubleToLongBits(v); registerBits[i] = (int)l; registerBits[i+1] = (int)(l>>32L); }
	private void putDouble(int i, long l) { registerBits[i] = (int)l; registerBits[i+1] = (int)(l>>32L); }

	private boolean floatingPointIsEnabled() { return ((FPEXC>>FPEXC_BIT_EN)&1)!=0; }
	
	@Override
	public void storeCoprocessorRegisterToMemory(boolean unconditional,
			int coproc, boolean D, int base, int CRd, int option, int iword)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		if(unconditional) throw new UndefinedException();
		if(!floatingPointIsEnabled()) throw new UndefinedException();
		/* Extension register load/store instructions (A7-274) */
		boolean double_precision = (coproc&1)==1;
		int d = CRd<<1;
		if(double_precision) {
			if(D) d|=32;
		}
		else {
			if(D) d|=1;
		}
		if((iword & 0x1200000) != 0x1000000) {
			/* Vector Store Multiple (A8-1080) */
			/* Vector Push Registers (A8-992) */
			int regs = iword & 255;
			// TODO: document that VSTM can wrap around the register file
			if(double_precision) {
				// TODO: document that deprecated instruction FLDMX is not implemented
				if((regs&1) == 1) throw new UndefinedException();
				for(int CR = d; CR < d + regs; CR += 2) { 
					storeDouble(base, CR%64);
					base += 8;
				}
			}
			else {
				for(int CR = d; CR < d + regs; ++CR) { 
					storeSingle(base, CR%64);
					base += 4;
				}
			}
		}
		else {
			/* Vector Store Register (A8-1082) */
			if(double_precision)
				storeDouble(base, d);
			else
				storeSingle(base, d);
		}
	}

	@Override
	public void loadCoprocessorRegisterFromMemory(boolean unconditional,
			int coproc, boolean D, int base, int CRd, int option, int iword)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		if(unconditional) throw new UndefinedException();
		if(!floatingPointIsEnabled()) throw new UndefinedException();
		/* Extension register load/store instructions (A7-274) */
		boolean double_precision = (coproc&1)==1;
		int d = CRd<<1;
		if(double_precision) {
			if(D) d|=32;
		}
		else {
			if(D) d|=1;
		}
		if((iword & 0x1200000) != 0x1000000) {
			/* Vector Load Multiple (A8-922) */
			/* Vector Pop Registers (A8-990) */
			int regs = iword & 255;
			// TODO: document that VLDM can wrap around the register file
			if(double_precision) {
				// TODO: document that deprecated instruction FLDMX is not implemented
				if((regs&1) == 1) throw new UndefinedException();
				for(int CR = d; CR < d + regs; CR += 2) { 
					loadDouble(base, CR%64);
					base += 8;
				}
			}
			else {
				for(int CR = d; CR < d + regs; ++CR) { 
					loadSingle(base, CR%64);
					base += 4;
				}
			}
		}
		else {
			/* Vector Load Register (A8-924) */
			if(double_precision)
				loadDouble(base, d);
			else
				loadSingle(base, d);
		}
	}

	@Override
	public void moveCoreRegisterToCoprocessorRegister(boolean unconditional,
			int coproc, int opc1, int opc2, int CRn, int CRm, int Rt)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		if(unconditional) throw new UndefinedException();
		/* 8, 16, and 32-bit transfer between ARM core and extension registers (A7-278) */
		/* L = 0 */
		final int A = opc1;
		final int B_x = opc2; /* might have 4 set, but B is two bits */
		if((coproc&1) == 0) {
			/* C = 0 */
			if(A == 0) {
				/* VMOV (A8-944) */
				/* CRm != 0 is UNPREDICTABLE, opc2 & 3 != 0 is unpredictable */
				int n = CRn<<1;
				if((opc2&4)!=0) n|=1;
				registerBits[n] = cpu.readRegister(Rt);
				return;
			}
			else if(A == 7) {
				/* VMSR (A8-956, B9-2016) */
				if(CRn != 1 && !cpu.isPrivileged()) throw new UndefinedException();
				switch(CRn) {
				// 0 is FPSID
				case 1:
					if(!floatingPointIsEnabled()) throw new UndefinedException();
					FPSCR = (FPSCR&~FPSCR_WRITE_MASK)|(cpu.readRegister(Rt)&FPSCR_WRITE_MASK); return;
				case 8: FPEXC = (FPEXC&~FPEXC_WRITE_MASK)|(cpu.readRegister(Rt)&FPEXC_WRITE_MASK); return;
				}
			}
		}
		else {
			/* C = 1 */
			if((A&4) == 0) {
				/* VMOV (A8-940) */
				/* CRm != 0 is UNPREDICTABLE */
				if((opc1&2)!=0) throw new UndefinedException(); /* Advanced SIMD */
				if((opc2&3)!=0) throw new UndefinedException(); /* Advanced SIMD or undefined */
				int d = CRn<<1;
				if((opc2&4)!=0) d|=32;
				if((opc1&1)!=0) d|=1;
				registerBits[d] = cpu.readRegister(Rt);
				return;
			}
			else if((B_x&2) == 0) {
				/* VDUP (A8-886) */
				// this is Advanced SIMD, we don't implement that! */
				//throw new UnimplementedInstructionException("VDUP");
			}
		}
		throw new UndefinedException();
	}

	@Override
	public void moveCoprocessorRegisterToCoreRegister(boolean unconditional,
			int coproc, int opc1, int opc2, int CRn, int CRm, int Rt)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		if(unconditional) throw new UndefinedException();
		/* 8, 16, and 32-bit transfer between ARM core and extension registers (A7-278) */
		/* L = 1 */
		final int A = opc1;
		if((coproc&1) == 0) {
			/* C = 0 */
			if(A == 0) {
				/* VMOV (A8-944) */
				/* CRm != 0 is unpredictable, opc2 & 3 != 0 is unpredictable */
				int n = CRn<<1;
				if((opc2&4)!=0) n|=1;
				cpu.writeRegister(Rt, registerBits[n]);
				return;
			}
			else if(A == 7) {
				/* VMRS (A8-954, B9-2014) */
				if(CRn != 1 && !cpu.isPrivileged()) throw new UndefinedException();
				switch(CRn) {
				case 0: cpu.writeRegister(Rt, FPSID); return;
				case 1:
					if(!floatingPointIsEnabled()) throw new UndefinedException();
					if(Rt == 15)
						cpu.setConditions(FPSCR>>28);
					else
						cpu.writeRegister(Rt, FPSCR&FPSCR_READ_MASK);
					return;
				case 6: cpu.writeRegister(Rt, MVFR1); return;
				case 7: cpu.writeRegister(Rt, MVFR0); return;
				case 8: cpu.writeRegister(Rt, FPEXC&FPEXC_READ_MASK); return;
				}
			}
		}
		else {
			/* C = 1 */
			/* VMOV (A8-942) */
			/* CRm != 0 is UNPREDICTABLE */
			if((opc1&6)!=0) throw new UndefinedException(); /* Advanced SIMD */
			if((opc2&3)!=0) throw new UndefinedException(); /* Advanced SIMD or undefined */
			int d = CRn<<1;
			if((opc2&4)!=0) d|=32;
			if((opc1&1)!=0) d|=1;
			cpu.writeRegister(Rt, registerBits[d]);
			return;
		}
		throw new UndefinedException();
	}

	@Override
	public void coprocessorDataOperation(boolean unconditional, int coproc,
			int _opc1, int _opc2, int CRn, int CRm, int CRd)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		if(unconditional) throw new UndefinedException();
		if(!floatingPointIsEnabled()) throw new UndefinedException();
		if((FPSCR&FPSCR_IMPLEMENTED_MASK)!=FPSCR_IMPLEMENTED_SUBSET) throw new UndefinedException();
		int D = (_opc1 >> 2) & 1;
		int N = (_opc2 >> 2) & 1;
		int M = (_opc2) & 1;
		boolean double_precision = (coproc & 1) != 0;
		/* ISN'T IT GREAT THAT JAVA DOESN'T HAVE MACROS?! */
		int Rn = CRn<<1, Rd = CRd<<1, Rm = CRm<<1;
		if(double_precision) {
			if(N != 0) Rn |= 32;
			if(M != 0) Rm |= 32;
			if(D != 0) Rd |= 32;
		}
		else {
			if(N != 0) Rn |= 1;
			if(M != 0) Rm |= 1;
			if(D != 0) Rd |= 1;
		}
		/* Floating-point data processing instructions (A7-272) */
		/* CDP fields as they map to fields on A7-272 */
		final int opc1 = _opc1;
		final int opc2 = CRn;
		final int opc3_x = _opc2; // == op3 << 1
		switch(opc1 & 11) {
		case 9:
			/* Vector Fused Negate Multiply Accumulate or Subtract (A8-894) */
			// fall through
		case 10:
			/* Vector Fused Multiply Accumulate or Subtract (A8-892) */
			// cannot be implemented in Java, let software try
			throw new UndefinedException();
		case 0: {
			/* Vector Multiply Accumulate or Subtract (A8-932) */
			boolean isSubtraction = (_opc2&2)!=0;
			if(isSubtraction) {
				if(double_precision)
					putDouble(Rd, getDouble(Rd)+getDouble(Rn)*getDouble(Rm));
				else
					putSingle(Rd, getSingle(Rd)+getSingle(Rn)*getSingle(Rm));
			}
			else {
				if(double_precision)
					putDouble(Rd, getDouble(Rd)-getDouble(Rn)*getDouble(Rm));
				else
					putSingle(Rd, getSingle(Rd)-getSingle(Rn)*getSingle(Rm));
			}
			return;
		}
		case 2:
			if((opc3_x&2)==0) {
				/* Vector Multiply (A8-960) */
				if(double_precision)
					putDouble(Rd, getDouble(Rn)*getDouble(Rm));
				else
					putSingle(Rd, getSingle(Rn)*getSingle(Rm));
				return;
			}
			else {
				/* Vector Negate Multiply Accumulate or Subtract (A8-970) */
				if(double_precision)
					putDouble(Rd, -(getDouble(Rn)*getDouble(Rm)));
				else
					putSingle(Rd, -(getSingle(Rn)*getSingle(Rm)));
				return;
			}
		case 1: {
			/* Vector Negate Multiply Accumulate or Subtract (A8-970) */
			boolean isSubtraction = (_opc2&2)!=0;
			if(isSubtraction) {
				if(double_precision)
					putDouble(Rd, -getDouble(Rd)+getDouble(Rn)*getDouble(Rm));
				else
					putSingle(Rd, -getSingle(Rd)+getSingle(Rn)*getSingle(Rm));
			}
			else {
				if(double_precision)
					putDouble(Rd, -getDouble(Rd)-getDouble(Rn)*getDouble(Rm));
				else
					putSingle(Rd, -getSingle(Rd)-getSingle(Rn)*getSingle(Rm));
			}
			return;
		}
		case 3:
			if((opc3_x&2)==0) {
				/* Vector Add (A8-830) */
				if(double_precision)
					putDouble(Rd, getDouble(Rn)+getDouble(Rm));
				else
					putSingle(Rd, getSingle(Rn)+getSingle(Rm));
				return;
			}
			else {
				/* Vector Subtract (A8-1086) */
				if(double_precision)
					putDouble(Rd, getDouble(Rn)-getDouble(Rm));
				else
					putSingle(Rd, getSingle(Rn)-getSingle(Rm));
				return;
			}
		case 8:
			if((opc3_x&2)==0) {
				/* Vector Divide (A8-882) */
				if(double_precision)
					putDouble(Rd, getDouble(Rn)/getDouble(Rm));
				else
					putSingle(Rd, getSingle(Rn)/getSingle(Rm));
				return;
			}
			break;
		case 11:
			if((opc3_x&2)==0) {
				/* Vector Move (immediate, A8-936) */
				int imm8 = CRm|(CRn<<4);
				if(double_precision) {
					registerBits[Rd] = 0;
					registerBits[Rd+1] = expandVFPImmediateDoubleHighRawBits(imm8);
				}
				else
					registerBits[Rd] = expandVFPImmediateSingleRawBits(imm8);
				return;
			}
			else {
				switch(opc2) {
				case 0:
					switch(opc3_x&6) {
					case 2:
						/* Vector Move (register, A8-938) */
						if(double_precision) putDouble(Rd, getDoubleRawBits(Rm));
						else putSingle(Rd, getSingleRawBits(Rm));
						return;
					case 6:
						/* Vector Absolute (A8-824) */
						if(double_precision) putDouble(Rd, Math.abs(getDouble(Rm)));
						else putSingle(Rd, (float)Math.abs(getSingle(Rm)));
						return;
					}
					break;
				case 1:
					switch(opc3_x&6) {
					case 2:
						/* Vector Negate (A8-968) */
						if(double_precision)
							putDouble(Rd, -getDouble(Rm));
						else
							putSingle(Rd, -getSingle(Rm));
						return;
					case 6:
						/* Vector Square Root (A8-1058) */
						if(double_precision)
							putDouble(Rd, Math.sqrt(getDouble(Rm)));
						else
							putSingle(Rd, (float)Math.sqrt(getSingle(Rm)));
						return;
					}
					break;
				case 2:
				case 3:
					/* Vector Convert (A8-880) */
					// throw new UnimplementedInstructionException("VCVTB/VCVTT");
					/* Half-Precision extension, which we don't implement */
					/* TODO: implement it someday? */
					break;
				case 4:
				case 5:
					/* Vector Compare (A8-864) */
					boolean with_zero = (opc2&1)!=0;
					// TODO: invalid operation exception
					// boolean quiet_nan_exceptions = (_opc2&4)!=0;
					if(double_precision) {
						double a = getDouble(Rd);
						double b = with_zero ? 0.0 : getDouble(Rm);
						if(Double.isNaN(a) || Double.isNaN(b))
							FPSCR = (FPSCR&0x0FFFFFFF)|0x30000000;
						else {
							FPSCR = (FPSCR&0x0FFFFFFF);
							if(a < b) FPSCR |= 1<<FPSCR_BIT_N;
							if(a == b) FPSCR |= 1<<FPSCR_BIT_Z;
							if(a >= b) FPSCR |= 1<<FPSCR_BIT_C; // >= is not a typo
							// do not set V
						}
					}
					else {
						float a = getSingle(Rd);
						float b = with_zero ? 0.0f : getSingle(Rm);
						if(Float.isNaN(a) || Float.isNaN(b))
							FPSCR = (FPSCR&0x0FFFFFFF)|0x30000000;
						else {
							FPSCR = (FPSCR&0x0FFFFFFF);
							if(a < b) FPSCR |= 1<<FPSCR_BIT_N;
							if(a == b) FPSCR |= 1<<FPSCR_BIT_Z;
							if(a >= b) FPSCR |= 1<<FPSCR_BIT_C; // >= is not a typo
							// do not set V
						}
					}
					return;
				case 7:
					if(opc3_x == 6) {
						/* Vector Convert (double-precision <-> single-precision, A8-876) */
						if(D != 0) Rd ^= 33;
						if(double_precision)
							putSingle(Rd, (float)getDouble(Rm));
						else
							putDouble(Rd, (double)getSingle(Rm));
						return;
					}
					else
						break;
				case 8:
				case 12:
				case 13:
					/* Vector Convert (floating point <-> integer, FP, A8-870) */
					if((opc2&4)!=0) {
						/* float -> integer */
						/* ignore rounding bit! */
						if(double_precision) {
							if(D != 0) Rd ^= 33;
							registerBits[Rd] = doubleToInt(getDouble(Rm), (opc2&1)==0);
						}
						else registerBits[Rd] = singleToInt(getSingle(Rm), (opc2&1)==0);
					}
					else {
						/* integer -> float */
						boolean unsigned = (opc3_x & 4) == 0;
						if(double_precision) {
							if(M != 0) Rm ^= 33;
							double n;
							if(unsigned) n = (double)(registerBits[Rm]&0xFFFFFFFFL);
							else n = (double)registerBits[Rm];
							putDouble(Rd, n);
						}
						else {
							float n;
							if(unsigned) n = (float)(registerBits[Rm]&0xFFFFFFFFL);
							else n = (float)registerBits[Rm];
							putSingle(Rd, n);
						}
					}
					return;
				case 10:
				case 11: {
					/* Vector Convert (floating point <-> fixed-point, FP, A8-874) */
					/* (fixed to float) */
					boolean sixteen_bit = (_opc2&4)!=0;
					int frac_bits = (sixteen_bit?16:32) - (CRm | ((_opc2&1)<<4));
					if(frac_bits < 0 || (frac_bits == 0 && !sixteen_bit)) throw new UndefinedException();
					boolean unsigned = (opc2&1)!=0;
					int input = registerBits[Rd];
					if(sixteen_bit) {
						input = input << 16;
						if(unsigned) input = input >>> 16;
						else input = input >> 16;
					}
					double asDouble = Math.scalb((double)input, -frac_bits);
					if(double_precision) putDouble(Rd, asDouble);
					else putSingle(Rd, (float)asDouble);
					return;
				}
				case 14:
				case 15: {
					/* Vector Convert (floating point <-> fixed-point, FP, A8-874) */
					/* (float to fixed) */
					boolean sixteen_bit = (_opc2&4)!=0;
					int frac_bits = (sixteen_bit?16:32) - (CRm | ((_opc2&1)<<4));
					if(frac_bits < 0 || (frac_bits == 0 && !sixteen_bit)) throw new UndefinedException();
					boolean unsigned = (_opc2&1)==0;
					int result;
					if(double_precision)
						result = doubleToInt(Math.scalb(getDouble(Rd), frac_bits), unsigned);
					else // NOT singleToInt here!
						result = doubleToInt(Math.scalb(getSingle(Rd), frac_bits), unsigned);
					if(sixteen_bit) {
						if(unsigned) {
							if(result > 65535 || result < 0) result = 65535;
							result = result & 65535;
						}
						else {
							if(result > 32767) result = 32767;
							else if(result < -32768) result = -32768;
						}
					}
					if(double_precision) {
						if(unsigned)
							putDouble(Rd, (long)(result)&0xFFFFFFFFL);
						else
							putDouble(Rd, result);
					}
					else
						putSingle(Rd, result);
					return;
				}
				}
			}
		}
		throw new UndefinedException();
	}

	@Override
	public void moveCoreRegistersToCoprocessorRegister(boolean unconditional,
			int coproc, int opc1, int CRm, int Rt, int Rt2)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		if(unconditional) throw new UndefinedException();
		if(!floatingPointIsEnabled()) throw new UndefinedException();
		/* 64-bit transfers between ARM core and extension registers (A7-279) */
		/* [20] = 0 */
		if((opc1&13) != 1) throw new UndefinedException();
		int m = CRm<<1;
		if((coproc&1)==0) {
			/* C = 0 */
			/* VMOV (A8-946) */
			if((opc1&2)!=0) m|=1;
		}
		else {
			/* C = 1 */
			/* VMOV (A8-948) */
			if((opc1&2)!=0) m|=32;
		}
		registerBits[m] = cpu.readRegister(Rt);
		registerBits[(m+1)%64] = cpu.readRegister(Rt2);
	}

	@Override
	public void moveCoprocessorRegisterToCoreRegisters(boolean unconditional,
			int coproc, int opc1, int CRm, int Rt, int Rt2)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		if(unconditional) throw new UndefinedException();
		if(!floatingPointIsEnabled()) throw new UndefinedException();
		/* 64-bit transfers between ARM core and extension registers (A7-279) */
		/* [20] = 1 */
		if((opc1&13) != 1) throw new UndefinedException();
		int m = CRm<<1;
		if((coproc&1)==0) {
			/* C = 0 */
			/* VMOV (A8-946) */
			if((opc1&2)!=0) m|=1;
		}
		else {
			/* C = 1 */
			/* VMOV (A8-948) */
			if((opc1&2)!=0) m|=32;
		}
		cpu.writeRegister(Rt, registerBits[m]);
		cpu.writeRegister(Rt2, registerBits[(m+1)%64]);
	}

	@Override
	public void reset() {
		for(int n = 0; n < registerBits.length; ++n) {
			registerBits[n] = 0;
		}
		FPEXC = 0;
		FPSCR = FPSCR_IMPLEMENTED_SUBSET;
	}

	public int readRegister(int register) {
		return registerBits[register];
	}
	
	public void writeRegister(int register, int value){
		registerBits[register] = value;
	}

	public int readFPSCR() {
		return FPSCR;
	}

	public void writeFPSCR(int FPSCR) {
		this.FPSCR = FPSCR;
	}
}
