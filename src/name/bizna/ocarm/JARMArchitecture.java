package name.bizna.ocarm;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.Item;
import li.cil.oc.api.driver.item.Memory;
import li.cil.oc.api.driver.item.Processor;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.Architecture.NoMemoryRequirements;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.ExecutionResult;
import li.cil.oc.api.machine.LimitReachedException;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.machine.Value;
import name.bizna.jarm.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

@Architecture.Name("OC-ARM")
@NoMemoryRequirements
public class JARMArchitecture implements Architecture {
	
	private static final int MAX_RUN_TIME = 20; /* TODO: config option? */
	private static final int ROM_MIN_BIT_DEPTH = 12; // 4K ROM
	private static final int MAX_VALUES = 256; /* May not even be enough... */

	CPU cpu = null;
	PhysicalMemorySpace mem;
	int ramSize = 0;
	Machine machine = null;
	RAMModule[] ramModules = new RAMModule[0];
	RAMModule[] oldModules = null;
	int cpuTier = 0;
	int cpuCyclesPerTick = 10;
	boolean romMappingValid = false;
	boolean sramMappingValid = false;
	long lastRunTime = Long.MIN_VALUE;
	byte[] romArray, sramArray;
	int romArrayMask;
	int romArchSafeOffset;
	boolean blockRemTick;
	boolean needRandomizeMemory;
	boolean lastYieldWasSleep;
	
    private HashMap<Value, Integer> valueToHandle = new HashMap<Value, Integer>();
    private TreeMap<Integer, Value> handleToValue = new TreeMap<Integer, Value>();
    private int nextValueHandle = 1;
	
	CP3 cp3;
	private class ROMRegion extends ByteBackedRegion {
		ROMRegion(int latency, boolean wide) { super(latency, wide); }
		@Override
		public long getRegionSize() {
			return 1<<30;
		}
		@Override
		public void backingWriteByte(int address, byte b) throws BusErrorException {
			throw new BusErrorException("ROM is readonly: Requested address is " + address);
		}
		@Override
		public byte backingReadByte(int address) throws BusErrorException, EscapeRetryException {
			if(!romMappingValid) attemptShadowEEPROM();
			if(romArray == null) throw new BusErrorException("ROM doesn't exist: Requested address is " + address);
			int p = (int)((address&romArrayMask)+romArchSafeOffset);
			if(p < 0 || p >= romArray.length) return (byte)0;
			return romArray[p];
		}
	}
	private class SRAMRegion extends ByteBackedRegion {
		SRAMRegion(int latency, boolean wide) { super(latency, wide); }
		@Override
		public long getRegionSize() {
			return 1<<30;
		}
		@Override
		public void backingWriteByte(int address, byte b) throws BusErrorException, EscapeRetryException {
			if(!sramMappingValid) attemptShadowEEPROM();
			if(sramArray == null) throw new BusErrorException("SRAM doesn't exist: Requested address is " + address);
			if(address < 0 || address >= sramArray.length) throw new BusErrorException("Address is out of bounds for SRAM: Requested address is " + address);
			sramArray[address] = b;
		}
		@Override
		public byte backingReadByte(int address) throws BusErrorException, EscapeRetryException {
			if(!sramMappingValid) attemptShadowEEPROM();
			if(sramArray == null) throw new BusErrorException("SRAM doesn't exist: Requested address is " + address);
			if(address < 0 || address >= sramArray.length) throw new BusErrorException("Address is out of bounds for SRAM: Requested address is " + address);
			return sramArray[address];
		}
	}
	
	void flushNVRAM() throws EscapeRetryException {
		if(!sramMappingValid || sramArray == null) return;
		Map<String,String> components = machine.components();
		String addr = null;
		for(Map.Entry<String, String> entry : components.entrySet()) {
			if(entry.getValue().equals("eeprom")) {
				addr = entry.getKey();
				break;
			}
		}
    	try {
    		int size;
    		for(size = sramArray.length; size > 0 && sramArray[size-1] == 0; --size)
    			;
    		machine.invoke(addr, "setData", new Object[]{Arrays.copyOf(sramArray,size)});
    	}
    	catch(LimitReachedException e) { blockRemTick = true; throw new EscapeRetryException(); }
    	catch(Exception e) {
    		OCARM.logger.error("Exception while invoking eeprom.setData", e);
    	}
	}
	
