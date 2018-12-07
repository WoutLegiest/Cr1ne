package be.kul.gantry.domain;

import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import static java.lang.Math.abs;

@SuppressWarnings("Duplicates")
public class Solution {

    private double clock = 0;
    private Problem problem;

    private FreeSlots freeSlots;
    private List<Output> performedActions = new ArrayList<>();
    private double timeToAdd = 0;
    private Gantry gantryInput;
    private SlotStructure slotStructure;

    private int pickupLevel;

    static boolean crossed = true;

    /**
     * Constructor, create problem from Json file, setup gantries, setup free slot Queue.
     * @param inputFileName Name of the inputfile
     */
    Solution(String inputFileName) {

        String[] inputFile = inputFileName.split("_");
        crossed = Boolean.parseBoolean(inputFile[4]);

        //Reading the information from the jason file
        try {
            problem = Problem.fromJson(new File(inputFileName));
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }

        //Setup gantries
        gantryInput = problem.getGantries().get(0);

        if (problem.getGantries().size() == 2) {
            Gantry gantryOutput = problem.getGantries().get(1);
        }

        //Create new slot structure
        slotStructure = new SlotStructure();

        //Adding all the slots to the HashMap
        String slotCoordinate;
        List<Slot> tempFreeSlots = new ArrayList<>();

        for (Slot s : problem.getSlots()) {

            //create key by coordinate
            slotCoordinate = String.valueOf(s.getCenterX()) + ","
                    + String.valueOf(s.getCenterY()) + "," + String.valueOf(s.getZ());
            System.out.print(slotCoordinate);

            if (s.getItem() == null)
                tempFreeSlots.add(s);

            //Calculate gantry move time between input and current slot (usable for optimalization)
            s.setPickupTime(calculateSlotInputMoveTime(gantryInput, s));
            slotStructure.getSlotStructureMap().put(slotCoordinate, s);

            //add slot to possible parent as a child
            if(crossed)
                slotStructure.setChildCrossed(s);
            else
                slotStructure.setChildStacked(s);
        }

        //Create new freeSlots object
        freeSlots = new FreeSlots(tempFreeSlots);
        //Remove input slot from the queue (bug in file reader)
        freeSlots.getFreeSlots().remove();

        //add start position of gantry to output
        performedActions.add(new Output(gantryInput.getId(), clock,
                gantryInput.getCurrentX(), gantryInput.getCurrentY(), -1));

    }

    public void executeSolution(){
        //get first item from input queue
        Job initialInputJob = problem.getInputJobSequence().get(0);
        //get first item of output queue
        Job initialOutputJob = problem.getOutputJobSequence().get(0);
        while(true){
            //print current position gantries

            //check
            // When moving a gantry, update x position (based on how many x positions we can move during the time interval of 5)
            // and update the Y coordinate until the y coordinate of the pickup/drop slot is reached

            //check if output gantry can continue based on current position of input gantry
            // if so move the output gantry
            // if not move input gantry as far as needed to let the output gantry reach the pickup slot
            // and update clock so that the rest of the output job can be performed as a whole without interruption

            //check if input gantry can continue based on current position of the output gantry
            // if so move the input gantry
        }
    }

    /**
     * Function which goes through the input job sequence
     */
    public void handleInputJobsCrossed() {

        //Alle inputJobs are been handeld
        for (Job j : problem.getInputJobSequence()) {

            //Eerst de kraan klaarzetten om de job te doen
            //Deze print ook al moves uit van de kraan
            executeGantryMove(j);

            //Generate free slot
            Slot freeSlotTemp;
            Slot toFillSlot = null;
            String slotCoordinate = "";

            //Fill freeSlot
            while (true) {

                freeSlotTemp = freeSlots.getFreeSlots().remove();

                //map toFillSlot object to object from hashMap, with centercoordinates
                slotCoordinate = String.valueOf(freeSlotTemp.getCenterX()) + ","
                        + String.valueOf(freeSlotTemp.getCenterY()) + "," + String.valueOf(freeSlotTemp.getZ());

                //Lus stop met lopen als er een goede slot is gevonden
                //Een slot waarvan de de 2 plaatsen onder hem vrij zijn
                if(checkUnderneath(freeSlotTemp))
                    break;

                freeSlots.addSlot(freeSlotTemp);
            }

            toFillSlot = slotStructure.getSlotStructureMap().get(slotCoordinate);

            toFillSlot.setItem(j.getItem());  //add item to slot
            j.getItem().setSlot(toFillSlot);  //add slot to item

            executeMoveJob(toFillSlot,j);
        }
    }

