package name.bizna.ocarmsim.gdb;

/**
 *
 * @author Jean-Rémy Buchs <jrb0001@692b8c32.de>
 */
public enum GDBError {
	UNKNOWN, MEMORY_ACCESS, RETRY;

	public String message() {
		if (ordinal() < 16) {
			return "E0" + Integer.toHexString(ordinal());
		} else {
			return "E" + Integer.toHexString(ordinal());
		}
	}
}
