package be.kul.gantry.domain;

import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import static java.lang.Math.abs;

public class Solution {

    private double clock = 0;
    private Problem problem;

    private FreeSlots freeSlots;
    private List<Slot> tempFreeSlots = new ArrayList<>();
    private List<Output> performedActions = new ArrayList<>();
    private double timeToAdd = 0;
    private Gantry gantryInput;
    private Gantry gantryOutput;
    private SlotStructure slotStructure;

    private double temporaryClock = 0;

    private int pickupLevel;

    /**
     * Constructor, create problem from Json file, setup gantries, setup free slot Queue.
     *
     * @param inputFileName the name of the file we're going to use
     */
    Solution(String inputFileName) {

        //Reading the information from the jason file
        try {
            problem = Problem.fromJson(new File(inputFileName));
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }

        //Create new slot structure
        slotStructure = new SlotStructure();

        //Adding all the slots to the HashMap
        String slotCoordinate;
        for (Slot s : problem.getSlots()) {

            //create key by coordinate
            slotCoordinate = String.valueOf(s.getCenterX()) + "," + String.valueOf(s.getCenterY())
                    + "," + String.valueOf(s.getZ());
            //System.out.print(slotCoordinate);
            if (s.getItem() == null)
                tempFreeSlots.add(s);

            //Calculate gantry move time between input and current slot (usable for optimalization)
            //s.setPickupTime(calculateSlotInputMoveTime(gantryInput, s));
            slotStructure.getSlotStructureMap().put(slotCoordinate, s);

            //add slot to possible parent as a child
            slotStructure.setChild(s);
        }

        //Create new freeSlots object
        freeSlots = new FreeSlots(tempFreeSlots);
        //Remove input slot from the queue (bug in file reader)
        freeSlots.getFreeSlots().remove();

        //Setup gantries
        gantryInput = problem.getGantries().get(0);
        if (problem.getGantries().size() == 2) {
            gantryOutput = problem.getGantries().get(1);
            System.out.println("Two gantries Detected");
        }

        //add start position of gantry to output
        performedActions.add(new Output(gantryInput.getId(), clock,
                gantryInput.getCurrentX(), gantryInput.getCurrentY(), -1));
        performedActions.add(new Output(gantryOutput.getId(), clock,
                gantryOutput.getCurrentX(), gantryOutput.getCurrentY(), -1));
    }

    void executionWithDoubleGantry() {

        int i = 0;
        int k = 0;

        while (true) {

            if (i < problem.getInputJobSequence().size()) {
                handleInputJobs(problem.getInputJobSequence().get(i));
                i++;
            }

            if (k < problem.getOutputJobSequence().size()) {
                Job j = problem.getOutputJobSequence().get(k);
                if(j.getItem().getSlot() != null){
                    handleOutputJobs(j);
                    k++;
                }

            }else
                break;

        }
    }

    /**
     * Function which goes through the input job sequence
     */
    private void handleInputJobs(Job j) {
        //for (Job j : problem.getInputJobSequence()) {

        //check if gantry move is safe, if not move output crane
        if (!gantryMoveIsSafe(gantryInput, problem.getInputSlot())) {
            executeGantryMove(new Job(0, null, null, problem.getOutputSlot()), gantryOutput);
            // add wait time to output
            performedActions.add(new Output(gantryInput.getId(), clock, gantryInput.getCurrentX(), gantryInput.getCurrentY(), -1));

        }

        performedActions.add(new Output(gantryInput.getId(), clock, gantryInput.getCurrentX(), gantryInput.getCurrentY(), -1));
        executeGantryMove(j, gantryInput);

        //Get free slot
        Slot freeSlotTemp;
        Slot toFillSlot;
        String slotCoordinate;

        while (true) {

            freeSlotTemp = freeSlots.getFreeSlots().remove();

            //map toFillSlot object to object from hashMap
            slotCoordinate = String.valueOf(freeSlotTemp.getCenterX()) + "," + String.valueOf(freeSlotTemp.getCenterY()) + "," + String.valueOf(freeSlotTemp.getZ());

            if (checkUnderneath(freeSlotTemp))
                break;


            freeSlots.addSlot(freeSlotTemp);
        }

        toFillSlot = slotStructure.getSlotStructureMap().get(slotCoordinate);

        toFillSlot.setItem(j.getItem());  //add item to slot
        j.getItem().setSlot(toFillSlot);  //add slot to item

        if (!gantryMoveIsSafe(gantryInput, toFillSlot)) {
            executeGantryMove(new Job(0, null,
                    slotStructure.getSlotStructureMap().get(gantryInput.getCurrentSlot()),
                    problem.getOutputSlot()), gantryOutput);
            //add wait time to output
            performedActions.add(new Output(gantryInput.getId(), clock,
                    gantryInput.getCurrentX(), gantryInput.getCurrentY(), j.getItem().getId()));

        }
        executeMoveJob(toFillSlot, j, gantryInput);

        //}
    }