    /**
     * Methode for the stacked version of the problem
     */
    public void handleInputJobsStacked() {

        for (Job j : problem.getInputJobSequence()) {

            //Move the gantry to the pickup location
            executeGantryMove(j);

            //Initialize variables to find free slot
            Slot freeSlotTemp = null;
            String slotCoordinate = "";

            //Setup loop
            boolean goodSlot = false;
            while(!goodSlot){
                //Get slot from the queue
                freeSlotTemp = freeSlots.getFreeSlots().remove();

                //Check if place slot is not the same as the pickup slot
                if(freeSlotTemp.getCenterX() != j.getPickup().getSlot().getCenterX()
                        && freeSlotTemp.getCenterY() != j.getPickup().getSlot().getCenterY()){
                    goodSlot = true;
                }

                //If slot is not correct, add to the queue
                freeSlots.addSlot(freeSlotTemp);
            }

            System.out.println("Graken uit de while");

            //Set both references
            freeSlotTemp.setItem(j.getItem());  //add item to slot
            j.getItem().setSlot(freeSlotTemp);  //add slot to item

            //Execute the move from the pickup location to the place location
            executeMoveJob(freeSlotTemp, j);
        }
    }

    /**
     * This method checks if there are items under the slot
     *
     * @param freeSlotTemp The to check slot
     * @return Boolean that is true if there are items under the current item
     */
    private boolean checkUnderneath(Slot freeSlotTemp){

        String slotCoordinateUnderLeft = String.valueOf(freeSlotTemp.getCenterX() - 5) + ","
                + String.valueOf(freeSlotTemp.getCenterY()) + "," + String.valueOf(freeSlotTemp.getZ() - 1);
        String slotCoordinateUnderRight = String.valueOf(freeSlotTemp.getCenterX() + 5) + ","
                + String.valueOf(freeSlotTemp.getCenterY()) + "," + String.valueOf(freeSlotTemp.getZ() - 1);

        Slot underLeft = slotStructure.getSlotStructureMap().get(slotCoordinateUnderLeft);
        Slot underRight = slotStructure.getSlotStructureMap().get(slotCoordinateUnderRight);

        //TODO: Verduidelijking, mag toch ook op de grond ?
        if (underLeft != null && underRight != null)
            return underLeft.getItem() != null && underRight.getItem() != null;

        return false;
    }

    /**
     * Moves the grantry to the place where the item is, and pick up
     * Write it out to performedActions
     * Part One of a Job
     * @param j To do job
     */
    private void executeGantryMove(Job j){

        //calculate pickup time (moving gantry from current position to input slot)
        timeToAdd = calculateGantryMoveTime(j.getPickup().getSlot(), gantryInput);
        updateClock(clock, timeToAdd);

        //set new gantry coordinates
        gantrySetCoordinates(gantryInput, j.getPickup().getSlot());

        //action performed: adding to output
        performedActions.add(new Output(gantryInput.getId(), clock,
                gantryInput.getCurrentX(), gantryInput.getCurrentY(), -1));
        updateClock(clock, problem.getPickupPlaceDuration());

        performedActions.add(new Output(gantryInput.getId(), clock,
                gantryInput.getCurrentX(), gantryInput.getCurrentY(), j.getItem().getId()));

    }

