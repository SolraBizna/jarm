package name.bizna.ocarmsim.gdb;

/**
 *
 * @author Jean-Rémy Buchs <jrb0001@692b8c32.de>
 */
public class GDBCommandPacket extends GDBPacket {

	private final char command;

	public GDBCommandPacket(char command, String data) {
		super(data);
		this.command = command;
	}

	public char getCommand() {
		return command;
	}

	@Override
	public int calculateChecksum() {
		int sum = command;
		for (byte b : getData().getBytes()) {
			sum += b;
		}
		return sum % 256;
	}

	@Override
	public String getCompleteContent() {
		return command + super.getCompleteContent();
	}

	@Override
	public String toString() {
		return "GDBCommandPacket{" + "command=" + command + ", data=" + getData() + '}';
	}
}
