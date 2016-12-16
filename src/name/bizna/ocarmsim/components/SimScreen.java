package name.bizna.ocarmsim.components;

import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import name.bizna.ocarmsim.SimScreenPanel;

public class SimScreen extends SimComponent {

	private final int width;
	private final int height;
	private final int bits;
	private SimScreenPanel panel;
	private SimKeyboard keyboard;

	public SimScreen(Machine machine, String address, int width, int height, int bits) {
		super(machine, address);

		this.width = width;
		this.height = height;
		this.bits = bits;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public int getBits() {
		return bits;
	}

	public SimScreenPanel getPanel() {
		return panel;
	}

	public void setPanel(SimScreenPanel panel) {
		this.panel = panel;
	}

	public SimKeyboard getKeyboard() {
		return keyboard;
	}

	public void setKeyboard(SimKeyboard keyboard) {
		this.keyboard = keyboard;
	}

	@Override
	public String name() {
		return "screen";
	}

	@Callback
	public Object[] isOn(Context ctx, Object[] args) {
		return new Object[]{panel.isOn()};
	}

	@Callback
	public Object[] turnOn(Context ctx, Object[] args) {
		return new Object[]{panel.turnOn()};
	}

	@Callback
	public Object[] turnOff(Context ctx, Object[] args) {
		return new Object[]{panel.turnOff()};
	}

	@Callback
	public Object[] aspectRatio(Context ctx, Object[] args) {
		return new Object[]{1, 1};
	}

	@Callback
	public Object[] getKeyboards(Context ctx, Object[] args) {
		return new Object[]{new String[]{keyboard.address()}};
	}

	@Callback
	public Object[] setPrecise(Context ctx, Object[] args) {
		return new Object[]{panel.setPrecise(((Boolean) args[0]))};
	}

	@Callback
	public Object[] isPrecise(Context ctx, Object[] args) {
		return new Object[]{panel.isPrecise()};
	}

	@Callback
	public Object[] setTouchModeInverted(Context ctx, Object[] args) {
		return new Object[]{panel.setTouchModeInverted(((Boolean) args[0]))};
	}

	@Callback
	public Object[] isTouchModeInverted(Context ctx, Object[] args) {
		return new Object[]{panel.isTouchModeInverted()};
	}

	@Override
	public SimScreenPanel getUIComponent() {
		return panel;
	}

	@Override
	public void reset() {
		panel.reset();
	}
}
