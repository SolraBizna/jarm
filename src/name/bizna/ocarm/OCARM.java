package name.bizna.ocarm;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import li.cil.oc.api.Machine;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;
import net.minecraftforge.fml.relauncher.Side;

@Mod(name = OCARM.NAME, modid = OCARM.MODID, version = OCARM.VERSION)
public class OCARM {
    public static final String MODID = "OpenComputers-ARM";
    public static final String NAME = "OpenComputers ARM Architecture";
    public static final String VERSION = "0.0a3";

    /* TODO: move to CP3? */
    /* Type tags for interchange */
    public static final short ICTAG_STRING = (short)0;
    public static final short ICTAG_BYTE_ARRAY = (short)0x4000;
    public static final short ICTAG_VALUE = (short)-9;
    public static final short ICTAG_UUID = (short)-8;
    public static final short ICTAG_COMPOUND = (short)-7;
    public static final short ICTAG_ARRAY = (short)-6;
    public static final short ICTAG_INT = (short)-5;
    public static final short ICTAG_DOUBLE = (short)-4;
    public static final short ICTAG_BOOLEAN = (short)-3;
    public static final short ICTAG_NULL = (short)-2;
    public static final short ICTAG_END = (short)-1;
    /* Maximum interchange string length, in bytes */
    public static final short MAX_STRING_LENGTH = 16383;
    public static final short MAX_BYTE_ARRAY_LENGTH = 16383;
    /* Errors for interchange */
    public static final short INVOKE_SUCCESS = 0;
    public static final short INVOKE_UNKNOWN_ERROR = 1;
    public static final short INVOKE_LIMIT_REACHED = 2;
    public static final short INVOKE_UNKNOWN_RECEIVER = 3;
    public static final short INVOKE_INDIRECT_REQUIRED = 4;
    public static final short INVOKE_UNKNOWN_METHOD = 5;
    
    public static final String CURRENT_CONFIG_VERSION = "0.0a3 (will be wiped in next release)";

    public static final int defaultRamSizes[] = new int[]{192, 256, 384, 512, 768, 1024};
    public static final int defaultCpuCyclesPerTick[] = new int[]{1000, 5000, 25000};
    public static final int defaultRamTier1Latency[] = new int[]{1, 5, 25};
    public static final int defaultRamTier2Latency[] = new int[]{1, 1, 5};
    public static final int defaultRamTier3Latency[] = new int[]{1, 1, 1};
    public static final int defaultRomLatency[] = new int[]{2, 10, 50};
    public static final boolean defaultAllowRAMlessComputers = true;
    public static final boolean defaultAllowROMDevArchitecture = false;
    public static final String defaultROMDevCoreDumpFile = "";
    public static final boolean defaultTraceInvocations = false;
    public static final boolean defaultRandomInitialRAM = false;
    public static final boolean defaultAllowSerialDebugCP = false;
    public static final boolean defaultWideRom = false, defaultWideCpu[] = new boolean[]{false,true,true}, defaultWideRam[] = new boolean[]{false,true,true};

    public static Logger logger = LogManager.getFormatterLogger("MCJARM");

    private int[] ramSizes;
    private int[] cpuCyclesPerTick;
    private int[] ramTier1Latency, ramTier2Latency, ramTier3Latency, romLatency;
    private boolean allowRAMlessComputers, allowROMDevArchitecture;
    private boolean randomInitialRAM, allowSerialDebugCP;
    private boolean wideRom, wideCpu[], wideRam[];
	private boolean traceInvocations;
	private String romDevCoreDumpFile;

    @Instance(value=OCARM.MODID)
    public static OCARM instance;

    boolean shouldAllowRAMlessComputers() {
    	return allowRAMlessComputers;
    }
    
    boolean shouldAllowDebugCoprocessor() {
    	return allowSerialDebugCP;
    }

    int getCPUCyclesPerTick(int tier) {
    	return cpuCyclesPerTick[tier];
    }

    int getRAMLatency(int cpuTier, int ramTier) {
    	switch(ramTier) {
    	case 0: return ramTier1Latency[cpuTier];
    	case 1: return ramTier2Latency[cpuTier];
    	case 2: return ramTier3Latency[cpuTier];
    	}
    	throw new ArrayIndexOutOfBoundsException();
    }
    
    int getROMLatency(int cpu_tier) {
    	return romLatency[cpu_tier];
    }
    
    int getSRAMLatency(int cpu_tier) {
    	return romLatency[cpu_tier];
    }
    
    boolean isROMWide() { return wideRom; }
    boolean isSRAMWide() { return wideRom; }
    boolean isCPUWide(int tier) { return wideCpu[tier]; }
    boolean isRAMWide(int tier) { return wideRam[tier]; }
    boolean shouldTraceInvocations() { return traceInvocations; }
    boolean shouldDumpCore() { return romDevCoreDumpFile.length() > 0; }
    String coreDumpFile() { return romDevCoreDumpFile; }

