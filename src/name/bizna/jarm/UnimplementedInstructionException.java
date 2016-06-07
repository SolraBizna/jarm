package name.bizna.jarm;

public class UnimplementedInstructionException extends RuntimeException {
	static final long serialVersionUID = 1;
	private String insn;
	private int iword;
	private short iword1, iword2;
	public static enum Type {
		ARM, THUMB16, THUMB32, NONE
	}
	private Type type;
	public UnimplementedInstructionException(String insn) {
		this.insn = insn;
		this.type = Type.NONE;
	}
	public UnimplementedInstructionException(int iword, String insn) {
		this.insn = insn;
		this.iword = iword;
		this.type = Type.ARM;
	}
	public UnimplementedInstructionException(short iword, String insn) {
		this.insn = insn;
		this.iword1 = iword;
		this.type = Type.THUMB16;
	}
	public UnimplementedInstructionException(short iword1, short iword2, String insn) {
		this.insn = insn;
		this.iword1 = iword1;
		this.iword2 = iword2;
		this.type = Type.THUMB32;
	}
	@Override
	public String toString() {
		switch(type) {
		default: return "Unimplemented instruction";
		case NONE: return String.format("Unimplemented instruction: %s", insn);
		case ARM: return String.format("Unimplemented ARM instruction: %s (%08X)", insn, iword);
		case THUMB16: return String.format("Unimplemented Thumb16 instruction: %s (%04X)", insn, iword1);
		case THUMB32: return String.format("Unimplemented Thumb32 instruction: %s (%04X %04X)", insn, iword1, iword2);
		}
	}
	public String getInstruction() { return insn; }
	public Type getType() { return type; }
	public int getARMWord() { if(type != Type.ARM) throw new NullPointerException(); return iword; }
	public short getThumbWord() { if(type != Type.THUMB16) throw new NullPointerException(); return iword1; }
	public short getThumbWord1() { if(type != Type.THUMB32) throw new NullPointerException(); return iword1; }
	public short getThumbWord2() { if(type != Type.THUMB32) throw new NullPointerException(); return iword2; }
}
