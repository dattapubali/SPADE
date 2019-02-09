package spade.storage.wlog.jgraph;

import java.text.MessageFormat;
import java.util.*;

public class ULogNode {
	private int id;
	public Hashtable<String, Object> attributes;
	
	public ULogNode(int _id) {
		id = _id;
		attributes = new Hashtable<String, Object>();
	}
	
	public int GetId() {
		return id;
	}
	
	public Object GetAttribute(String key) {
		// get attribute with the give key
		return attributes.get(key);
	}
	
	public void SetAttribute(String key, Object val) {
		if (!attributes.containsKey(key))
			attributes.put(key, val);
	}
	
	// for debugging
	public String toString() {
		if (attributes.containsKey("val"))
			return MessageFormat.format("< Node: {0} -- {1} >", id, attributes.get("val"));
		else
			return MessageFormat.format("< Node: {0} -- null >", id);
	}
	
	// shortcuts to the node's attributes that are useful
	public boolean IsStartNode() {
		if (attributes.containsKey("is_start")) {
			return ((boolean)attributes.get("is_start"));
		}
		return false;
	}
	
	public boolean IsEndNode() {
		if (attributes.containsKey("is_end")) {
			return ((boolean)attributes.get("is_end"));
		}
		return false;
	}
	
	public String GetStr() {
		return (String)attributes.get("val");
	}
	
	public boolean IsPhonyNode() {
		if (attributes.containsKey("is_phony")) {
			return ((boolean)attributes.get("is_phony"));
		}
		return false;
	}
	
	public boolean IsLikelyExec() {
		if (attributes.containsKey("is_exec")) {
			return ((boolean)attributes.get("is_exec"));
		}
		return false;
	}
	
	public boolean IsFuncHead() {
		if (attributes.containsKey("is_func_head")) {
			return ((boolean)attributes.get("is_func_head"));
		}
		return false;
	}
	
	public boolean IsFuncOut() {
		if (attributes.containsKey("is_func_out")) {
			return ((boolean)attributes.get("is_func_out"));
		}
		return false;
	}
	
	// overriding the equality operator
	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		
		if (!(o instanceof ULogNode)) 
			return false;
		
		ULogNode other = (ULogNode)o;
		return id == other.GetId();
	}
	
}
