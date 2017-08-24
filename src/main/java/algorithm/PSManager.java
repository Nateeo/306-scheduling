package algorithm;

import graph.Edge;
import graph.Graph;
import graph.Node;

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
    private Cache _cache;

    //cache the constant portion of the idle time heuristic (total work / processors)
    private double _idleConstantHeuristic;

    //lists for calculating earliest times
    private int[] _maxPredecessorTime;
    private int[] _earliestTimes;
  
    public PSManager(int processors, Graph graph){
        _numberOfProcessors = processors;
        _graph = graph;
        _idleConstantHeuristic = (double)graph.totalMinimumWork() / processors;
        _bottomLevelWork = graph._bottomLevelWork;
        _cache = new Cache(processors);
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
     * @return void
     */
    public void generateChildren(PartialSolution parentPS, PSPriorityQueue queue) {
        List<Node> freeNodes = getFreeNodes(parentPS);
        PartialSolution partialSolution = null;
        //for every free node, create the partial solutions that can be generated
        for (Node freeNode: freeNodes) {
                //calculate latest time to put on (dependency)
                int[] earliestTimeOnProcessor = earliestTimeOnProcessors(parentPS, freeNode);
                //for every processor, create partial solution that can be generated by scheduling the
                //free variable to that processor. Calculate the new cost and add it to the priority queue.
                if (parentPS._zeroStarts > 1) {
                    boolean done = false;
                    for (int i = 0; i < _numberOfProcessors; i++) {
                        int earliestTime = earliestTimeOnProcessor[i];
                        if (parentPS._startingNodes[i] != 0) {
                            partialSolution = addSlotToProcessor(parentPS, freeNode, i, earliestTime);
                            checkAndAdd(partialSolution, i, queue);
                        } else if (!done) { // if we haven't added it to an empty processor
                            partialSolution = addSlotToProcessor(parentPS, freeNode, i, earliestTime);
                            checkAndAdd(partialSolution, i, queue);
                            done = true;
                        }
                    }
                } else {
                    for (int i = 0; i < _numberOfProcessors; i++) {
                        partialSolution = addSlotToProcessor(parentPS, freeNode, i, earliestTimeOnProcessor[i]);
                        checkAndAdd(partialSolution, i, queue);
                    }
                }
            }
        _cache.add(parentPS);
    }

    /**
     * Function to calculate and update the work for a partialSolution aka the cost function f(s)
     * @param ps a partial solution
     */
    public void calculateUnderestimate(PartialSolution ps) {


        // update idle time heuristic TODO: optimise
        int idleTimeHeuristic = (int)Math.ceil(_idleConstantHeuristic + (ps._idleTime / _numberOfProcessors));

        // data ready time heuristic
        int dataReadyTimeHeuristic = calculateDataReadyTime(ps);
      
        // update estimate, aka cost function f(s)
        ps._cost = Math.max(Math.max(Math.max(ps._bottomLevelWork, ps._cost), idleTimeHeuristic), dataReadyTimeHeuristic);
    }

    /**
     * Third component of the proposed cost function f(s)
     * calculates the data ready time (DRT) of a node n_j on processor p
     * @return
     */
    public int calculateDataReadyTime(PartialSolution ps){
        // construct a list of all free nodes in the given partial solution to iterate over
        List<Node> freeNodeList = getFreeNodes(ps);
        int maximumDRT = 0;

        // for every free node
        for(Node freeNode : freeNodeList){
            // get minimum drt on each processor
            int minDrt = -1;

            // get the blw for the free node
            int blw = _bottomLevelWork.get(freeNode.getName());

            //for the earliest time this given freenode can be placed on each processor
            for (int i : earliestTimeOnProcessors(ps, freeNode)) {
                if (i < minDrt || minDrt == -1) { // if this earliest time is earlier than minDrt
                    minDrt = i; // update it
                }
            }
            // calcs data ready time for this given free node
            int dataReadyFinish = blw + minDrt;

            // if this free nodes drt is greater than all previous free nodes drt's update the drt for this PS
            if (dataReadyFinish > maximumDRT) {
                maximumDRT = dataReadyFinish;
            }
        }
        return maximumDRT;
    }

    /**
     * gets all the freenodes by finding all nodes not on the PartialSolution that have all their
     * predecessors on the PartialSolution
     * @param parentPS
     * @return
     */
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
        _earliestTimes = new int[_numberOfProcessors];
        _maxPredecessorTime = new int[_numberOfProcessors];
        ArrayList<Node> parents = freeNode.getParentNodes();
        int maxTime = 0;
        ProcessorSlot maxSlot = null;
        // iterate through each processor and check for successor nodes. Find the maximum time and edge time (for transfer)
        for (int i = 0; i < _numberOfProcessors; i++) {
            ArrayList<ProcessorSlot> processor = parentPS._processors[i];
            for (int j = processor.size() - 1; j >= 0; j--) {
                ProcessorSlot slot = processor.get(j);
                if (parents.contains(slot.getNode())) { // slot contains a predecessor
                    int slotProcessor = slot.getProcessor();
                    Edge parentEdge = _graph.getEdge(slot.getNode().getId(), freeNode.getId());
                    int parentTime = parentEdge.getWeight() + slot.getFinish();
                    if (parentTime > _maxPredecessorTime[slotProcessor]) { // can only be max if it was at least greater than the prev one in processor
                        _maxPredecessorTime[slotProcessor] = parentTime;
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
                    _earliestTimes[i] = 0; // there is no slot on the processor, we can start at 0
                } else {
                    _earliestTimes[i] = latestSlot.getFinish();
                }
            }
            return _earliestTimes;
        } else { // predecessor constraint is there, we can schedule at earliest maxSlot.finishTime + maxEdge
            // we need to find the second maxSlot for predecessor constraints on the maxSlotProcessor
            int maxSuccessorProcessor = maxSlot.getProcessor();
            int secondMaxSuccessorTime = 0;
            ProcessorSlot finalSlot;
            int finalSlotTime;
            for (int i = 0; i < _numberOfProcessors; i++) {
                finalSlot = parentPS._latestSlots[i];
                finalSlotTime = 0;
                if (finalSlot != null) finalSlotTime = finalSlot.getFinish();
                _earliestTimes[i] = Math.max(finalSlotTime, maxTime);
                if (_maxPredecessorTime[i] > secondMaxSuccessorTime && i != maxSuccessorProcessor) {
                    secondMaxSuccessorTime = _maxPredecessorTime[i];
                }
            }
            // we need to check predecessor constraints on other processors for the maxSuccessorProcessor slot
            finalSlot = parentPS._latestSlots[maxSuccessorProcessor];
            finalSlotTime = 0;
            if (finalSlot != null) finalSlotTime = finalSlot.getFinish();
            _earliestTimes[maxSuccessorProcessor] = Math.max(secondMaxSuccessorTime, finalSlotTime);
            return _earliestTimes;
        }
    }

    /**
     * check if a node is present within the schedule of the partial schedule.
     *
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
        ps._slotMap.put(slot.getNode().getId(), slot);

        ProcessorSlot latestSlot = ps._latestSlots[slot.getProcessor()];
        int prevSlotFinishTime;

        // calculate when the prev slot finished
        if (latestSlot == null) { // this is the first slot in the processor
            prevSlotFinishTime = 0;
            ps._zeroStarts--;
            addToSorted(ps._startingNodes, slot.getNode().getId(), ps._startingNodeIndices, slot.getProcessor());
        } else {
            prevSlotFinishTime = latestSlot.getFinish();
            if ((latestSlot.getNode().getTopId() < slot.getNode().getId()) && ps._priority == 0) {
                ps._priority = 0;
            } else {
                ps._priority = 1;
            }
        }

        int processorNo = slot.getProcessor();
        ps._processors[processorNo].add(slot);
        ps._idleTime += slot.getStart() - prevSlotFinishTime; // add any idle time found
        ps._bottomLevelWork = Math.max(ps._bottomLevelWork, slot.getStart() + _bottomLevelWork.get(slot.getNode().getName()));// update max bottom level work
        ps._latestSlots[processorNo] = slot; // the newest slot becomes the latest
        ps._nodes.add(slot.getNode().getName()); // add node to node string

        if (ps._latestSlot == null || ps._latestSlot.getFinish() < slot.getFinish()) {
            ps._latestSlot = slot; // last slot across all processors is the new slot if it finishes later
        }
    }

    private void checkAndAdd(PartialSolution ps, int processorIndex, PSPriorityQueue queue) {
        if (!equivalenceCheck(ps, processorIndex)) {
            if (_cache.add(ps)) {
                queue.add(ps);
            }
        }
    }

    private PartialSolution addSlotToProcessor(PartialSolution parentPS, Node freeNode, int processor, int time) {
        ProcessorSlot slot = new ProcessorSlot(freeNode, time, processor);
        PartialSolution partialSolution = new PartialSolution(parentPS);
        addSlot(partialSolution, slot);
        calculateUnderestimate(partialSolution);
        return partialSolution;
    }

    private boolean equivalenceCheck(PartialSolution ps, int processorIndex) {
        ArrayList<ProcessorSlot> toCopy = ps.getProcessors()[processorIndex];
        ArrayList<ProcessorSlot> copy = new ArrayList<>(toCopy); // copy we use re order

        //backups
        ArrayList<ProcessorSlot> backup = new ArrayList<>(ps.getProcessors()[processorIndex]);
        HashMap<Integer, ProcessorSlot> backupSlotMap = new HashMap<>(ps._slotMap);
        ProcessorSlot[] latestSlotsBackup = ps._latestSlots.clone();

        ArrayList<ProcessorSlot>[] processors = ps.getProcessors();
        int addedIndex = processors[processorIndex].size() - 1;
        Node addedNode = processors[processorIndex].get(addedIndex).getNode();
        int maxTime = processors[processorIndex].get(addedIndex).getFinish();
        int i = addedIndex - 1; // where we check switch to
        // empty the corresponding partialsolution processor
        processors[processorIndex].clear();
        ps._latestSlots[processorIndex] = null;
        while (i >= 0 && (addedNode.getTopId() < copy.get(i).getNode().getTopId())) {
            Collections.swap(copy, addedIndex, i);
            for (ProcessorSlot slot : copy) {
                int earliestTime = earliestTimeOnProcessors(ps, slot.getNode())[processorIndex];
                ProcessorSlot newSlot = new ProcessorSlot(slot.getNode(), earliestTime, processorIndex);
                processors[processorIndex].add(newSlot);
                ps._latestSlots[processorIndex] = newSlot;
            }
            int newFinishTime = processors[processorIndex].get(processors[processorIndex].size() - 1).getFinish();
            if (newFinishTime <= maxTime && outgoingCheck(ps, backup, processorIndex)) {
                return true;
            }
            i--;
        }
        // restore and return
        processors[processorIndex] = backup;
        ps._slotMap = backupSlotMap;
        ps._latestSlots = latestSlotsBackup;
        return false;
    }
    // i is where m is

    private boolean outgoingCheck(PartialSolution ps, ArrayList<ProcessorSlot> oldProcessor, int processorIndex) {
        for (int i = 0; i < oldProcessor.size(); i++) {
            ArrayList<ProcessorSlot> newProcessor = ps.getProcessors()[processorIndex];
            ProcessorSlot newSlot = newProcessor.get(i);
            ProcessorSlot oldSlot = oldProcessor.get(oldProcessor.indexOf(newSlot));
            if (newSlot.getStart() > oldSlot.getStart()) {
                // for all children, check affected time
                for (Edge e : newSlot.getNode().getOutgoing()) {
                    Node child = e.getTo();
                    int dataTime = newSlot.getFinish() + e.getWeight();
                    if (ps._slotMap.containsKey(child.getId())) { // child is already schedule
                        ProcessorSlot childSlot = ps._slotMap.get(child.getId());
                        if (!(childSlot.getProcessor() == processorIndex || childSlot.getStart() > dataTime)) {
                            return false;
                        }
                    } else { // child is not scheduled
                        boolean atLeastOneLater = false;
                        // for all parents, check at least one comm time is later
                        for (Edge parentEdge : child.getIncoming()) {
                            Node parent = parentEdge.getFrom();
                            if (ps._nodes.contains(parent.getName())) {
                                // go through each processor and find it, compare it to dataTime
                                ProcessorSlot parentSlot = ps._slotMap.get(parent.getId());
                                if (parentSlot.getFinish() + parentEdge.getWeight() > dataTime) {
                                    atLeastOneLater = true;
                                }
                            }
                        }
                        if (!atLeastOneLater) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }


    private void addToSorted(int[] array, int value, int[] indicesArray, int index) {
        for (int i = 0; i < array.length; i++) {
            if (value < array[i]) {
                for (int j = array.length-1; j > i; j--) {
                    array[j] = array[j-1];
                    indicesArray[j] = indicesArray[j-1];
                }
                array[i] = value;
                indicesArray[i] = index;
                return;
            } else if (array[i] == 0) {
                array[i] = value;
                indicesArray[i] = index;
                return;
            }
        }
    }
}
