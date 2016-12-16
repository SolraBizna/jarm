package name.bizna.ocarmsim.components;

import javax.swing.JComponent;
import li.cil.oc.api.machine.Machine;
import name.bizna.ocarmsim.SimScreenPanel;

public class SimKeyboard extends SimComponent {

	private SimScreenPanel panel;

	public SimKeyboard(Machine machine, String address) {
		super(machine, address);
	}

	public SimScreenPanel getPanel() {
		return panel;
	}

	public void setPanel(SimScreenPanel panel) {
		this.panel = panel;
	}

	@Override
	public String name() {
		return "keyboard";
	}

	@Override
	public JComponent getUIComponent() {
		return null;
	}

	@Override
	public void reset() {
		// Keyboard is stateless.
	}
}
