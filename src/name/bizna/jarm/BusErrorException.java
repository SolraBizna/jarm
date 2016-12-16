package name.bizna.jarm;

public final class BusErrorException extends Exception {

	private static final long serialVersionUID = 1;

	private final String reason;
	private final long address;
	private final AccessType accessType;

	public BusErrorException(String reason, long address, AccessType accessType) {
		super(String.format("Failed to %s 0x%8x (%s)", accessType.name(), address, reason));

		this.reason = reason;
		this.address = address;
		this.accessType = accessType;
	}

	public String getReason() {
		return reason;
	}

	public long getAddress() {
		return address;
	}

	public AccessType getAccessType() {
		return accessType;
	}

	public static enum AccessType {
		READ, WRITE, UNKNOWN;
	}
}
