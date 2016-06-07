package name.bizna.jarm;

/**
 * Thrown during memory access or instruction execution to indicate that the current CPU.execute(int) should be aborted and the instruction restarted on next call.
 * @author sbizna
 */
public class EscapeRetryException extends Exception { public static final long serialVersionUID = 1; }