	int getMemoryLatency(int index) {
		if(index == 0) {
			return (OCARM.instance.getROMLatency(cpuTier) << 1) | ((OCARM.instance.isROMWide()&&OCARM.instance.isCPUWide(cpuTier))?0:1);
		}
		else if(index <= ramModules.length) {
			/* TODO: option to switch between 16- and 32-bit RAMs */
			return (OCARM.instance.getRAMLatency(cpuTier, ramModules[index-1].tier) << 1) | ((OCARM.instance.isRAMWide(ramModules[index-1].tier)&&OCARM.instance.isCPUWide(cpuTier))?0:1);
		}
		else return 0;
	}
	
	int getMemorySize(int index) throws EscapeRetryException {
		if(index == 0) {
			if(!sramMappingValid) attemptShadowEEPROM();
			return sramArray == null ? 0 : sramArray.length;
		}
		else if(index <= ramModules.length) {
			return ramModules[index-1].byteSize;
		}
		else return 0;
	}
	
	int getCPUCyclesPerTick() { return cpuCyclesPerTick; }
	
	private static class RAMModule {
		int byteSize;
		int tier;
		private byte[] backing;
		RAMModule(int byteSize, int tier) {
			this.byteSize = byteSize;
			if(tier < 0) tier = 0;
			else if(tier > 2) tier = 2;
			this.tier = tier;
		}
		/**
		 * Actually returns whether the other RAM module is <b>equivalent</b>
		 * @param _other
		 * @return
		 */
		@Override
		public boolean equals(Object _other) {
			if(!(_other instanceof RAMModule)) return false;
			RAMModule other = (RAMModule)_other;
			return other.byteSize == byteSize && other.tier == tier;
		}
		/* not used */
		@Override
		public int hashCode() {
			return super.hashCode();
		}
		byte[] getBacking() {
			if(backing == null) backing = new byte[byteSize];
			return backing;
		}
	}

	@Override
	public boolean isInitialized() {
		return cpu != null;
	}

	@Override
	public boolean recomputeMemory(Iterable<ItemStack> components) {
		romMappingValid = false;
		// SRAM stays valid when components switch out
		// sramMappingValid = false;
		LinkedList<RAMModule> newModules = new LinkedList<RAMModule>();
		for(ItemStack stack : components) {
			Item driver = Driver.driverFor(stack);
			if(driver instanceof Memory) {
				double itemTier = ((Memory)driver).amount(stack);
				double byteSize = OCARM.instance.getMemorySize(itemTier);
				if(byteSize < 1) continue;
				if(byteSize > 1 * 1024 * 1024 * 1024) byteSize = 1 * 1024 * 1024 * 1024;
				else byteSize = Math.round(byteSize);
				newModules.add(new RAMModule((int)byteSize, driver.tier(stack)));
			}
			else if(driver instanceof Processor) {
				int tier = driver.tier(stack);
				cpuTier = tier;
				cpuCyclesPerTick = OCARM.instance.getCPUCyclesPerTick(tier);
			}
		}
		/* TODO: do this in runThreaded? */
		boolean sameModules = false;
		if(ramModules.length == newModules.size()) {
			sameModules = true; // maybe they are!
			for(int i = 0; i < ramModules.length; ++i) {
				if(!newModules.get(i).equals(ramModules[i])) {
					sameModules = false; // guess not!
					break;
				}
			}
		}
		if(!sameModules) {
			ramModules = (RAMModule[])newModules.toArray(new RAMModule[newModules.size()]);
			needRandomizeMemory = true;
			if(machine != null) machine.crash("RAM modules changed");
			if(cpu != null) remapMemory();
		}
		return ramModules.length > 0 || OCARM.instance.shouldAllowRAMlessComputers();
	}

