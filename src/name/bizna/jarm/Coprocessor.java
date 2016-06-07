package name.bizna.jarm;

/**
 * ARM allows coprocessors to implement almost any instruction. You want to subclass {@link SaneCoprocessor} instead, probably.
 */
public abstract class Coprocessor {
	/**
	 * Execute an instruction. The instruction SHOULD be interpreted as one of:
	 * <ul>
	 * <li>CDP (A8-358)</li>
	 * <li>LDC (A8-392)</li>
	 * <li>MCR (A8-476)</li>
	 * <li>MCRR (A8-478)</li>
	 * <li>MRC (A8-476)</li>
	 * <li>MRRC (A8-494)</li>
	 * <li>STC (A8-662)</li>
	 * </ul>
	 * {@link UndefinedException} should be thrown on any non-handled instruction. Any
	 * memory accesses, state changes, etc. should go through the {@link CPU}'s methods.
	 * (Keep a reference to the CPU handy from construction time.)
	 * @param unconditional true for T2/A2 encoding, false for T1/A1 encoding. The
	 * "conditionality" of a coprocessor instruction is normally ignored, but if you
	 * depend on it please check this rather than the relevant bits of iword, as Thumb
	 * and ARM encodings for coprocessor instructions differ here.
	 * @param iword The instruction to decode.
	 */
	public abstract void executeInstruction(boolean unconditional, int iword) throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException;
	/**
	 * Called by CPU when a reset occurs.
	 */
	public abstract void reset();
}
