package name.bizna.ocarmsim.gdb;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.Socket;
import name.bizna.ocarmsim.OCARM;

/**
 *
 * @author Jean-Rémy Buchs <jrb0001@692b8c32.de>
 */
public class GDBSocket implements Closeable {

	private final boolean verbose;
	private final Socket socket;
	private final Reader reader;
	private final Writer writer;

	public GDBSocket(Socket socket, boolean verbose) throws IOException {
		this.verbose = verbose;
		this.socket = socket;
		this.reader = new InputStreamReader(socket.getInputStream());
		this.writer = new OutputStreamWriter(socket.getOutputStream());

		require('+');
	}

	public void write(GDBPacket packet) throws IOException {
		if (verbose) {
			OCARM.logger.info("gdbsocket: write: %s", packet.getCompleteContent());
		}

		while (true) {
			writer.append('$');
			writer.append(packet.getCompleteContent());
			writer.append('#');
			int sum = packet.calculateChecksum();
			if (sum < 16) {
				writer.append('0');
			}
			writer.append(Integer.toHexString(sum));
			writer.flush();

			char c = (char) reader.read();
			switch (c) {
				case '+':
					return;
				case '-':
					OCARM.logger.error("gdbsocket: sent invalid checksum");
					break;
				default:
					throw new RuntimeException("gdbserver: protocol error: expected '+' or '-', got " + c + " (" + (int) c + ")");
			}
		}
	}

	public GDBCommandPacket read() throws IOException {
		while (true) {
			require('$');
			char command = (char) reader.read();
			StringBuilder builder = new StringBuilder();
			while (true) {
				char c = (char) reader.read();
				if (c == '#') {
					break;
				}
				builder.append(c);
			}

			GDBCommandPacket packet = new GDBCommandPacket(command, builder.toString());

			int checksum = Integer.parseInt(new String(new char[]{(char) reader.read(), (char) reader.read()}), 16);

			if (checksum == packet.calculateChecksum()) {
				writer.append('+');
				writer.flush();
				if (verbose) {
					OCARM.logger.info("gdbsocket: read: %s", packet.getCompleteContent());
				}
				return packet;
			} else {
				OCARM.logger.error("gdbsocket: received invalid checksum: expected %i got %i", packet.calculateChecksum(), checksum);
				writer.append('-');
				writer.flush();
			}
		}
	}

	@Override
	public void close() throws IOException {
		if (!isClosed()) {
			socket.close();
		}
	}

	public boolean isClosed() {
		return socket.isClosed();
	}

	private void require(char expected) throws IOException {
		int raw = reader.read();
		if (raw == -1) {
			throw new IOException("Stream closed?");
		}
		char got = (char) raw;

		if (expected != got) {
			throw new RuntimeException("gdbserver: protocol error expected " + expected + " (" + (int) expected + "), got " + got + " (" + (int) got + ")");
		}
	}
}
