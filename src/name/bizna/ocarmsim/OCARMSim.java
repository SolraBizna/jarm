package name.bizna.ocarmsim;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import name.bizna.jarm.ByteArrayRegion;
import name.bizna.jarm.CPU;
import name.bizna.jarm.PhysicalMemorySpace;

public class OCARMSim {
	public static void usage() {
		System.err.println("Usage: ocarmsim [options]");
		System.err.println("At the very least, an EEPROM image must be specified.");
		System.err.println("Options:");
		System.err.println("  -rom path/to/rom");
		System.err.println("    Specify the EEPROM image to use.");
		System.err.println("  -sramro path/to/sram");
		System.err.println("  -sramrw path/to/sram");
		System.err.println("    Specify the SRAM image to use, if any. -sramro does not save changes, -sramrw does. It is not an error if -sramrw's target does not exist.");
		System.err.println("  -sram-size 256");
		System.err.println("    The size of SRAM to use, in bytes. The default is to use 256 bytes UNLESS an SRAM image is specified, in which case the size of that image is rounded up to the nearest power of two and used. If this option is specified, it is always rounded up to the nearest power of two and used.");
		System.err.println("  -screen 0-3");
		System.err.println("    Specify the tier of screen and GPU to use. 0 means none. Default is 1.");
		System.err.println("  -memory 192");
		System.err.println("    Specifies how much RAM to have, in kibibytes. 0 is a valid option. Max is 2097152 (2GiB). Default is 192.");
		System.err.println("  -fsro path/to/filesystem/base");
		System.err.println("  -fsrw path/to/filesystem/base");
		System.err.println("    Adds a normal filesystem at the specified path. -fsro adds a read-only filesystem, -fsrw adds a writable one.");
		System.err.println("  -addrinfocmd \"command to execute\"");
		System.err.println("    Specifies an external command to use to map instruction addresses to useful information. The command should read hexadecimal addresses one line at a time, and output exactly one line of information for each line of input. (e.g. -addrinfocmd \"arm-none-eabi-addr2line -spfe path/to/unstripped_binary.elf\")");
		System.err.println("    The parser that processes the command string is very simple. If you want complex argument escaping, consider making a shell script and executing that.");
		/*
		 * TODO: analyze drive's error behavior and write this code
		 */
		/*
		System.err.println("  -drivero path/to/drive/image");
		System.err.println("  -driverw path/to/drive/image");
		System.err.println("    Adds an unmanaged drive. The specified path should hold a raw image. -drivero adds a read-only drive, -driverw adds a writable one.");
		*/
	}
	private static class FSSpec {
		File basepath;
		boolean writable;
		FSSpec(File basepath, boolean writable) {
			this.basepath = basepath;
			this.writable = writable;
		}
	}
	public static void main(String[] args) throws IOException {
		if(args.length == 0) {
			usage();
			System.exit(1);
		}
		boolean command_line_valid = true;
		int ram_quantity = 192;
		int sram_quantity = -256;
		String eeprom_image_path = null;
		String sram_image_path = null;
		String addrinfocmd = null;
		ArrayList<FSSpec> filesystems = new ArrayList<FSSpec>();
		boolean sram_writable = false;
		int screen_tier = 1;
		int i = 0;
		while(i < args.length) {
			String arg = args[i++];
			if(arg.equals("-rom")) {
				if(i >= args.length) {
					System.err.println("No parameter given to -rom");
					command_line_valid = false;
				}
				else eeprom_image_path = args[i++];
			}
			else if(arg.equals("-sramro") || arg.equals("-sramrw")) {
				if(i >= args.length) {
					System.err.println("No parameter given to -sram*");
					command_line_valid = false;
				}
				else {
					sram_image_path = args[i++];
					sram_writable = arg.endsWith("rw");
				}
			}
			else if(arg.equals("-memory")) {
				if(i >= args.length) {
					System.err.println("No parameter given to -memory");
					command_line_valid = false;
				}
				else {
					int q = Integer.parseInt(args[i++]);
					if(q < 0 || q > 2097152) {
						System.err.println("Out-of-range parameter for -memory");
						command_line_valid = false;
					}
					else ram_quantity = q;
				}
			}
			else if(arg.equals("-screen")) {
				if(i >= args.length) {
					System.err.println("No parameter given to -screen");
					command_line_valid = false;
				}
				else {
					int q = Integer.parseInt(args[i++]);
					if(q < 0 || q > 3) {
						System.err.println("Out-of-range parameter for -screen");
						command_line_valid = false;
					}
					else screen_tier = q;
				}
			}
			else if(arg.equals("-sram-size")) {
				if(i >= args.length) {
					System.err.println("No parameter given to -sram-size");
					command_line_valid = false;
				}
				else {
					int q = Integer.parseInt(args[i++]);
					if(q < 0 || q > 1073741824) {
						System.err.println("Out-of-range parameter for -sram-size");
						command_line_valid = false;
					}
					else sram_quantity = q;
				}
			}
			else if(arg.equals("-fsro") || arg.equals("-fsrw")) {
				if(i >= args.length) {
					System.err.println("No parameter given to -fs*");
					command_line_valid = false;
				}
				else {
					File basepath = new File(args[i++]);
					if(!basepath.exists() || !basepath.isDirectory()) {
						System.err.println("Argument to "+arg+" must be a directory, and must already exist.");
						command_line_valid = false;
					}
					else
						filesystems.add(new FSSpec(basepath, arg.equals("-fsrw")));
				}
			}
			else if(arg.equals("-addrinfocmd")) {
				if(i >= args.length) {
					System.err.println("No parameter given to -addrinfocmd");
					command_line_valid = false;
				}
				else {
					addrinfocmd = args[i++];
				}
			}
			else {
				System.err.println("Unknown command line argument: " + arg);
				command_line_valid = false;
			}
			/* TODO: -fs* -drive* */
		}
		if(eeprom_image_path == null) {
			System.err.println("No EEPROM image given.");
			command_line_valid = false;
		}
		if(!command_line_valid) {
			usage();
			System.exit(1);
		}
		CPU cpu = new CPU();
		FakeMachine machine = new FakeMachine();
		JARMArchitecture arch = new JARMArchitecture();
		CP3 cp3 = new CP3(cpu, machine, arch);
		cpu.mapCoprocessor(3, cp3);
		cpu.mapCoprocessor(7, new CP7(cpu));
		PhysicalMemorySpace mem = cpu.getMemorySpace();
		ByteArrayRegion[] rams = new ByteArrayRegion[2];
		if(ram_quantity > 1048576) {
			/* we need to use two modules, since no mapping can exceed 1GiB */
			int upper_quantity = ram_quantity - 1048576;
			mem.mapRegion(0x40000000, rams[1] = new ByteArrayRegion(upper_quantity * 1024));
			arch.setModule2Size(upper_quantity * 1024);
			ram_quantity = 1048576;
		}
		if(ram_quantity > 0) {
			mem.mapRegion(0x00000000, rams[0] = new ByteArrayRegion(ram_quantity * 1024));
			arch.setModule1Size(ram_quantity * 1024);
		}
		ROMRegion rom = new ROMRegion(new File(eeprom_image_path));
		mem.mapRegion(0xC0000000, rom);
		SRAMRegion sram = new SRAMRegion(sram_image_path == null ? null : new File(sram_image_path), sram_quantity, sram_writable);
		machine.addNode(new SimEEPROM(rom, sram));
		arch.setModule0Size((int)sram.getRegionSize());
		arch.setSRAMRegion(sram);
		mem.mapRegion(0x80000000, sram);
		for(FSSpec spec : filesystems) {
			machine.addNode(new SimFilesystem(spec.basepath, spec.writable));
		}
		new SimUI(screen_tier, machine, cpu, cp3, rom, sram, rams, addrinfocmd);
	}
}
