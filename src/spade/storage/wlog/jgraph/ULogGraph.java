package spade.storage.wlog.jgraph;

import java.util.*;
public class ULogGraph {
	private Map<Integer, ULogNode> nodes;
	private Map<ULogNode, List<ULogLink>> adjLinks;
	private Map<ULogNode, List<ULogLink>> inLinks;
	
	private List<ULogNode> candidateCache;
	
	// error codes
	public static final int OpOkay = 0;
	public static final int NodeAlreadyPresent = 1;
	public static final int NodeNotFoundError = 2;
	
	public ULogGraph() {
		nodes = new HashMap<Integer, ULogNode>();
		adjLinks = new HashMap<ULogNode, List<ULogLink>> ();
		inLinks = new HashMap<ULogNode, List<ULogLink>> ();
		candidateCache = null;
	}
	
	/** 
	 * Emulate networkx as much as possible 
	 */

	public Map<Integer, ULogNode> Nodes() {
		return nodes;
	}
	
	/**
	 * Caller should handle the null case
	 */
	public ULogNode GetNode(int id) {
		return nodes.get(id);
	}
	
	public int AddNode(ULogNode n) {
		if (nodes.get(n.GetId()) == null) {
			nodes.put(n.GetId(), n); return OpOkay;
		}
		
		return NodeAlreadyPresent;
	}
	
	public int AddEdge(int srcId, int dstId) {
		ULogNode src = nodes.get(srcId);
		ULogNode dst = nodes.get(dstId);
		
		if (src == null || dst == null)
			return NodeNotFoundError;
		return AddEdge(src, dst);
	}
	
	public int AddEdge(ULogNode src, ULogNode dst) {
		if (nodes.containsKey(src.GetId()) &&
				nodes.containsKey(dst.GetId())) {
			// create the edge
			ULogLink edge = new ULogLink(src, dst);
			_addAdjEdge(edge);
			return OpOkay;
		}
		return NodeNotFoundError;
	}
	
	public List<ULogLink> GetInEdges(ULogNode n) {
		if (!inLinks.containsKey(n)) {
			return new LinkedList<ULogLink> ();
		}
		return inLinks.get(n);
	}
	
	public List<ULogLink> GetInEdges(int id) {
		ULogNode n = nodes.get(id);
		
		return GetInEdges(n);
	}
	
	public List<ULogLink> GetOutEdges(ULogNode n) {
		if (!adjLinks.containsKey(n)) {
			return new LinkedList<ULogLink> ();
		}
		return adjLinks.get(n);
	}
	
	public List<ULogLink> GetOutEdges(int id) {
		ULogNode n = nodes.get(id);
		
		return GetOutEdges(n);
	}
	
	public ULogNode FindStartNode() {
		// iterate over the nodes and find the starting one
		for (Map.Entry<Integer, ULogNode> entry : nodes.entrySet()) {
			if (entry.getValue().IsStartNode())
				return entry.getValue();
		}
		
		return null;
	}
	
	public List<ULogNode> GetCache() {
		if (candidateCache == null) {
			candidateCache = new LinkedList<ULogNode> ();
			populateCache();
		}
		
		return candidateCache;
	}

	private void _addAdjEdge(ULogLink edge) {
		ULogNode src = edge.src;
		ULogNode dst = edge.dst;

		// add it the adjacent links
		List <ULogLink> outEdges;
		if (adjLinks.containsKey(src)) {
			// already in there
			outEdges = adjLinks.get(src);
			outEdges.add(edge);
		} else {
			// not found there
			outEdges = new LinkedList<ULogLink>();
			outEdges.add(edge);
			adjLinks.put(src, outEdges);
		}
		
		List <ULogLink> inEdges = inLinks.get(dst);
		if (inEdges == null) {
			// not found there
			inEdges = new LinkedList<ULogLink>();
			inEdges.add(edge);
			inLinks.put(dst,  inEdges);
		} else {
			// already in there
			inEdges.add(edge);
		}
	}
	
	private void populateCache() {
		for (Map.Entry<Integer, ULogNode> entry: nodes.entrySet()) {
			ULogNode node = entry.getValue();
			
			if (node.IsStartNode() || node.IsLikelyExec()) {
				candidateCache.add(node);
			}
		}
	}
}
