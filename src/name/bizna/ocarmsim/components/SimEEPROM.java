package name.bizna.ocarmsim.components;

import java.io.IOException;
import javax.swing.JComponent;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import name.bizna.ocarmsim.ROMRegion;
import name.bizna.ocarmsim.SRAMRegion;

public class SimEEPROM extends SimComponent {

	private final ROMRegion rom;
	private final SRAMRegion sram;

	public SimEEPROM(Machine machine,String address, ROMRegion rom, SRAMRegion sram) {
		super(machine, address);

		this.rom = rom;
		this.sram = sram;
	}

	@Override
	public String name() {
		return "eeprom";
	}

	@Callback
	public Object[] get(Context ctx, Object[] args) {
		return new Object[]{rom.getArray()};
	}

	@Callback
	public Object[] set(Context ctx, Object[] args) {
		return new Object[]{null, "storage is readonly"};
	}

	@Callback
	public Object[] getLabel(Context ctx, Object[] args) {
		return new Object[]{"EEPROM"};
	}

	@Callback
	public Object[] setLabel(Context ctx, Object[] args) {
		return new Object[]{null, "storage is readonly"};
	}

	@Callback
	public Object[] getSize(Context ctx, Object[] args) {
		return new Object[]{4096};
	}

	@Callback
	public Object[] getDataSize(Context ctx, Object[] args) {
		return new Object[]{sram.getSramArray().length < 256 ? 256 : sram.getSramArray().length};
	}

	@Callback
	public Object[] getData(Context ctx, Object[] args) {
		return new Object[]{sram.getNvramArray()};
	}

	@Callback
	public Object[] setData(Context ctx, Object[] args) {
		byte[] array;
		if (args.length == 0) {
			array = new byte[0];
		} else {
			array = (byte[]) args[0];
		}
		if (array.length > (sram.getSramArray().length < 256 ? 256 : sram.getSramArray().length)) {
			return new Object[]{null, "data too long"};
		}
		sram.setNvramArray(array);
		try {
			sram.maybeWriteNVRAM();
		} catch (IOException e) {
			System.err.println("While saving NVRAM");
			e.printStackTrace(System.err);
		}
		return null;
	}

	@Callback
	public Object[] getChecksum(Context ctx, Object[] args) {
		return new Object[]{new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF}};
	}

	@Callback
	public Object[] makeReadonly(Context ctx, Object[] args) {
		return new Object[]{null, "storage is readonly"};
	}

	@Override
	public JComponent getUIComponent() {
		return null;
	}

	@Override
	public void reset() {
		// Keep EEPROM between resets?
	}
}
