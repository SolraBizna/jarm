package name.bizna.jarm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PhysicalMemorySpace {
	public static final class MappedRegion {
		public MappedRegion(long base, MemoryRegion region) {
			this.base = base;
			this.end = base + region.getRegionSize();
			this.region = region;
		}
		private final long base, end;
		private MemoryRegion region;

		public long getBase() {
			return base;
		}

		public long getEnd() {
			return end;
		}

		public MemoryRegion getRegion() {
			return region;
		}

		public void setRegion(MemoryRegion region) {
			this.region = region;
		}
	}
	private MappedRegion[] memoryMap = new MappedRegion[0];
	private MappedRegion getRegion(long address) throws BusErrorException {
		int t = 0, b = memoryMap.length;
		while(b > t) {
			int c = (b - t) / 2 + t;
			MappedRegion region = memoryMap[c];
			if(address >= region.base && address < region.end) return region;
			else if(address < region.base) b = c;
			else t = c + 1;
		}
		throw new BusErrorException("failed to get physical region" , address, BusErrorException.AccessType.UNKNOWN);
	}
	private int accessCycleBill;
	public final byte readByte(long address) throws BusErrorException, EscapeRetryException {
		MappedRegion mapping = getRegion(address);
		byte ret = mapping.region.readByte(this, address - mapping.base);
		return ret;
	}
	public final void writeByte(long address, byte value) throws BusErrorException, EscapeRetryException {
		MappedRegion mapping = getRegion(address);
		mapping.region.writeByte(this, address - mapping.base, value);
	}
	public final short readShort(long address, boolean bigEndian) throws BusErrorException, EscapeRetryException {
		assert((address&1)==0);
		MappedRegion mapping = getRegion(address);
		short ret;
		if(bigEndian) ret = mapping.region.readShortBE(this, address - mapping.base);
		else ret = mapping.region.readShortLE(this, address - mapping.base);
		return ret;
	}
	public final void writeShort(long address, short value, boolean bigEndian) throws BusErrorException, EscapeRetryException {
		assert((address&1)==0);
		MappedRegion mapping = getRegion(address);
		if(bigEndian) mapping.region.writeShortBE(this, address - mapping.base, value);
		else mapping.region.writeShortLE(this, address - mapping.base, value);
	}
	public final int readInt(long address, boolean bigEndian) throws BusErrorException, EscapeRetryException {
		assert((address&3)==0);
		MappedRegion mapping = getRegion(address);
		int ret;
		if(bigEndian) ret = mapping.region.readIntBE(this, address - mapping.base);
		else ret = mapping.region.readIntLE(this, address - mapping.base);
		return ret;
	}
	public final void writeInt(long address, int value, boolean bigEndian) throws BusErrorException, EscapeRetryException {
		assert((address&3)==0);
		MappedRegion mapping = getRegion(address);
		if(bigEndian) mapping.region.writeIntBE(this, address - mapping.base, value);
		else mapping.region.writeIntLE(this, address - mapping.base, value);
	}
	public final int settleAccessBill() {
		int ret = accessCycleBill;
		accessCycleBill = 0;
		return ret;
	}
	public final void addToBill(int i) {
		assert(i > 0);
		accessCycleBill += i;
	}
	public final void mapRegion(int _address, MemoryRegion region) {
		long address = _address & 0xFFFFFFFFL;
		MappedRegion[] newMap = new MappedRegion[memoryMap.length+1];
		int i = 0;
		for(; i < memoryMap.length; ++i) {
			MappedRegion it = memoryMap[i];
			if(it.base > address) break;
			newMap[i] = it;
		}
		newMap[i] = new MappedRegion(address, region);
		for(; i < memoryMap.length; ++i) {
			MappedRegion it = memoryMap[i];
			newMap[i+1] = it;
		}
		memoryMap = newMap;
	}
	public final void unmapRegion(int _address, MemoryRegion region) {
		long address = _address & 0xFFFFFFFFL;
		ArrayList<MappedRegion> newMap = new ArrayList<MappedRegion>(memoryMap.length);
		for(MappedRegion it : memoryMap) {
			if(it.base != address || (region == null || it.region != region)) newMap.add(it);
		}
		memoryMap = newMap.toArray(new MappedRegion[newMap.size()]);
	}
	public final void unmapAllRegions() {
		memoryMap = new MappedRegion[0];
	}
	
	public List<MappedRegion> getMappedRegions() {
		return Arrays.asList(memoryMap);
	}
}
