package algorithm;

import graph.Edge;
import graph.Graph;
import graph.Node;
import logger.Logger;

import java.util.*;

/**
 * This static class utilises the Partial solutions to generate children partial solutions.
 * author: Sam Li, Edison Rho, Nathan Hur
 */

public class PSManager {

    private Graph _graph;
    private int _numberOfProcessors;

    //calculate all bottom level work values and cache them for the cost function
    private HashMap<String, Integer> _bottomLevelWork;

    //cache the constant portion of the idle time heuristic (total work / processors)
    private int _idleConstantHeuristic;

    private ArrayList<PartialSolution> _closed = new ArrayList<>();

    public PSManager(int processors, Graph graph){
        _numberOfProcessors = processors;
        _graph = graph;
        _idleConstantHeuristic = graph.totalMinimumWork() / processors;
        _bottomLevelWork = bottomLevelCalculator(graph);
    }

    //BFS of  children of partial solution
    //for ever node, addNode to add to partial solution then return that
    //calculate functional cost i.e. the max formula

    /**
     * Add the children of a partial solution to a given PSPriorityQueue
     * It uses all the free variables that are available from the input partial solution
     * and generates partial solutions from those free variables.
     * @param parentPS the parent partial solution to generate children from
     * @param queue the queue to add the children to
     * @return
     */
    public void generateChildren(PartialSolution parentPS, PSPriorityQueue queue) {
        boolean partialExpansionBreak = false;
        List<Node> freeNodes = getFreeNodes(parentPS);
        //for every free node, create the partial solutions that can be generated
        for (Node freeNode: freeNodes) {
            //calculate latest time to put on (dependency)
            int[] earliestTimeOnProcessor = earliestTimeOnProcessors(parentPS, freeNode);
            //for every processor, create partial solution that can be generated by scheduling the
            //free variable to that processor. Calculate the new cost and add it to the priority queue.
            for (int i = 0; i < _numberOfProcessors; i++) {
                ProcessorSlot slot = new ProcessorSlot(freeNode, earliestTimeOnProcessor[i], i);
                PartialSolution partialSolution = new PartialSolution(parentPS);
                addSlot(partialSolution, slot);
                int childCost = calculateUnderestimate(partialSolution);
                checkAndAdd(partialSolution, queue);
            }
        }
        _closed.add(parentPS);
    }

    /**
     * Calculates the bottomLevel value for all nodes in the graph, this only has to be run once in the initialization
     * of the PSManager
     * @param graph
     * @return
     */
    private static HashMap<String,Integer> bottomLevelCalculator(Graph graph) {
        List<Node> allNodes = graph.getNodes();
        HashMap<String, Integer> bottomLevels = new HashMap<String, Integer>(allNodes.size());
        Queue<Node> queuedNodes = new LinkedList<Node>();
        Node predecessorNode;
        Node currentNode;
        int maxBottomLevel;
        int currentNodeBL;
        boolean allSuccessorsCalculated;

        for(Node node: allNodes) {
            if(node.getOutgoing().isEmpty()) {
                queuedNodes.add(node);
            }
        }

        while(!queuedNodes.isEmpty()) {
            maxBottomLevel = 0;
            currentNode = queuedNodes.remove();
            if (!currentNode.getOutgoing().isEmpty()) {
                for (Edge successors : currentNode.getOutgoing()) {
                    currentNodeBL = bottomLevels.get(successors.getTo().getName()) + successors.getTo().getWeight();
                    if (currentNodeBL > maxBottomLevel) {
                        maxBottomLevel = currentNodeBL;
                    }
                }
            }
            bottomLevels.put(currentNode.getName(),maxBottomLevel);
            if (!currentNode.getIncoming().isEmpty()) {
                for (Edge predecessors : currentNode.getIncoming()) {
                    predecessorNode = predecessors.getFrom();
                    allSuccessorsCalculated = true;
                    for (Edge pSuccessors : predecessorNode.getOutgoing()) {
                        if (!bottomLevels.containsKey(pSuccessors.getTo().getName())) {
                            allSuccessorsCalculated = false;
                        }
                    }
                    if (allSuccessorsCalculated) {
                        queuedNodes.add(predecessorNode);
                    }
                }
            }
        }
        return bottomLevels;
    }

    /**
     * Function to calculate and update the work for a partialSolution
     * @param ps a partial solution
     */
    public int calculateUnderestimate(PartialSolution ps) {

        // get bottom level work
        int bottomLevelWork = 0;
        ProcessorSlot lastSlot = ps._latestSlot;
        if (lastSlot != null) { // TODO: fix
            bottomLevelWork = _bottomLevelWork.get(lastSlot.getNode().getName()) + lastSlot.getFinish();
        }


        // update idle time heuristic TODO: optimise
        int idleTimeHeuristic = _idleConstantHeuristic + ps._idleTime / _numberOfProcessors;

        // update estimate
        ps._cost = Math.max(bottomLevelWork, Math.max(idleTimeHeuristic, ps._cost));
        return ps._cost;
    }

    private List<Node> getFreeNodes(PartialSolution parentPS) {
        List<Node> freeNodes = new ArrayList<Node>();
        List<Node> nodes = _graph.getNodes();
        //get all the free variables from list of all nodes in graph
        for (Node node : nodes) {
            if (parentPS._nodes.contains(node.toString())) {
            } else {
                boolean hasMissingSuccessor = false;
                for (Edge e : node.getIncoming()) {
                    if (!parentPS._nodes.contains(e.getFrom().getName())) {
                        hasMissingSuccessor = true;
                    }
                }
                if (!hasMissingSuccessor) {
                    freeNodes.add(node);
                }
            }
        }
        return freeNodes;
    }

