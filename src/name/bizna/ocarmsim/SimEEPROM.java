package name.bizna.ocarmsim;

import java.io.IOException;

import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;

public class SimEEPROM extends SimComponent {

	public static final String eepromAddress = "9bb08d1f-c322-476d-a105-e47e1ad8d58c";
	
	private final ROMRegion rom;
	private final SRAMRegion sram;
	
	public SimEEPROM(ROMRegion rom, SRAMRegion sram) {
		this.rom = rom;
		this.sram = sram;
	}

	@Override
	public String name() {
		return "eeprom";
	}

	@Override
	public String address() {
		return eepromAddress;
	}
	
	@Callback
	public Object[] get(Context ctx, Object[] args) {
		return new Object[]{rom.array};
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
		return new Object[]{sram.sramArray.length < 256 ? 256 : sram.sramArray.length};
	}
	
	@Callback
	public Object[] getData(Context ctx, Object[] args) {
		return new Object[]{sram.nvramArray};
	}
	
	@Callback
	public Object[] setData(Context ctx, Object[] args) {
		byte[] array;
		if(args.length == 0) array = new byte[0];
		else array = (byte[])args[0];
		if(array.length > (sram.sramArray.length < 256 ? 256 : sram.sramArray.length))
			return new Object[]{null, "data too long"};
		sram.nvramArray = array;
		try {
			sram.maybeWriteNVRAM();
		}
		catch(IOException e) {
			System.err.println("While saving NVRAM");
			e.printStackTrace(System.err);
		}
		return null;
	}

	@Callback
	public Object[] getChecksum(Context ctx, Object[] args) {
		return new Object[]{new byte[]{(byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF}};
	}
	
	@Callback
	public Object[] makeReadonly(Context ctx, Object[] args) {
		return new Object[]{null, "storage is readonly"};
	}
}
