package name.bizna.ocarmsim;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

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
		System.err.println("  -trace");
		System.err.println("    Trace all component invocations.");
		System.err.println("  -gdb port");
		System.err.println("    Specifies the port where the gdbserver should listen.");
		System.err.println("  -gdbverbose");
		System.err.println("    Print all gdb packets that are sent or received.");
		System.err.println("  -addrinfocmd \"command to execute\"");
		System.err.println("    Specifies an external command to use to map instruction addresses to useful information. The command should read hexadecimal addresses one line at a time, and output exactly one line of information for each line of input. (e.g. -addrinfocmd \"arm-none-eabi-addr2line -spfe path/to/unstripped_binary.elf\")");
		System.err.println("    The parser that processes the command string is very simple. If you want complex argument escaping, consider making a shell script and executing that.");
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
		final StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<hardwareDefinition>\n");
		final StringBuilder devicesSection = new StringBuilder();

		if (args.length == 0) {
			usage();
			System.exit(1);
		}
		boolean commandLineValid = true;
		boolean romGiven = false;
		String addrinfocmd = null;
		int gdbPort = 0;
		boolean gdbVerbose = false;
		int i = 0;
		while (i < args.length) {
			String arg = args[i++];
			switch (arg) {
				case "-rom":
					if (i >= args.length) {
						System.err.println("No parameter given to -rom");
						commandLineValid = false;
					} else {
						xml.append("\t<rom>").append(args[i++]).append("</rom>\n");
						romGiven = true;
					}
					break;
				case "-sramro":
				case "-sramrw":
					if (i >= args.length) {
						System.err.println("No parameter given to -sram*");
						commandLineValid = false;
					} else {
						xml.append("\t<sram>").append(args[i++]).append("</sram>\n");
						xml.append("\t<sramRO>").append(arg.endsWith("ro")).append("</sramRO>");
					}
					break;
				case "-memory":
					if (i >= args.length) {
						System.err.println("No parameter given to -memory");
						commandLineValid = false;
					} else {
						int q = Integer.parseInt(args[i++]);
						if (q < 0 || q > 2097152) {
							System.err.println("Out-of-range parameter for -memory");
							commandLineValid = false;
						} else {
							xml.append("\t<memory>").append(q).append("</memory>\n");
						}
					}
					break;
				case "-screen":
					if (i >= args.length) {
						System.err.println("No parameter given to -screen");
						commandLineValid = false;
					} else {
						int q = Integer.parseInt(args[i++]);
						if (q < 0 || q > 3) {
							System.err.println("Out-of-range parameter for -screen");
							commandLineValid = false;
						} else {
							switch (q) {
								case 1:
									devicesSection.append("\t\t<screen width=\"50\" height=\"16\" bits=\"1\" />\n");
									break;
								case 2:
									devicesSection.append("\t\t<screen width=\"80\" height=\"25\" bits=\"4\" />\n");
									break;
								case 3:
									devicesSection.append("\t\t<screen width=\"160\" height=\"50\" bits=\"8\" />\n");
									break;
							}
							devicesSection.append("\t\t<gpu />\n");
							devicesSection.append("\t\t<keyboard />\n");
						}
					}
					break;
				case "-sram-size":
					if (i >= args.length) {
						System.err.println("No parameter given to -sram-size");
						commandLineValid = false;
					} else {
						int q = Integer.parseInt(args[i++]);
						if (q < 0 || q > 1073741824) {
							System.err.println("Out-of-range parameter for -sram-size");
							commandLineValid = false;
						} else {
							xml.append("\t<sramSize>").append(q).append("</sramSize>\n");
						}
					}
					break;
				/* TODO: -fs* -drive* */
				case "-fsro":
				case "-fsrw":
					if (i >= args.length) {
						System.err.println("No parameter given to -fs*");
						commandLineValid = false;
					} else {
						File basepath = new File(args[i++]);
						if (!basepath.exists() || !basepath.isDirectory()) {
							System.err.println("Argument to " + arg + " must be a directory, and must already exist.");
							commandLineValid = false;
						} else {
							devicesSection.append("\t\t<fs basepath=\"").append(basepath).append("\" writable=\"").append(arg.equals("-fsrw")).append("\" />\n");
						}
					}
					break;
				case "-trace":
					OCARM.instance.setTraceInvocations(true);
					break;
				case "-addrinfocmd":
					if (i >= args.length) {
						System.err.println("No parameter given to -addrinfocmd");
						commandLineValid = false;
					} else {
						addrinfocmd = args[i++];
					}
					break;
				case "-gdb":
					if (i >= args.length) {
						System.err.println("No parameter given to -gdb");
						commandLineValid = false;
					} else {
						gdbPort = Integer.parseInt(args[i++]);
					}
					break;
				case "-gdbverbose":
					gdbVerbose = true;
					break;
				default:
					System.err.println("Unknown command line argument: " + arg);
					commandLineValid = false;
					break;
			}
		}
		if (!romGiven) {
			System.err.println("No EEPROM image given.");
			commandLineValid = false;
		}
		if (!commandLineValid) {
			usage();
			System.exit(1);
		}

		xml.append("\t<devices>\n");
		xml.append(devicesSection);
		xml.append("\t</devices>\n");
		xml.append("</hardwareDefinition>");

		System.out.println("Generated hardware definition:");
		System.out.println(xml.toString());
		
		Launcher.launch(new StringReader(xml.toString()), addrinfocmd, gdbPort, gdbVerbose);
	}
}