    private boolean checkUnderneath(Slot freeSlotTemp) {
        String slotCoordinateUnderLeft = String.valueOf(freeSlotTemp.getCenterX() - 5)
                + "," + String.valueOf(freeSlotTemp.getCenterY()) + "," + String.valueOf(freeSlotTemp.getZ() - 1);
        String slotCoordinateUnderRight = String.valueOf(freeSlotTemp.getCenterX() + 5)
                + "," + String.valueOf(freeSlotTemp.getCenterY()) + "," + String.valueOf(freeSlotTemp.getZ() - 1);

        Slot underLeft = slotStructure.getSlotStructureMap().get(slotCoordinateUnderLeft);
        Slot underRight = slotStructure.getSlotStructureMap().get(slotCoordinateUnderRight);

        if (underLeft != null && underRight != null)
            return underLeft.getItem() != null && underRight.getItem() != null;

        return false;
    }

    /**
     * Function which executes the input job
     * Beweging van een item van in de kade(of van in de input)  naar een andere plaats in de kade
     */
    private void performInput(Job j) {

        //Eerst de kraan klaarzetten om de job te doen
        if (!gantryMoveIsSafe(gantryOutput, j.getPickup().getSlot())) {
            executeGantryMove(new Job(0, null, null, problem.getInputSlot()), gantryOutput);
            performedActions.add(new Output(gantryInput.getId(), clock,
                    gantryInput.getCurrentX(), gantryInput.getCurrentY(), -1));
        }
        executeGantryMove(j, gantryOutput);

        //Get free slot
        Slot freeSlotTemp;
        Slot toFillSlot = null;
        String slotCoordinate = "";

        //Variables needed for range calculation
        int minX;
        int maxX;

        //Initiate loop to find a correct slot
        while (true) {

            //Calculate placement ranges
            minX = calculatePlacementRangeMinX(pickupLevel,
                    problem.getMaxLevels(), j.getPickup().getSlot().getCenterX());
            maxX = calculatePlacementRangeMaxX(pickupLevel,
                    problem.getMaxLevels(), j.getPickup().getSlot().getCenterX());

            //Get slot from the queue
            freeSlotTemp = freeSlots.getFreeSlots().remove();

            //Check if freeSlotTemp is not within the range
            if (freeSlotTemp.getCenterY() == j.getPickup().getSlot().getCenterY()) {
                if (freeSlotTemp.getCenterX() < minX && freeSlotTemp.getCenterX() > maxX) {
                    //Check if the slot has two filled underlying slots.
                    if (checkUnderneath(freeSlotTemp))
                        break;
                }
            }
            //If slots has a different Y coordinate, no range check is needed
            else if (freeSlotTemp.getCenterY() != j.getPickup().getSlot().getCenterY()) {
                if (checkUnderneath(freeSlotTemp))
                    break;
            }

            //If slot is not feasible, add to the queue again and rerun the loop
            freeSlots.addSlot(freeSlotTemp);
        }

        //Loop ended, assign the slot to the item and vice versa
        freeSlotTemp.setItem(j.getItem());  //add item to slot
        j.getItem().setSlot(freeSlotTemp);  //add slot to item

        //Execute the move from the pickup location to the place location
        if (!gantryMoveIsSafe(gantryOutput, freeSlotTemp)) {
            executeGantryMove(new Job(0, null, null, problem.getInputSlot()), gantryInput);
            performedActions.add(new Output(gantryInput.getId(), clock,
                    gantryInput.getCurrentX(), gantryInput.getCurrentY(), -1));
        }
        executeMoveJob(freeSlotTemp, j, gantryOutput);

    }

