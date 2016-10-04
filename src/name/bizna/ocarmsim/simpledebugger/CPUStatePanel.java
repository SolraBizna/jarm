package name.bizna.ocarmsim.simpledebugger;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import name.bizna.jarm.AlignmentException;
import name.bizna.jarm.BusErrorException;
import name.bizna.jarm.EscapeRetryException;
import name.bizna.ocarmsim.BasicDebugger;

/**
 *
 * @author Jean-Rémy Buchs <jrb0001@692b8c32.de>
 */
public class CPUStatePanel extends JPanel {

	private static final String[] gprNames = new String[]{" r0", " r1", " r2", " r3", " r4", " r5", " r6", " r7", " r8", " r9", "r10", "r11", "r12", " sp", " lr", " pc"};

	private final BasicDebugger debugger;
	private final Writer addrinfoWriter;

	private final JLabel crashedWhy;
	private final JLabel[] gprLabels = new JLabel[16];
	private final JLabel nextLabel;
	private final JLabel stateLabel;
	private final JLabel cpsrBitsLabel;

	private final AtomicBoolean updateRunning = new AtomicBoolean();

	public CPUStatePanel(BasicDebugger debugger, String addrinfocmd) {
		this.debugger = debugger;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		crashedWhy = new JLabel("                                ");
		crashedWhy.setForeground(new Color(0xFF0000));
		add(crashedWhy);
		JPanel panel = null;
		for (int n = 0; n < 16; ++n) {
			if (n % 4 == 0) {
				panel = new JPanel();
				add(panel);
			}
			gprLabels[n] = new JLabel(String.format("%3s: XXXXXXXX", gprNames[n]));
			panel.add(gprLabels[n]);
		}
		panel = new JPanel();
		add(panel);
		nextLabel = new JLabel("Next instruction: XXXXXXXX=XXXXXXXX");
		panel.add(nextLabel);
		stateLabel = new JLabel(" State: PAUSED");
		panel.add(stateLabel);

		Writer addrinfoWriter = null;
		if (addrinfocmd != null) {
			try {
				panel = new JPanel();
				add(panel);
				JLabel addrInfoLabel = new JLabel(" ");
				panel.add(addrInfoLabel);

				List<String> list = new ArrayList<>();
				Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(addrinfocmd);
				while (m.find()) {
					list.add(m.group(1).replace("\"", ""));
				}
				Process addrInfoProcess = new ProcessBuilder(list).redirectInput(ProcessBuilder.Redirect.PIPE).redirectOutput(ProcessBuilder.Redirect.PIPE).redirectError(ProcessBuilder.Redirect.INHERIT).start();
				addrinfoWriter = new OutputStreamWriter(addrInfoProcess.getOutputStream());
				Thread errThread = new Thread() {
					@Override
					public void run() {
						BufferedReader reader = new BufferedReader(new InputStreamReader(addrInfoProcess.getErrorStream()));
						try {
							while (true) {
								String line = reader.readLine();
								if (line == null) {
									break;
								}
								System.err.println("addrinfocmd stderr: " + line);
							}
						} catch (IOException ignored) {
						}
					}
				};
				errThread.setDaemon(true);
				errThread.start();
				Thread outThread = new Thread() {
					@Override
					public void run() {
						BufferedReader reader = new BufferedReader(new InputStreamReader(addrInfoProcess.getInputStream()));
						try {
							while (true) {
								String line = reader.readLine();
								if (line == null) {
									break;
								}
								addrInfoLabel.setText(line);
							}
						} catch (IOException ignored) {
						}
					}
				};
				outThread.setDaemon(true);
				outThread.start();
			} catch (Exception e) {
				Logger.getLogger(CPUStatePanel.class.getName()).log(Level.SEVERE, "Exception while setting up the addrinfocmd process", e);
			}
		}
		this.addrinfoWriter = addrinfoWriter;

		panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		add(panel);
		JLabel cpsrHeaderLabel = new JLabel("NZCVQitJ....<ge><.it.>EAIFT<.m.>");
		Box box = Box.createHorizontalBox();
		box.add(cpsrHeaderLabel);
		panel.add(box);
		cpsrBitsLabel = new JLabel("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
		box = Box.createHorizontalBox();
		box.add(cpsrBitsLabel);
		panel.add(box);

		// Set monospace font.
		Font font = Font.decode("Monospaced-PLAIN-14");
		setFont(this, font);
	}

	private void setFont(Container base, Font font) {
		for (Component c : base.getComponents()) {
			if (c instanceof JLabel) {
				c.setFont(font);
			} else if (c instanceof Container) {
				setFont((Container) c, font);
			}
		}
	}

	public void update() {
		if (!updateRunning.getAndSet(true)) {
			SwingUtilities.invokeLater(() -> {
				for (int n = 0; n < 16; ++n) {
					gprLabels[n].setText(String.format("%3s: %08X", gprNames[n], debugger.getCpu().readRegister(n)));
				}
				int pc = debugger.getCpu().readPC() - 4;
				if (addrinfoWriter != null) {
					try {
						addrinfoWriter.write(String.format("%08X\n", pc));
						addrinfoWriter.flush();
					} catch (IOException ex) {
						Logger.getLogger(CPUStatePanel.class.getName()).log(Level.SEVERE, "Exception while writing to the addrinfocmd process", ex);
					}
				}
				String nextInstr = "Error";
				try {
					nextInstr = String.format("%08X", debugger.getCpu().getVirtualMemorySpace().readInt(pc, true, false));
				} catch (AlignmentException | BusErrorException | EscapeRetryException e) {
				}
				nextLabel.setText(String.format("Next instruction: %08X=%8s", pc, nextInstr));
				stateLabel.setText(" State: " + debugger.getState().toString());
				int cpsr = debugger.getCpu().readCPSR();
				int mask = 1 << 31;
				StringBuilder cpsrBitsBuilder = new StringBuilder();
				while (mask != 0) {
					if ((cpsr & mask) != 0) {
						cpsrBitsBuilder.append('1');
					} else {
						cpsrBitsBuilder.append('0');
					}
					mask = mask >>> 1;
				}
				cpsrBitsLabel.setText(cpsrBitsBuilder.toString());
				if (debugger.getState() != BasicDebugger.State.CRASHED) {
					crashedWhy.setText("                                ");
				}

				repaint();

				updateRunning.set(false);
			});
		}
	}
}
