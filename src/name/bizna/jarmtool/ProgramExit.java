package name.bizna.jarmtool;

public class ProgramExit extends RuntimeException {
	public static final long serialVersionUID = 1;
	private int exitStatus;
	public ProgramExit(int exitStatus) {
		this.exitStatus = exitStatus;
	}
	public int getExitStatus() {
		return exitStatus;
	}
}
