package regressionTests;

import algorithm.PSManager;
import algorithm.PSPriorityQueue;
import algorithm.PartialSolution;
import dotParser.Parser;
import graph.Graph;
import logger.Logger;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static scheduleValidation.ScheduleValidation.scheduleIsValid;

/**
 * Tests that the algorithm correctly calculates an optimal schedule for all of the
 * client input test cases, as well as (rough) timing results.
 * Created by edisonrho on 12/08/17.
 */
public class RegressionTest {
    String MAPPERTEXT= "input_Mapper.txt";
    Map<String, String[]> _costDictionary;

    @Before
    public void readGraphs(){
        File graphMap = new File(MAPPERTEXT);

        _costDictionary = new HashMap<String, String[]>();

        try {
            BufferedReader br = new BufferedReader(new FileReader(graphMap));
            String line = br.readLine();
            while (line != null){
                String[] graphEntry = line.split(":");

                String[] processorCosts = graphEntry[1].split(",");

                _costDictionary.put(graphEntry[0], processorCosts);
                line = br.readLine();
            }
        } catch (Exception e){
            e.printStackTrace();
        }

    }

    @Test
    public void testAllInputs(){
        Set<String> inputFiles = _costDictionary.keySet();
        //loop through each graph
        for (String graphFileName : inputFiles){
            String[] processorArray =_costDictionary.get(graphFileName);
            //create graph
            File file = new File("input-graphs/"+graphFileName);
            Graph graph = Parser.parseDotFile(file);

            int processorNumber = 1;
            //for each processor
            for (String processorString : processorArray){
                int expectedCost = Integer.parseInt(processorString);

                //pass if 0
                if (expectedCost==0){
                    processorNumber++;
                    continue;
                }

                PSPriorityQueue priorityQueue = new PSPriorityQueue(graph, processorNumber);
                PartialSolution ps = null;
                PSManager psManager = new PSManager(processorNumber, graph);
                //find the optimal partial solution, and time the execution
                Logger.startTiming();
                System.out.println(">>>> attempting to process " + graph.getName());
                while (priorityQueue.hasNext()) {
                    ps = priorityQueue.getCurrentPartialSolution();
                    psManager.generateChildren(ps, priorityQueue);
                }
                ps = priorityQueue.getCurrentPartialSolution();
                long timeTaken = Logger.endTiming();

                double testCost = ps._cost;
                boolean isValid = scheduleIsValid(graph, ps);
                System.out.println("\n================\n\n[PROCESSORS: " + processorNumber + "] GRAPH: " + graph.getName()  +
                        "\nexpectedCost: " + expectedCost + "\tactualCost: " + testCost + "\tisValid: " + isValid + "\ttimeTaken: " + timeTaken + "ms" +
                        "\n\n================");


                // check solution has an optimal finish time, and that this is equal to the final cost estimate
                assertEquals("Cost of graph " + graph.getName() + " on " + processorNumber + " processors should be " + expectedCost,
                        expectedCost, testCost, 0);
                assertEquals("Cost should equal finish time", testCost, ps._latestSlot.getFinish(), 0);

                // check solution is a valid schedule
                assertTrue("The produced schedule should be valid", isValid);

                processorNumber++;//update processor number




            }


        }
    }



}
