package name.bizna.ocarmsim;

/* Remnants of arch class from OCARM, stripped down for the simulator */

public class OCARM {
    /* TODO: move to CP3? */
    /* Type tags for interchange */
    public static final short ICTAG_STRING = (short)0;
    public static final short ICTAG_BYTE_ARRAY = (short)0x4000;
    public static final short ICTAG_VALUE = (short)-9;
    public static final short ICTAG_UUID = (short)-8;
    public static final short ICTAG_COMPOUND = (short)-7;
    public static final short ICTAG_ARRAY = (short)-6;
    public static final short ICTAG_INT = (short)-5;
    public static final short ICTAG_DOUBLE = (short)-4;
    public static final short ICTAG_BOOLEAN = (short)-3;
    public static final short ICTAG_NULL = (short)-2;
    public static final short ICTAG_END = (short)-1;
    /* Maximum interchange string length, in bytes */
    public static final short MAX_STRING_LENGTH = 16383;
    public static final short MAX_BYTE_ARRAY_LENGTH = 16383;
    /* Errors for interchange */
    public static final short INVOKE_SUCCESS = 0;
    public static final short INVOKE_UNKNOWN_ERROR = 1;
    public static final short INVOKE_LIMIT_REACHED = 2;
    public static final short INVOKE_UNKNOWN_RECEIVER = 3;
    public static final short INVOKE_INDIRECT_REQUIRED = 4;
    public static final short INVOKE_UNKNOWN_METHOD = 5;
    
    public static class FakeLogger { 
    	public void error(String format, Object...args) {
    		System.err.println("ERROR: "+String.format(format, args));
    	}
    	public void info(String format, Object...args) {
    		System.err.println("INFO: "+String.format(format, args));
    	}
    };
    public static FakeLogger logger = new FakeLogger();

	private boolean traceInvocations = false;

    public static final OCARM instance = new OCARM();

    public boolean shouldTraceInvocations() { return traceInvocations; }

	public void setTraceInvocations(boolean traceInvocations) {
		this.traceInvocations = traceInvocations;
	}

    public static int padToWordLength(int i) {
    	return (i&3)!=0?(i&~3)+4:i;
    }
}
