package name.bizna.jarm;


public abstract class ByteBackedRegion extends MemoryRegion {
	public abstract byte backingReadByte(int address) throws BusErrorException, EscapeRetryException;
	public abstract void backingWriteByte(int address, byte v) throws BusErrorException, EscapeRetryException;
	protected int accessLatencyHalf, accessLatencyWord;
	public ByteBackedRegion() {
		this(1, true);
	}
	public ByteBackedRegion(int accessLatency) {
		this(accessLatency, true);
	}
	public ByteBackedRegion(int accessLatency, boolean wide) {
		assert(accessLatency >= 0);
		this.accessLatencyHalf = accessLatency;
		this.accessLatencyWord = wide ? accessLatency : accessLatency * 2;
	}
	@Override
	public final byte readByte(PhysicalMemorySpace mem, long address) throws BusErrorException, EscapeRetryException {
		mem.addToBill(accessLatencyHalf);
		return backingReadByte((int)address);
	}
	@Override
	public final void writeByte(PhysicalMemorySpace mem, long address, byte v) throws BusErrorException, EscapeRetryException {
		mem.addToBill(accessLatencyHalf);
		backingWriteByte((int)address, v);
	}
	@Override
	public final short readShortLE(PhysicalMemorySpace mem, long address) throws BusErrorException, EscapeRetryException {
		mem.addToBill(accessLatencyHalf);
		byte first = backingReadByte((int)address);
		byte second = backingReadByte((int)address+1);
		return (short)((second << 8) | (first & 0xFF));
	}
	@Override
	public final short readShortBE(PhysicalMemorySpace mem, long address) throws BusErrorException, EscapeRetryException {
		mem.addToBill(accessLatencyHalf);
		byte first = backingReadByte((int)address);
		byte second = backingReadByte((int)address+1);
		return (short)((first << 8) | (second & 0xFF));
	}
	@Override
	public final void writeShortLE(PhysicalMemorySpace mem, long address, short v) throws BusErrorException, EscapeRetryException {
		mem.addToBill(accessLatencyHalf);
		backingWriteByte((int)address, (byte)(v&255));
		backingWriteByte((int)address+1, (byte)(v>>8));
	}
	@Override
	public final void writeShortBE(PhysicalMemorySpace mem, long address, short v) throws BusErrorException, EscapeRetryException {
		mem.addToBill(accessLatencyHalf);
		backingWriteByte((int)address, (byte)(v>>8));
		backingWriteByte((int)address+1, (byte)(v&255));
	}
	@Override
	public final int readIntLE(PhysicalMemorySpace mem, long address) throws BusErrorException, EscapeRetryException {
		mem.addToBill(accessLatencyWord);
		byte first = backingReadByte((int)address);
		byte second = backingReadByte((int)address+1);
		byte third = backingReadByte((int)address+2);
		byte fourth = backingReadByte((int)address+3);
		return (fourth << 24)
				| ((third & 0xFF) << 16)
				| ((second & 0xFF) << 8)
				| (first & 0xFF);
	}
	@Override
	public final int readIntBE(PhysicalMemorySpace mem, long address) throws BusErrorException, EscapeRetryException {
		mem.addToBill(accessLatencyWord);
		byte first = backingReadByte((int)address);
		byte second = backingReadByte((int)address+1);
		byte third = backingReadByte((int)address+2);
		byte fourth = backingReadByte((int)address+3);
		return (first << 24)
				| ((second & 0xFF) << 16)
				| ((third & 0xFF) << 8)
				| (fourth & 0xFF);
	}
	@Override
	public final void writeIntLE(PhysicalMemorySpace mem, long address, int v) throws BusErrorException, EscapeRetryException {
		mem.addToBill(accessLatencyWord);
		backingWriteByte((int)address, (byte)v);
		backingWriteByte((int)address+1, (byte)(v>>8));
		backingWriteByte((int)address+2, (byte)(v>>16));
		backingWriteByte((int)address+3, (byte)(v>>24));
	}
	@Override
	public final void writeIntBE(PhysicalMemorySpace mem, long address, int v) throws BusErrorException, EscapeRetryException {
		mem.addToBill(accessLatencyWord);
		backingWriteByte((int)address, (byte)(v>>24));
		backingWriteByte((int)address+1, (byte)(v>>16));
		backingWriteByte((int)address+2, (byte)(v>>8));
		backingWriteByte((int)address+3, (byte)v);
	}
}
