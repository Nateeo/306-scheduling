package algorithm;

import graph.Node;

import java.util.ArrayList;
import java.util.HashMap;

/**
 *  This class creates temporary representations of the schedules being used to find the optimal schedule.
 *  Author: Sam Li, Edison Rho, Nathan Hur
 */

public class PartialSolution implements Comparable<PartialSolution> {

    public int _idleTime; // total idle time (between slots)
    public int _cost; // overall cost heuristic
    public int _bottomLevelWork;
    public int _currentFinishTime; // the finish time of the lowest node in the schedule
    public ProcessorSlot _latestSlot;
    public ProcessorSlot[] _latestSlots;
    public ArrayList<Node> _brokenNodes = new ArrayList<Node>();
    public ArrayList<ProcessorSlot>[] _processors;
    public ArrayList<String> _nodes; //trialing string to show nodes in solution;
    //public TreeMap<Integer, Integer> _id; // node id -> array int
    public int[] _startingNodes;
    public int[] _startingNodeIndices;
    public int _zeroStarts;

    public HashMap<Integer, ProcessorSlot> _slotMap;

    public PartialSolution(int numberOfProcessors) {
        _processors = new ArrayList[numberOfProcessors];
        for (int i = 0; i < numberOfProcessors; i++) {
            _processors[i] = new ArrayList<>();
        }
        _nodes = new ArrayList<String>();
        _latestSlots = new ProcessorSlot[numberOfProcessors];
        _startingNodes = new int[numberOfProcessors];
        _startingNodeIndices = new int[numberOfProcessors];
        _zeroStarts = numberOfProcessors;
        _slotMap = new HashMap<>();

    }

    /**
     * Copy constructor of parent
     * @param ps
     */
    public PartialSolution(PartialSolution ps) {
        _idleTime = ps._idleTime;
        _cost = ps._cost;
        _bottomLevelWork = ps._bottomLevelWork;
        _currentFinishTime = ps._currentFinishTime;
        _latestSlot = ps._latestSlot;
        _latestSlots = new ProcessorSlot[ps._latestSlots.length];
        for (int i = 0; i < _latestSlots.length; i++) {
            _latestSlots[i] = ps._latestSlots[i];
        }
        _processors = new ArrayList[ps._processors.length];
        for (int i = 0; i < _processors.length; i++) {
            _processors[i] = new ArrayList<>(ps._processors[i]);
        }
        _nodes = (ArrayList)ps._nodes.clone();
        if (ps._zeroStarts == 0) { // starts are all full, reuse
            _zeroStarts = 0;
            _startingNodeIndices = ps._startingNodeIndices;
            _startingNodes = ps._startingNodes;
        } else {
            _zeroStarts = ps._zeroStarts;
            _startingNodeIndices = ps._startingNodeIndices.clone();
            _startingNodes = ps._startingNodes.clone();
        }
        _slotMap = (HashMap)ps._slotMap.clone();
    }

    public int compareTo(PartialSolution o) {
        if (_cost == o._cost) {
            return o._nodes.size() - _nodes.size();
        }
        return _cost - o._cost;
    }

    public ArrayList<ProcessorSlot>[] getProcessors() {
        return _processors;
    }

    @Override // TODO: remove when working
    public String toString() {
        String s = "===========================\nPARTIAL SOLUTION contains: " + _nodes + "\n";
        for (int i = 0; i < _processors.length; i++) {
            s += "PROCESSOR " + (i+1) + "\n";
            for (ProcessorSlot slot : _processors[i]) {
                s += "start: " + slot.getStart() + " finish: " + slot.getFinish() + " node: " + slot.getNode().getId() +  "\n";
            }
        }
        s+= "cost estimate: " + _cost;
        s += "\n===========================\n";
        return s;
    }
}
