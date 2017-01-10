package name.bizna.ocarmsim;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.JComponent;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import li.cil.oc.api.machine.Machine;
import name.bizna.jarm.ByteArrayRegion;
import name.bizna.jarm.Debugger;
import name.bizna.jarm.PhysicalMemorySpace;
import name.bizna.ocarmsim.components.SimComponent;
import name.bizna.ocarmsim.components.SimEEPROM;
import name.bizna.ocarmsim.components.SimFilesystem;
import static name.bizna.ocarmsim.components.SimFilesystem.filesystemAddressFormatString;
import name.bizna.ocarmsim.components.SimGPU;
import name.bizna.ocarmsim.components.SimKeyboard;
import name.bizna.ocarmsim.components.SimScreen;

/**
 *
 * @author Jean-Rémy Buchs <jrb0001@692b8c32.de>
 */
@XmlRootElement(name = "hardwareDefinition")
@XmlAccessorType(XmlAccessType.FIELD)
public class HardwareDefinition {

	private int memory = 192;
	private String rom = null;
	private String sram = null;
	private int sramSize = -256;
	private boolean sramRO = false;
	@XmlElementWrapper(name = "devices")
	@XmlElementRef
	private List<Device> devices = new ArrayList<Device>();

	public int getMemory() {
		return memory;
	}

	public String getRom() {
		return rom;
	}

	public String getSram() {
		return sram;
	}

	public int getSramSize() {
		return sramSize;
	}

	public boolean isSramRO() {
		return sramRO;
	}

	public List<Device> getDevices() {
		return devices;
	}

	public List<JComponent> prepareSimulation(Debugger debugger) throws IOException {
		if (rom == null) {
			throw new NullPointerException("No rom provided");
		}

		FakeMachine machine = new FakeMachine(debugger);
		JARMArchitecture arch = new JARMArchitecture();
		CP3 cp3 = new CP3(debugger.getCpu(), machine, arch);
		debugger.getCpu().mapCoprocessor(3, cp3);
		debugger.getCpu().mapCoprocessor(7, new CP7(debugger.getCpu()));

		int ram_quantity = getMemory();
		PhysicalMemorySpace mem = debugger.getCpu().getMemorySpace();
		if (ram_quantity > 1048576) {
			/* we need to use two modules, since no mapping can exceed 1GiB */
			int upper_quantity = ram_quantity - 1048576;
			mem.mapRegion(0x40000000, new ByteArrayRegion(upper_quantity * 1024));
			arch.setModule2Size(upper_quantity * 1024);
			ram_quantity = 1048576;
		}
		if (ram_quantity > 0) {
			mem.mapRegion(0x00000000, new ByteArrayRegion(ram_quantity * 1024));
			arch.setModule1Size(ram_quantity * 1024);
		}

		ROMRegion rom = new ROMRegion(new File(getRom()));
		mem.mapRegion(0xC0000000, rom);
		SRAMRegion sram = new SRAMRegion(getSram() == null ? null : new File(getSram()), getSramSize(), !isSramRO());
		machine.addNode(new SimEEPROM(machine, UUID.randomUUID().toString(), rom, sram));
		arch.setModule0Size((int) sram.getRegionSize());
		arch.setSRAMRegion(sram);
		mem.mapRegion(0x80000000, sram);

		List<SimScreen> screens = new ArrayList<SimScreen>();
		List<SimGPU> gpus = new ArrayList<SimGPU>();
		List<SimKeyboard> keyboards = new ArrayList<SimKeyboard>();

		for (Device dev : getDevices()) {
			SimComponent component = dev.createComponent(machine);
			machine.addNode(component);

			if (component instanceof SimScreen) {
				screens.add((SimScreen) component);
			} else if (component instanceof SimGPU) {
				gpus.add((SimGPU) component);
			} else if (component instanceof SimKeyboard) {
				keyboards.add((SimKeyboard) component);
			}
		}

		List<JComponent> components = new ArrayList<JComponent>();
		for (int i = 0; i < Math.max(screens.size(), keyboards.size()); i++) {
			SimScreen screen = i >= screens.size() ? null : screens.get(0);
			SimGPU gpu = i >= gpus.size() ? null : gpus.get(0);
			SimKeyboard keyboard = i >= keyboards.size() ? null : keyboards.get(0);

			SimScreenPanel panel = new SimScreenPanel(screen, gpu, keyboard, machine);

			if (screen != null) {
				screen.setPanel(panel);
				screen.setKeyboard(keyboard);
			}
			if (gpu != null) {
				gpu.setPanel(panel);
				gpu.setScreen(screen);
			}
			if (keyboard != null) {
				keyboard.setPanel(panel);
			}

			components.add(panel);
		}

		return components;
	}

	@XmlRootElement(name = "dev")
	@XmlSeeAlso({FSDevice.class, ScreenDevice.class, GPUDevice.class, KeyboardDevice.class})
	public abstract static class Device {

		@XmlAttribute
		protected String address = UUID.randomUUID().toString();

		public abstract SimComponent createComponent(Machine machine);
	}

	@XmlRootElement(name = "fs")
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class FSDevice extends Device {

		@XmlAttribute
		private String basepath;
		@XmlAttribute
		private boolean writable;

		@Override
		public SimComponent createComponent(Machine machine) {
			return new SimFilesystem(machine, address == null ? String.format(filesystemAddressFormatString, basepath.hashCode()) : address, new File(basepath), writable);
		}
	}

	@XmlRootElement(name = "screen")
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ScreenDevice extends Device {

		@XmlAttribute
		private int width;
		@XmlAttribute
		private int height;
		@XmlAttribute
		private int bits;

		@Override
		public SimComponent createComponent(Machine machine) {
			return new SimScreen(machine, address, width, height, bits);
		}
	}

	@XmlRootElement(name = "gpu")
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class GPUDevice extends Device {

		@Override
		public SimComponent createComponent(Machine machine) {
			return new SimGPU(machine, address);
		}
	}

	@XmlRootElement(name = "keyboard")
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class KeyboardDevice extends Device {

		@Override
		public SimComponent createComponent(Machine machine) {
			return new SimKeyboard(machine, address);
		}
	}
}
