package name.bizna.ocarmsim;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import name.bizna.jarm.BusErrorException;
import name.bizna.jarm.ByteBackedRegion;
import name.bizna.jarm.EscapeRetryException;

public class SRAMRegion extends ByteBackedRegion {
	
	private File path;
	private boolean writable;
	byte[] sramArray;
	byte[] nvramArray;

	SRAMRegion(File inpath, int arraySize, boolean writable) throws IOException {
		path = inpath;
		this.writable = writable;
		if(path == null || !path.exists()) {
			if(arraySize < 0) arraySize = -arraySize;
			sramArray = new byte[arraySize];
			nvramArray = new byte[arraySize];
		}
		else {
			long len = path.length();
			if(len > 0x40000000) throw new IOException("That SRAM image is WAY too big!");
			if(arraySize > 0)
				sramArray = new byte[arraySize];
			else {
				int fileSizeBits = 0;
				while((1L << fileSizeBits) < len) ++fileSizeBits;
				sramArray = new byte[1 << fileSizeBits];
			}
			FileInputStream fis = new FileInputStream(path);
			try {
				if(fis.read(sramArray) < 0) throw new IOException();
			}
			finally {
				fis.close();
			}
			nvramArray = Arrays.copyOf(sramArray, sramArray.length);
		}
	}

	@Override
	public byte backingReadByte(int address) throws BusErrorException,
			EscapeRetryException {
		return sramArray[address];
	}

	@Override
	public void backingWriteByte(int address, byte v) throws BusErrorException,
			EscapeRetryException {
		sramArray[address] = v;
	}

	@Override
	public long getRegionSize() {
		return sramArray.length;
	}
	
	public void flushToNVRAM() throws IOException {
		nvramArray = Arrays.copyOf(sramArray, sramArray.length);
		maybeWriteNVRAM();
	}
	
	public void maybeWriteNVRAM() throws IOException {
		if(path != null && writable) {
			FileOutputStream fos = new FileOutputStream(path);
			try {
				fos.write(sramArray);
			}
			finally {
				fos.close();
			}
		}
	}

}
