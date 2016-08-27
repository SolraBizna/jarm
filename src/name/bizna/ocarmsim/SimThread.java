package name.bizna.ocarmsim;

import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import li.cil.oc.api.machine.ExecutionResult;
import name.bizna.jarm.AlignmentException;
import name.bizna.jarm.BusErrorException;
import name.bizna.jarm.CPU;
import name.bizna.jarm.UndefinedException;
import name.bizna.jarm.UnimplementedInstructionException;
import name.bizna.jarm.VirtualMemorySpace;

public class SimThread extends Thread implements ActionListener {
	/* this is expensive and rarely necessary, so it's not a checkbox */
	private static final boolean tracePC = false;
	private static FileWriter pcTraceOut;
	static {
		if(tracePC) {
			try {
				pcTraceOut = new FileWriter(new File("/tmp/pcTrace.txt"));
			}
			catch(IOException e) {
				e.printStackTrace(System.err);
			}
		}
	}
	private static void recordPC(int x) {
		try {
			pcTraceOut.write(String.format("%08x\n", x));
			pcTraceOut.flush();
		}
		catch(IOException e) {
			e.printStackTrace(System.err);
		}
	}
	public static enum ExecutionMode {
		PAUSED, STEPPING, RUNNING, RESETTING, FAILED, CRASHED, SLEEPING
	};
	private FakeMachine machine;
	private CPU cpu;
	private CP3 cp3;
	private ExecutionMode mode = ExecutionMode.PAUSED;
	private String[] gprNames = new String[]{" r0"," r1"," r2"," r3"," r4"," r5"," r6"," r7"," r8"," r9","r10","r11","r12"," sp"," lr"," pc"};
	private JLabel[] gprLabels;
	private JLabel nextLabel, stateLabel, crashedWhy;
	private JLabel cpsrBitsLabel;
	private JButton updateBreakpointsButton, updateMinstackButton;
	private JTextField breakpointsField;
	private JTextField minstackField;
	private long minstack = 0;
	private String crashReason = "";
	private SimUI ui;
	private JLabel addrInfoLabel = null;
	private Process addrInfoProcess = null;
	private PrintStream addrInfoInput = null;
	private BufferedReader addrInfoOutput = null;
	private ReentrantLock addrInfoLock = null;
	private boolean labelUpdateQueued = false, skipBreakpointsOnce = false;
	private Set<Integer> breakpointSet = new TreeSet<Integer>();
	private ReentrantLock lock = new ReentrantLock(true);
	private Condition cond = lock.newCondition();
	public SimThread(FakeMachine machine, CPU cpu, CP3 cp3, Container stateContainer, SimUI ui, String addrinfocmd) {
		super("Simulation thread");
		this.machine = machine;
		this.cpu = cpu;
		this.cp3 = cp3;
		this.ui = ui;
		gprLabels = new JLabel[16];
		Font font = Font.decode("Monospaced-PLAIN-14");
		JPanel panel = new JPanel();
		crashedWhy = new JLabel("                                ");
		crashedWhy.setFont(font);
		crashedWhy.setForeground(new Color(0xFF0000));
		crashedWhy.setHorizontalAlignment(SwingConstants.CENTER);
		panel.add(crashedWhy);
		stateContainer.add(panel);
		for(int n = 0; n < 16; ++n) {
			if(n % 4 == 0) { panel = new JPanel(); stateContainer.add(panel); }
			gprLabels[n] = new JLabel(String.format("%3s: XXXXXXXX", gprNames[n]));
			gprLabels[n].setFont(font);
			panel.add(gprLabels[n]);
		}
		panel = new JPanel();
		stateContainer.add(panel);
		nextLabel = new JLabel("Next instruction: XXXXXXXX=XXXXXXXX");
		nextLabel.setFont(font);
		panel.add(nextLabel);
		stateLabel = new JLabel(" State: PAUSED");
		stateLabel.setFont(font);
		panel.add(stateLabel);
		if(addrinfocmd != null) {
			try {
				addrInfoProcess = Runtime.getRuntime().exec(addrinfocmd);
				addrInfoInput = new PrintStream(addrInfoProcess.getOutputStream());
				addrInfoOutput = new BufferedReader(new InputStreamReader(addrInfoProcess.getInputStream()));
				Thread errThread = new Thread() {
					public void run() {
						BufferedReader reader = new BufferedReader(new InputStreamReader(addrInfoProcess.getErrorStream()));
						while(true) {
							try {
								String line = reader.readLine();
								if(line == null) break;
								System.err.println("addrinfocmd stderr: "+line);
							}
							catch(IOException e) {
								// eh
								return;
							}
						}
					}
				};
				errThread.setDaemon(true);
				errThread.start();
				panel = new JPanel();
				stateContainer.add(panel);
				addrInfoLabel = new JLabel(" ");
				addrInfoLabel.setFont(font);
				panel.add(addrInfoLabel);
				addrInfoLock = new ReentrantLock();
			}
			catch(Exception e) {
				System.err.println("Exception while setting up the addrinfocmd process");
				e.printStackTrace();
			}
		}
		panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		stateContainer.add(panel);
		JLabel cpsrHeaderLabel = new JLabel("NZCVQitJ....<ge><.it.>EAIFT<.m.>");
		cpsrHeaderLabel.setFont(font);
		Box box = Box.createHorizontalBox();
		box.add(cpsrHeaderLabel);
		panel.add(box);
		cpsrBitsLabel = new JLabel("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
		cpsrBitsLabel.setFont(font);
		box = Box.createHorizontalBox();
		box.add(cpsrBitsLabel);
		panel.add(box);
		panel = new JPanel();
		stateContainer.add(panel);
		JLabel breakpointsLabel = new JLabel("Breakpoints:");
		breakpointsLabel.setFont(font);
		panel.add(breakpointsLabel);
		breakpointsField = new JTextField(30);
		breakpointsField.setFont(font);
		panel.add(breakpointsField);
		updateBreakpointsButton = new JButton("Apply");
		updateBreakpointsButton.addActionListener(this);
		panel.add(updateBreakpointsButton);
		panel = new JPanel();
		stateContainer.add(panel);
		JLabel minstackLabel = new JLabel("Min stack:");
		minstackLabel.setFont(font);
		panel.add(minstackLabel);
		minstackField = new JTextField(8);
		minstackField.setFont(font);
		panel.add(minstackField);
		updateMinstackButton = new JButton("Apply");
		updateMinstackButton.addActionListener(this);
		panel.add(updateMinstackButton);
	}
	@Override
	public void run() {
		long sleepHowLong = 0;
		cpu.reset(false, true, true);
		while(true) {
			lock.lock();
			try {
				cpu.zeroBudget(false);
				if(!labelUpdateQueued) {
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							if(lock.tryLock()) {
								try {
									String addrInfoParam = null;
									try {
										for(int n = 0; n < 16; ++n) {
											gprLabels[n].setText(String.format("%3s: %08X", gprNames[n], cpu.readRegister(n)));
										}
										int pc = cpu.readPC() - 4;
										if(addrInfoProcess != null) addrInfoParam = String.format("%08X", pc);
										String nextInstr = "Error";
										try {
											nextInstr = String.format("%08X", cpu.getVirtualMemorySpace().readInt(pc, true, false));
										}
										catch(Exception e) {}
										nextLabel.setText(String.format("Next instruction: %08X=%8s", pc, nextInstr));
										stateLabel.setText(" State: "+mode.toString());
										int cpsr = cpu.readCPSR();
										int mask = 1 << 31;
										StringBuilder cpsrBitsBuilder = new StringBuilder();
										while(mask != 0) {
											if((cpsr & mask) != 0) cpsrBitsBuilder.append('1');
											else cpsrBitsBuilder.append('0');
											mask = mask >>> 1;
										}
										cpsrBitsLabel.setText(cpsrBitsBuilder.toString());
										if(mode != ExecutionMode.CRASHED) {
											crashedWhy.setText("                                ");
											ui.enableMainButtons();
										}
										else ui.enableOnlyResetButton();
										labelUpdateQueued = false;
										if(addrInfoParam != null) addrInfoLock.lock();
									}
									finally { lock.unlock(); }
									if(addrInfoProcess != null && addrInfoParam != null && addrInfoLock.isHeldByCurrentThread()) {
										try {
											addrInfoInput.println(addrInfoParam);
											addrInfoInput.flush();
											String str = addrInfoOutput.readLine();
											if(str == null) {
												System.err.println("addrinfocmd closed down");
												addrInfoLabel.setText("addrinfocmd closed down");
												addrInfoInput = null;
												addrInfoOutput = null;
												addrInfoProcess = null;
											}
											else {
												addrInfoLabel.setText(str);
											}
										}
										catch(IOException e) {
											System.err.println("IO exception reading line info");
											e.printStackTrace();
											addrInfoLabel.setText("IO exception reading line info");
										}
									}
								}
								finally {
									if(addrInfoLock != null && addrInfoLock.isHeldByCurrentThread()) addrInfoLock.unlock();
								}
							}
							else SwingUtilities.invokeLater(this);
						}
					});
					labelUpdateQueued = true;
				}
				if(mode == ExecutionMode.SLEEPING) {
					try {
						cond.awaitNanos(sleepHowLong);
					}
					catch(InterruptedException e) {}
					mode = ExecutionMode.RUNNING;
				}
				else while(mode == ExecutionMode.PAUSED || mode == ExecutionMode.CRASHED)
					cond.await();
				ExecutionResult ret = null;
				if(!cp3.mayExecute()) cp3.runSynchronized();
				else {
					switch(mode) {
					case STEPPING:
						if(tracePC) recordPC(cpu.readPC() - 4);
						cpu.execute(1);
						mode = ExecutionMode.PAUSED;
						break;
					case RUNNING:
						if(breakpointSet.isEmpty() && !tracePC && minstack == 0)
							cpu.execute(250000);
						else {
							int next = cpu.readPC() - 4;
							if(!skipBreakpointsOnce && breakpointSet.contains(next))
								mode = ExecutionMode.PAUSED;
							else if((cpu.readSP()&0xFFFFFFFFL) < minstack)
								mode = ExecutionMode.PAUSED;
							else {
								skipBreakpointsOnce = false;
								if(tracePC) recordPC(next);
								cpu.execute(1);
							}
						}
						break;
					case RESETTING:
						cpu.reset(false, true, true);
						mode = ExecutionMode.PAUSED;
						break;
					default:
						break;
					}
				}
				if(ret == null) ret = cp3.getExecutionResult();
				if(ret != null) {
					if(ret instanceof ExecutionResult.Sleep) {
						sleepHowLong = ((ExecutionResult.Sleep)ret).ticks * 50000000L;
						if(mode == ExecutionMode.RUNNING)
							mode = ExecutionMode.SLEEPING;
					}
					else if(ret instanceof ExecutionResult.Error) {
						crashForReason("CRASH: " + ((ExecutionResult.Error)ret).message);
						continue;
					}
					else if(ret instanceof ExecutionResult.Shutdown) {
						crashForReason("Computer is shutting down.");
						continue;
					}
					else if(ret instanceof ExecutionResult.SynchronizedCall) {
						cp3.runSynchronized();
					}
				}
				/*else if(blockRemTick) {
					cpu.zeroBudget(true);
					cp3.runSynchronized();
				}*/
			}
			catch(UnimplementedInstructionException e) {
				crashForReason("UNIMPLEMENTED INSTRUCTION: "+e.toString());
				cpu.dumpState(System.err);
			}
			catch(Exception e) {
				crashForReason("Exception in cpu.execute: "+e.toString());
				e.printStackTrace(System.err);
				if(e instanceof BusErrorException) {
					VirtualMemorySpace vm = cpu.getVirtualMemorySpace();
					OCARM.logger.error("While attempting a %d-bit %s at %08X", 8<<vm.getLastAccessWidth(), vm.getLastAccessWasStore()?"store":"load", vm.getLastAccessAddress());
				}
				cpu.dumpState(System.err);
				if(!(e instanceof AlignmentException || e instanceof BusErrorException || e instanceof UndefinedException))
					break;
			}
			finally {
				lock.unlock();
			}
		}
		synchronized(this) {
			mode = ExecutionMode.FAILED;
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					stateLabel.setText(" State: "+mode.toString());
					ui.disableMainButtons();
				}
			});
		}
	}
	private void crashForReason(String reason) {
		mode = ExecutionMode.CRASHED;
		crashReason = reason;
		System.err.println(reason);
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				crashedWhy.setText(crashReason);
			}
		});
	}
	public void reset() {
		lock.lock();
		try {
			if(mode == ExecutionMode.FAILED) return;
			mode = ExecutionMode.RESETTING;
			skipBreakpointsOnce = false;
			cond.signal();
		}
		finally {
			lock.unlock();
		}
	}
	public void step() {
		lock.lock();
		try {
			if(mode == ExecutionMode.FAILED || mode == ExecutionMode.CRASHED) return;
			mode = ExecutionMode.STEPPING;
			skipBreakpointsOnce = false;
			cond.signal();
		}
		finally {
			lock.unlock();
		}
	}
	public void go() {
		lock.lock();
		try {
			if(mode == ExecutionMode.FAILED || mode == ExecutionMode.CRASHED) return;
			mode = ExecutionMode.RUNNING;
			skipBreakpointsOnce = true;
			cond.signal();
		}
		finally {
			lock.unlock();
		}
	}
	public void pause() {
		lock.lock();
		try {
			if(mode == ExecutionMode.FAILED || mode == ExecutionMode.CRASHED) return;
			mode = ExecutionMode.PAUSED;
			skipBreakpointsOnce = false;
			cond.signal();
		}
		finally {
			lock.unlock();
		}
	}
	@Override
	public void actionPerformed(ActionEvent arg0) {
		if(arg0.getSource() == updateBreakpointsButton) {
			if(breakpointsField.getText().isEmpty()) {
				lock.lock();
				breakpointSet.clear();
				lock.unlock();
			}
			else {
				Set<Integer> newBreakpointSet = new TreeSet<Integer>();
				String[] split = breakpointsField.getText().split(" ");
				boolean erroneous = false;
				for(String splat : split) {
					try {
						long l = Long.parseLong(splat, 16);
						if(l < 0 || l > 0xFFFFFFFFL) { erroneous = true; break; }
						newBreakpointSet.add((int)l);
					}
					catch(NumberFormatException e) { erroneous = true; break; }
				}
				if(erroneous) {
					JOptionPane.showMessageDialog(null, "The breakpoints list must consist of hexadecimal addresses separated by spaces.\nFor example:\nFFFF0000 0000021C", null, JOptionPane.ERROR_MESSAGE);
				}
				else {
					lock.lock();
					breakpointSet = newBreakpointSet;
					lock.unlock();
				}
			}
		}
		else if(arg0.getSource() == updateMinstackButton) {
			if(minstackField.getText().isEmpty()) {
				lock.lock();
				minstack = 0;
				lock.unlock();
			}
			else {
				boolean erroneous = false;
				long l = 0;
				try {
					l = Long.parseLong(minstackField.getText(), 16);
					if(l < 0 || l > 0xFFFFFFFFL) erroneous = true;
				}
				catch(NumberFormatException e) {
					erroneous = true;
				}
				if(erroneous) {
					JOptionPane.showMessageDialog(null, "Min stack must be a hexadecimal address.", null, JOptionPane.ERROR_MESSAGE);
				}
				else {
					lock.lock();
					minstack = l;
					lock.unlock();
				}
			}
		}
	}
}
