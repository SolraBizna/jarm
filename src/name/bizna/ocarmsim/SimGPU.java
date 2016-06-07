package name.bizna.ocarmsim;

import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;

public class SimGPU extends SimComponent {

	public static final String gpuAddress = "259f120c-a148-42b8-8ab9-cda2f663f12a";
	
	private SimScreenPanel panel;
	private int background = 0x000000;
	private int foreground = 0xFFFFFF;
	
	public SimGPU(SimScreenPanel panel) {
		this.panel = panel;
	}
	
	@Override
	public String name() {
		return "gpu";
	}

	@Override
	public String address() {
		return gpuAddress;
	}
	
	@Callback(direct=true)
	Object[] bind(Context ctx, Object[] args) {
		if(args.length == 0) return null;
		if(!args[0].equals(SimScreen.screenAddress)) return new Object[]{false};
		if(args.length < 2 || args[1] == null || !args[1].equals(Boolean.FALSE)) {
			panel.reset();
		}
		return new Object[]{true};
	}
	
	@Callback(direct=true)
	Object[] getScreen(Context ctx, Object[] args) {
		return new Object[]{SimScreen.screenAddress};
	}
	
	@Callback(direct=true)
	Object[] getBackground(Context ctx, Object[] args) {
		return new Object[]{Integer.valueOf(background < 0 ? -background : background), Boolean.valueOf(background < 0)};
	}
	
	@Callback(direct=true)
	Object[] getForeground(Context ctx, Object[] args) {
		return new Object[]{Integer.valueOf(foreground < 0 ? -foreground : foreground), Boolean.valueOf(foreground < 0)};
	}
	
	@Callback(direct=true)
	Object[] setBackground(Context ctx, Object[] args) {
		if(args.length < 1) return null;
		int oldBackground = background;
		background = ((Number)args[0]).intValue();
		if(args.length >= 2 && args[1] != null && !args[1].equals(Boolean.FALSE)) background = -background;
		return new Object[]{Integer.valueOf(oldBackground < 0 ? -oldBackground : oldBackground), Boolean.valueOf(oldBackground < 0)};
	}
	
	@Callback(direct=true)
	Object[] setForeground(Context ctx, Object[] args) {
		if(args.length < 1) return null;
		int oldForeground = foreground;
		foreground = ((Number)args[0]).intValue();
		if(args.length >= 2 && args[1] != null && !args[1].equals(Boolean.FALSE)) foreground = -foreground;
		return new Object[]{Integer.valueOf(oldForeground < 0 ? -oldForeground : oldForeground), Boolean.valueOf(oldForeground < 0)};
	}
	
	@Callback(direct=true)
	Object[] getPaletteColor(Context ctx, Object[] args) {
		if(args.length < 1) return null;
		return new Object[]{panel.getPaletteColor(((Number)args[0]).intValue())};
	}
	
	@Callback(direct=true)
	Object[] setPaletteColor(Context ctx, Object[] args) {
		if(args.length < 2) return null;
		return new Object[]{panel.setPaletteColor(((Number)args[0]).intValue(), ((Number)args[1]).intValue())};
	}
	
	@Callback(direct=true)
	Object[] maxDepth(Context ctx, Object[] args) {
		return new Object[]{panel.maxBits()};
	}
	
	@Callback(direct=true)
	Object[] getDepth(Context ctx, Object[] args) {
		return new Object[]{panel.curBits()};
	}
	
	@Callback(direct=true)
	Object[] setDepth(Context ctx, Object[] args) {
		return new Object[]{panel.setBits(((Number)args[0]).intValue())};
	}
	
	@Callback(direct=true)
	Object[] maxResolution(Context ctx, Object[] args) {
		return new Object[]{panel.getMaxCols(), panel.getMaxRows()};
	}
	
	@Callback(direct=true)
	Object[] getResolution(Context ctx, Object[] args) {
		return new Object[]{panel.getCurCols(), panel.getCurRows()};
	}
	
	@Callback(direct=true)
	Object[] setResolution(Context ctx, Object[] args) {
		if(args.length < 2) return null;
		int w = ((Number)args[0]).intValue();
		int h = ((Number)args[1]).intValue();
		boolean worked = panel.setResolution(w, h);
		if(worked)
			ctx.signal("screen_resized", SimScreen.screenAddress, w, h);
		return new Object[]{worked};
	}
	
	@Callback(direct=true)
	Object[] get(Context ctx, Object[] args) {
		if(args.length < 2) return null;
		return panel.getPixel(((Number)args[0]).intValue(), ((Number)args[1]).intValue());
	}
	
	@Callback(direct=true)
	Object[] set(Context ctx, Object[] args) {
		if(args.length < 3) return null;
		return new Object[]{panel.set(background, foreground, ((Number)args[0]).intValue(), ((Number)args[1]).intValue(),
				toString(args[2]), args.length >= 4 && args[3] != null && !args[3].equals(Boolean.FALSE))};
	}
	
	@Callback(direct=true)
	Object[] copy(Context ctx, Object[] args) {
		if(args.length < 6) return null;
		return new Object[]{panel.copy(((Number)args[0]).intValue(), ((Number)args[1]).intValue(),
				((Number)args[2]).intValue(), ((Number)args[3]).intValue(),
				((Number)args[4]).intValue(), ((Number)args[5]).intValue())};
	}
	
	@Callback(direct=true)
	Object[] fill(Context ctx, Object[] args) {
		if(args.length < 5) return null;
		return new Object[]{panel.fill(background, foreground, ((Number)args[0]).intValue(), ((Number)args[1]).intValue(),
				((Number)args[2]).intValue(), ((Number)args[3]).intValue(),
				toString(args[4]).charAt(0))};
	}
	
}