    /**
     * Get a memory module size, in bytes, from a memory module tier.
     * Try to handle non-integer, non-standard tiers in a sane manner.
     * @param tier The return value of memory.amount
     * @return A number of bytes.
     */
    double getMemorySize(double tier) {
    	if(tier <= 0.0) return 0;
    	else {
    		double floored = Math.floor(tier);
			double frac = tier-floored;
    		if(floored == tier && floored <= 6) {
    			/* Exact match. Exact return. */
    			return ramSizes[(int)floored-1] * 1024;
    		}
    		else if(tier < 1.0) {
    			/* Less than one. Fraction of tier 1 RAM size. */
    			return Math.round(ramSizes[0] * 1024 * tier);
    		}
    		else if(tier < 5) {
    			/* Between existing tiers. Interpolate between adjacent RAM sizes. */
    			int base = (int)floored-1;
    			return Math.round(ramSizes[base] + (ramSizes[base+1]-ramSizes[base]) * frac);
    		}
    		else {
    			/* Above highest tier. Each "astral tier" is twice as big as the tier before it.
    			   Linearly interpolate as if such sizes were specified indefinitely. */
    			floored -= 6;
    			return Math.round(Math.pow(2, floored) * (1.0 + frac) * ramSizes[5]);
    		}
    	}
    }
    
