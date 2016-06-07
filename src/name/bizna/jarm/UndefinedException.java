package name.bizna.jarm;

public class UndefinedException extends Exception {
	static final long serialVersionUID = 1;
	public UndefinedException() { super(); }
	public UndefinedException(Throwable e) { super(e); }
}
