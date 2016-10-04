package name.bizna.jarm;

public final class ByteArrayRegion extends ByteBackedRegion {

	protected boolean allowWrites;
	protected boolean dirty;
	protected byte[] backing;

	/* Because of JVM limitations, size cannot be larger than 1GB. */
	public ByteArrayRegion(long size, int accessLatency, boolean wide) {
		super(accessLatency, wide);
		assert (size <= (1 * 1024 * 1024 * 1024));
		this.allowWrites = true;
		this.backing = new byte[(int) size];
	}

	public ByteArrayRegion(long size, int accessLatency) {
		this(size, accessLatency, true);
	}

	public ByteArrayRegion(long size) {
		this(size, 1, true);
	}

	public ByteArrayRegion(byte[] backing, boolean allowWrites, int accessLatency, boolean wide) {
		super(accessLatency, wide);
		this.allowWrites = allowWrites;
		this.backing = backing;
	}

	public ByteArrayRegion(byte[] backing, boolean allowWrites, int accessLatency) {
		this(backing, allowWrites, accessLatency, true);
	}

	public ByteArrayRegion(byte[] backing, boolean allowWrites) {
		this(backing, allowWrites, 1, true);
	}

	public ByteArrayRegion(byte[] backing) {
		this(backing, true, 1, true);
	}

	public boolean isDirty() {
		return dirty;
	}

	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

	@Override
	public long getRegionSize() {
		return backing.length;
	}

	@Override
	public byte backingReadByte(int address) throws BusErrorException {
		return backing[address];
	}

	@Override
	public void backingWriteByte(int address, byte b) throws BusErrorException {
		if (allowWrites) {
			dirty = true;
			backing[address] = b;
		} else {
			throw new BusErrorException("ByteArrayRegion is readonly", address, BusErrorException.AccessType.WRITE);
		}
	}

	public byte[] getBackingArray() {
		return backing;
	}

}
