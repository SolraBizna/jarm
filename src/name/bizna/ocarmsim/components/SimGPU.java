package name.bizna.ocarmsim.components;

import javax.swing.JComponent;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import name.bizna.ocarmsim.SimScreenPanel;

public class SimGPU extends SimComponent {

	private SimScreenPanel panel;
	private SimScreen screen;
	private int background;
	private int foreground;

	public SimGPU(Machine machine, String address) {
		super(machine, address);
	}

	public SimScreenPanel getPanel() {
		return panel;
	}

	public void setPanel(SimScreenPanel panel) {
		this.panel = panel;
	}

	public SimScreen getScreen() {
		return screen;
	}

	public void setScreen(SimScreen screen) {
		this.screen = screen;
	}

	@Override
	public String name() {
		return "gpu";
	}

	@Callback(direct = true)
	Object[] bind(Context ctx, Object[] args) {
		if (args.length == 0) {
			return null;
		}
		if (!args[0].equals(screen.address())) {
			return new Object[]{false};
		}
		if (args.length < 2 || args[1] == null || !args[1].equals(Boolean.FALSE)) {
			screen.getUIComponent().reset();
		}
		return new Object[]{true};
	}

	@Callback(direct = true)
	Object[] getScreen(Context ctx, Object[] args) {
		return new Object[]{screen.address()};
	}

	@Callback(direct = true)
	Object[] getBackground(Context ctx, Object[] args) {
		return new Object[]{background < 0 ? -background : background, background < 0};
	}

	@Callback(direct = true)
	Object[] getForeground(Context ctx, Object[] args) {
		return new Object[]{foreground < 0 ? -foreground : foreground, foreground < 0};
	}

	@Callback(direct = true)
	Object[] setBackground(Context ctx, Object[] args) {
		if (args.length < 1) {
			return null;
		}
		int oldBackground = background;
		background = ((Number) args[0]).intValue();
		if (args.length >= 2 && args[1] != null && !args[1].equals(Boolean.FALSE)) {
			background = -background;
		}
		return new Object[]{oldBackground < 0 ? -oldBackground : oldBackground, oldBackground < 0};
	}

	@Callback(direct = true)
	Object[] setForeground(Context ctx, Object[] args) {
		if (args.length < 1) {
			return null;
		}
		int oldForeground = foreground;
		foreground = ((Number) args[0]).intValue();
		if (args.length >= 2 && args[1] != null && !args[1].equals(Boolean.FALSE)) {
			foreground = -foreground;
		}
		return new Object[]{oldForeground < 0 ? -oldForeground : oldForeground, oldForeground < 0};
	}

	@Callback(direct = true)
	Object[] getPaletteColor(Context ctx, Object[] args) {
		if (args.length < 1) {
			return null;
		}
		return new Object[]{screen.getUIComponent().getPaletteColor(((Number) args[0]).intValue())};
	}

	@Callback(direct = true)
	Object[] setPaletteColor(Context ctx, Object[] args) {
		if (args.length < 2) {
			return null;
		}
		return new Object[]{screen.getUIComponent().setPaletteColor(((Number) args[0]).intValue(), ((Number) args[1]).intValue())};
	}

	@Callback(direct = true)
	Object[] maxDepth(Context ctx, Object[] args) {
		return new Object[]{screen.getUIComponent().maxBits()};
	}

	@Callback(direct = true)
	Object[] getDepth(Context ctx, Object[] args) {
		return new Object[]{screen.getUIComponent().curBits()};
	}

	@Callback(direct = true)
	Object[] setDepth(Context ctx, Object[] args) {
		return new Object[]{screen.getUIComponent().setBits(((Number) args[0]).intValue())};
	}

	@Callback(direct = true)
	Object[] maxResolution(Context ctx, Object[] args) {
		return new Object[]{screen.getUIComponent().getMaxCols(), screen.getUIComponent().getMaxRows()};
	}

	@Callback(direct = true)
	Object[] getResolution(Context ctx, Object[] args) {
		return new Object[]{screen.getUIComponent().getCurCols(), screen.getUIComponent().getCurRows()};
	}

	@Callback(direct = true)
	Object[] setResolution(Context ctx, Object[] args) {
		if (args.length < 2) {
			return null;
		}
		int w = ((Number) args[0]).intValue();
		int h = ((Number) args[1]).intValue();
		boolean worked = screen.getUIComponent().setResolution(w, h);
		if (worked) {
			ctx.signal("screen_resized", screen.address(), w, h);
		}
		return new Object[]{worked};
	}

	@Callback(direct = true)
	Object[] get(Context ctx, Object[] args) {
		if (args.length < 2) {
			return null;
		}
		return screen.getUIComponent().getPixel(((Number) args[0]).intValue(), ((Number) args[1]).intValue());
	}

	@Callback(direct = true)
	Object[] set(Context ctx, Object[] args) {
		if (args.length < 3) {
			return null;
		}
		return new Object[]{screen.getUIComponent().set(background, foreground, ((Number) args[0]).intValue(), ((Number) args[1]).intValue(),
			toString(args[2]), args.length >= 4 && args[3] != null && !args[3].equals(Boolean.FALSE))};
	}

	@Callback(direct = true)
	Object[] copy(Context ctx, Object[] args) {
		if (args.length < 6) {
			return null;
		}
		return new Object[]{screen.getUIComponent().copy(
			((Number) args[0]).intValue(), ((Number) args[1]).intValue(),
			((Number) args[2]).intValue(), ((Number) args[3]).intValue(),
			((Number) args[4]).intValue(), ((Number) args[5]).intValue()
			)};
	}

	@Callback(direct = true)
	Object[] fill(Context ctx, Object[] args) {
		if (args.length < 5) {
			return null;
		}
		return new Object[]{screen.getUIComponent().fill(background, foreground, ((Number) args[0]).intValue(), ((Number) args[1]).intValue(),
			((Number) args[2]).intValue(), ((Number) args[3]).intValue(),
			toString(args[4]).charAt(0))};
	}

	@Override
	public JComponent getUIComponent() {
		return null;
	}

	@Override
	public void reset() {
		background = 0x000000;
		foreground = 0xFFFFFF;
		
		// Top-left is (1, 1).
		panel.fill(background, foreground, 1, 1, screen.getWidth(), screen.getHeight(), ' ');
	}
}
