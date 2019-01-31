package spade.storage.wlog;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.edge.cdm.SimpleEdge;
import spade.vertex.opm.Process;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

public class NodeSplitter {

    // go to each process node by using pidmap
    // check if the processname matches
    // if it does split the node
    // input: process name, split string
    // scan all incoming vertex, sort the event id
    private Graph g;
    private Map<String,String> pidVertexMap = null;

    public NodeSplitter(Graph g){
        this.g = g;
        this.pidVertexMap = GraphWlog.scanPidNodes(g);
    }

    public Graph getGraph() {
        return g;
    }

    public void partitionExecution(String process, String logMsg){
        for(Map.Entry<String,String> entry : pidVertexMap.entrySet()){
            String vertexhash = entry.getValue();
            AbstractVertex processNode = g.getVertex(vertexhash);

            if(processNode.getAnnotation("name").equalsIgnoreCase(process)){
                System.out.println("Found vertex with procname "+processNode.getAnnotation("name")+" "
                        +processNode.getAnnotation("pid"));
                splitNode(processNode,entry.getValue(), logMsg);
            }
        }

    }

    private void splitNode(AbstractVertex procnode, String processhash, String logMsg) {

        int count = 1;
        int lastsplitIdx = 0;
        Graph childSubgraph = g.getChildren(processhash);
        Set<AbstractVertex> vertices = childSubgraph.vertexSet();

        System.out.println(vertices);

        // remove the process node itself
        vertices.remove(procnode);

        AbstractVertex[] vertexArr = buildVertexArray(vertices);
        //printArray(vertexArr);
        Arrays.sort(vertexArr, Comparator.comparing(a -> Long.valueOf(a.getAnnotation("appeventid"))));
        printArray(vertexArr);

        for(int i =0 ; i < vertexArr.length; i++){
            AbstractVertex v = vertexArr[i];

            String logstring = v.getAnnotation("log");

            // if the log msg is contained in the applog node then split the original procnode
            if(logstring.toLowerCase().contains(logMsg.toLowerCase())){
                if(i==vertexArr.length-1)
                    continue;
                // split node here
                System.out.println("Going to split node now");
                AbstractVertex newNode = new Process();
                newNode.addAnnotations(procnode.getAnnotations());
                newNode.addAnnotation("name",procnode.getAnnotation("name")+(count++));
                g.putVertex(newNode);

                //create an edge form the original node to this one
                g.putEdge(new SimpleEdge(newNode,procnode));

                //remove edges till splitpoint from original node, add them to new node
                for(int j=lastsplitIdx;j<=i;j++){
                    AbstractEdge edge = childSubgraph.getEdge(vertexArr[j].bigHashCode(),procnode.bigHashCode());
                    g.removeEdge(edge);
                    g.putEdge(new SimpleEdge(vertexArr[j],newNode));
                }

                // update last split index
                lastsplitIdx = i;
            }
        }
    }

    private AbstractVertex[] buildVertexArray(Set<AbstractVertex> vertices) {
        int i=0;
        AbstractVertex[] arr = new AbstractVertex[vertices.size()];
        for(AbstractVertex v : vertices)
            arr[i++] = v;
        return arr;
    }

    private void printArray(AbstractVertex[] vertexArr) {
        for(int i=0; i<vertexArr.length;i++)
            System.out.println(vertexArr[i].toString());
    }

}
