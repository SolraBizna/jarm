package name.bizna.ocarmsim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import javax.swing.JComponent;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.machine.MachineHost;
import li.cil.oc.api.machine.Signal;
import li.cil.oc.api.machine.Value;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Network;
import li.cil.oc.api.network.Node;
import name.bizna.jarm.Debugger;
import name.bizna.ocarmsim.components.SimComponent;
import net.minecraft.nbt.NBTTagCompound;

public class FakeMachine implements Machine {

	private class MachineNode extends SimComponent {

		public MachineNode(Machine machine, String address) {
			super(machine, address);
		}

		@Override
		public String name() {
			return "computer";
		}

		@Override
		public Network network() {
			return network;
		}

		@Callback
		public Object[] start(Context ctx, Object[] args) {
			return new Object[]{FakeMachine.this.start()};
		}

		@Callback
		public Object[] stop(Context ctx, Object[] args) {
			return new Object[]{FakeMachine.this.stop()};
		}

		@Callback
		public Object[] isRunning(Context ctx, Object[] args) {
			return new Object[]{FakeMachine.this.isRunning()};
		}

		@Callback
		public Object[] beep(Context ctx, Object[] args) {
			if (args.length == 1 && args[0] instanceof String) {
				FakeMachine.this.beep((String) args[0]);
			} else {
				float freq = 200f;
				float duration = 0.5f;
				if (args.length >= 1 && args[0] instanceof Number) {
					freq = ((Number) args[0]).floatValue();
				}
				if (args.length >= 2 && args[1] instanceof Number) {
					duration = ((Number) args[1]).floatValue();
				}
				FakeMachine.this.beep((short) freq, (short) (duration * 20));
			}
			return new Object[]{};
		}

		@Override
		public JComponent getUIComponent() {
			return null;
		}

		@Override
		public void reset() {
			signals.clear();
		}

	}
	private final Debugger debugger;
	private final MachineNode node = new MachineNode(this, UUID.randomUUID().toString());
	private final FakeNetwork network = new FakeNetwork();
	private final Queue<Signal> signals = new ArrayBlockingQueue<>(32);
	private final long startTime = System.currentTimeMillis();

	FakeMachine(Debugger debugger) {
		this.debugger = debugger;
	}

	@Override
	public boolean canUpdate() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void update() {
		// TODO Auto-generated method stub

	}

	@Override
	public Node node() {
		return node;
	}

	@Override
	public void onConnect(Node node) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onDisconnect(Node node) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onMessage(Message message) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean canInteract(String player) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isRunning() {
		return true;
	}

	@Override
	public boolean isPaused() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean start() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean pause(double seconds) {
		try {
			Thread.sleep((long) (seconds * 1000));
		} catch (InterruptedException e) {
		}
		return true;
	}

	@Override
	public boolean stop() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean signal(String name, Object... args) {
		if (OCARM.instance.shouldTraceInvocations()) {
			OCARM.logger.info("signal pushed: %s", name);
		}

		synchronized (signals) {
			signals.add(new SimSignal(name, args));
		}
		debugger.onSignal(name, args);
		return true;
	}

	@Override
	public MachineHost host() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onHostChanged() {
		// TODO Auto-generated method stub

	}

	@Override
	public Architecture architecture() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, String> components() {
		Map<String, String> ret = new HashMap<>();
		network.populateComponentList(ret);
		return ret;
	}

	@Override
	public int componentCount() {
		return network.getNodeCount();
	}

	@Override
	public int maxComponents() {
		return 1048576;
	}

	@Override
	public double getCostPerTick() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setCostPerTick(double value) {
		// TODO Auto-generated method stub

	}

	@Override
	public String tmpAddress() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String lastError() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long worldTime() {
		return System.currentTimeMillis() / 50;
	}

	@Override
	public double upTime() {
		return (System.currentTimeMillis() - startTime) / 1000.0;
	}

	@Override
	public double cpuTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void beep(short frequency, short duration) {
		OCARM.logger.info("BEEP! %dHz %d duration", frequency, duration);
	}

	@Override
	public void beep(String pattern) {
		OCARM.logger.info("BEEP! pattern %s", pattern);
	}

	@Override
	public boolean crash(String message) {
		OCARM.logger.error("CRASH! %s", message);
		return true;
	}

	@Override
	public Signal popSignal() {
		synchronized (signals) {
			if (signals.isEmpty()) {
				OCARM.logger.info("signal poped: %s", "{none}");
				return null;
			} else {
				Signal signal = signals.poll();
				if (OCARM.instance.shouldTraceInvocations()) {
					OCARM.logger.info("signal poped: %s", signal.name());
				}

				return signal;
			}
		}
	}

	@Override
	public Map<String, Callback> methods(Object value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object[] invoke(String address, String method, Object[] args)
			throws Exception {
		Node n = network.node(address);
		if (n == null || !(n instanceof Component)) {
			throw new NoSuchMethodError();
		}
		return ((Component) n).invoke(method, this, args);
	}

	@Override
	public Object[] invoke(Value value, String method, Object[] args)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] users() {
		return null;
	}

	@Override
	public void addUser(String name) throws Exception {
		// silently succeed
	}

	@Override
	public boolean removeUser(String name) {
		return false;
	}

	@Override
	public void load(NBTTagCompound nbt) {
		// TODO Auto-generated method stub

	}

	@Override
	public void save(NBTTagCompound nbt) {
		// TODO Auto-generated method stub

	}

	public void addNode(Node n) {
		network.add(n);
	}

	List<SimComponent> getComponents() {
		List<SimComponent> components = new ArrayList<>();
		for (Node n : network.nodes()) {
			if (n instanceof SimComponent) {
				components.add((SimComponent) n);
			}
		}

		return components;
	}
}
