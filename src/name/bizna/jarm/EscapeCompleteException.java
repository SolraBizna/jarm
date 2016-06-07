package name.bizna.jarm;

/**
 * Thrown during execution of an instruction to indicate that the current CPU.execute(int) should be aborted, but the current instruction <i>did</i> finish executing.
 * @author sbizna
 */
public class EscapeCompleteException extends Exception { public static final long serialVersionUID = 1; }
