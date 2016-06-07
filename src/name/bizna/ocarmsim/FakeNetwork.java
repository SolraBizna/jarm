package name.bizna.ocarmsim;

import java.util.HashMap;
import java.util.Map;

import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Network;
import li.cil.oc.api.network.Node;

public class FakeNetwork implements Network {
	
	private HashMap<String,Node> nodes = new HashMap<String,Node>();
	
	public void add(Node node) {
		nodes.put(node.address(), node);
	}

	@Override
	public boolean connect(Node nodeA, Node nodeB) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean disconnect(Node nodeA, Node nodeB) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean remove(Node node) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Node node(String address) {
		return nodes.get(address);
	}

	@Override
	public Iterable<Node> nodes() {
		return nodes.values();
	}

	@Override
	public Iterable<Node> nodes(Node reference) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<Node> neighbors(Node node) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void sendToAddress(Node source, String target, String name,
			Object... data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void sendToNeighbors(Node source, String name, Object... data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void sendToReachable(Node source, String name, Object... data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void sendToVisible(Node source, String name, Object... data) {
		// TODO Auto-generated method stub

	}
	
	public void populateComponentList(Map<String, String> ret) {
		for(Node n : nodes.values()) {
			ret.put(n.address(), ((Component)n).name());
		}
	}

}
