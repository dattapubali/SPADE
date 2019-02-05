package spade.storage.wlog;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.edge.cdm.SimpleEdge;
import spade.reporter.audit.AuditEventReader;
import spade.reporter.audit.OPMConstants;
import spade.vertex.opm.Process;

import java.util.*;

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
                //System.out.println("Found vertex with procname "+processNode.getAnnotation("name")+" "
                 //       +processNode.getAnnotation("pid"));
                splitNode(processNode,entry.getValue(), logMsg);
            }
        }

    }

    private void splitNode(AbstractVertex procnode, String processhash, String logMsg) {

        int count = 1;
        AbstractVertex lastNewNode = procnode;

        // get children of the process (application logs are grafted as child nodes)
        Graph childSubgraph = g.getChildren(processhash);

        // sort the edges based on event id
        //incidentEdges.addAll(childSubgraph.edgeSet());
        //incidentEdges.sort(Comparator.comparing(a -> Long.valueOf(a.getAnnotation(OPMConstants.EDGE_EVENT_ID))));


        // The application logs are in the child subgraph
        Set<AbstractVertex> vertices = childSubgraph.vertexSet();

        // remove the process node itself
        vertices.remove(procnode);

        // remove nodes that are not application logs
        Iterator<AbstractVertex> itr = vertices.iterator();
        while(itr.hasNext()){
            AbstractVertex v = itr.next();
            if(!v.getAnnotation("type").equalsIgnoreCase("Application"))
                itr.remove();
        }


        AbstractVertex[] vertexArr = buildVertexArray(vertices);
        Arrays.sort(vertexArr, Comparator.comparing(a -> Long.valueOf(a.getAnnotation(AuditEventReader.EVENT_ID))));

        for(int i =0 ; i < vertexArr.length; i++){
            AbstractVertex v = vertexArr[i];

            String logstring = v.getAnnotation("log");
            String splitEventid = v.getAnnotation(AuditEventReader.EVENT_ID);

            // if the log msg is contained in the applog node then split the original procnode
            if(logstring.toLowerCase().contains(logMsg.toLowerCase())){

                System.out.println("Going to split node now");
                AbstractVertex newNode = new Process();
                newNode.addAnnotations(procnode.getAnnotations());
                newNode.addAnnotation("name",procnode.getAnnotation("name"));
                newNode.addAnnotation("compnum", String.valueOf(count++));
                g.putVertex(newNode);

                // If splitpoint is at the end of the array handle that
                if(i==vertexArr.length-1){
                    // check for edges those have event ids greater than applog node
                    // move those edges to a new mode
                    /*if(moveExistingEdges(splitEventid,newNode,processhash,g)){
                        g.putEdge(new SimpleEdge(newNode,procnode));
                    }else{
                        g.removeVertex(newNode);
                    }*/
                    if(!moveExistingEdges(splitEventid,newNode,processhash,g))
                        g.removeVertex(newNode);
                    continue;
                }

                //create an edge form the original node to this one
                g.putEdge(new SimpleEdge(newNode,lastNewNode));

                //remove applog edges till splitpoint from original node, add them to new node
                // NOT REQUIRED: moveExistingEdges will do it
                /*for(int j=lastsplitIdx;j<=i;j++){
                    AbstractEdge edge = childSubgraph.getEdge(vertexArr[j].bigHashCode(),procnode.bigHashCode());
                    g.removeEdge(edge);
                    g.putEdge(new SimpleEdge(vertexArr[j],newNode));
                }*/

                // move existing edges till splitpoint to the new node
                boolean update = moveExistingEdges(splitEventid,newNode,processhash,g);
                lastNewNode = newNode;

                // update last split index
                //lastsplitIdx = i;
                /*if(update) {
                    System.out.println("Split event id: "+splitEventid);
                    long id = Long.parseLong(splitEventid);
                    printEdges(newNode, id, false);
                    printEdges(procnode, id, true);
                    //break;
                }*/
            }
        }
    }

    private void printEdges(AbstractVertex newNode, long splitEventid, boolean b) {
        System.out.println("printing data for "+newNode);
        Graph subgraph = g.getChildren(newNode.bigHashCode());

        for(AbstractEdge e : subgraph.edgeSet()){
            long val = Long.parseLong(e.getChildVertex().getAnnotation("eventid"));
            if(b) {
                if(val <= splitEventid)
                    System.out.println("Wrong "+val);
            }
            else{
                if(val > splitEventid)
                    System.out.println("Wrong "+val);
            }
        }

        Graph subgraph1 = g.getParents(newNode.bigHashCode());
        for(AbstractEdge e : subgraph1.edgeSet()){
            long val = Long.parseLong(e.getAnnotation(OPMConstants.EDGE_EVENT_ID));
            if(b) {
                if(val < splitEventid)
                    System.out.println("Wrong "+val);
            }
            else{
                if(val >= splitEventid)
                    System.out.println("Wrong "+val);
            }
        }
    }

    private boolean moveExistingEdges(String annotation, AbstractVertex newNode, String processhash, Graph g) {

        boolean changed = false;

        long splitEventID = Long.parseLong(annotation);

        // Update parent of child edges
        Set<AbstractEdge> childEdges = g.getChildren(processhash).edgeSet();
        for(AbstractEdge e : childEdges){
            String id = e.getAnnotation(OPMConstants.EDGE_EVENT_ID);
            if(id == null || id.isEmpty())
                continue;

            long idval = Long.parseLong(id);
            if(idval <= splitEventID){
                e.setParentVertex(newNode);
                changed = true;
            }
        }

        // Update child of parent edges
        Set<AbstractEdge> parentEdges = g.getParents(processhash).edgeSet();
        for(AbstractEdge e : parentEdges){
            String id = e.getAnnotation(OPMConstants.EDGE_EVENT_ID);
            if(id == null || id.isEmpty())
                continue;

            long idval = Long.parseLong(id);
            if(idval <= splitEventID){
                e.setChildVertex(newNode);
                changed = true;
            }
        }
        return changed;

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