    private void readConfig(FMLPreInitializationEvent event) {
    	Configuration cfg = new Configuration(event.getSuggestedConfigurationFile(), CURRENT_CONFIG_VERSION);
    	// implied
    	//cfg.load();
    	if(cfg.getLoadedConfigVersion() == null || !cfg.getLoadedConfigVersion().equals(CURRENT_CONFIG_VERSION)) {
    		/* wipe it */
    		event.getSuggestedConfigurationFile().delete();
    		cfg = new Configuration(event.getSuggestedConfigurationFile(), CURRENT_CONFIG_VERSION);
    	}
    	Property prop;
    	/* Isn't it great that Java doesn't have macros? */
    	/* RAM sizes */
    	prop = cfg.get(Configuration.CATEGORY_GENERAL, "ramSizes", defaultRamSizes);
    	ramSizes = prop.getIntList();
    	if(ramSizes == null || ramSizes.length < 6) {
    		ramSizes = defaultRamSizes;
    		prop.set(defaultRamSizes);
    	}
    	prop.setComment("RAM module sizes in kibibytes, like the similar option in\nOpenComputers.cfg. Default values are 192, 256, 384, 512, 768, 1024,\nsame as for the Lua architecture.");
    	/* CPU speeds */
    	prop = cfg.get(Configuration.CATEGORY_GENERAL, "cpuCyclesPerTick", defaultCpuCyclesPerTick);
    	cpuCyclesPerTick = prop.getIntList();
    	if(cpuCyclesPerTick == null || cpuCyclesPerTick.length < 3) {
    		cpuCyclesPerTick = defaultCpuCyclesPerTick;
    		prop.set(defaultCpuCyclesPerTick);
    	}
    	prop.setComment("CPU cycles per Minecraft tick.\nDefault values are 1000, 5000, 25000 (20KHz/100KHz/500KHz)");
    	/* RAM latencies */
    	prop = cfg.get(Configuration.CATEGORY_GENERAL, "ramTier1Latency", defaultRamTier1Latency);
    	ramTier1Latency = prop.getIntList();
    	if(ramTier1Latency == null || ramTier1Latency.length < 3) {
    		ramTier1Latency = defaultRamTier1Latency;
    		prop.set(defaultRamTier1Latency);
    	}
    	prop.setComment("Iron-tier RAM access latencies for each CPU tier, in cycles.\nDefault values are 1, 5, and 25.");
    	prop = cfg.get(Configuration.CATEGORY_GENERAL, "ramTier2Latency", defaultRamTier2Latency);
    	ramTier2Latency = prop.getIntList();
    	if(ramTier2Latency == null || ramTier2Latency.length < 3) {
    		ramTier2Latency = defaultRamTier1Latency;
    		prop.set(defaultRamTier2Latency);
    	}
    	prop.setComment("Gold-tier RAM access latencies for each CPU tier, in cycles.\nDefault values are 1, 1, and 5.");
    	prop = cfg.get(Configuration.CATEGORY_GENERAL, "ramTier3Latency", defaultRamTier3Latency);
    	ramTier3Latency = prop.getIntList();
    	if(ramTier3Latency == null || ramTier3Latency.length < 3) {
    		ramTier3Latency = defaultRamTier3Latency;
    		prop.set(defaultRamTier3Latency);
    	}
    	prop.setComment("Diamond-tier RAM access latencies for each CPU tier, in cycles.\nDefault values are 1, 1, and 1.");
    	/* ROM latency */
    	prop = cfg.get(Configuration.CATEGORY_GENERAL, "romLatency", defaultRomLatency);
    	romLatency = prop.getIntList();
    	if(romLatency == null || romLatency.length < 3) {
    		romLatency = defaultRomLatency;
    		prop.set(defaultRomLatency);
    	}
    	prop.setComment("ROM/SRAM access latencies for each CPU tier, in cycles.\nDefault values are 2, 10, and 50, simulating ~10KHz/~100us ROM.");
    	/* RAMless computers */
    	prop = cfg.get(Configuration.CATEGORY_GENERAL, "allowRAMlessComputers", defaultAllowRAMlessComputers);
    	allowRAMlessComputers = prop.getBoolean(defaultAllowRAMlessComputers);
    	prop.set(allowRAMlessComputers);
    	prop.setComment("Whether to allow booting of computers with no RAM installed.\nUnlike with Lua computers, ARM computers can *technically* get some stuff\ndone even with no RAM...");
    	/* ROMDev */
    	prop = cfg.get(Configuration.CATEGORY_GENERAL, "allowROMDevArchitecture", defaultAllowROMDevArchitecture);
    	allowROMDevArchitecture = prop.getBoolean(defaultAllowROMDevArchitecture);
    	prop.set(allowROMDevArchitecture);
    	prop.setComment("Whether an additional architecture will be enabled, which dumps CPU state\nand errors out whenever an exception occurs. This can be useful when\ndeveloping ROMs, and shouldn't be used otherwise.");
    	/* ROMDev core dumps */
    	prop = cfg.get(Configuration.CATEGORY_GENERAL, "romDevCoreDumpFile", defaultROMDevCoreDumpFile);
    	romDevCoreDumpFile = prop.getString();
    	prop.set(romDevCoreDumpFile);
    	prop.setComment("If non-empty, the ROMDev architecture will make a core dump on exceptions\ncontaining the complete memory and register state of the CPU when an\nexception occurs, suitable for examination in GDB.\nNote that core dumps assumes some things that aren't always true:\n  * Virtual memory not in use\n  * ROM mapped at 0xFFFF0000 (_mostly_ accurate)\n  * FPU not in use\n  * ROM mapping valid (EEPROM present)");
    	/* Random initial RAM */
    	prop = cfg.get(Configuration.CATEGORY_GENERAL, "randomInitialRAM", defaultRandomInitialRAM);
    	randomInitialRAM = prop.getBoolean(defaultRandomInitialRAM);
    	prop.set(randomInitialRAM);
    	prop.setComment("Whether to randomize RAM on bootup");
    	/* Trace invocations */
    	prop = cfg.get(Configuration.CATEGORY_GENERAL, "traceInvocations", defaultTraceInvocations);
    	traceInvocations = prop.getBoolean(defaultTraceInvocations);
    	prop.set(traceInvocations);
    	prop.setComment("Whether to log every invocation and sleep");
    	/* Debug coprocessor */
    	prop = cfg.get(Configuration.CATEGORY_GENERAL, "allowSerialDebugCP", defaultAllowSerialDebugCP);
    	allowSerialDebugCP = prop.getBoolean(defaultAllowSerialDebugCP);
    	prop.set(allowSerialDebugCP);
    	prop.setComment("Whether to enable coprocessor #7, allowing writing to the game log.\nONLY enable this if you are developing a ROM and are facing a problem you\ncan't debug another way!");
    	/* Wide ROM */
    	prop = cfg.get(Configuration.CATEGORY_GENERAL, "wideRom", defaultWideRom);
    	wideRom = prop.getBoolean(defaultWideRom);
    	prop.set(wideRom);
    	prop.setComment("Whether ROM/SRAM support 32-bit access.");
    	/* Wide CPUs */
    	prop = cfg.get(Configuration.CATEGORY_GENERAL, "wideCpu", defaultWideCpu);
    	wideCpu = prop.getBooleanList();
    	if(wideCpu == null || wideCpu.length < 3) {
    		wideCpu = defaultWideCpu;
    		prop.set(wideCpu);
    	}
    	prop.setComment("Whether CPUs at each tier are capable of 32-bit access.\nFor 32-bit accesses to take place, both the CPU and the memory module\nbeing accessed must support 32-bit operation.");
    	/* Wide RAMs */
    	prop = cfg.get(Configuration.CATEGORY_GENERAL, "wideRam", defaultWideRam);
    	wideRam = prop.getBooleanList();
    	if(wideRam == null || wideRam.length < 3) {
    		wideRam = defaultWideRam;
    		prop.set(wideRam);
    	}
    	prop.setComment("Whether memory modules at each tier are capable of 32-bit access.");
    	/* done */
    	cfg.save();
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
    	instance = this;
    	readConfig(event);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        Machine.add(JARMArchitecture.class);
        if(allowROMDevArchitecture) Machine.add(JARMROMDevArchitecture.class);
    }
    
    @NetworkCheckHandler
    public boolean versionOkay(Map<String,String> mods, Side side) {
    	return true;
    }
    
    public static int padToWordLength(int i) {
    	return (i&3)!=0?(i&~3)+4:i;
    }
}
