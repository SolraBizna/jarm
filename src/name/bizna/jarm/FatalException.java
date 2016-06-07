package name.bizna.jarm;

public class FatalException extends RuntimeException {
	static final long serialVersionUID = 1;
	public FatalException(String why) { super(why); }
}