	@Override
	public boolean initialize() {
		cpu = new CPU(null);
		cp3 = new CP3(cpu, machine, this);
		cpu.mapCoprocessor(3, cp3);
		lastRunTime = Long.MIN_VALUE;
		lastYieldWasSleep = false;
		if(OCARM.instance.shouldAllowDebugCoprocessor()) cpu.mapCoprocessor(7, new CP7(cpu));
		// recomputeMemory(machine.host().internalComponents());
		remapMemory();
		cpu.reset(false, true, true);
		return true;
	}

	@Override
	public void close() {
		if(cpu != null) {
			cpu = null;
			mem = null;
			cp3 = null;
			lastRunTime = Long.MIN_VALUE;
		}
	}

	@Override
	public void runSynchronized() {
		cp3.runSynchronized();
	}

	@Override
	public ExecutionResult runThreaded(boolean isSynchronizedReturn) {
		try {
		//MCJARM.logger.info("runThreaded(%b) at about %08X",isSynchronizedReturn,cpu.readPC()-4);
		int cycleCount;
		synchronized(this) {
			if(needRandomizeMemory) {
				Random rng = new Random();
				needRandomizeMemory = false;
				for(RAMModule module : ramModules) {
					rng.nextBytes(module.backing);
				}
			}
		}
		long thisRunTime = machine.worldTime();
		if(isSynchronizedReturn || lastYieldWasSleep) cycleCount = cpuCyclesPerTick;
		else if(thisRunTime < lastRunTime) {
			OCARM.logger.warn("World time ran backwards!");
			cycleCount = 0;
		}
		else if(thisRunTime - lastRunTime > MAX_RUN_TIME) {
			OCARM.logger.warn("We fell behind the world!");
			cycleCount = cpuCyclesPerTick * MAX_RUN_TIME;
		}
		else cycleCount = (int)((thisRunTime - lastRunTime) * cpuCyclesPerTick);
		lastRunTime = thisRunTime;
		lastYieldWasSleep = false;
		blockRemTick = false;
		try {
			while(true) {
				ExecutionResult ret = null;
				if(cp3.mayExecute()) {
					synchronized(this) {
						cpu.execute(cycleCount);
					}
					cycleCount = 0;
				}
				if(ret == null) ret = cp3.getExecutionResult();
				if(ret != null) {
					lastYieldWasSleep = ret instanceof ExecutionResult.Sleep;
					cpu.zeroBudget(true);
					return ret;
				}
				else if(blockRemTick) {
					cpu.zeroBudget(true);
					return new ExecutionResult.SynchronizedCall();
				}
				else if(cpu.budgetFullySpent())
					return new ExecutionResult.SynchronizedCall();
			}
		}
		catch(UnimplementedInstructionException e) {
			OCARM.logger.error("UNIMPLEMENTED INSTRUCTION: "+e.toString(), e);
			cpu.dumpState(System.err);
			return new ExecutionResult.Error("UNIMPLEMENTED INSTRUCTION "+e.toString());
		}
		catch(Exception e) {
			OCARM.logger.error("Exception in cpu.execute", e);
			if(e instanceof BusErrorException) {
				VirtualMemorySpace vm = cpu.getVirtualMemorySpace();
				OCARM.logger.error("While attempting a %d-bit %s at %08X", 8<<vm.getLastAccessWidth(), vm.getLastAccessWasStore()?"store":"load", vm.getLastAccessAddress());
			}
			cpu.dumpState(System.err);
			if(shouldDumpCore()) {
				try {
					FileOutputStream fos = new FileOutputStream(OCARM.instance.coreDumpFile());
					dumpCore(fos);
					fos.close();
					OCARM.logger.error("Core was dumped to %s", OCARM.instance.coreDumpFile());
				}
				catch(Exception f) {
					OCARM.logger.error("And, an exception while trying to dump core", f);
				}
			}
			return new ExecutionResult.Error("Exception in cpu.execute: "+e.getClass().getSimpleName()+" (see log for details)");
		}
		}
		catch(Exception e) {
			OCARM.logger.error("Unexpected exception!", e);
			return new ExecutionResult.Error("Unexpected exception in JARM module. This should never happen! (see log for details)");
		}
	}

