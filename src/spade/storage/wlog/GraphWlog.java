package spade.storage.wlog;

import org.apache.commons.codec.binary.Hex;
import spade.core.*;
import spade.edge.cdm.SimpleEdge;

import java.util.*;

public class GraphWlog {

    private final static String dirpath = "/tmp/";
    private final static String lineageGraphString = "lineage.dot";
    private final static String prunedGraphString = "pruned.dot";
    private final static String appGraftString = "appGrafted.dot";
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

    public static void generateLineageDot(String procname){
        if(spadeGraph==null) {
            System.err.println("Spade Graph is not imported");
            return;
        }

        Graph lineageGraph = getProcessLineage(procname);
        lineageGraph.exportGraph(dirpath + lineageGraphString);
    }

    public static void generatePrunedDot(String procname){
        if(spadeGraph==null) {
            System.err.println("Spade Graph is not imported");
            return;
        }
        Graph lineageGraph = getProcessLineage(procname);
        Graph pruned = Graph.remove(spadeGraph,lineageGraph);
        pruned.exportGraph(dirpath + prunedGraphString);

    }

    public static Graph getDepGraph(AbstractVertex root){
        String hash = root.bigHashCode();
        Graph lineageBackward = spadeGraph.getLineage(hash,AbstractStorage.DIRECTION_ANCESTORS,maxdepth);
        Graph lineageForward = spadeGraph.getLineage(hash,AbstractStorage.DIRECTION_DESCENDANTS,maxdepth);

        return Graph.union(lineageBackward, lineageForward);
    }

    public static void graftApplicationNodes(){
        if(pidVertexMap == null){
            scanPidNodes();
        }
        Set<AbstractVertex> abstractVertices = spadeGraph.vertexSet();
        for(AbstractVertex v : abstractVertices) {
            if (!v.getAnnotation("type").equalsIgnoreCase("Application")) continue;
            String relatedProcess = v.getAnnotation("pid");
            AbstractVertex procnode = spadeGraph.getVertex(pidVertexMap.get(relatedProcess));
            System.out.println(v);
            System.out.println(procnode);
            SimpleEdge edge = new SimpleEdge(v,procnode);
            spadeGraph.putEdge(edge);
        }
        spadeGraph.exportGraph(dirpath + appGraftString );
    }

    public static void scanPidNodes(){
        Set<AbstractVertex> abstractVertices = spadeGraph.vertexSet();
        for(AbstractVertex v : abstractVertices){
            if (!v.getAnnotation("type").equalsIgnoreCase("Process")) continue;
            String pid = v.getAnnotation("pid");
            if(pidVertexMap==null){
                pidVertexMap = new HashMap<>();
            }
            pidVertexMap.put(pid,v.bigHashCode());
        }
    }

    public static void main(String[] args){
        importModifiedSpadeGraph("/Users/pubalidatta/UIUC/projects/SPADE/appprov.dot");
        graftApplicationNodes();
        //generateLineageDot("ftpbench");
        //generatePrunedDot("ftpbench");
    }
}