    private boolean gantryMoveIsSafe(Gantry gantry, Slot toGoSlot) {

        //get coordinates of the other crane
        int currentXOtherCrane;

        if (gantry.getId() == 0) {
            //if the current crane is the input crane, get coordinates of the output crane
            currentXOtherCrane = gantryOutput.getCurrentX();
            if (toGoSlot.getCenterX() > currentXOtherCrane) {
                System.out.println(clock + " || input would cross output");
                return false;
                //the pickup location is beyond the output crane its x value.
            }
            // SO the input crane should go over the output crane, which is not possible
        } else {
            currentXOtherCrane = gantryInput.getCurrentX();
            if (toGoSlot.getCenterX() < currentXOtherCrane) {
                System.out.println(clock + " || output would cross input");
                return false;
            }
        }

        //If the absolute value of the difference between the x value of the pickup
        // and the x value of the other crane is smaller than 20, the move is not safe
        return abs(toGoSlot.getCenterX() - currentXOtherCrane) >= 20;

    }

    private void executeGantryMove(Job j, Gantry gantry) {

        if(j.getPickup().getSlot() != null ){
            //calculate pickup time (moving gantry from current position to input slot)
            timeToAdd = calculateGantryMoveTime(j.getPickup().getSlot(), gantry);
            updateClock(clock, timeToAdd);

            //set new gantry coordinates
            gantrySetCoordinates(gantry, j.getPickup().getSlot());
            gantry.setCurrentSlot(j.getPickup().getSlot());

            //action performed: adding to output
            performedActions.add(new Output(gantry.getId(), clock,
                    gantry.getCurrentX(), gantry.getCurrentY(), -1));

        }

        if (j.getItem() != null) {
            updateClock(clock, problem.getPickupPlaceDuration());
            performedActions.add(new Output(gantry.getId(), clock,
                    gantry.getCurrentX(), gantry.getCurrentY(), j.getItem().getId()));
        }

        if(j.getPickup().getSlot() == null){
            timeToAdd = calculateGantryMoveTime(j.getPlace().getSlot(), gantry);
            updateClock(clock, timeToAdd);

            System.out.println("CLOCK:" + clock);

            gantrySetCoordinates(gantry, j.getPlace().getSlot());
            gantry.setCurrentSlot(j.getPlace().getSlot());

            //action performed: adding to output
            performedActions.add(new Output(gantry.getId(), clock,
                    gantry.getCurrentX(), gantry.getCurrentY(), -1));
        }


    }

    private void executeMoveJob(Slot toFillSlot, Job j, Gantry gantry) {

        //calculate delivery time (moving from input slot to free slot
        timeToAdd = calculateGantryMoveTime(toFillSlot, gantry);
        updateClock(clock, timeToAdd);

        //set new gantry coordinates
        //new references between slot and item are both ways, so we can reference the slot via the item
        gantrySetCoordinates(gantry, j.getItem().getSlot());

        //action performed; adding to output
        gantry.setCurrentSlot(toFillSlot);
        performedActions.add(new Output(gantry.getId(), clock,
                gantry.getCurrentX(), gantry.getCurrentY(), j.getItem().getId()));
        updateClock(clock, problem.getPickupPlaceDuration());
        performedActions.add(new Output(gantry.getId(), clock,
                gantry.getCurrentX(), gantry.getCurrentY(), -1));

    }

    /**
     * Function which handles the output job sequence
     * Deze methode handelt een output job af, het zal dus een item uitgraven als boven hem en dan naar de output plaats
     * brengen
     */
    private void handleOutputJobs(Job j) {

        //for (Job j : problem.getOutputJobSequence()) {
        // First, get item and find slot from item
        Item i = problem.getItems().get(j.getItem().getId());
        //System.out.println(i.getId());

        Slot pickupSlot = i.getSlot();

        if(pickupSlot != null){
            pickupLevel = pickupSlot.getZ();
            // Second recursively look for any children and reallocate them
            if (pickupSlot.getItem() != null)
                digSlotOut(pickupSlot);


            //Set the pickup slot, needed for the executeGantryMove function
            j.getPickup().setSlot(pickupSlot);

            //All children are dug out, move the gantry again
            if (!gantryMoveIsSafe(gantryOutput, j.getPickup().getSlot())) {
                executeGantryMove(new Job(0, null, null, problem.getInputSlot()), gantryInput);
                performedActions.add(new Output(gantryInput.getId(), clock,
                        gantryInput.getCurrentX(), gantryInput.getCurrentY(), -1));
            }

            performedActions.add(new Output(gantryOutput.getId(), clock,
                    gantryOutput.getCurrentX(), gantryOutput.getCurrentY(), -1));
            executeGantryMove(j, gantryOutput);

            //Assign output slot to the item
            i.setSlot(problem.getOutputSlot());

            //Execute the move from the pickup location to the place location
            if (!gantryMoveIsSafe(gantryOutput, problem.getOutputSlot())) {
                executeGantryMove(new Job(0, null, null, problem.getOutputSlot()), gantryInput);
                performedActions.add(new Output(gantryInput.getId(), clock, gantryInput.getCurrentX(), gantryInput.getCurrentY(), j.getItem().getId()));
            }

            executeMoveJob(problem.getOutputSlot(), j, gantryOutput);
            pickupSlot.setItem(null);
        }

        //}
    }

