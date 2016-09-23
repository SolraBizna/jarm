package name.bizna.ocarmsim;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import name.bizna.jarm.ByteArrayRegion;
import name.bizna.jarm.CPU;
import name.bizna.ocarmsim.gdb.GDBServer;

public class SimUI implements ActionListener, KeyListener {
	private JFrame window;
	private SimScreenPanel screenPanel;
	private JButton resetButton, pauseButton, stepButton, runButton, coreButton;
	private JCheckBox shouldTrace, fatalExceptions;
	private int maxRows, maxColumns;
	private boolean supportsMouse, supportsPreciseMouse;
	private SimThread thread;
	private FakeMachine machine;
	private CPU cpu;
	private JFileChooser coreFileChooser = new JFileChooser();
	private ROMRegion rom;
	private SRAMRegion sram;
	private ByteArrayRegion[] rams;
	private GDBServer gdbServer;
	
	public SimUI(int screen_tier, FakeMachine machine, CPU cpu, CP3 cp3, ROMRegion rom, SRAMRegion sram, ByteArrayRegion[] rams, String addrinfocmd, int gdbPort, boolean gdbVerbose) {
		if(screen_tier < 0) screen_tier = 0;
		else if(screen_tier > 3) screen_tier = 3;
		this.machine = machine;
		this.cpu = cpu;
		this.rom = rom;
		this.sram = sram;
		this.rams = rams;
		window = new JFrame("OC-ARM Simulator");
		window.setResizable(false);
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setLayout(new BoxLayout(window.getContentPane(), BoxLayout.Y_AXIS));
		int screenBits = 0;
		switch(screen_tier) {
		case 0: maxRows = 0; maxColumns = 0; screenBits = 0; supportsMouse = false; supportsPreciseMouse = false; break;
		case 1:	maxRows = 16; maxColumns = 50; screenBits = 1; supportsMouse = false; supportsPreciseMouse = false; break;
		case 2: maxRows = 25; maxColumns = 80; screenBits = 4; supportsMouse = true; supportsPreciseMouse = false; break;
		case 3: maxRows = 50; maxColumns = 160; screenBits = 8; supportsMouse = true; supportsPreciseMouse = true; break;
		}
		if(maxRows != 0 && maxColumns != 0) {
			screenPanel = new SimScreenPanel(maxRows, maxColumns, screenBits, machine);
			screenPanel.addKeyListener(this);
			window.add(screenPanel);
			window.add(Box.createRigidArea(new Dimension(5,5)));
		}
		thread = new SimThread(machine, cpu, cp3, window, this, addrinfocmd);
		machine.setThread(thread);
		JPanel panel = new JPanel();
		window.add(panel);
		resetButton = new JButton("Reset");
		resetButton.addActionListener(this);
		panel.add(resetButton);
		pauseButton = new JButton("Pause");
		pauseButton.addActionListener(this);
		panel.add(pauseButton);
		stepButton = new JButton("Step");
		stepButton.addActionListener(this);
		panel.add(stepButton);
		runButton = new JButton("Run");
		runButton.addActionListener(this);
		panel.add(runButton);
		coreButton = new JButton("Dump Core");
		coreButton.addActionListener(this);
		panel.add(coreButton);
		panel = new JPanel();
		window.add(panel);
		shouldTrace = new JCheckBox("Trace Invocations", false);
		shouldTrace.addActionListener(this);
		panel.add(shouldTrace);
		fatalExceptions = new JCheckBox("Fatal Exceptions", false);
		fatalExceptions.addActionListener(this);
		panel.add(fatalExceptions);
		window.pack();
		window.setVisible(true);
		thread.start();

		if (gdbPort != 0) {
			gdbServer = new GDBServer(gdbPort, gdbVerbose, thread, cpu);
			new Thread(gdbServer).start();
		}
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		if(src == resetButton) thread.reset();
		else if(src == pauseButton) thread.pause();
		else if(src == stepButton) thread.step();
		else if(src == runButton) thread.go();
		else if(src == shouldTrace) OCARM.instance.traceInvocations = shouldTrace.isSelected();
		else if(src == fatalExceptions) cpu.setExceptionDebugMode(fatalExceptions.isSelected());
		else if(src == coreButton) {
			if(coreFileChooser.showSaveDialog(window) == JFileChooser.APPROVE_OPTION) {
				File f = coreFileChooser.getSelectedFile();
				try {
					FileOutputStream o = new FileOutputStream(f);
					synchronized(thread) {
						dumpCore(o);
					}
					o.close();
				}
				catch(IOException exception) {
					JOptionPane.showMessageDialog(window, "Error while dumping core: "+exception.toString(), null, JOptionPane.ERROR_MESSAGE);
			}
		}
	}
	}
	// http://www.jpct.net/forum2/index.php?topic=749.0
	private int translateFromAWT(int aCode) {
		switch (aCode) {
		case KeyEvent.VK_ESCAPE: return 1;
		case KeyEvent.VK_1: return 2;
		case KeyEvent.VK_2: return 3;
		case KeyEvent.VK_3: return 4;
		case KeyEvent.VK_4: return 5;
		case KeyEvent.VK_5: return 6;
		case KeyEvent.VK_6: return 7;
		case KeyEvent.VK_7: return 8;
		case KeyEvent.VK_8: return 9;
		case KeyEvent.VK_9: return 10;
		case KeyEvent.VK_0: return 11;
		case KeyEvent.VK_MINUS: return 12;
		case KeyEvent.VK_EQUALS: return 13;
		case KeyEvent.VK_BACK_SPACE: return 14;
		case KeyEvent.VK_TAB: return 15;
		case KeyEvent.VK_Q: return 16;
		case KeyEvent.VK_W: return 17;
		case KeyEvent.VK_E: return 18;
		case KeyEvent.VK_R: return 19;
		case KeyEvent.VK_T: return 20;
		case KeyEvent.VK_Y: return 21;
		case KeyEvent.VK_U: return 22;
		case KeyEvent.VK_I: return 23;
		case KeyEvent.VK_O: return 24;
		case KeyEvent.VK_P: return 25;
		case KeyEvent.VK_OPEN_BRACKET: return 26;
		case KeyEvent.VK_CLOSE_BRACKET: return 27;
		case KeyEvent.VK_ENTER: return 28;
		case KeyEvent.VK_CONTROL: return 29;
		case KeyEvent.VK_A: return 30;
		case KeyEvent.VK_S: return 31;
		case KeyEvent.VK_D: return 32;
		case KeyEvent.VK_F: return 33;
		case KeyEvent.VK_G: return 34;
		case KeyEvent.VK_H: return 35;
		case KeyEvent.VK_J: return 36;
		case KeyEvent.VK_K: return 37;
		case KeyEvent.VK_L: return 38;
		case KeyEvent.VK_SEMICOLON: return 39;
		case KeyEvent.VK_QUOTE: return 40;
		case KeyEvent.VK_DEAD_GRAVE: return 41;
		case KeyEvent.VK_SHIFT: return 42;
		case KeyEvent.VK_BACK_SLASH: return 43;
		case KeyEvent.VK_Z: return 44;
		case KeyEvent.VK_X: return 45;
		case KeyEvent.VK_C: return 46;
		case KeyEvent.VK_V: return 47;
		case KeyEvent.VK_B: return 48;
		case KeyEvent.VK_N: return 49;
		case KeyEvent.VK_M: return 50;
		case KeyEvent.VK_COMMA: return 51;
		case KeyEvent.VK_PERIOD: return 52;
		case KeyEvent.VK_SLASH: return 53;
		case KeyEvent.VK_MULTIPLY: return 55;
		case KeyEvent.VK_ALT: return 56;
		case KeyEvent.VK_SPACE: return 57;
		case KeyEvent.VK_CAPS_LOCK: return 58;
		case KeyEvent.VK_F1: return 59;
		case KeyEvent.VK_F2: return 60;
		case KeyEvent.VK_F3: return 61;
		case KeyEvent.VK_F4: return 62;
		case KeyEvent.VK_F5: return 63;
		case KeyEvent.VK_F6: return 64;
		case KeyEvent.VK_F7: return 65;
		case KeyEvent.VK_F8: return 66;
		case KeyEvent.VK_F9: return 67;
		case KeyEvent.VK_F10: return 68;
		case KeyEvent.VK_NUM_LOCK: return 69;
		case KeyEvent.VK_SCROLL_LOCK: return 70;
		case KeyEvent.VK_NUMPAD7: return 71;
		case KeyEvent.VK_NUMPAD8: return 72;
		case KeyEvent.VK_NUMPAD9: return 73;
		case KeyEvent.VK_SUBTRACT: return 74;
		case KeyEvent.VK_NUMPAD4: return 75;
		case KeyEvent.VK_NUMPAD5: return 76;
		case KeyEvent.VK_NUMPAD6: return 77;
		case KeyEvent.VK_ADD: return 78;
		case KeyEvent.VK_NUMPAD1: return 79;
		case KeyEvent.VK_NUMPAD2: return 80;
		case KeyEvent.VK_NUMPAD3: return 81;
		case KeyEvent.VK_NUMPAD0: return 82;
		case KeyEvent.VK_DECIMAL: return 83;
		case KeyEvent.VK_F11: return 87;
		case KeyEvent.VK_F12: return 88;
		case KeyEvent.VK_F13: return 100;
		case KeyEvent.VK_F14: return 101;
		case KeyEvent.VK_F15: return 102;
		case KeyEvent.VK_KANA: return 112;
		case KeyEvent.VK_CONVERT: return 121;
		case KeyEvent.VK_NONCONVERT: return 123;
		case KeyEvent.VK_CIRCUMFLEX: return 144;
		case KeyEvent.VK_AT: return 145;
		case KeyEvent.VK_COLON: return 146;
		case KeyEvent.VK_UNDERSCORE: return 147;
		case KeyEvent.VK_KANJI: return 148;
		case KeyEvent.VK_STOP: return 149;
		case KeyEvent.VK_DIVIDE: return 181;
		case KeyEvent.VK_PAUSE: return 197;
		case KeyEvent.VK_HOME: return 199;
		case KeyEvent.VK_UP: return 200;
		case KeyEvent.VK_PAGE_UP: return 201;
		case KeyEvent.VK_LEFT: return 203;
		case KeyEvent.VK_RIGHT: return 205;
		case KeyEvent.VK_END: return 207;
		case KeyEvent.VK_DOWN: return 208;
		case KeyEvent.VK_PAGE_DOWN: return 209;
		case KeyEvent.VK_INSERT: return 210;
		case KeyEvent.VK_DELETE: return 211;
		case KeyEvent.VK_META: return 219;
		}
		return 0;
	}
	@Override
	public void keyPressed(KeyEvent arg0) {
		Integer keyChar = Integer.valueOf(0);
		if(arg0.getKeyChar() != KeyEvent.CHAR_UNDEFINED) keyChar = Integer.valueOf(arg0.getKeyChar());
		machine.signal("key_down", SimKeyboard.keyboardAddress, keyChar, translateFromAWT(arg0.getKeyCode()), "Player");
	}
	@Override
	public void keyReleased(KeyEvent arg0) {
		Integer keyChar = Integer.valueOf(0);
		if(arg0.getKeyChar() != KeyEvent.CHAR_UNDEFINED) keyChar = Integer.valueOf(arg0.getKeyChar());
		machine.signal("key_up", SimKeyboard.keyboardAddress, keyChar, translateFromAWT(arg0.getKeyCode()), "Player");
	}
	@Override
	public void keyTyped(KeyEvent arg0) {
		// do nothing
	}
	public void enableMainButtons() {
		resetButton.setEnabled(true);
		pauseButton.setEnabled(true);
		stepButton.setEnabled(true);
		runButton.setEnabled(true);
	}
	public void disableMainButtons() {
		resetButton.setEnabled(false);
		pauseButton.setEnabled(false);
		stepButton.setEnabled(false);
		runButton.setEnabled(false);
	}
	public void enableOnlyResetButton() {
		resetButton.setEnabled(true);
		pauseButton.setEnabled(false);
		stepButton.setEnabled(false);
		runButton.setEnabled(false);
	}
	public void enableCoreDumps() {
		coreButton.setEnabled(true);
	}
	public void disableCoreDumps() {
		coreButton.setEnabled(false);
	}

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
		int numRams = 0;
		for(ByteArrayRegion module : rams) {
			if(module != null) ++numRams;
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
		offset = putLoadHeader(buf, E, offset, 0xFFFF0000, rom.arrayMask+1, 5/*PF_X|PF_R*/);
		channel.write(buf);
		offset = putLoadHeader(buf, E, offset, 0x80000000, (int)sram.getRegionSize(), 7/*PF_X|PF_W|PF_R*/);
		channel.write(buf);
		int mappingOffset = 0;
		for(ByteArrayRegion module : rams) {
			if(module != null) {
				offset = offset + putLoadHeader(buf, E, offset, mappingOffset, (int)module.getRegionSize(), 7/*PF_X|PF_W|PF_R*/);
				channel.write(buf);
				mappingOffset += module.getRegionSize();
			}
		}
		/* write data */
		channel.write(noteBuf);
		out.write(rom.array);
		out.write(sram.sramArray);
		for(ByteArrayRegion module : rams) {
			if(module != null) {
				out.write(module.getBackingArray());
			}
		}
		channel.close();
	}
	private void dumpCore(OutputStream out) throws IOException {
		// ELFOSABI_ARM, 0
		dumpCore(out, (byte)97, (byte)0);
	}
}