    /**
     * The item is already picked up and going to place it at its detination ( toFillSlot)
     * Part 2 of the job
     * @param toFillSlot place where the items needs to be
     * @param j the job that has to be handled
     */
    private void executeMoveJob(Slot toFillSlot, Job j){

        //calculate delivery time (moving from input slot to free slot
        timeToAdd = calculateGantryMoveTime(toFillSlot, gantryInput);
        updateClock(clock, timeToAdd);

        //set new gantry coordinates
        gantrySetCoordinates(gantryInput, j.getItem().getSlot());
        //new references between slot and item are both ways, so we can reference the slot via the item

        //action performed; adding to output
        performedActions.add(new Output(gantryInput.getId(), clock,
                gantryInput.getCurrentX(), gantryInput.getCurrentY(), j.getItem().getId()));
        updateClock(clock, problem.getPickupPlaceDuration());

        performedActions.add(new Output(gantryInput.getId(), clock,
                gantryInput.getCurrentX(), gantryInput.getCurrentY(), -1));

    }

    /**
     * Function which handles the output job sequence
     * Deze methode handelt een output job af, het zal dus een item uitgraven als boven hem en dan naar de output plaats
     * brengen
     */
    void handleOutputJobs() {

        for (Job j : problem.getOutputJobSequence()) {

            // First, get item and find slot from item
            Item i = problem.getItems().get(j.getItem().getId());

            //System.out.println(i.getId());
            Slot pickupSlot = i.getSlot();
            pickupLevel = pickupSlot.getZ();

            // Second recursively look for any children and reallocate them
            //Print it also out

            if(crossed){
                if (pickupSlot.getItem() != null)
                    digSlotOutCrossed(pickupSlot);
            }
            else{
                System.out.println("Juiste digout");
                if (pickupSlot.getItem() != null && pickupSlot.getChildLeft() != null) {
                    if (pickupSlot.getChildLeft().getItem() != null) {
                        System.out.println("Recursie");
                        digSlotOutStacked(pickupSlot.getChildLeft());
                    }

                }
            }

            System.out.println("Na digOut");

            //Set the pickup slot, needed for the executeGantryMove function
            j.getPickup().setSlot(pickupSlot);

            //All children are dug out, move the gantry again
            executeGantryMove(j);

            //Assign output slot to the item
            i.setSlot(problem.getOutputSlot());

            //Execute the move from the pickup location to the place location
            executeMoveJob(problem.getOutputSlot(),j);
            pickupSlot.setItem(null);
        }
    }

    /**
     * Resurive methode die een effectief een item zal uitgraven
     */
    private void digSlotOutCrossed(Slot s) {

        if (s.getChildLeft() != null)
            if (s.getChildLeft().getItem() != null)
                digSlotOutCrossed(s.getChildLeft());

        //Every move can be seen as an input job but with a different start slot.

        if (s.getChildRight() != null)
            if (s.getChildRight().getItem() != null)
                digSlotOutCrossed(s.getChildRight());

        if (s.getZ() != pickupLevel) {
            performInputCrossed(new Job(s.getId(), problem.getItems().get(s.getItem().getId()), s, null));
            s.setItem(null);
        }

    }

    /**
     * Recursive method that will dig out a slot
     * @param s the to dig out slot
     */
    private void digSlotOutStacked(Slot s) {

        if (s.getChildLeft() != null) {
            if (s.getChildLeft().getItem() != null){
                System.out.println("Recursie voor ");
                digSlotOutStacked(s.getChildLeft());
            }


        }

        //Every move can be seen as an input job but with a different start slot.
        performInputStacked(new Job(s.getId(), problem.getItems().get(s.getItem().getId()), s, null));
        s.setItem(null);

    }

