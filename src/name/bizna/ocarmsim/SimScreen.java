package name.bizna.ocarmsim;

import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;

public class SimScreen extends SimComponent {
	
	public static final String screenAddress = "8a043742-22b3-441d-bb80-5a989ab924c2";

	private SimScreenPanel panel;
	
	public SimScreen(SimScreenPanel panel) {
		this.panel = panel;
	}
	
	@Override
	public String name() {
		return "screen";
	}

	@Override
	public String address() {
		return screenAddress;
	}
	
	@Callback
	public Object[] isOn(Context ctx, Object[] args) {
		return new Object[]{Boolean.valueOf(panel.isOn())};
	}
	
	@Callback
	public Object[] turnOn(Context ctx, Object[] args) {
		return new Object[]{Boolean.valueOf(panel.turnOn())};
	}
	
	@Callback
	public Object[] turnOff(Context ctx, Object[] args) {
		return new Object[]{Boolean.valueOf(panel.turnOff())};
	}
	
	@Callback
	public Object[] aspectRatio(Context ctx, Object[] args) {
		return new Object[]{Integer.valueOf(1), Integer.valueOf(1)};
	}
	
	@Callback
	public Object[] getKeyboards(Context ctx, Object[] args) {
		return new Object[]{new String[]{SimKeyboard.keyboardAddress}};
	}
	
	@Callback
	public Object[] setPrecise(Context ctx, Object[] args) {
		return new Object[]{Boolean.valueOf(panel.setPrecise(((Boolean)args[0]).booleanValue()))};
	}

	@Callback
	public Object[] isPrecise(Context ctx, Object[] args) {
		return new Object[]{Boolean.valueOf(panel.isPrecise())};
	}

	@Callback
	public Object[] setTouchModeInverted(Context ctx, Object[] args) {
		return new Object[]{Boolean.valueOf(panel.setTouchModeInverted(((Boolean)args[0]).booleanValue()))};
	}

	@Callback
	public Object[] isTouchModeInverted(Context ctx, Object[] args) {
		return new Object[]{Boolean.valueOf(panel.isTouchModeInverted())};
	}

}
