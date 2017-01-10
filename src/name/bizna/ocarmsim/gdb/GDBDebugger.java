package name.bizna.ocarmsim.gdb;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import name.bizna.jarm.BusErrorException;
import name.bizna.jarm.EscapeRetryException;
import name.bizna.jarm.FPU;
import name.bizna.ocarmsim.BasicDebugger;
import name.bizna.ocarmsim.Breakpoint;
import name.bizna.ocarmsim.BreakpointException;
import name.bizna.ocarmsim.OCARM;

/**
 *
 * @author Jean-Rémy Buchs <jrb0001@692b8c32.de>
 */
public class GDBDebugger extends BasicDebugger {

	private final boolean verbose;
	private final ServerSocket serverSocket;
	private final AtomicBoolean running = new AtomicBoolean(true);
	private GDBSocket socket;

	public GDBDebugger(int port, boolean verbose) {
		this.verbose = verbose;

		try {
			serverSocket = new ServerSocket(port);
		} catch (IOException ex) {
			throw new RuntimeException("Failed to start gdbserver", ex);
		}
	}

	@Override
	public void run() {
		while (running.get()) {
			try {
				socket = new GDBSocket(serverSocket.accept(), verbose);

				while (!socket.isClosed()) {
					GDBCommandPacket packet = socket.read();
					switch (packet.getCommand()) {
						case 'q':
							answerQuery(packet);
							break;
						case '?':
							socket.write(new GDBPacket("S" + new String(toHex(getCurrentSignal(), 1))));
							break;
						case 'D':
							socket.close();
							break;
						case 'g':
							readRegisters(packet);
							break;
						case 'G':
							writeRegisters(packet);
							break;
						case 'p':
							readRegister(packet);
							break;
						case 'P':
							writeRegister(packet);
							break;
						case 'm':
							readMemory(packet);
							break;
						case 'M':
							writeMemory(packet);
							break;
						case 'H':
							// No threads.
							socket.write(new GDBPacket("E00"));
							break;
						case 'v':
							doV(packet);
							break;
						case 'S':
							step(packet);
							break;
						case 's':
							step(new GDBCommandPacket('S', "00;" + packet.getData()));
							break;
						case 'C':
							cont(packet);
							break;
						case 'c':
							cont(new GDBCommandPacket('C', "00;" + packet.getData()));
							break;
						case 'z':
							removePoint(packet);
							break;
						case 'Z':
							addPoint(packet);
							break;
						case '!':
							socket.write(new GDBPacket("OK"));
							break;
						default:
							throw new RuntimeException("gdbserver: unknown command: " + packet.getCommand());
					}
				}
			} catch (Exception ex) {
				try {
					socket.close();
				} catch (IOException ignored) {
				}
				OCARM.logger.error("Error in gdbserver: %s", ex.toString());
				ex.printStackTrace();
			}
		}
	}

	private char[] toHex(int number, int length) throws IOException {
		char[] buffer = new char[length * 2];
		String text = Integer.toHexString(number);

		if (text.length() > length * 2) {
			text = text.substring(text.length() - length * 2);
		}

		int i;
		for (i = 0; i < length * 2 - text.length(); i++) {
			buffer[i] = '0';
		}
		for (char c : text.toCharArray()) {
			buffer[i++] = c;
		}

		return buffer;
	}

	private char[] toHex(byte[] bytes) throws IOException {
		char[] buffer = new char[bytes.length * 2];

		for (int i = 0; i < bytes.length; i++) {
			char[] buf = toHex(bytes[i], 1);
			buffer[i * 2] = buf[0];
			buffer[i * 2 + 1] = buf[1];
		}

		return buffer;
	}

	private int swapEndian(int i) {
//		return (i & 0xff) << 24 | (i & 0xff00) << 8 | (i & 0xff0000) >> 8 | (i >> 24) & 0xff;
		return i;
	}

	private void answerQuery(GDBCommandPacket packet) throws IOException {
		String command = packet.getData().contains(":") ? packet.getData().substring(0, packet.getData().indexOf(':')) : packet.getData();
		// even more thans, Java 1.6
		if(command.equals("Supported"))
			socket.write(new GDBPacket("multiprocess-;swbreak-;hwbreak+"));
		else if(command.equals("Attached"))
			socket.write(new GDBPacket("1"));
		else if(command.equals("Symbol"))
			// TODO: Symbols?
			socket.write(new GDBPacket("OK"));
		else if(command.equals("Offsets"))
			// TODO: Relocate?
			socket.write(new GDBPacket(""));
		else if(command.equals("TStatus"))
			// TODO: Tracing not supported.
			socket.write(new GDBPacket(""));
		else if(command.equals("fThreadInfo"))
			// No threads.
			socket.write(new GDBPacket("l"));
		else if(command.equals("C"))
			// No threads.
			socket.write(new GDBPacket("E00"));
		else
			throw new RuntimeException("Unsupported query command: " + command);
	}