    /**
     * Function which executes the input job
     * Beweging van een item van in de kade(of van in de input)  naar een andere plaats in de kade
     */
    private void performInputCrossed(Job j) {

        //Eerst de kraan klaarzetten om de job te doen
        executeGantryMove(j);

        //Get free slot
        Slot freeSlotTemp;

        //Variables needed for range calculation
        int minX;
        int maxX;

        //Initiate loop to find a correct slot
        while (true) {

            //Calculate placement ranges
            minX = calculatePlacementRangeMinX(pickupLevel, problem.getMaxLevels(),
                    j.getPickup().getSlot().getCenterX());
            maxX = calculatePlacementRangeMaxX(pickupLevel, problem.getMaxLevels(),
                    j.getPickup().getSlot().getCenterX());

            //Get slot from the queue
            freeSlotTemp = freeSlots.getFreeSlots().remove();

            //Check if freeSlotTemp is not within the range
            if (freeSlotTemp.getCenterY() == j.getPickup().getSlot().getCenterY()) {
                if (freeSlotTemp.getCenterX() < minX && freeSlotTemp.getCenterX() > maxX) {

                    //Check if the slot has two filled underlying slots.
                    if(checkUnderneath(freeSlotTemp))
                        break;
                }
            }
            //If slots has a different Y coordinate, no range check is needed
            else if (freeSlotTemp.getCenterY() != j.getPickup().getSlot().getCenterY())
                if(checkUnderneath(freeSlotTemp))
                    break;

            //If slot is not feasible, add to the queue again and rerun the loop
            freeSlots.addSlot(freeSlotTemp);

            System.out.println("Lus lus");
        }

        //Loop ended, assign the slot to the item and vice versa
        freeSlotTemp.setItem(j.getItem());  //add item to slot
        j.getItem().setSlot(freeSlotTemp);  //add slot to item

        //Execute the move from the pickup location to the place location
        executeMoveJob(freeSlotTemp,j);

    }

    private void performInputStacked(Job j) {

        //Move the gantry to the pickup location
        executeGantryMove(j);

        //Initialize variables to find free slot
        Slot freeSlotTemp = null;
        String slotCoordinate = "";

        //Setup loop
        boolean goodSlot = false;
        while(!goodSlot){
            //Get slot from the queue
            freeSlotTemp = freeSlots.getFreeSlots().remove();

            //Check if place slot is not the same as the pickup slot
            if(freeSlotTemp.getCenterX() != j.getPickup().getSlot().getCenterX() && freeSlotTemp.getCenterY() != j.getPickup().getSlot().getCenterY()){
                goodSlot = true;
            }

            //If slot is not correct, add to the queue
            freeSlots.addSlot(freeSlotTemp);
        }

        //Set both references
        freeSlotTemp.setItem(j.getItem());  //add item to slot
        j.getItem().setSlot(freeSlotTemp);  //add slot to item

        //Execute the move from the pickup location to the place location
        executeMoveJob(freeSlotTemp, j);

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
     * Function which calculates the minimal amount of time required
     * to go from the gantry's current position to the slot
     */
    private double calculateGantryMoveTime(Slot pickupSlot, Gantry gantry) {

        //calculate both x and y moving times, add this can be done in parallel, the shortest one is returned
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
    private double calculateSlotInputMoveTime(Gantry gantryInput, Slot s) {
        //calculate both x and y moving times, ad this can be done in parallel, the shortest one is returned
        double timeX = abs(problem.getInputSlot().getCenterX() - s.getCenterX()) / gantryInput.getXSpeed();
        double timeY = abs(problem.getInputSlot().getCenterY() - s.getCenterY()) / gantryInput.getYSpeed();
        if (timeX < timeY) {
            return timeY;
        } else {
            return timeX;
        }
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
     * @param outputFileName Name of the outputFile
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

    public static boolean isCrossed() {
        return crossed;
    }

    public static void setCrossed(boolean crossed) {
        Solution.crossed = crossed;
    }
}
