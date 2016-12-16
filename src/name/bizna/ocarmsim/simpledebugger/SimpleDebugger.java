package name.bizna.ocarmsim.simpledebugger;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import name.bizna.jarm.CPU;
import name.bizna.ocarmsim.BasicDebugger;
import name.bizna.ocarmsim.Breakpoint;
import name.bizna.ocarmsim.OCARM;

/**
 *
 * @author Jean-Rémy Buchs <jrb0001@692b8c32.de>
 */
public class SimpleDebugger extends BasicDebugger {

	private final String addrinfocmd;
	private final DebugPanel panel;
	private final ConcurrentLinkedQueue<Command> commands = new ConcurrentLinkedQueue<>();
	private CoredumpUtils coredumpUtils;
	private final AtomicReference<String> breakpointsString = new AtomicReference<>("");
	private final AtomicReference<String> readWatchpointsString = new AtomicReference<>("");
	private final AtomicReference<String> writeWatchpointsString = new AtomicReference<>("");
	private final AtomicLong minStack = new AtomicLong();

	public SimpleDebugger(String addrinfocmd) {
		this.addrinfocmd = addrinfocmd;
		panel = new DebugPanel();
	}

	@Override
	public void run() {
		if (coredumpUtils == null) {
			coredumpUtils = new CoredumpUtils(cpu);
		}

		super.run();

		processCommands();

		// Don't busy wait.
		synchronized (this) {
			while (getState() == State.PAUSED || getState() == State.CRASHED || getState() == State.FAILED) {
				try {
					wait();
					processCommands();
				} catch (InterruptedException ex) {
					Logger.getLogger(BasicDebugger.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
	}

	@Override
	public void onReadMemory(int addr, int size, boolean bigEndian) {
		super.onReadMemory(addr, size, bigEndian);
	}

	@Override
	public void onWriteMemory(int addr, int size, boolean bigEndian, long value) {
		super.onWriteMemory(addr, size, bigEndian, value);
	}

	@Override
	public void onInstruction(CPU cpu, int addr) {
		super.onInstruction(cpu, addr);

		if ((cpu.readSP() & 0xFFFFFFFFL) < minStack.get()) {
			pause();
		}
	}

	@Override
	public JComponent getGUIComponent() {
		return panel;
	}

	private void processCommands() {
		while (!commands.isEmpty()) {
			switch (commands.poll()) {
				case RESET:
					reset();
					break;
				case STEP:
					step();
					break;
				case RUN:
					go();
					break;
				case PAUSE:
					pause();
					break;
				case DUMP:
					JFileChooser coreFileChooser = new JFileChooser();
					if (coreFileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
						File f = coreFileChooser.getSelectedFile();
						try {
							FileOutputStream o = new FileOutputStream(f);
							coredumpUtils.dumpCore(o);
							o.close();
						} catch (IOException exception) {
							JOptionPane.showMessageDialog(null, "Error while dumping core: " + exception.toString(), null, JOptionPane.ERROR_MESSAGE);
						}
					}
					break;
				case UPDATE_BREAKPOINTS:
					for (Breakpoint point : getBreakpoints()) {
						removeBreakpoint(point);
					}

					if (!breakpointsString.get().isEmpty()) {
						String[] split = breakpointsString.get().split(" ");
						boolean erroneous = false;
						for (String splat : split) {
							try {
								long l = Long.parseLong(splat, 16);
								if (l < 0 || l > 0xFFFFFFFFL) {
									erroneous = true;
								}
								addBreakpoint(new Breakpoint(l, 4));
							} catch (NumberFormatException e) {
								erroneous = true;
							}
						}
						if (erroneous) {
							JOptionPane.showMessageDialog(null, "The breakpoints list must consist of hexadecimal addresses separated by spaces.\nFor example:\nFFFF0000 0000021C", null, JOptionPane.ERROR_MESSAGE);
						}
					}
					break;
				case UPDATE_READWATCHPOINTS:
					for (Breakpoint point : getReadWatchpoints()) {
						removeReadWatchpoint(point);
					}

					if (!readWatchpointsString.get().isEmpty()) {
						String[] split = readWatchpointsString.get().split(" ");
						boolean erroneous = false;
						for (String splat : split) {
							try {
								long l = Long.parseLong(splat, 16);
								if (l < 0 || l > 0xFFFFFFFFL) {
									erroneous = true;
								}
								addReadWatchpoint(new Breakpoint(l, 4));
							} catch (NumberFormatException e) {
								erroneous = true;
							}
						}
						if (erroneous) {
							JOptionPane.showMessageDialog(null, "The read watchpoints list must consist of hexadecimal addresses separated by spaces.\nFor example:\nFFFF0000 0000021C", null, JOptionPane.ERROR_MESSAGE);
						}
					}
					break;
				case UPDATE_WRITEWATCHPOINTS:
					for (Breakpoint point : getWriteWatchpoints()) {
						removeWriteWatchpoint(point);
					}

					if (!writeWatchpointsString.get().isEmpty()) {
						String[] split = writeWatchpointsString.get().split(" ");
						boolean erroneous = false;
						for (String splat : split) {
							try {
								long l = Long.parseLong(splat, 16);
								if (l < 0 || l > 0xFFFFFFFFL) {
									erroneous = true;
								}
								addWriteWatchpoint(new Breakpoint(l, 4));
							} catch (NumberFormatException e) {
								erroneous = true;
							}
						}
						if (erroneous) {
							JOptionPane.showMessageDialog(null, "The write watchpoints list must consist of hexadecimal addresses separated by spaces.\nFor example:\nFFFF0000 0000021C", null, JOptionPane.ERROR_MESSAGE);
						}
					}
					break;
			}
		}

		// Update state.
		panel.update();
	}

	private synchronized void pushCommand(Command command) {
		commands.add(command);
		notifyAll();
	}

	private class DebugPanel extends JPanel {

		private final CPUStatePanel cpuStatePanel;

		public DebugPanel() {
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

			// CPU state.
			cpuStatePanel = new CPUStatePanel(SimpleDebugger.this, addrinfocmd);
			add(cpuStatePanel);

			// Buttons.
			JPanel buttonPanel = new JPanel();
			add(buttonPanel);
			JButton resetButton = new JButton(new AbstractAction("Reset") {
				@Override
				public void actionPerformed(ActionEvent e) {
					pushCommand(Command.RESET);
				}
			});
			buttonPanel.add(resetButton);
			JButton stepButton = new JButton(new AbstractAction("Step") {
				@Override
				public void actionPerformed(ActionEvent e) {
					pushCommand(Command.STEP);
				}
			});
			buttonPanel.add(stepButton);
			JButton runButton = new JButton(new AbstractAction("Run") {
				@Override
				public void actionPerformed(ActionEvent e) {
					pushCommand(Command.RUN);
				}
			});
			buttonPanel.add(runButton);
			JButton pauseButton = new JButton(new AbstractAction("Pause") {
				@Override
				public void actionPerformed(ActionEvent e) {
					pushCommand(Command.PAUSE);
				}
			});
			buttonPanel.add(pauseButton);
			JButton coreButton = new JButton(new AbstractAction("Dump Core") {
				@Override
				public void actionPerformed(ActionEvent e) {
					pushCommand(Command.DUMP);
				}
			});
			buttonPanel.add(coreButton);

			// Breakpoints.
			JPanel breakpointsPanel = new JPanel();
			add(breakpointsPanel);
			breakpointsPanel.add(new JLabel("Breakpoints: "));
			final JTextField breakpointsField = new JTextField(40);
			breakpointsPanel.add(breakpointsField);
			JButton breakpointsButton = new JButton(new AbstractAction("Apply") {
				@Override
				public void actionPerformed(ActionEvent e) {
					breakpointsString.set(breakpointsField.getText());
					pushCommand(Command.UPDATE_BREAKPOINTS);
				}
			});
			breakpointsPanel.add(breakpointsButton);

			// Read watchpoints.
			JPanel readWatchpointsPanel = new JPanel();
			add(readWatchpointsPanel);
			readWatchpointsPanel.add(new JLabel("Read watchpoints: "));
			final JTextField readWatchpointsField = new JTextField(35);
			readWatchpointsPanel.add(readWatchpointsField);
			JButton readWatchpointsButton = new JButton(new AbstractAction("Apply") {
				@Override
				public void actionPerformed(ActionEvent e) {
					readWatchpointsString.set(readWatchpointsField.getText());
					pushCommand(Command.UPDATE_READWATCHPOINTS);
				}
			});
			readWatchpointsPanel.add(readWatchpointsButton);

			// Write watchpoints.
			JPanel writeWatchpointsPanel = new JPanel();
			add(writeWatchpointsPanel);
			writeWatchpointsPanel.add(new JLabel("Write watchpoints: "));
			final JTextField writeWatchpointsField = new JTextField(35);
			writeWatchpointsPanel.add(writeWatchpointsField);
			JButton writeWatchpointsButton = new JButton(new AbstractAction("Apply") {
				@Override
				public void actionPerformed(ActionEvent e) {
					writeWatchpointsString.set(writeWatchpointsField.getText());
					pushCommand(Command.UPDATE_WRITEWATCHPOINTS);
				}
			});
			writeWatchpointsPanel.add(writeWatchpointsButton);

			// Min stack.
			JPanel minStackPanel = new JPanel();
			add(minStackPanel);
			minStackPanel.add(new JLabel("Min stack: "));
			final JTextField minStackField = new JTextField(8);
			minStackPanel.add(minStackField);
			JButton minStackButton = new JButton(new AbstractAction("Apply") {
				@Override
				public void actionPerformed(ActionEvent e) {
					minStack.set(Long.parseUnsignedLong(minStackField.getText(), 16));
				}
			});
			minStackPanel.add(minStackButton);

			// Checkboxes.
			JPanel checkboxPanel = new JPanel();
			add(checkboxPanel);
			final JCheckBox shouldTrace = new JCheckBox("Trace Invocations", OCARM.instance.shouldTraceInvocations());
			shouldTrace.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					OCARM.instance.setTraceInvocations(shouldTrace.isSelected());
				}
			});
			checkboxPanel.add(shouldTrace);
			final JCheckBox fatalExceptions = new JCheckBox("Fatal Exceptions", false);
			fatalExceptions.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					cpu.setExceptionDebugMode(fatalExceptions.isSelected());
				}
			});
			checkboxPanel.add(fatalExceptions);
		}

		public void update() {
			cpuStatePanel.update();
		}
	}

	private enum Command {
		RESET, STEP, RUN, PAUSE, DUMP, UPDATE_BREAKPOINTS, UPDATE_READWATCHPOINTS, UPDATE_WRITEWATCHPOINTS
	}
}
