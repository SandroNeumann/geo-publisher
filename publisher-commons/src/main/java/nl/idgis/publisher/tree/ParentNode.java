package nl.idgis.publisher.tree;

import java.io.Serializable;

public class ParentNode extends ValueNode implements Serializable {

	private static final long serialVersionUID = 8815083348398329740L;
	
	private final Node[] children;
	
	public ParentNode(String name, Object value, Node[] children) {
		super(name, value);
		
		this.children = children;
	}
	
	public Node[] getChildren() {
		return children;
	}
	
	@Override
	StringBuilder toStringBuilder(int depth) {
		StringBuilder sb = super.toStringBuilder(depth);
				
		for(Node child : children) {
			sb.append("\n");
			sb.append(child.toStringBuilder(depth + 1));			
		}
		
		return sb;
	}
}
