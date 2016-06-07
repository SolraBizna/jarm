package name.bizna.jarm;


/*
 * A contiguous region of PHYSICAL memory space.
 * All addresses begin at zero and are guaranteed to be less than GetRegionSize.
 * Reads of shorts, ints, and longs are guaranteed to be aligned.
 */
public abstract class MemoryRegion {
	/* This function is only used once and its return value is stored elsewhere.
	 * Therefore, it should not change post-construction.
	 * It should also never be greater than 2^32. */
	public abstract long getRegionSize();
	public abstract byte readByte(PhysicalMemorySpace mem, long address) throws BusErrorException, EscapeRetryException;
	public abstract void writeByte(PhysicalMemorySpace mem, long address, byte b) throws BusErrorException, EscapeRetryException;
	public abstract short readShortLE(PhysicalMemorySpace mem, long address) throws BusErrorException, EscapeRetryException;
	public abstract short readShortBE(PhysicalMemorySpace mem, long address) throws BusErrorException, EscapeRetryException;
	public abstract void writeShortLE(PhysicalMemorySpace mem, long address, short value) throws BusErrorException, EscapeRetryException;
	public abstract void writeShortBE(PhysicalMemorySpace mem, long address, short value) throws BusErrorException, EscapeRetryException;
	public abstract int readIntLE(PhysicalMemorySpace mem, long address) throws BusErrorException, EscapeRetryException;
	public abstract int readIntBE(PhysicalMemorySpace mem, long address) throws BusErrorException, EscapeRetryException;
	public abstract void writeIntLE(PhysicalMemorySpace mem, long address, int value) throws BusErrorException, EscapeRetryException;
	public abstract void writeIntBE(PhysicalMemorySpace mem, long address, int value) throws BusErrorException, EscapeRetryException;
}
