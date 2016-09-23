package name.bizna.ocarmsim.gdb;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import name.bizna.jarm.BusErrorException;
import name.bizna.jarm.CPU;
import name.bizna.jarm.EscapeRetryException;
import name.bizna.jarm.FPU;
import name.bizna.ocarmsim.OCARM;
import name.bizna.ocarmsim.SimThread;

/**
 *
 * @author Jean-Rémy Buchs <jrb0001@692b8c32.de>
 */
public class GDBServer implements Runnable, AutoCloseable {

	private final boolean verbose;
	private final ServerSocket serverSocket;
	private final SimThread thread;
	private final CPU cpu;
	private final AtomicBoolean running = new AtomicBoolean(true);
	private GDBSocket socket;

	public GDBServer(int port, boolean verbose, SimThread thread, CPU cpu) {
		this.verbose = verbose;

		this.thread = thread;
		this.cpu = cpu;

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

	@Override
	public void close() throws Exception {
		running.set(false);
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
		return (i & 0xff) << 24 | (i & 0xff00) << 8 | (i & 0xff0000) >> 8 | (i >> 24) & 0xff;
	}

	private void answerQuery(GDBCommandPacket packet) throws IOException {
		String command = packet.getData().contains(":") ? packet.getData().substring(0, packet.getData().indexOf(':')) : packet.getData();
		switch (command) {
			case "Supported":
				socket.write(new GDBPacket("multiprocess-"));
				break;
			case "Attached":
				socket.write(new GDBPacket("1"));
				break;
			case "Symbol":
				// TODO: Symbols?
				socket.write(new GDBPacket("OK"));
				break;
			case "Offsets":
				// TODO: Relocate?
				socket.write(new GDBPacket(""));
				break;
			case "TStatus":
				// TODO: Tracing not supported.
				socket.write(new GDBPacket(""));
				break;
			case "fThreadInfo":
				// No threads.
				socket.write(new GDBPacket("l"));
				break;
			case "C":
				// No threads.
				socket.write(new GDBPacket("E00"));
				break;
			default:
				throw new RuntimeException("Unsupported query command: " + command);
		}
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
			case 15:
				return toHex(swapEndian(cpu.readRegister(register)), 4);
			case 16:
			case 17:
			case 18:
			case 19:
			case 20:
			case 21:
			case 22:
			case 23:
				// TODO: checkx!
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
		synchronized (thread) {
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < 16 + 8 + 2; i++) {
				builder.append(readRegister(i));
			}

			socket.write(new GDBPacket(builder.toString()));
		}
	}

	private void writeRegisters(GDBCommandPacket packet) throws IOException {
		synchronized (thread) {
			int offset = 0;
			for (int i = 0; i < 16 + 8 + 2; i++) {
				offset += writeRegister(i, packet.getData(), offset);
			}

			socket.write(new GDBPacket("OK"));
		}
	}

	private void readRegister(GDBCommandPacket packet) throws IOException {
		int reg = Integer.parseUnsignedInt(packet.getData(), 16);

		synchronized (thread) {
			socket.write(new GDBPacket(new String(readRegister(reg))));
		}
	}

	private void writeRegister(GDBCommandPacket packet) throws IOException {
		int reg = Integer.parseUnsignedInt(packet.getData().split("=")[0], 16);

		synchronized (thread) {
			writeRegister(reg, packet.getData().split("=")[1], 0);

			socket.write(new GDBPacket("OK"));
		}
	}

	private void readMemory(GDBCommandPacket packet) throws IOException {
		int addr = Integer.parseUnsignedInt(packet.getData().split(",")[0], 16);
		int length = Integer.parseUnsignedInt(packet.getData().split(",")[1], 16);
		byte[] buffer = new byte[length];

		synchronized (thread) {
			try {
				for (int i = 0; i < length; i++) {
					buffer[i] = cpu.getVirtualMemorySpace().readByte(addr + i);
				}

				socket.write(new GDBPacket(new String(toHex(buffer))));
			} catch (BusErrorException ex) {
				socket.write(new GDBPacket(GDBError.MEMORY_ACCESS.message()));
			} catch (EscapeRetryException ex) {
				socket.write(new GDBPacket(GDBError.RETRY.message()));
			}
		}
	}

	private void writeMemory(GDBCommandPacket packet) throws IOException {
		int addr = Integer.parseUnsignedInt(packet.getData().split(",")[0], 16);
		int length = Integer.parseUnsignedInt(packet.getData().split(",")[1].split(":")[0], 16);

		synchronized (thread) {
			try {
				char[] data = packet.getData().split(":")[1].toCharArray();
				for (int i = 0; i < length; i++) {
					byte value = Byte.parseByte(new String(new char[]{data[i * 2], data[i * 2 + 1]}), 16);
					cpu.getVirtualMemorySpace().writeByte(addr, value);
				}

				socket.write(new GDBPacket("OK"));
			} catch (BusErrorException ex) {
				socket.write(new GDBPacket(GDBError.MEMORY_ACCESS.message()));
			} catch (EscapeRetryException ex) {
				socket.write(new GDBPacket(GDBError.RETRY.message()));
			}
		}
	}

	private int getCurrentSignal() {
		switch (thread.getMode()) {
			case CRASHED:
			case FAILED:
			case RESETTING:
				return 9;
			case PAUSED:
			case RUNNING:
			case SLEEPING:
			case STEPPING:
				return 0;
			default:
				throw new IllegalStateException("Unknown mode: " + thread.getMode());
		}
	}

	private void doV(GDBCommandPacket packet) throws IOException {
		String command = packet.getData().contains(":") ? packet.getData().substring(0, packet.getData().indexOf(':')) : packet.getData();
		switch (command) {
			case "Cont?":
				// vCont not supported.
				socket.write(new GDBPacket(""));
				break;
			default:
				throw new RuntimeException("Unsupported v command: " + command);
		}
	}

	private void step(GDBCommandPacket packet) throws IOException {
		int signal = Integer.parseUnsignedInt(packet.getData().split(";")[0], 16);
		int addr = packet.getData().split(";").length > 1 && !packet.getData().split(";")[1].isEmpty() ? Integer.parseUnsignedInt(packet.getData().split(";")[1], 16) : cpu.readPC();

		synchronized (thread) {
			cpu.writePC(addr);
			thread.step();
		}

		socket.write(new GDBPacket("S" + new String(toHex(getCurrentSignal(), 1))));
	}

	private void cont(GDBCommandPacket packet) throws IOException {
		int signal = Integer.parseUnsignedInt(packet.getData().split(";")[0], 16);
		int addr = packet.getData().split(";").length > 1 && !packet.getData().split(";")[1].isEmpty() ? Integer.parseUnsignedInt(packet.getData().split(";")[1], 16) : cpu.readPC();

		synchronized (thread) {
			cpu.writePC(addr);
			thread.go();
		}

		try {
			while (thread.getMode() == SimThread.ExecutionMode.PAUSED) {
				Thread.sleep(1);
			}

			while (thread.getMode() == SimThread.ExecutionMode.RUNNING) {
				Thread.sleep(1);
			}

			socket.write(new GDBPacket("S" + new String(toHex(getCurrentSignal(), 1))));
		} catch (InterruptedException ex) {
			Logger.getLogger(GDBServer.class.getName()).log(Level.SEVERE, null, ex);
		}
	}
}
