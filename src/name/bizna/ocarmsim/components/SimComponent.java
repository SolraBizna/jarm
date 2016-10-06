package name.bizna.ocarmsim.components;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import javax.swing.JComponent;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Network;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import net.minecraft.nbt.NBTTagCompound;

public abstract class SimComponent implements Component {

	private static class CallbackBlob {

		Callback cb;
		Method m;

		CallbackBlob(Callback cb, Method m) {
			this.cb = cb;
			this.m = m;
		}
	}
	private HashMap<String, CallbackBlob> callbacks;

	private final Machine machine;
	private final String address;

	public SimComponent(Machine machine, String address) {
		this.machine = machine;
		this.address = address;
	}

	public abstract JComponent getUIComponent();
	
	public abstract void reset();

	public Machine getMachine() {
		return machine;
	}

	@Override
	public String address() {
		return address;
	}

	@Override
	public Environment host() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Visibility reachability() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Network network() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isNeighborOf(Node other) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean canBeReachedFrom(Node other) {
		return true;
	}

	@Override
	public Iterable<Node> neighbors() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<Node> reachableNodes() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void connect(Node node) {
		// TODO Auto-generated method stub

	}

	@Override
	public void disconnect(Node node) {
		// TODO Auto-generated method stub

	}

	@Override
	public void remove() {
		// TODO Auto-generated method stub

	}

	@Override
	public void sendToAddress(String target, String name, Object... data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void sendToNeighbors(String name, Object... data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void sendToReachable(String name, Object... data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void sendToVisible(String name, Object... data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void load(NBTTagCompound nbt) {
		// TODO Auto-generated method stub

	}

	@Override
	public void save(NBTTagCompound nbt) {
		// TODO Auto-generated method stub

	}

	@Override
	public Visibility visibility() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setVisibility(Visibility value) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean canBeSeenFrom(Node other) {
		// TODO Auto-generated method stub
		return false;
	}

	private void gatherCallbacks() {
		callbacks = new HashMap<String, CallbackBlob>();
		Class<? extends SimComponent> klaso = this.getClass();
		for (Method m : klaso.getDeclaredMethods()) {
			AnnotatedElement ae = m;
			Callback cb = ae.getAnnotation(Callback.class);
			if (cb == null) {
				continue;
			}
			String name = cb.value();
			if (name == null || name.isEmpty()) {
				name = m.getName();
			}
			callbacks.put(name, new CallbackBlob(cb, m));
		}
	}

	@Override
	public Collection<String> methods() {
		synchronized (this) {
			if (callbacks == null) {
				gatherCallbacks();
			}
			return callbacks.keySet();
		}
	}

	@Override
	public Callback annotation(String method) {
		synchronized (this) {
			if (callbacks == null) {
				gatherCallbacks();
			}
			CallbackBlob ret = callbacks.get(method);
			return ret == null ? null : ret.cb;
		}
	}

	@Override
	public Object[] invoke(String method, Context context, Object... arguments)
			throws Exception {
		synchronized (this) {
			if (callbacks == null) {
				gatherCallbacks();
			}
			CallbackBlob ret = callbacks.get(method);
			if (ret == null) {
				return null;
			}
			return (Object[]) ret.m.invoke(this, context, arguments);
		}
	}

	static String toString(Object obj) {
		if (obj instanceof String) {
			return (String) obj;
		} else {
			assert (obj instanceof byte[]);
			return new String((byte[]) obj, Charset.forName("UTF-8"));
		}
	}
}
