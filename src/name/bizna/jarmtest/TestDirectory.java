package name.bizna.jarmtest;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import name.bizna.jarm.AlignmentException;
import name.bizna.jarm.BusErrorException;
import name.bizna.jarm.ByteArrayRegion;
import name.bizna.jarm.CPU;
import name.bizna.jarm.PhysicalMemorySpace;
import name.bizna.jarm.UndefinedException;
import name.bizna.jarmtest.TestSpec.InvalidSpecException;

public class TestDirectory {
	public static final String CODE_FILENAME = "code.elf";
	public static final int MAX_PROGRAM_SPACE = 1<<30;
	private File path;
	private String name;
	private byte[] programBytes = null, hiBytes = null;
	private boolean littleEndian = false, hasEntryPoint = false;
	private int entryPoint = 0;
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
	private void loadProgram(File path, String id) throws NonLoadableFileException {
		RandomAccessFile f = null;
		programBytes = null;
		hiBytes = null;
		littleEndian = false;
		entryPoint = 0;
		hasEntryPoint = false;
		try {
			f = new RandomAccessFile(path, "r");
			byte[] headerReadArray = new byte[52];
			ByteBuffer headerReadBuf = ByteBuffer.wrap(headerReadArray);
			f.read(headerReadArray);
			/*** Read the Elf32_Ehdr ***/
			/* EI_MAG0-3 = backspace, ELF */
			if(headerReadBuf.getInt() != 0x7F454C46)
				throw new NonLoadableFileException("not an ELF file", id);
			/* EI_CLASS = ELFCLASS32 */
			if(headerReadBuf.get() != 1) throw new NonLoadableFileException("not 32-bit", id);
			/* EI_DATA = ELFDATA2LSB or ELFDATA2MSB */
			final byte EI_DATA = headerReadBuf.get();
			switch(EI_DATA) {
			case 1: // ELFDATA2LSB
				littleEndian = true;
				break;
			case 2: // ELFDATA2MSB
				// big endian by default
				break;
			default: throw new NonLoadableFileException("neither little-endian nor big-endian", id);
			}
			/* EI_VERSION = 1 */
			if(headerReadBuf.get() != 1) throw new NonLoadableFileException("not version 1", id);
			/* Remainder of e_ident ignored */
			if(littleEndian) headerReadBuf.order(ByteOrder.LITTLE_ENDIAN);
			headerReadBuf.position(16);
			/* e_type = ET_EXEC */
			if(headerReadBuf.getShort() != 2) throw new NonLoadableFileException("not executable", id);
			/* e_machine = EM_ARM */
			if(headerReadBuf.getShort() != 40) throw new NonLoadableFileException("not ARM", id);
			/* e_version = 1 */
			if(headerReadBuf.getInt() != 1) throw new NonLoadableFileException("not version 1", id);
			/* e_entry */
			entryPoint = headerReadBuf.getInt();
			/* e_phoff */
			int e_phoff = headerReadBuf.getInt();
			if(e_phoff < 52) throw new NonLoadableFileException("impossibly small e_phoff", id);
			/* e_shoff ignored */
			headerReadBuf.getInt();
			/* e_flags */
			int e_flags = headerReadBuf.getInt();
			/* only accept a zero entry point if e_flags contains EF_ARM_HASENTRY (0x00000002) */
			if((e_flags & 2) != 0 && entryPoint == 0) hasEntryPoint = false;
			else hasEntryPoint = true;
			/* e_ehsize */
			int e_ehsize = headerReadBuf.getShort() & 0xFFFF;
			if(e_ehsize < 52) throw new NonLoadableFileException("has an invalid e_ehsize field", id);
			/* e_phentsize */
			int e_phentsize = headerReadBuf.getShort() & 0xFFFF;
			if(e_phentsize < 32) throw new NonLoadableFileException("has an invalid e_phentsize field", id);
			/* e_phnum */
			int e_phnum = headerReadBuf.getShort() & 0xFFFF;
			if(e_phnum == 0 || e_phnum == 65535) throw new NonLoadableFileException("contains no program entries", id);
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
				if(phent.p_vaddr < 0 && phent.p_vaddr >= -65536) {
					// it's a high section
					if(phent.p_memsz > -phent.p_vaddr) throw new NonLoadableFileException("high section is too long", id);
					if(hiBytes == null) {
						try {
							hiBytes = new byte[65536];
						}
						catch(OutOfMemoryError e) {
							throw new NonLoadableFileException("can't even allocate 64k for hiBytes, please increase the maximum memory size of the Java VM", id);
						}
					}
				}
				else {
					if(phent.p_vaddr < 0 || phent.p_vaddr >= MAX_PROGRAM_SPACE) throw new NonLoadableFileException("virtual address out of range", id);
					if(phent.p_memsz >= MAX_PROGRAM_SPACE) throw new NonLoadableFileException("absurdly long section", id);
					int phent_top = phent.p_vaddr + phent.p_memsz;
					if(phent_top < 0 || phent_top > MAX_PROGRAM_SPACE) throw new NonLoadableFileException("absurdly long section", id);
					if(phent_top > memtop) memtop = phent_top;
				}
			}
			assert(memtop <= MAX_PROGRAM_SPACE);
			try {
				programBytes = new byte[memtop];
			}
			catch(OutOfMemoryError e) {
				throw new NonLoadableFileException("too big to fit in memory, please increase the maximum memory size of the Java VM", id);
			}
			for(ProgramHeaderEntry phent : phents) {
				if(phent.p_type != 1) continue;
				if(phent.p_filesz > 0) {
					f.seek(phent.p_offset);
					if(phent.p_vaddr >= -65536 && phent.p_vaddr < 0)
						f.read(hiBytes, phent.p_vaddr + 65536, phent.p_filesz);
					else
						f.read(programBytes, phent.p_vaddr, phent.p_filesz);
				}
				/* byte arrays are zero-filled when created, so we don't have to zero-fill */
			}
		}
		catch(EOFException e) {
			throw new NonLoadableFileException("unexpected EOF", id);
		}
		catch(FileNotFoundException e) {
			throw new NonLoadableFileException("file not found", id);
		}
		catch(IOException e) {
			throw new NonLoadableFileException("IO error", id);
		}
		finally {
			if(f != null) try { f.close(); } catch(IOException e) { e.printStackTrace(); }
		}
	}
	public TestDirectory(File path, String name) {
		this.path = path;
		this.name = name;
	}
	public static boolean isValidTestDir(File dir) {
		return new File(dir, CODE_FILENAME).exists();
	}
	public boolean runTestWithSpec(CPU cpu, File specFile, String specId, List<String> subtestFailureList) throws NonLoadableFileException {
		TestSpec spec;
		try {
			spec = new TestSpec(specFile);
		}
		catch(InvalidSpecException e) {
			throw new NonLoadableFileException("invalid spec", specId);
		}
		catch(EOFException e) {
			throw new NonLoadableFileException("unexpected EOF", specId);
		}
		catch(FileNotFoundException e) {
			throw new NonLoadableFileException("file not found", specId);
		}
		catch(IOException e) {
			throw new NonLoadableFileException("IO error", specId);
		}
		spec.applyInitialStateAndReset(cpu, littleEndian);
		if(hasEntryPoint) cpu.loadPC(entryPoint);
		cpu.zeroBudget(false);
		try {
			cpu.execute(1<<30);
		}
		catch(BusErrorException e) { /* NOTREACHED */ }
		catch(AlignmentException e) { /* NOTREACHED */ }
		catch(UndefinedException e) { /* NOTREACHED */ }
		return spec.checkFinalState(cpu, subtestFailureList);
	}
	public boolean runTest(CPU cpu, List<String> failureList) {
		boolean success = true;
		List<File> specFiles = new ArrayList<File>();
		for(File file : path.listFiles()) {
			if(file.getName().endsWith(".spec")) {
				specFiles.add(file);
			}
		}
		if(specFiles.isEmpty()) {
			System.err.println(name+": Has no specs");
			success = false;
		}
		else {
			try {
				loadProgram(new File(path, CODE_FILENAME), name+File.separator+CODE_FILENAME);
				PhysicalMemorySpace mem = cpu.getMemorySpace();
				if(specFiles.size() == 1) {
					mem.unmapAllRegions();
					if(programBytes != null) mem.mapRegion(0, new ByteArrayRegion(programBytes));
					if(hiBytes != null) mem.mapRegion(-65536, new ByteArrayRegion(hiBytes));
					String specId = name+File.separator+specFiles.get(0).getName();
					List<String> subtestFailureList = new ArrayList<String>();
					if(!runTestWithSpec(cpu, specFiles.get(0), specId, subtestFailureList)) {
						if(subtestFailureList.isEmpty()) failureList.add(specId+" (unknown failure)");
						else for(String failure : subtestFailureList) failureList.add(specId+" ("+failure+")");
						success = false;
					}
				}
				else {
					for(File specFile : specFiles) {
						mem.unmapAllRegions();
						if(programBytes != null) mem.mapRegion(0, new ByteArrayRegion(programBytes.clone()));
						if(hiBytes != null) mem.mapRegion(-65536, new ByteArrayRegion(hiBytes.clone()));
						String specId = name+File.separator+specFile.getName();
						List<String> subtestFailureList = new ArrayList<String>();
						if(!runTestWithSpec(cpu, specFile, specId, subtestFailureList)) {
							if(subtestFailureList.isEmpty()) failureList.add(specId+" (unknown failure)");
							else for(String failure : subtestFailureList) failureList.add(specId+" ("+failure+")");
							success = false;
						}
					}
				}
				
			}
			catch(NonLoadableFileException e) {
				System.err.println(e.getIdentifier()+": Not loadable: "+e.getWay());
				failureList.add(e.getIdentifier());
				success = false;
			}
		}
		return success;
	}
	public String getName() {
		return name;
	}
}