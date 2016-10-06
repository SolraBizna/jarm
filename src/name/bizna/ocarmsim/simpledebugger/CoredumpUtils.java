package name.bizna.ocarmsim.simpledebugger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import name.bizna.jarm.ByteArrayRegion;
import name.bizna.jarm.CPU;
import name.bizna.jarm.PhysicalMemorySpace;
import name.bizna.ocarmsim.ROMRegion;
import name.bizna.ocarmsim.SRAMRegion;

/**
 *
 * @author Jean-Rémy Buchs <jrb0001@692b8c32.de>
 */
public class CoredumpUtils {

	private static final byte[] CORE_NAME = new byte[]{'C', 'O', 'R', 'E', 0, 0, 0, 0};

	private final CPU cpu;
	private final ROMRegion rom;
	private final SRAMRegion sram;
	private final ByteArrayRegion[] rams;

	public CoredumpUtils(CPU cpu) {
		this.cpu = cpu;

		ROMRegion romRegion = null;
		SRAMRegion sramRegion = null;
		final List<ByteArrayRegion> ramRegions = new ArrayList<>();
		for (PhysicalMemorySpace.MappedRegion mappedRegion : cpu.getMemorySpace().getMappedRegions()) {
			if (mappedRegion.getRegion() instanceof ROMRegion) {
				romRegion = (ROMRegion) mappedRegion.getRegion();
			} else if (mappedRegion.getRegion() instanceof SRAMRegion) {
				sramRegion = (SRAMRegion) mappedRegion.getRegion();
			} else if (mappedRegion.getRegion() instanceof ByteArrayRegion) {
				ramRegions.add((ByteArrayRegion) mappedRegion.getRegion());
			}
		};
		rom = Objects.requireNonNull(romRegion, "No ROM found");
		sram = Objects.requireNonNull(sramRegion, "No SRAM found");
		rams = ramRegions.toArray(new ByteArrayRegion[ramRegions.size()]);
	}

	public void dumpCore(OutputStream out) throws IOException {
		// ELFOSABI_ARM, 0
		dumpCore(out, (byte) 97, (byte) 0);
	}