    /**
     * This finds the earliest time on each processor that a node can be scheduled to
     * based on successor nodes and the latest slot finishing time on each processor.
     * @param parentPS
     * @param freeNode
     * @return int[] index is the processor, value is the time
     */
    private int[] earliestTimeOnProcessors(PartialSolution parentPS, Node freeNode) {
        int[] earliestTimes = new int[_numberOfProcessors];
        ArrayList<Node> parents = freeNode.getParentNodes();
        int[] maxPredecessorTime = new int[_numberOfProcessors];
        int maxTime = 0;
        ProcessorSlot maxSlot = null;
        // iterate through each processor and check for successor nodes. Find the maximum time and edge time (for transfer)
        for (int i = 0; i < _numberOfProcessors; i++) {
            ArrayList<ProcessorSlot> processor = parentPS._processors[i];
            for (int j = processor.size() - 1; j >= 0; j--) {
                ProcessorSlot slot = processor.get(j);
                if (parents.contains(slot.getNode())) { // slot contains a predecessor
                    int slotProcessor = slot.getProcessor();
                    Edge parentEdge = _graph.getEdge(new Edge(slot.getNode(), freeNode, 0));
                    int parentTime = parentEdge.getWeight() + slot.getFinish();
                    if (parentTime > maxPredecessorTime[slotProcessor]) { // can only be max if it was at least greater than the prev one in processor
                        maxPredecessorTime[slotProcessor] = parentTime;
                        if (parentTime > maxTime) {
                            maxTime = parentTime;
                            maxSlot = slot;
                        }
                    }
                }
            }
        }

        //DEBUG
        if (maxSlot == null) { // no predecessor constraints, we can schedule as early as possible on each processor based on their last slot
            for (int i = 0; i < _numberOfProcessors; i++) {
                ProcessorSlot latestSlot = parentPS._latestSlots[i];
                if (latestSlot == null) {
                    earliestTimes[i] = 0; // there is no slot on the processor, we can start at 0
                } else {
                    earliestTimes[i] = latestSlot.getFinish();
                }
            }
            return earliestTimes;
        } else { // predecessor constraint is there, we can schedule at earliest maxSlot.finishTime + maxEdge
            // we need to find the second maxSlot for predecessor constraints on the maxSlotProcessor
            int maxSuccessorProcessor = maxSlot.getProcessor();
            int secondMaxSuccessorTime = 0;
            ProcessorSlot finalSlot;
            int finalSlotTime = 0;
            for (int i = 0; i < _numberOfProcessors; i++) {
                finalSlot = parentPS._latestSlots[i];
                finalSlotTime = 0;
                if (finalSlot != null) finalSlotTime = finalSlot.getFinish();
                earliestTimes[i] = Math.max(finalSlotTime, maxTime);
                if (maxPredecessorTime[i] > secondMaxSuccessorTime && i != maxSuccessorProcessor) {
                    secondMaxSuccessorTime = maxPredecessorTime[i];
                }
            }
            // we need to check predecessor constraints on other processors for the maxSuccessorProcessor slot
            finalSlot = parentPS._latestSlots[maxSuccessorProcessor];
            finalSlotTime = 0;
            if (finalSlot != null) finalSlotTime = finalSlot.getFinish();
            earliestTimes[maxSuccessorProcessor] = Math.max(secondMaxSuccessorTime, finalSlotTime);
            return earliestTimes;
        }
    }

    /**
     * check if a node is present within the schedule of the partial schedule.
     * @param node
     * @return
     */
    protected boolean contains(PartialSolution ps, Node node){
        for (ArrayList<ProcessorSlot> processor : ps._processors){
            if (processor.contains(node)){
                return true;
            }
        }
        return false;
    }

    /**
     * Add a slot to a processor, updating latestSlots, latestSlot and idleTime as necessary
     * @param slot
     */
    public void addSlot(PartialSolution ps, ProcessorSlot slot) {
        if (slot == null) Logger.error("Slot is null");
        ProcessorSlot latestSlot = ps._latestSlots[slot.getProcessor()];
        int prevSlotFinishTime;
        if (latestSlot == null) {
            prevSlotFinishTime = 0;
        } else {
            prevSlotFinishTime = latestSlot.getFinish();
        }
        int processor = slot.getProcessor();
        ps._id[processor] += slot.getNode() + "-";
        ps._processors[processor].add(slot);
        ps._idleTime += slot.getStart() - prevSlotFinishTime; // add any idle time found
        ps._latestSlots[processor] = slot; // the newest slot becomes the latest
        ps._nodes.add(slot.getNode().getName()); // add node to node string
        if (ps._latestSlot == null || ps._latestSlot.getFinish() <= slot.getFinish()) {
            ps._latestSlot = slot; // last slot across all processors is the new slot if it finishes later
        }
    }

    public void checkAndAdd(PartialSolution ps, PSPriorityQueue queue) {
        if (_closed.contains(ps)) {
            return;
        }
        if (!queue.contains(ps)) {
            queue.add(ps);
        }
    }
}