    /**
     * Resurive methode die een effectief een item zal uitgraven
     */
    private void digSlotOut(Slot s) {

        if (s.getChildLeft() != null) {
            if (s.getChildLeft().getItem() != null)
                digSlotOut(s.getChildLeft());
        }

        //Every move can be seen as an input job but with a different start slot.

        if (s.getChildRight() != null) {
            if (s.getChildRight().getItem() != null)
                digSlotOut(s.getChildRight());
        }

        if (s.getZ() != pickupLevel) {
            performInput(new Job(s.getId(), problem.getItems().get(s.getItem().getId()), s, null));
            s.setItem(null);
        }
    }

    /**
     * Function which calculates the minimum x-coordinate of the range in which no slots may be taken to ensure correct digging.
     */
    private int calculatePlacementRangeMinX(int pickupLevel, int maxLevels, int centerX) {

        int differenceInLevels = maxLevels - pickupLevel;
        int totalRangeWidth = differenceInLevels * 10;
        return centerX - (totalRangeWidth / 2);
    }

    /**
     * Function which calculates the maximum x-coordinate of the range in which no slots may be taken to ensure correct digging.
     */
    private int calculatePlacementRangeMaxX(int pickupLevel, int maxLevels, int centerX) {

        int differenceInLevels = maxLevels - pickupLevel;
        int totalRangeWidth = differenceInLevels * 10;
        return centerX + (totalRangeWidth / 2);
    }

    /**
     * Function which calculates the minimal amount of time required to go from the gantry's current position to the slot
     */
    private double calculateGantryMoveTime(Slot pickupSlot, Gantry gantry) {

        //calculate both x and y moving times, ad this can be done in parallel, the shortest one is returned
        double timeX = abs(gantry.getCurrentX() - pickupSlot.getCenterX()) / gantry.getXSpeed();
        double timeY = abs(gantry.getCurrentY() - pickupSlot.getCenterY()) / gantry.getYSpeed();

        if (timeX < timeY)
            return timeY;
        else
            return timeX;

    }

    /**
     * Function which calculates the minimal time required to move an item from the input slot to the given slot.
     */
    public double calculateSlotInputMoveTime(Gantry gantryInput, Slot s) {

        //calculate both x and y moving times, ad this can be done in parallel, the shortest one is returned
        double timeX = abs(problem.getInputSlot().getCenterX() - s.getCenterX()) / gantryInput.getXSpeed();
        double timeY = abs(problem.getInputSlot().getCenterY() - s.getCenterY()) / gantryInput.getYSpeed();

        if (timeX < timeY)
            return timeY;
        else
            return timeX;
    }

    /**
     * Function which updates the clock
     */
    private void updateClock(double clock, double time) {
        setClock(clock + time);
    }

    /**
     * Function which updates the coordinates of the crane
     */
    private void gantrySetCoordinates(Gantry gantry, Slot slot) {
        gantry.setCurrentX(slot.getCenterX());
        gantry.setCurrentY(slot.getCenterY());
    }

    public void printOutput() {
        for (Output o : performedActions) {
            System.out.println("///////////////////////////////////");
            System.out.println("Gantry ID: " + o.getGantryID());
            System.out.println("Clock Time: " + o.getTimeStamp());
            System.out.println("X coordinate: " + o.getxCoordinate());
            System.out.println("Y coordinate: " + o.getyCoordinate());
            System.out.println("item ID: " + o.getItemInCraneID());

        }
    }

    /**
     * Function which converts all output objects into a .csv file
     *
     * @param outputFileName Name of the output file
     */
    void writeOutput(String outputFileName) throws IOException {

        PrintWriter fw = new PrintWriter(outputFileName);

        for (Output o : performedActions) {
            String s = "";
            s = o.getGantryID() + ";" + o.getTimeStamp() + ";" + o.getxCoordinate()
                    + ";" + o.getyCoordinate() + ";" + o.getItemInCraneID() + "\n";
            System.out.println(s);
            fw.write(s);
        }
        fw.close();
    }

    //Getters and setters

    public double getClock() {
        return clock;
    }

    private void setClock(double clock) {
        this.clock = clock;
    }

}
