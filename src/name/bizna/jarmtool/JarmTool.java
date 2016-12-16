package name.bizna.jarmtool;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import name.bizna.jarm.AlignmentException;
import name.bizna.jarm.BusErrorException;
import name.bizna.jarm.ByteArrayRegion;
import name.bizna.jarm.CPU;
import name.bizna.jarm.EscapeCompleteException;
import name.bizna.jarm.EscapeRetryException;
import name.bizna.jarm.UndefinedException;

public class JarmTool {
	private static final int MAX_PROGRAM_SPACE = 0x40000000;
	private static final int PROGRAM_ARGS_BLOCK_ADDR = 0x40000000;
	private static boolean littleEndian = false;
	private static int entryPoint;
	private static class NonLoadableFileException extends Exception {
		public static final long serialVersionUID = 1;
		private String way;
		public NonLoadableFileException(String way) {
			this.way = way;
		}
		public String getWay() {
			return way;
		}
	}
	private static class ProgramHeaderEntry {
		int p_type;
		int p_offset;
		int p_vaddr;
		// int p_paddr;
		int p_filesz;
		int p_memsz;
		// int p_flags;
		// int p_align;
		public ProgramHeaderEntry(ByteBuffer buf) {
			p_type = buf.getInt();
			p_offset = buf.getInt();
			p_vaddr = buf.getInt();
			/*p_paddr =*/ buf.getInt();
			p_filesz = buf.getInt();
			p_memsz = buf.getInt();
			/*p_flags = buf.getInt();*/
			/*p_align = buf.getInt();*/
		}
	}
	private static byte[] loadProgram(String pathstring) {
		RandomAccessFile f = null;
		try {
			f = new RandomAccessFile(pathstring, "r");
			byte[] headerReadArray = new byte[52];
			ByteBuffer headerReadBuf = ByteBuffer.wrap(headerReadArray);
			f.read(headerReadArray);
			/*** Read the Elf32_Ehdr ***/
			/* EI_MAG0-3 = backspace, ELF */
			if(headerReadBuf.getInt() != 0x7F454C46) {
				System.err.println(pathstring+": Not an ELF file");
				return null;
			}
			/* EI_CLASS = ELFCLASS32 */
			if(headerReadBuf.get() != 1) throw new NonLoadableFileException("not 32-bit");
			/* EI_DATA = ELFDATA2LSB or ELFDATA2MSB */
			final byte EI_DATA = headerReadBuf.get();
			switch(EI_DATA) {
			case 1: // ELFDATA2LSB
				littleEndian = true;
				break;
			case 2: // ELFDATA2MSB
				// big endian by default
				break;
			default: throw new NonLoadableFileException("neither little-endian nor big-endian");
			}
			/* EI_VERSION = 1 */
			if(headerReadBuf.get() != 1) throw new NonLoadableFileException("not version 1");
			/* Remainder of e_ident ignored */
			if(littleEndian) headerReadBuf.order(ByteOrder.LITTLE_ENDIAN);
			headerReadBuf.position(16);
			/* e_type = ET_EXEC */
			if(headerReadBuf.getShort() != 2) throw new NonLoadableFileException("not executable");
			/* e_machine = EM_ARM */
			if(headerReadBuf.getShort() != 40) throw new NonLoadableFileException("not ARM");
			/* e_version = 1 */
			if(headerReadBuf.getInt() != 1) throw new NonLoadableFileException("not version 1");
			/* e_entry */
			entryPoint = headerReadBuf.getInt();
			/* e_phoff */
			int e_phoff = headerReadBuf.getInt();
			if(e_phoff < 52) throw new NonLoadableFileException("impossibly small e_phoff");
			/* e_shoff ignored */
			headerReadBuf.getInt();
			/* e_flags */
			int e_flags = headerReadBuf.getInt();
			/* only accept a zero entry point if e_flags contains EF_ARM_HASENTRY (0x00000002) */
			if((e_flags & 2) != 0 && entryPoint == 0) throw new NonLoadableFileException("contains no entry point");
			/* e_ehsize */
			int e_ehsize = headerReadBuf.getShort() & 0xFFFF;
			if(e_ehsize < 52) throw new NonLoadableFileException("has an invalid e_ehsize field");
			/* e_phentsize */
			int e_phentsize = headerReadBuf.getShort() & 0xFFFF;
			if(e_phentsize < 32) throw new NonLoadableFileException("has an invalid e_phentsize field");
			/* e_phnum */
			int e_phnum = headerReadBuf.getShort() & 0xFFFF;
			if(e_phnum == 0 || e_phnum == 65535) throw new NonLoadableFileException("contains no program entries");
			/* e_shentsize, e_shnum, e_shstrndx are ignored */
			ProgramHeaderEntry phents[] = new ProgramHeaderEntry[e_phnum];
			for(int n = 0; n < e_phnum; ++n) {
				f.seek(e_phoff + n * e_phentsize);
				headerReadBuf.rewind();
				f.read(headerReadArray, 0, 32);
				phents[n] = new ProgramHeaderEntry(headerReadBuf);
			}
			int memtop = 0;
			for(ProgramHeaderEntry phent : phents) {
				// PT_LOAD = 1
				if(phent.p_type != 1) continue;
				if(phent.p_vaddr < 0 || phent.p_vaddr >= MAX_PROGRAM_SPACE) throw new NonLoadableFileException("virtual address out of range");
				if(phent.p_memsz >= MAX_PROGRAM_SPACE) throw new NonLoadableFileException("absurdly long section");
				int phent_top = phent.p_vaddr + phent.p_memsz;
				if(phent_top < 0 || phent_top > MAX_PROGRAM_SPACE) throw new NonLoadableFileException("absurdly long section");
				if(phent_top > memtop) memtop = phent_top;
			}
			assert(memtop <= MAX_PROGRAM_SPACE);
			byte[] programSpace;
			try {
				programSpace = new byte[memtop];
			}
			catch(OutOfMemoryError e) {
				throw new NonLoadableFileException("too big to fit in memory, please increase the maximum memory size of the Java VM");
			}
			for(ProgramHeaderEntry phent : phents) {
				if(phent.p_type != 1) continue;
				if(phent.p_filesz > 0) {
					f.seek(phent.p_offset);
					f.read(programSpace, phent.p_vaddr, phent.p_filesz);
				}
				/* byte arrays are zero-filled when created, so we don't have to zero-fill */
			}
			return programSpace;
		}
		catch(FileNotFoundException e) {
			System.err.println(pathstring+": No such file");
			return null;
		}
		catch(NonLoadableFileException e) {
			System.err.println(pathstring+": Not loadable: "+e.getWay());
			return null;
		}
		catch(EOFException e) {
			System.err.println(pathstring+": Unexpected end of file");
			return null;
		}
		catch(IOException e) {
			System.err.println(pathstring+": IOException caught");
			e.printStackTrace();
			return null;
		}
		finally {
			if(f != null) try { f.close(); } catch(IOException e) { e.printStackTrace(); }
		}
	}
	public static byte[] coalesceArgs(String args[]) {
		int size = 8 + 4 * args.length; /* big enough for argc, and argv, but not their contents */
		byte[][] stringBytes = new byte[args.length][];
		for(int n = 0; n < args.length; ++n) {
			stringBytes[n] = args[n].getBytes();
			size += stringBytes[n].length + 1;
		}
		byte[] ret = new byte[size];
		ByteBuffer buf = ByteBuffer.wrap(ret);
		buf.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
		int argvCursor = 8 + 4 * args.length + PROGRAM_ARGS_BLOCK_ADDR;
		buf.putInt(args.length);
		for(int n = 0; n < args.length; ++n) {
			buf.putInt(argvCursor);
			argvCursor += stringBytes[n].length + 1;
		}
		buf.putInt(0);
		for(int n = 0; n < args.length; ++n) {
			buf.put(stringBytes[n]);
			buf.put((byte)0);
		}
		return ret;
	}
	public static void main(String args[]) {
		if(args.length == 0) {
			System.err.println("Usage: jarmtool path/to/tool.elf [args]");
			System.err.println("Please note that a jarmtool has effectively the same access to the filesystem as the running JVM!");
			System.exit(126);
		}
		byte[] programSpace = loadProgram(args[0]);
		if(programSpace == null) {
			System.err.println("Program load failed, exiting");
			System.exit(127);
		}
		byte[] argSpace = coalesceArgs(args);
		CPU cpu = new CPU();
		CP3 cp3 = new CP3(cpu);
		CP7 cp7 = new CP7(cpu);
		cpu.mapCoprocessor(3, cp3);
		cpu.mapCoprocessor(7, cp7);
		cpu.getMemorySpace().mapRegion(0x00000000, new ByteArrayRegion(programSpace));
		cpu.getMemorySpace().mapRegion(PROGRAM_ARGS_BLOCK_ADDR, new ByteArrayRegion(argSpace, false));
		cpu.reset(false, !littleEndian, false);
		cpu.loadPC(entryPoint);
		try {
			while(true) {
				cpu.execute();
			}
		}
		catch(ProgramExit e) {
			System.exit(e.getExitStatus());
		}
		catch(UndefinedException e) {
			e.printStackTrace();
			cpu.dumpState(System.err);
		}
		catch(AlignmentException e) {
			e.printStackTrace();
			cpu.dumpState(System.err);
		}
		catch(BusErrorException e) {
			e.printStackTrace();
			cpu.dumpState(System.err);
		}
		catch(EscapeCompleteException e) {
			// ?
		}
		catch(EscapeRetryException e) {
			// ?
		}
	}
}
