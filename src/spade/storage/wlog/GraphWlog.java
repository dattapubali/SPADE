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
    private final static int loglength = 20;

    private Graph spadeGraph = null;
    private Map<String,String> pidVertexMap = null;

    public GraphWlog(String dotfilepath){
        importModifiedSpadeGraph(dotfilepath);
    }

    public Graph getSpadeGraph(){
        return spadeGraph;
    }

    public void importModifiedSpadeGraph(String path){
        spadeGraph = Graph.importGraph(path);
    }

    public Graph getProcessLineage (String procname) {

        Graph unionGraph = new Graph();

        Set<AbstractVertex> abstractVertices = spadeGraph.vertexSet();

        for (AbstractVertex v : abstractVertices) {
            String type = v.getAnnotation("type");
            if(type == null || type.isEmpty())
                continue;
            if (!type.equalsIgnoreCase("Process")) continue;
            if ((v.getAnnotation("name")).equals(procname)) {
                Graph depGraph = getDepGraph(v);
                unionGraph = Graph.union(unionGraph, depGraph);
            }
        }

        return unionGraph;
    }

    public Graph generateLineageGraph(String procname){
        if(spadeGraph==null) {
            System.err.println("Spade Graph is not imported");
            return null;
        }

        Graph lineageGraph = getProcessLineage(procname);
        return lineageGraph;
        //lineageGraph.exportGraph(dirpath + lineageGraphString);
    }

    public Graph generatePrunedGraph(String procname){
        if(spadeGraph==null) {
            System.err.println("Spade Graph is not imported");
            return null;
        }
        Graph lineageGraph = getProcessLineage(procname);
        Graph pruned = Graph.remove(spadeGraph,lineageGraph);
        //pruned.exportGraph(dirpath + prunedGraphString);
        return pruned;
    }

    public Graph getDepGraph(AbstractVertex root){
        String hash = root.bigHashCode();
        Graph lineageBackward = spadeGraph.getLineage(hash,AbstractStorage.DIRECTION_ANCESTORS,maxdepth);
        Graph lineageForward = spadeGraph.getLineage(hash,AbstractStorage.DIRECTION_DESCENDANTS,maxdepth);

        return Graph.union(lineageBackward, lineageForward);
    }

    public void graftApplicationNodes(){
        if(pidVertexMap == null){
            pidVertexMap = scanPidNodes(spadeGraph);
        }
        Set<AbstractVertex> abstractVertices = spadeGraph.vertexSet();
        for(AbstractVertex v : abstractVertices) {
            String type = v.getAnnotation("type");
            if(type == null || type.isEmpty())
                continue;
            if (type.equalsIgnoreCase("Application")) continue;

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
            String type = v.getAnnotation("type");
            if(type == null || type.isEmpty())
                continue;
            if (!type.equalsIgnoreCase("Process")) continue;
            String pid = v.getAnnotation("pid");
            pidmap.put(pid,v.bigHashCode());
        }
        return pidmap;
    }

    public static void exportDotGraph(Graph g, String file, String logKeyword){
        shortenLogMessages(g, logKeyword);
        g.exportGraph(dirpath + file );
    }

    private static void shortenLogMessages(Graph g, String keyword) {
        Set<AbstractVertex> abstractVertices = g.vertexSet();
        for(AbstractVertex v : abstractVertices) {
            if (!v.getAnnotation("type").equalsIgnoreCase("Application")) continue;

            //editing the log msg here
            String msg = v.getAnnotation("log");
            int startindex = msg.indexOf(keyword);
            if(startindex <0) startindex = 0;

            int endindex = msg.length() > startindex+loglength? startindex+loglength : msg.length();

            g.addAnnotationToVertex(v, "log", msg.substring(startindex, endindex));
        }
    }

    public static void main(String[] args){
        GraphWlog wlog = new GraphWlog("/Users/pubalidatta/UIUC/projects/SPADE/graphdots/nginx.dot");
        //importModifiedSpadeGraph("/Users/pubalidatta/UIUC/projects/SPADE/appprov.dot");
        wlog.graftApplicationNodes();
        NodeSplitter n = new NodeSplitter(wlog.getSpadeGraph());

        String logKeyword = "GET";
        //n.partitionExecution("proftpd","FTP session closed");
        n.partitionExecution("nginx",logKeyword);
        Graph g1 = wlog.generateLineageGraph("nginx");

        //exportDotGraph(n.getGraph(),"nginx"+partitionedGraph);
        exportDotGraph(g1,"nginx"+lineageGraphString, logKeyword);

        //Graph g2 = generatePrunedGraph("ftpbench");
        //exportDotGraph(g1,lineageGraphString);
    }
}
