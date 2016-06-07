package name.bizna.ocarmsim;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import name.bizna.jarm.BusErrorException;
import name.bizna.jarm.ByteBackedRegion;
import name.bizna.jarm.EscapeRetryException;

public class ROMRegion extends ByteBackedRegion {

	// cribbed from JARMArchitecture
	private static final int ROM_MIN_BIT_DEPTH = 12; // 4K ROM
	
	byte[] array;
	int arrayMask;

	ROMRegion(File inpath) throws IOException {
		int arraySizeBits = ROM_MIN_BIT_DEPTH;
		long len = inpath.length();
		if(len > 0x40000000) throw new IOException("That EEPROM image is WAY too big!");
		while((1L << arraySizeBits) < len) ++arraySizeBits;
		FileInputStream fis = new FileInputStream(inpath);
		try {
			array = new byte[1 << arraySizeBits];
			arrayMask = (1 << arraySizeBits) - 1;
			if(fis.read(array) < 0) throw new IOException();
		}
		finally {
			fis.close();
		}
		if(array[0] == '-' && array[1] == '-' && array[2] == '[') {
			int n = 3;
			while(n < array.length && array[n] == '=') ++ n;
			if(n < array.length-1 && array[n] == '[') {
				// it's Lua-compatible, trim that part of it
				++n;
				int amt = array.length - n;
				for(int m = 0; m < amt; ++m, ++n) {
					array[m] = array[n];
				}
			}
		}
	}

	@Override
	public byte backingReadByte(int address) throws BusErrorException,
			EscapeRetryException {
		return array[address & arrayMask];
	}

	@Override
	public void backingWriteByte(int address, byte v) throws BusErrorException,
			EscapeRetryException {
		throw new BusErrorException();
	}

	@Override
	public long getRegionSize() {
		return 0x40000000;
	}

}