	private void dumpCore(OutputStream out, byte abi, byte abiVersion) throws IOException {
		boolean E = cpu.isBigEndian();
		ByteBuffer noteBuf = ByteBuffer.allocate(168);
		try (WritableByteChannel channel = Channels.newChannel(out)) {
			// PRSTATUS
			/* Elf32_Nhdr */
			noteBuf.putInt(swapInt(E, 5)); // n_namesz = strlen("CORE")+1
			noteBuf.putInt(swapInt(E, 148)); // n_descsz = length of PRSTATUS descriptor
			noteBuf.putInt(swapInt(E, 1)); // n_type = NT_PRSTATUS
			noteBuf.put(CORE_NAME);
			noteBuf.putInt(swapInt(E, 0)); // pr_info.si_signo = 0
			noteBuf.putInt(swapInt(E, 0)); // pr_info.si_code = 0
			noteBuf.putInt(swapInt(E, 0)); // pr_info.si_errno = 0
			noteBuf.putShort(swapShort(E, 0)); // pr_cursig = 0
			noteBuf.putShort((short) 0); // (padding)
			noteBuf.putInt(swapInt(E, 0)); // pr_pid = 0
			noteBuf.putInt(swapInt(E, 0)); // pr_sigpend = 0
			noteBuf.putInt(swapInt(E, 0)); // pr_sighold = 0
			noteBuf.putInt(swapInt(E, 0)); // pr_ppid = 0
			noteBuf.putInt(swapInt(E, 0)); // pr_gid = 0
			noteBuf.putInt(swapInt(E, 0)); // pr_sid = 0
			noteBuf.putInt(swapInt(E, 0)); // pr_utime.tv_sec = 0
			noteBuf.putInt(swapInt(E, 0)); // pr_utime.tv_usec = 0
			noteBuf.putInt(swapInt(E, 0)); // pr_stime.tv_sec = 0
			noteBuf.putInt(swapInt(E, 0)); // pr_stime.tv_usec = 0
			noteBuf.putInt(swapInt(E, 0)); // pr_cutime.tv_sec = 0
			noteBuf.putInt(swapInt(E, 0)); // pr_cutime.tv_usec = 0
			noteBuf.putInt(swapInt(E, 0)); // pr_cstime.tv_sec = 0
			noteBuf.putInt(swapInt(E, 0)); // pr_cstime.tv_usec = 0
			// r0--r15
			for (int n = 0; n < 16; ++n) {
				noteBuf.putInt(swapInt(E, cpu.readRegister(n)));
			}
			noteBuf.putInt(swapInt(E, 0)); // ???
			noteBuf.putInt(swapInt(E, cpu.readCPSR())); // CPSR
			noteBuf.putInt(swapInt(E, 0)); // pr_fpvalid = 0
			noteBuf.flip();
			ByteBuffer buf = ByteBuffer.allocate(36);
			/* Elf32_Ehdr 
			 * magic; ELFCLASS32 (1), ELFDATA2LSB/ELFDATA2MSB (1/2) depending on endianness
			 * ELF_VERSION (1), caller-provided ABI and version, and padding to 16
			 */
			out.write(new byte[]{0x7f, 'E', 'L', 'F', 1, (byte) (E ? 2 : 1), 1, abi,
				abiVersion, 0, 0, 0, 0, 0, 0, 0});
			buf.clear();
			buf.putShort(swapShort(E, 4)); // e_type = ET_CORE
			buf.putShort(swapShort(E, 40)); // e_machine = EM_ARM
			buf.putInt(swapInt(E, 1)); // e_version = 1
			buf.putInt(swapInt(E, cpu.readPC() - 4)); // e_entry = currently executing instruction (ish)
			buf.putInt(swapInt(E, 52)); // e_phoff = immediately after Ehdr
			buf.putInt(swapInt(E, 0)); // e_shoff = 0
			buf.putInt(swapInt(E, (E ? 0x00800000 : 0x00000000) | 0x0400)); // e_flags = EF_ARM_BE8/0 | EF_ARM_VFP_FLOAT
			buf.putShort(swapShort(E, 52)); // e_ehsize = 52
			buf.putShort(swapShort(E, 32)); // e_phentsize = 32
			int numRams = 0;
			for (ByteArrayRegion module : rams) {
				if (module != null) {
					++numRams;
				}
			}
			// one PT_NOTE, one PT_LOAD for ROM, one PT_LOAD for SRAM, one PT_LOAD for each RAM module
			buf.putShort(swapShort(E, 3 + numRams)); // e_phnum as above
			// e_shentsize/shnum/shstrndx = 0
			buf.putShort(swapShort(E, 0));
			buf.putShort(swapShort(E, 0));
			buf.putShort(swapShort(E, 0)); // SHN_UNDEF
			buf.flip();
			channel.write(buf);
			/* write Elf32_Phdrs */
			int offset = 52 + 32 * (3 + numRams);
			offset = putNoteHeader(buf, E, offset, noteBuf.remaining());
			channel.write(buf);
			offset = putLoadHeader(buf, E, offset, 0xFFFF0000, rom.getArrayMask() + 1, 5/*PF_X|PF_R*/);
			channel.write(buf);
			offset = putLoadHeader(buf, E, offset, 0x80000000, (int) sram.getRegionSize(), 7/*PF_X|PF_W|PF_R*/);
			channel.write(buf);
			int mappingOffset = 0;
			for (ByteArrayRegion module : rams) {
				if (module != null) {
					offset = offset + putLoadHeader(buf, E, offset, mappingOffset, (int) module.getRegionSize(), 7/*PF_X|PF_W|PF_R*/);
					channel.write(buf);
					mappingOffset += module.getRegionSize();
				}
			}
			/* write data */
			channel.write(noteBuf);
			out.write(rom.getArray());
			out.write(sram.getSramArray());
			for (ByteArrayRegion module : rams) {
				if (module != null) {
					out.write(module.getBackingArray());
				}
			}
		}
	}

	private short swapShort(boolean bigEndian, int v) {
		if (bigEndian) {
			return (short) v;
		} else {
			return Short.reverseBytes((short) v);
		}
	}

	private int swapInt(boolean bigEndian, int v) {
		if (bigEndian) {
			return v;
		} else {
			return Integer.reverseBytes(v);
		}
	}

	private int putNoteHeader(ByteBuffer buf, boolean E, int offset, int size) {
		/* Elf32_Phdr */
		buf.clear();
		buf.putInt(swapInt(E, 4)); // p_type = PT_NOTE;
		buf.putInt(swapInt(E, offset)); // p_offset
		buf.putInt(swapInt(E, 0)); // p_vaddr
		buf.putInt(swapInt(E, 0)); // p_paddr
		buf.putInt(swapInt(E, size)); // p_filesz
		buf.putInt(swapInt(E, size)); // p_memsz
		buf.putInt(swapInt(E, 0)); // p_flags
		buf.putInt(swapInt(E, 1)); // p_align = 1
		buf.flip();
		return offset + size;
	}

	private int putLoadHeader(ByteBuffer buf, boolean E, int offset, int addr, int size, int flags) {
		/* Elf32_Phdr */
		buf.clear();
		buf.putInt(swapInt(E, 1)); // p_type = PT_LOAD;
		buf.putInt(swapInt(E, offset)); // p_offset
		buf.putInt(swapInt(E, addr)); // p_vaddr
		buf.putInt(swapInt(E, addr)); // p_paddr
		buf.putInt(swapInt(E, size)); // p_filesz
		buf.putInt(swapInt(E, size)); // p_memsz
		buf.putInt(swapInt(E, flags)); // p_flags
		buf.putInt(swapInt(E, 1)); // p_align = 1
		buf.flip();
		return offset + size;
	}
}
