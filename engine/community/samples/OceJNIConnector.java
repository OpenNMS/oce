import java.util.List;
import java.util.ArrayList;
import java.security.MessageDigest;
import java.math.BigInteger;
import  java.security.NoSuchAlgorithmException;

public class OceJNIConnector {
	
	private static OceJNIConnector connector;

	//Making it singleton
	private OceJNIConnector() {}

	public static OceJNIConnector getInstance() {
		if(connector == null)
			connector = new OceJNIConnector();

		return connector;
	}
	
	public IGraphResponse sendSimpleIGraphRequest (OCEGraph graph) {
		int count = graphSize(graph.getVertices(), graph.getEdges());
		return new IGraphResponse("Number of vertices : " + count);
	}
	
	private native int graphSize(Node [] nodeArray, Edge [] edgeArray);
	
	public static void main(String [] args) {
		//This is just for testing purpose
		OCEGraph graph = new OCEGraph();
		graph.initSimpleData();
		OceJNIConnector client = OceJNIConnector.getInstance();
		IGraphResponse response = client.sendSimpleIGraphRequest(graph);
		System.out.println(response);
	}
	
	
	static {
        System.loadLibrary("OceJNIConnector");
    }
	
	class IGraphRequest {}

	class IGraphResponse {
		private String message;
		
		public IGraphResponse(String message) {
			this.message = message;
		}
		
		@Override
		public String toString() {
			return message;
		}
	}
}

class OCEGraph {
	private List<Edge> edges;
	private List<Node> vertices;

	public void initSimpleData() {
		edges = new ArrayList<>();
		vertices = new ArrayList<>();
		Node device = new Node("Device", null, "#22", 1,  "Device #22");
		Node card1 = new Node("Card", null, "#22_#1", 2, "Card #22_#1");
		Node card2 = new Node("Card", null, "#22_#2", 3, "Card #22_#2");

		Node port1 = new Node("Port", null, "#22_#1_#1", 4, "Port #22_#1_#1");
		Node port2 = new Node("Port", null, "#22_#1_#2", 5, "Port #22_#1_#2");
		Node port3 = new Node("Port", null, "#22_#1_#3", 6, "Port #22_#1_#3");
		Node port4 = new Node("Port", null, "#22_#1_#4", 7, "Port #22_#1_#4");

		Node port5 = new Node("Port", null, "#22_#2_#1", 8, "Port #22_#2_#1");
		Node port6 = new Node("Port", null, "#22_#2_#2", 9, "Port #22_#2_#2");
		Node port7 = new Node("Port", null, "#22_#2_#3", 10, "Port #22_#2_#3");
		Node port8 = new Node("Port", null, "#22_#2_#4", 11, "Port #22_#2_#4");

		//Add vertices
		vertices.add(device);
		vertices.add(card1);
		vertices.add(card2);
		vertices.add(port1);
		vertices.add(port2);
		vertices.add(port3);
		vertices.add(port4);
		vertices.add(port5);
		vertices.add(port6);
		vertices.add(port7);
		vertices.add(port8);

		//Add adges
		edges.add(new Edge(device, card1));
		edges.add(new Edge(device, card2));

		edges.add(new Edge(card1, port1));
		edges.add(new Edge(card1, port2));
		edges.add(new Edge(card1, port3));
		edges.add(new Edge(card1, port4));

		edges.add(new Edge(card2, port5));
		edges.add(new Edge(card2, port6));
		edges.add(new Edge(card2, port7));
		edges.add(new Edge(card2, port8));

		for(Edge e : edges) {
			System.out.println(e.getStartNode().getUniqueId() + ":" + e.getEndNode().getUniqueId());
		}
	}

	public Node [] getVertices() {
		return vertices.toArray(new Node[0]);
	}

	public Edge [] getEdges() {
		return edges.toArray(new Edge[0]);
	}
}

class Edge {
	public Node start;
	public Node end;
	public double weight;

	public Edge(Node start, Node end) {
		this.start = start;
		this.end = end;
	}

	public Node getStartNode() {
		return start;
	}

	public Node getEndNode() {
		return end;
	}
}

class Node {
	private String type;
	private String subType;
	private String id; // friendly unique identifier
	private String friendlyName;
	private int unqueId; //internal digital unique identifier (it is int as igraph uses int for its vertices ids)
	//public List<Edge> connections;

	public Node(){}

	public Node(String type, String subType, String id, String friendlyName) {
		this.type = type;
		this.subType = subType;
		this.id = id;
		this.friendlyName = friendlyName;
	}

	public Node(String type, String subType, String id, int uniqueId, String friendlyName) {
		this(type, subType, id, friendlyName);
		setUniqueId(uniqueId);
	}

	public String getId() {
		return id;
	}

	public int getUniqueId() {
		return  unqueId;
	}
	
	//private as we don't want someone modifies it
	private void setUniqueId(int unqueId) {
		this.unqueId = unqueId;
	}

	private long generateUniqueId(String id) {
		try {
			MessageDigest m = MessageDigest.getInstance("SHA-256");
			m.reset();
			m.update(id.getBytes());
			byte[] digest = m.digest();
			BigInteger bigInt = new BigInteger(1,digest);
			//System.out.println(bigInt);
			
			return bigInt.longValue();
		} catch (NoSuchAlgorithmException e) {
			//TODO
		}

		return (-1);
	}

	private int generateIntUniqueId(String id) {
		int hash = id.hashCode();
		MessageDigest m;
		try {
			m = MessageDigest.getInstance("MD5");
			m.reset();
			m.update(id.getBytes());
			byte[] digest = m.digest();
			BigInteger bigInt = new BigInteger(1,digest);
			String hashtext = bigInt.toString(10);
			// Now we need to zero pad it if we actually want the full 32 chars.
			while(hashtext.length() < 32 ){
				hashtext = "0"+hashtext;
			}
			int temp = 0;
			for(int i =0; i<hashtext.length();i++){
				char c = hashtext.charAt(i);
				temp+=(int)c;
			}
			//System.out.println(hash + "+" +temp);
			return hash+temp;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return hash;
	}
}