	@Override
	public void onConnect() {
		/* do nothing */
	}

	@Override
	public void load(NBTTagCompound nbt) {
		/* TODO: stub */
		machine.stop();
	}

	@Override
	public void save(NBTTagCompound nbt) {
		/* TODO: stub */
	}
	
	private void mapRAM() {
		int addr = 0;
		for(RAMModule module : ramModules) {
			int next_addr = addr + module.byteSize;
			int next_size = module.byteSize;
			if(next_addr > (1<<30) || next_addr < 0 || next_size > (1<<30) || next_size < 0)
				throw new RuntimeException("Okay, stopping you right there. I'm not going to let you put more than 1GB RAM into a machine. That's just silly. Running Minecraft in the emulated CPU takes less RAM than that for crying out loud! Seriously!");
			mem.mapRegion(addr, new ByteArrayRegion(module.getBacking(), true, OCARM.instance.getRAMLatency(cpuTier, module.tier), OCARM.instance.isCPUWide(cpuTier) && OCARM.instance.isRAMWide(module.tier)));
			addr += module.byteSize;
		}
	}
	
	/**
	 * If the ROM image in question begins with a Lua block comment, give the length of the "open".
	 * @param rom
	 * @return
	 */
	protected int getArchSafetyOffset(byte[] rom) {
		if(rom.length < 3) return 0;
		else if(rom[0] == '-' && rom[1] == '-' && rom[2] == '[') {
			int i = 3;
			while(i < rom.length) {
				if(rom[i] == '[') break;
				else if(rom[i] != '=') return 0; // not a valid block comment
				++i;
			}
			if(i < rom.length) return i+1;
		}
		return 0;
	}

	protected void attemptShadowEEPROM() throws EscapeRetryException {
		Map<String,String> components = machine.components();
		String addr = null;
		for(Map.Entry<String, String> entry : components.entrySet()) {
			if(entry.getValue().equals("eeprom")) {
				addr = entry.getKey();
				break;
			}
		}
		if(addr == null) {
			romArray = null;
			romMappingValid = true;
			// This will only happen if no ROM has been mapped yet (which means nothing to try to access SRAM) or the ROM is removed after the machine
			// starts up (which means the SRAM array has already been mapped and is valid)
			// sramArray = null;
			// sramMappingValid = true;
			return;
		}
        if(!romMappingValid) {
            try {
            	Object[] result = machine.invoke(addr, "get", new Object[0]);
                if(result == null || result.length < 1 || !(result[0] instanceof byte[])) {
                	OCARM.logger.error("Got an invalid result from eeprom.get");
                	romArray = null;
                }
                else {
                	romArray = (byte[])result[0];
                	romArchSafeOffset = getArchSafetyOffset(romArray);
                }
            }
            catch(NullPointerException e) { blockRemTick = true; throw new EscapeRetryException(); }
            catch(LimitReachedException e) { blockRemTick = true; throw new EscapeRetryException(); }
            catch(Exception e) {
            	OCARM.logger.error("Exception while invoking eeprom.get", e);
            	romArray = null;
            }
            romMappingValid = true;
            int romBitDepth;
            if(romArray != null) {
            	romBitDepth = Integer.numberOfTrailingZeros(Integer.highestOneBit(romArray.length-romArchSafeOffset))+1;
                if(romBitDepth < ROM_MIN_BIT_DEPTH) romBitDepth = ROM_MIN_BIT_DEPTH;
            }
            else romBitDepth = ROM_MIN_BIT_DEPTH;
            romArrayMask = (1<<romBitDepth)-1;
        }
        if(!sramMappingValid) {
        	int nvramDataSize = -1;
            try {
            	Object[] result = machine.invoke(addr, "getDataSize", new Object[0]);
                if(result == null || result.length < 1 || !(result[0] instanceof Integer)) {
                	OCARM.logger.error("Got an invalid result from eeprom.getDataSize");
            		/* try again later? */
            		throw new EscapeRetryException();
                }
                nvramDataSize = ((Integer)result[0]).intValue();
            }
            catch(NullPointerException e) { blockRemTick = true; throw new EscapeRetryException(); }
            catch(LimitReachedException e) { blockRemTick = true; throw new EscapeRetryException(); }
            catch(Exception e) {
            	OCARM.logger.error("Exception while invoking eeprom.getDataSize", e);
        		/* try again later? */
        		throw new EscapeRetryException();
            }
            if(nvramDataSize >= 0) {
            	try {
            		Object[] result = machine.invoke(addr, "getData", new Object[0]);
            		if(result == null || result.length < 1 || !(result[0] instanceof byte[])) {
            			OCARM.logger.error("Got an invalid result from eeprom.getData, assuming empty array");
            			sramArray = new byte[nvramDataSize];
            		}
            		else {
            			byte[] newArray = (byte[])result[0];
            			sramArray = Arrays.copyOf(newArray, nvramDataSize);
            		}
                	sramMappingValid = true;
            	}
                catch(NullPointerException e) { blockRemTick = true; throw new EscapeRetryException(); }
            	catch(LimitReachedException e) { blockRemTick = true; throw new EscapeRetryException(); }
            	catch(Exception e) {
            		OCARM.logger.error("Exception while invoking eeprom.getData", e);
            		/* try again later? */
            		throw new EscapeRetryException();
            	}
            }
        }
    }
	
