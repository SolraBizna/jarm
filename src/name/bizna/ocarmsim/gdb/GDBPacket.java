package name.bizna.ocarmsim.gdb;

/**
 *
 * @author Jean-Rémy Buchs <jrb0001@692b8c32.de>
 */
public class GDBPacket {

	private final String data;

	public GDBPacket(String data) {
		this.data = data;
	}

	public String getData() {
		return data;
	}

	public int calculateChecksum() {
		int sum = 0;
		for (byte b : data.getBytes()) {
			sum += b;
		}
		return sum % 256;
	}

	public String getCompleteContent() {
		return data;
	}

	@Override
	public String toString() {
		return "GDBPacket{" + "data=" + data + '}';
	}
}
