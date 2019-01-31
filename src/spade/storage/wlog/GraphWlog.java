package spade.storage.wlog;

import org.apache.commons.codec.binary.Hex;
import spade.core.*;
import spade.edge.cdm.SimpleEdge;

import javax.xml.soap.Node;
import java.util.*;

public class GraphWlog {

    private final static String dirpath = "/tmp/";
    private final static String lineageGraphString = "lineage.dot";
    private final static String prunedGraphString = "pruned.dot";
    private final static String appGraftString = "appGrafted.dot";
    private final static String partitionedGraph = "partitionGraph.dot";
    private final static int maxdepth = 10;
    private static Graph spadeGraph = null;
    private static Map<String,String> pidVertexMap = null;


    public static void importModifiedSpadeGraph(String path){
        spadeGraph = Graph.importGraph(path);
    }

    public static Graph getProcessLineage (String procname) {

        Graph unionGraph = new Graph();

        Set<AbstractVertex> abstractVertices = spadeGraph.vertexSet();

        for (AbstractVertex v : abstractVertices) {
            if (!v.getAnnotation("type").equalsIgnoreCase("Process")) continue;
            if ((v.getAnnotation("name")).equals(procname)) {
                Graph depGraph = getDepGraph(v);
                unionGraph = Graph.union(unionGraph, depGraph);
            }
        }

        return unionGraph;
    }

    public static Graph generateLineageGraph(String procname){
        if(spadeGraph==null) {
            System.err.println("Spade Graph is not imported");
            return null;
        }

        Graph lineageGraph = getProcessLineage(procname);
        return lineageGraph;
        //lineageGraph.exportGraph(dirpath + lineageGraphString);
    }

    public static Graph generatePrunedGraph(String procname){
        if(spadeGraph==null) {
            System.err.println("Spade Graph is not imported");
            return null;
        }
        Graph lineageGraph = getProcessLineage(procname);
        Graph pruned = Graph.remove(spadeGraph,lineageGraph);
        //pruned.exportGraph(dirpath + prunedGraphString);
        return pruned;
    }

    public static Graph getDepGraph(AbstractVertex root){
        String hash = root.bigHashCode();
        Graph lineageBackward = spadeGraph.getLineage(hash,AbstractStorage.DIRECTION_ANCESTORS,maxdepth);
        Graph lineageForward = spadeGraph.getLineage(hash,AbstractStorage.DIRECTION_DESCENDANTS,maxdepth);

        return Graph.union(lineageBackward, lineageForward);
    }

    public static void graftApplicationNodes(){
        if(pidVertexMap == null){
            pidVertexMap = scanPidNodes(spadeGraph);
        }
        Set<AbstractVertex> abstractVertices = spadeGraph.vertexSet();
        for(AbstractVertex v : abstractVertices) {
            if (!v.getAnnotation("type").equalsIgnoreCase("Application")) continue;

            //editing the log msg here
            String msg = v.getAnnotation("log");
            //v.addAnnotation("log",msg.substring(msg.length()/2));

            String relatedProcess = v.getAnnotation("pid");
            AbstractVertex procnode = spadeGraph.getVertex(pidVertexMap.get(relatedProcess));
            if(procnode!=null) {
                SimpleEdge edge = new SimpleEdge(v, procnode);
                spadeGraph.putEdge(edge);
            }
        }
        //spadeGraph.exportGraph(dirpath + appGraftString );
    }

    public static Map<String,String> scanPidNodes(Graph g){
        if(g == null) {
            System.err.println("Graph is not imported.");
        }
        Map<String,String> pidmap = new HashMap<>();
        Set<AbstractVertex> abstractVertices = g.vertexSet();
        for(AbstractVertex v : abstractVertices){
            if (!v.getAnnotation("type").equalsIgnoreCase("Process")) continue;
            String pid = v.getAnnotation("pid");
            pidmap.put(pid,v.bigHashCode());
        }
        return pidmap;
    }

    public static void exportDotGraph(Graph g, String file){
        g.exportGraph(dirpath + file );
    }

    public static void main(String[] args){
        importModifiedSpadeGraph("/Users/pubalidatta/UIUC/projects/SPADE/appprov.dot");
        graftApplicationNodes();
        NodeSplitter n = new NodeSplitter(spadeGraph);
        n.partitionExecution("proftpd","FTP session closed");
        exportDotGraph(spadeGraph,partitionedGraph);
        //Graph g1 = generateLineageGraph("ftpbench");
        //Graph g2 = generatePrunedGraph("ftpbench");
        //exportDotGraph(g1,lineageGraphString);
    }
}