	protected void mapEEPROM() {
		mem.mapRegion(0xC0000000, new ROMRegion(OCARM.instance.getROMLatency(cpuTier), OCARM.instance.isCPUWide(cpuTier) && OCARM.instance.isROMWide()));
		mem.mapRegion(0x80000000, new SRAMRegion(OCARM.instance.getSRAMLatency(cpuTier), OCARM.instance.isCPUWide(cpuTier) && OCARM.instance.isSRAMWide()));
	}

	protected void remapMemory() {
		assert(cpu != null);
		synchronized(this) {
			mem = cpu.getMemorySpace();
			mem.unmapAllRegions();
			mapRAM();
			mapEEPROM();
		}
	}
	
	/* overridden by JARMROMDevArchitecture to return true if the appropriate config setting is set */
	protected boolean shouldDumpCore() {
		return false;
	}

	public JARMArchitecture(Machine machine) { this.machine = machine; }

	private short swapShort(boolean bigEndian, int v) {
		if(bigEndian) return (short)v;
		else return Short.reverseBytes((short)v);
	}
	private int swapInt(boolean bigEndian, int v) {
		if(bigEndian) return v;
		else return Integer.reverseBytes(v);
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
	private static final byte[] CORE_NAME = new byte[]{'C','O','R','E',0,0,0,0};
	private void dumpCore(OutputStream out, byte abi, byte abiVersion) throws IOException {
		boolean E = cpu.isBigEndian();
		ByteBuffer noteBuf = ByteBuffer.allocate(168);
		WritableByteChannel channel = Channels.newChannel(out);
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
		noteBuf.putShort((short)0); // (padding)
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
		for(int n = 0; n < 16; ++n) {
			noteBuf.putInt(swapInt(E, cpu.readRegister(n)));
		}
		noteBuf.putInt(swapInt(E, 0)); // ???
		noteBuf.putInt(swapInt(E, cpu.readCPSR())); // CPSR
		noteBuf.putInt(swapInt(E, 0)); // pr_fpvalid = 0
		noteBuf.flip();
		ByteBuffer buf = ByteBuffer.allocate(36);
		/* Elf32_Ehdr */
		/* magic; ELFCLASS32 (1), ELFDATA2LSB/ELFDATA2MSB (1/2) depending on endianness
		 * ELF_VERSION (1), caller-provided ABI and version, and padding to 16
		 */
		out.write(new byte[]{0x7f, 'E', 'L', 'F', 1, (byte)(E?2:1), 1, abi,
				abiVersion, 0, 0, 0, 0, 0, 0, 0});
		buf.clear();
		buf.putShort(swapShort(E, 4)); // e_type = ET_CORE
		buf.putShort(swapShort(E, 40)); // e_machine = EM_ARM
		buf.putInt(swapInt(E, 1)); // e_version = 1
		buf.putInt(swapInt(E, cpu.readPC()-4)); // e_entry = currently executing instruction (ish)
		buf.putInt(swapInt(E, 52)); // e_phoff = immediately after Ehdr
		buf.putInt(swapInt(E, 0)); // e_shoff = 0
		buf.putInt(swapInt(E, (E?0x00800000:0x00000000) | 0x0400)); // e_flags = EF_ARM_BE8/0 | EF_ARM_VFP_FLOAT
		buf.putShort(swapShort(E, 52)); // e_ehsize = 52
		buf.putShort(swapShort(E, 32)); // e_phentsize = 32
		// one PT_NOTE, one PT_LOAD for ROM, one PT_LOAD for SRAM, one PT_LOAD for each RAM module
		buf.putShort(swapShort(E, 3 + ramModules.length)); // e_phnum as above
		// e_shentsize/shnum/shstrndx = 0
		buf.putShort(swapShort(E, 0));
		buf.putShort(swapShort(E, 0));
		buf.putShort(swapShort(E, 0)); // SHN_UNDEF
		buf.flip();
		channel.write(buf);
		/* write Elf32_Phdrs */
		int offset = 52 + 32 * (3 + ramModules.length);
		offset = putNoteHeader(buf, E, offset, noteBuf.remaining());
		channel.write(buf);
		offset = putLoadHeader(buf, E, offset, 0xFFFF0000, romArrayMask+1, 5/*PF_X|PF_R*/);
		channel.write(buf);
		offset = putLoadHeader(buf, E, offset, 0x80000000, sramArray.length, 7/*PF_X|PF_W|PF_R*/);
		channel.write(buf);
		int mappingOffset = 0;
		for(RAMModule module : ramModules) {
			offset = offset + putLoadHeader(buf, E, offset, mappingOffset, module.byteSize, 7/*PF_X|PF_W|PF_R*/);
			channel.write(buf);
			mappingOffset += module.byteSize;
		}
		/* write data */
		channel.write(noteBuf);
		byte[] romData = Arrays.copyOfRange(romArray, romArchSafeOffset, romArchSafeOffset+romArrayMask+1);
		out.write(romData);
		out.write(sramArray);
		for(RAMModule module : ramModules) {
			out.write(module.backing);
		}
		channel.close();
	}
	private void dumpCore(OutputStream out) throws IOException {
		// ELFOSABI_ARM, 0
		dumpCore(out, (byte)97, (byte)0);
	}
    public int mapValue(Value value) {
        Integer wat = valueToHandle.get(value);
        if(wat != null)
                throw new RuntimeException("Same Value mapped more than once");
        else {
                if(handleToValue.size() >= MAX_VALUES) throw new RuntimeException("Value limit exceeded");                                                                     
                while(nextValueHandle == 0 || handleToValue.get(nextValueHandle) != null) ++nextValueHandle;
                valueToHandle.put(value, nextValueHandle);
                handleToValue.put(nextValueHandle, value);
                System.err.println("Mapped value: "+nextValueHandle);
                return nextValueHandle++;
        }
    }
    public Value getValue(int handle) {
    	return handleToValue.get(handle);
    }
    public void disposeValue(int handle, Context ctx) {
    	Value v = handleToValue.get(handle);
    	if(v != null) {
    		OCARM.logger.debug("Disposed value: "+handle);
    		v.dispose(ctx);
    		handleToValue.remove(handle);
    		valueToHandle.remove(v);
    	}
    	else OCARM.logger.debug("NONEXISTENT VALUE DISPOSED: "+handle);
    }
    public void disposeAllValues(Context ctx) {
    	System.err.println("Disposed all values");
    	for(Value v : valueToHandle.keySet()) {
    		v.dispose(ctx);
    	}
    	valueToHandle.clear();
    	handleToValue.clear();
    }
}