	private char[] readRegister(int register) throws IOException {
		switch (register) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
				return toHex(swapEndian(cpu.readRegister(register)), 4);
			case 15:
				return toHex(swapEndian(cpu.readCurrentPC()), 4);
			case 16:
			case 17:
			case 18:
			case 19:
			case 20:
			case 21:
			case 22:
			case 23:
				// TODO: check!
				return new StringBuilder()
						.append(toHex(swapEndian(((FPU) cpu.getCoprocessor(10)).readRegister((register - 16) * 3 + 0)), 4))
						.append(toHex(swapEndian(((FPU) cpu.getCoprocessor(10)).readRegister((register - 16) * 3 + 1)), 4))
						.append(toHex(swapEndian(((FPU) cpu.getCoprocessor(10)).readRegister((register - 16) * 3 + 2)), 4))
						.toString().toCharArray();
			case 24:
				return toHex(swapEndian(((FPU) cpu.getCoprocessor(10)).readFPSCR()), 4);
			case 25:
				return toHex(swapEndian(cpu.readCPSR()), 4);
			default:
				throw new UnsupportedOperationException("Register " + register + " is not yet supported");
		}
	}

	private int writeRegister(int register, String value, int offset) throws IOException {
		switch (register) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				cpu.writeRegister(register, swapEndian(Integer.parseUnsignedInt(value.substring(offset, 4 * 2), 16)));
				return 4;
			case 16:
			case 17:
			case 18:
			case 19:
			case 20:
			case 21:
			case 22:
			case 23:
				// TODO: check!
				((FPU) cpu.getCoprocessor(10)).writeRegister((register - 16) * 3 + 0, swapEndian(Integer.parseUnsignedInt(value.substring(offset + 0 * 4 * 2, offset + 1 * 4 * 2), 16)));
				((FPU) cpu.getCoprocessor(10)).writeRegister((register - 16) * 3 + 1, swapEndian(Integer.parseUnsignedInt(value.substring(offset + 1 * 4 * 2, offset + 2 * 4 * 2), 16)));
				((FPU) cpu.getCoprocessor(10)).writeRegister((register - 16) * 3 + 2, swapEndian(Integer.parseUnsignedInt(value.substring(offset + 2 * 4 * 2, offset + 3 * 4 * 2), 16)));
				return 12;
			case 24:
				((FPU) cpu.getCoprocessor(10)).writeFPSCR(swapEndian(Integer.parseUnsignedInt(value.substring(offset, 4 * 2), 16)));
				return 4;
			case 25:
				cpu.writeCPSR(swapEndian(Integer.parseUnsignedInt(value.substring(offset, 4 * 2), 16)));
				return 4;
			default:
				throw new UnsupportedOperationException("Register " + register + " is not yet supported");
		}
	}

	private void readRegisters(GDBCommandPacket packet) throws IOException {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < 16 + 8 + 2; i++) {
			builder.append(readRegister(i));
		}

		socket.write(new GDBPacket(builder.toString()));
	}

	private void writeRegisters(GDBCommandPacket packet) throws IOException {
		int offset = 0;
		for (int i = 0; i < 16 + 8 + 2; i++) {
			offset += writeRegister(i, packet.getData(), offset);
		}

		socket.write(new GDBPacket("OK"));
	}

	private void readRegister(GDBCommandPacket packet) throws IOException {
		int reg = Integer.parseUnsignedInt(packet.getData(), 16);

		socket.write(new GDBPacket(new String(readRegister(reg))));
	}

	private void writeRegister(GDBCommandPacket packet) throws IOException {
		int reg = Integer.parseUnsignedInt(packet.getData().split("=")[0], 16);

		writeRegister(reg, packet.getData().split("=")[1], 0);

		socket.write(new GDBPacket("OK"));
	}

	private void readMemory(GDBCommandPacket packet) throws IOException {
		int addr = Integer.parseUnsignedInt(packet.getData().split(",")[0], 16);
		int length = Integer.parseUnsignedInt(packet.getData().split(",")[1], 16);
		byte[] buffer = new byte[length];

		try {
			for (int i = 0; i < length; i++) {
				try {
					buffer[i] = cpu.getVirtualMemorySpace().readByte(addr + i);
				} catch (BreakpointException ignored) {
					buffer[i] = 0;
				}
			}

			socket.write(new GDBPacket(new String(toHex(buffer))));
		} catch (BusErrorException ex) {
			socket.write(new GDBPacket(GDBError.MEMORY_ACCESS.message()));
		} catch (EscapeRetryException ex) {
			socket.write(new GDBPacket(GDBError.RETRY.message()));
		}
	}

	private void writeMemory(GDBCommandPacket packet) throws IOException {
		int addr = Integer.parseUnsignedInt(packet.getData().split(",")[0], 16);
		int length = Integer.parseUnsignedInt(packet.getData().split(",")[1].split(":")[0], 16);

		try {
			char[] data = packet.getData().split(":")[1].toCharArray();
			for (int i = 0; i < length; i++) {
				byte value = Byte.parseByte(new String(new char[]{data[i * 2], data[i * 2 + 1]}), 16);
				try {
					cpu.getVirtualMemorySpace().writeByte(addr, value);
				} catch (BreakpointException ignored) {
				}
			}

			socket.write(new GDBPacket("OK"));
		} catch (BusErrorException ex) {
			socket.write(new GDBPacket(GDBError.MEMORY_ACCESS.message()));
		} catch (EscapeRetryException ex) {
			socket.write(new GDBPacket(GDBError.RETRY.message()));
		}
	}

	private int getCurrentSignal() {
		switch (getState()) {
			case CRASHED:
			case FAILED:
				return 9;
			case SLEEPING:
				return 1;
			case PAUSED:
			case RUNNING:
				return 5;
			default:
				throw new IllegalStateException("Unknown state: " + getState());
		}
	}

	private void doV(GDBCommandPacket packet) throws IOException {
		String command = packet.getData().contains(";") ? packet.getData().substring(0, packet.getData().indexOf(';')) : packet.getData();
		if(command.equals("Cont?"))
			// vCont not supported.
			socket.write(new GDBPacket(""));
		else if(command.equals("Kill")) {
			reset();
			socket.write(new GDBPacket("OK"));
		}
		else
			throw new RuntimeException("Unsupported v command: " + command);
	}

	private void step(GDBCommandPacket packet) throws IOException {
		// int signal = Integer.parseUnsignedInt(packet.getData().split(";")[0], 16);
		int addr = packet.getData().split(";").length > 1 && !packet.getData().split(";")[1].isEmpty() ? Integer.parseUnsignedInt(packet.getData().split(";")[1], 16) : cpu.readCurrentPC();

		cpu.writePC(addr);
		do {
			step();
		} while (getState() == State.RUNNING || getState() == State.SLEEPING);

		socket.write(new GDBPacket("S" + new String(toHex(getCurrentSignal(), 1))));
	}

	private void cont(GDBCommandPacket packet) throws IOException {
		// int signal = Integer.parseUnsignedInt(packet.getData().split(";")[0], 16);
		int addr = packet.getData().split(";").length > 1 && !packet.getData().split(";")[1].isEmpty() ? Integer.parseUnsignedInt(packet.getData().split(";")[1], 16) : cpu.readCurrentPC();

		cpu.writePC(addr);
		do {
			go();
		} while (getState() == State.RUNNING || getState() == State.SLEEPING);

		socket.write(new GDBPacket("S" + new String(toHex(getCurrentSignal(), 1))));
	}

	private void removePoint(GDBCommandPacket packet) throws IOException {
		int type = Integer.parseUnsignedInt(packet.getData().split(",")[0], 16);
		int addr = Integer.parseUnsignedInt(packet.getData().split(",")[1], 16);
		int size = Integer.parseUnsignedInt(packet.getData().split(",")[2], 16);

		switch (type) {
			case 0:
			case 1:
				removeBreakpoint(new Breakpoint(addr, size));
				break;
			case 2:
				removeWriteWatchpoint(new Breakpoint(addr, size));
				break;
			case 3:
				removeReadWatchpoint(new Breakpoint(addr, size));
				break;
			default:
				throw new UnsupportedOperationException("Unsupported breakpoint type: " + type);
		}

		socket.write(new GDBPacket("OK"));
	}

	private void addPoint(GDBCommandPacket packet) throws IOException {
		int type = Integer.parseUnsignedInt(packet.getData().split(",")[0], 16);
		int addr = Integer.parseUnsignedInt(packet.getData().split(",")[1], 16);
		int size = Integer.parseUnsignedInt(packet.getData().split(",")[2], 16);

		switch (type) {
			case 0:
			case 1:
				addBreakpoint(new Breakpoint(addr, size));
				break;
			case 2:
				addWriteWatchpoint(new Breakpoint(addr, size));
				break;
			case 3:
				addReadWatchpoint(new Breakpoint(addr, size));
				break;
			default:
				throw new UnsupportedOperationException("Unsupported breakpoint type: " + type);
		}

		socket.write(new GDBPacket("OK"));
	}

	@Override
	public JComponent getGUIComponent() {
		return new DebugPanel();
	}

	private class DebugPanel extends JPanel {
		public static final long serialVersionUID = 1;

		public DebugPanel() {
			JButton resetButton = new JButton(new AbstractAction("Reset") {
				public static final long serialVersionUID = 1;
				@Override
				public void actionPerformed(ActionEvent e) {
					reset();
				}
			});
			add(resetButton);
			JButton pauseButton = new JButton(new AbstractAction("Pause") {
				public static final long serialVersionUID = 1;
				@Override
				public void actionPerformed(ActionEvent e) {
					pause();
				}
			});
			add(pauseButton);
		}

	}
}
