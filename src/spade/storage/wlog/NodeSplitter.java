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


    public void partitionExecution(String process, String logMsg){
        for(Map.Entry<String,String> entry : pidVertexMap.entrySet()){
            String vertexhash = entry.getValue();
            AbstractVertex processNode = g.getVertex(vertexhash);
            if(processNode.getAnnotation("name").equalsIgnoreCase(process)){
                splitNode(processNode,entry.getKey(), logMsg);
            }
        }

    }

    private void splitNode(AbstractVertex procnode, String processhash, String logMsg) {

        int count = 1;
        int lastsplitIdx = 0;
        Graph parentSubgraph = g.getParents(processhash);
        Set<AbstractVertex> vertices = parentSubgraph.vertexSet();

        // remove the process node itself
        vertices.remove(procnode);

        AbstractVertex[] vertexArr = vertices.stream().toArray(AbstractVertex[] ::new);

        Arrays.sort(vertexArr, Comparator.comparing(a -> Long.valueOf(a.getAnnotation("appeventid"))));

        for(int i =0 ; i < vertexArr.length; i++){
            AbstractVertex v = vertexArr[i];

            String logstring = v.getAnnotation("log");

            // if the log msg is contained in the applog node then split the original procnode
            if(logstring.toLowerCase().contains(logMsg.toLowerCase())){
                // split node here
                AbstractVertex newNode = new Process();
                newNode.addAnnotations(procnode.getAnnotations());
                newNode.addAnnotation("name",procnode.getAnnotation("name")+(count++));
                g.putVertex(newNode);

                //create an edge form the original node to this one
                g.putEdge(new SimpleEdge(procnode,newNode));

                //remove edges till splitpoint from original node, add them to new node
                for(int j=lastsplitIdx;j<=i;j++){
                    AbstractEdge edge = parentSubgraph.getEdge(procnode.bigHashCode(),vertexArr[j].bigHashCode());
                    g.removeEdge(edge);
                    g.putEdge(new SimpleEdge(newNode,vertexArr[j]));
                }

                // update last split index
                lastsplitIdx = i;
            }
        }
    }

}
