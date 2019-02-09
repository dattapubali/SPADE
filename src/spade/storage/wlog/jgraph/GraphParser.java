package spade.storage.wlog.jgraph;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class GraphParser {
	private static final Logger l = LogManager.getLogger(GraphParser.class.getName());

	private String fileName;
	private FileReader freader;
	
	public GraphParser(String _fileName) {
		fileName = _fileName;
		
		try {
			File inFile = new File(fileName);
			freader = new FileReader(inFile);
		} catch (IOException e) {
			System.err.println("Cannot read input file " + fileName);
			System.exit(-1);
		}
	}
	
	// parse a json file and generate a graph from it
	public ULogGraph ReadGraph() {
		int err;
		ULogGraph graph = null;
		JsonElement jsonTree = new JsonParser().parse(freader);
		
		if (jsonTree.isJsonObject()) {
			graph = new ULogGraph();
			JsonObject obj = jsonTree.getAsJsonObject();

			// get graph attributes
			// boolean directed = obj.get("directed").getAsBoolean();
			// boolean multigraph = obj.get("multigraph").getAsBoolean();
			
			// now get the list of nodes
			JsonElement nodeTree = obj.get("nodes");
			
			// should be an array
			if (nodeTree.isJsonArray()) {
				JsonArray nodeList = nodeTree.getAsJsonArray();
				
				for (JsonElement nodeEntry : nodeList) {
					ULogNode node = BuildNode(nodeEntry.getAsJsonObject());
					
					err = graph.AddNode(node);
					if (err != 0) {
						l.error("Adding duplicate node, check your json file");
						return null;
					}
				}
			}
			
			// okay now get the links
			JsonElement linkTree = obj.get("links");
			if (linkTree.isJsonArray()) {
				JsonArray linkList = linkTree.getAsJsonArray();
				
				for (JsonElement linkEntry : linkList) {
					JsonObject edgeObj = linkEntry.getAsJsonObject();
					
					int srcId = edgeObj.get("source").getAsInt();
					int dstId = edgeObj.get("target").getAsInt();
					
					err = graph.AddEdge(srcId, dstId);
					if (err != 0) {
						l.debug("Could not add edge from {} to {}: ({})", srcId, dstId, err);
						return null;
					}
					
					l.debug("Added edge from {} to {}", srcId, dstId);
				}
			}
		}
		
		// null if not able to parse
		return graph;
	}
	
	private ULogNode BuildNode(JsonObject nodeObj) {
		// get those parameters
		int id 			= nodeObj.get("id").getAsInt();
		String val 		= nodeObj.get("val").getAsString();
		
		// check if it is a loop
		Boolean isLoop = false;
		if (nodeObj.has("is_loop")) {
			isLoop 	= nodeObj.get("is_loop").getAsBoolean();
		}
		
		// check if is start node
		Boolean isStart = false;
		if (nodeObj.has("is_start")) {
			isStart = nodeObj.get("is_start").getAsBoolean();
		}
		
		// check for is end node
		Boolean isEnd = false;
		if (nodeObj.has("is_end")) {
			isEnd = nodeObj.get("is_end").getAsBoolean();
		}
		
		// check for is exec
		Boolean isExec = false;
		if (nodeObj.has("is_exec")) {
			isExec = nodeObj.get("is_exec").getAsBoolean();
		}
		
		// check for the phony flag
		Boolean isPhony = false;
		if (nodeObj.has("is_phony")) {
			isPhony = nodeObj.get("is_phony").getAsBoolean();
		}
		
		// check for function heads
		Boolean isFuncHead = false;
		if (nodeObj.has("is_func_head")) {
			isFuncHead = nodeObj.get("is_func_head").getAsBoolean();
		}
		
		// check for function returns
		Boolean isFuncOut = false;
		if (nodeObj.has("is_func_out")) {
			isFuncOut = nodeObj.get("is_func_out").getAsBoolean();
		}
		
		// create the node and assign the attributes
		ULogNode node = new ULogNode(id);
		node.SetAttribute("is_loop", isLoop);
		node.SetAttribute("is_start", isStart);
		node.SetAttribute("is_end", isEnd);
		node.SetAttribute("is_exec", isExec);
		node.SetAttribute("is_phony", isPhony);
		node.SetAttribute("is_func_head", isFuncHead);
		node.SetAttribute("is_func_out", isFuncOut);
		node.SetAttribute("val", val);
		
		l.debug("Added node " + node);
		
		return node;
	}
	
	
	public static void main(String[] args) {
		String fname = "src/01_test.json";
		
		GraphParser parser = new GraphParser(fname);
		
		parser.ReadGraph();
	}

}
