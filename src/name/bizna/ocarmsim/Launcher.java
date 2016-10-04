package name.bizna.ocarmsim;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.xml.bind.JAXB;
import name.bizna.ocarmsim.gdb.GDBDebugger;
import name.bizna.ocarmsim.simpledebugger.SimpleDebugger;

/**
 *
 * @author Jean-Rémy Buchs <jrb0001@692b8c32.de>
 */
public class Launcher {

	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			usage();
			System.exit(1);
		}

		boolean command_line_valid = true;
		int gdbPort = 0;
		boolean gdbVerbose = false;
		String addrinfocmd = null;

		int i = 0;
		while (i < args.length - 1) {
			String arg = args[i++];
			if (arg.equals("-trace")) {
				OCARM.instance.setTraceInvocations(true);
			} else if (arg.equals("-gdb")) {
				if (i >= args.length) {
					System.err.println("No parameter given to -gdb");
					command_line_valid = false;
				} else {
					gdbPort = Integer.parseInt(args[i++]);
				}
			} else if (arg.equals("-gdbverbose")) {
				gdbVerbose = true;
			} else if (arg.equals("-addrinfocmd")) {
				if (i >= args.length) {
					System.err.println("No parameter given to -addrinfocmd");
					command_line_valid = false;
				} else {
					addrinfocmd = args[i++];
				}
			} else {
				System.err.println("Unknown command line argument: " + arg);
				command_line_valid = false;
			}
		}
		if (!command_line_valid) {
			usage();
			System.exit(1);
		}

		launch(new FileReader(new File(args[args.length - 1])), addrinfocmd, gdbPort, gdbVerbose);
	}

	public static void launch(Reader hardwareDefinition, String addrinfocmd, int gdbPort, boolean gdbVerbose) throws IOException {
		BasicDebugger debugger = gdbPort == 0 ? new SimpleDebugger(addrinfocmd) : new GDBDebugger(gdbPort, gdbVerbose);

		HardwareDefinition hardware = JAXB.unmarshal(hardwareDefinition, HardwareDefinition.class);
		List<JComponent> components = hardware.prepareSimulation(debugger);

		JFrame frame = new JFrame("ocarmsim");
		frame.setMinimumSize(new Dimension(100, 30));
		frame.setLayout(new GridLayout(2, components.size() / 2));
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		for (JComponent c : components) {
			frame.add(c);
		}

		if (debugger.getGUIComponent() != null) {
			frame.add(debugger.getGUIComponent());
		}
		frame.pack();
		frame.setVisible(true);

		debugger.reset();
		while (true) {
			debugger.run();
		}
	}

	private static void usage() {
		System.err.println("Usage: ocarmsim [options] hwdefinition");
		System.err.println("options:");
		System.err.println("  -trace");
		System.err.println("    Trace all component invocations.");
		System.err.println("  -gdb port");
		System.err.println("    Specifies the port where the gdbserver should listen.");
		System.err.println("  -gdbverbose");
		System.err.println("    Print all gdb packets that are sent or received.");
		System.err.println("  -addrinfocmd \"command to execute\"");
		System.err.println("    Specifies an external command to use to map instruction addresses to useful information. The command should read hexadecimal addresses one line at a time, and output exactly one line of information for each line of input. (e.g. -addrinfocmd \"arm-none-eabi-addr2line -spfe path/to/unstripped_binary.elf\")");
		System.err.println("    The parser that processes the command string is very simple. If you want complex argument escaping, consider making a shell script and executing that.");
		System.err.println("hwdefinition:");
		System.err.println("  Path to xml file with the hardware definition");
	}
}
