package name.bizna.jarm;

public final class BusErrorException extends Exception {
	static final long serialVersionUID = 1;

	public BusErrorException(String message) {
		super(message);
	}
}
