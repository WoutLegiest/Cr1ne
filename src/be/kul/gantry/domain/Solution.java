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
    private List<Output> performedActions = new ArrayList<>();
    private double timeToAdd = 0;
    private Gantry gantryInput;
    private Gantry gantryOutput;
    private SlotStructure slotStructure;

    /**
     * Constructor, create problem from Json file, setup gantries, setup free slot Queue.
     */
    public Solution(String input) throws IOException, ParseException {
        // Reading the information from the jason file
        try {
            problem = Problem.fromJson(new File(input));
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }

        //Setup gantries
        gantryInput = problem.getGantries().get(0);
        if (problem.getGantries().size() == 2) {
            gantryOutput = problem.getGantries().get(1);
        }

        //Create new slot structure
        slotStructure = new SlotStructure();


        //Adding all the slots to the HashMap
        String slotCoordinate;
        List<Slot> tempFreeSlots = new ArrayList<>();
        for (Slot s : problem.getSlots()) {
            //create key by coordinate
            slotCoordinate = String.valueOf(s.getCenterX()) + "," + String.valueOf(s.getCenterY())
                    + "," + String.valueOf(s.getZ());
            System.out.print(slotCoordinate);
            if (s.getItem() == null)
                tempFreeSlots.add(s);

            //Calculate gantry move time between input and current slot (usable for optimalization)
            s.setPickupTime(calculateSlotInputMoveTime(gantryInput, s));
            slotStructure.getSlotStructureMap().put(slotCoordinate, s);

            //add slot to possible parent as a child
            slotStructure.setChild(s);
        }

        //Create new freeSlots object
        freeSlots = new FreeSlots(tempFreeSlots);
        //Remove input slot from the queue (bug in file reader)
        freeSlots.getFreeSlots().remove();

        //add start position of gantry to output
        performedActions.add(new Output(gantryInput.getId(), clock, gantryInput.getCurrentX(),
                gantryInput.getCurrentY(), -1));

    }

    /**
     * Function which goes through the input job sequence
     */
    public void handleInputJobs() {
        for (Job j : problem.getInputJobSequence()) {
            performInput(j);
        }
    }

    /**
     * Function which executes the input job
     *
     * @param @Job j
     */
    public void performInput(Job j) {

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
     * Function which handles the output job sequence
     */
    public void handleOutputJobs() {
        for (Job j : problem.getOutputJobSequence()) {
            // First, get item and find slot from item
            Item i = problem.getItems().get(j.getItem().getId());
            //System.out.println(i.getId());
            Slot pickupSlot = i.getSlot();
            // Second recursively look for any children and reallocate them
            if (pickupSlot.getItem() != null && pickupSlot.getChild() != null) {
                if (pickupSlot.getChild().getItem() != null) {
                    digSlotOut(pickupSlot.getChild());

                }

            }

            //Set the pickup slot, needed for the executeGantryMove function
            j.getPickup().setSlot(pickupSlot);

            //Move the gantry
            executeGantryMove(j);

            //Assign the output slot to the item
            i.setSlot(problem.getOutputSlot());

            //Execute the move from the pickup location to the place location
            executeMoveJob(problem.getOutputSlot(), j);

        }
    }

    /**
     * Function which executes the movement of the crane from its start location to the pickup location of the item.
     * Updates the clock where necessary. Adds movements to the performed actions list
     *
     * @param @Slot j
     * */
    public void executeGantryMove(Job j){
        //calculate pickup time (moving gantry from current position to input slot)
        timeToAdd = calculateGantryMoveTime(j.getPickup().getSlot(), gantryInput);
        updateClock(clock, timeToAdd);
        //set new gantry coordinates
        gantrySetCoordinates(gantryInput, j.getPickup().getSlot());
        //action performed: adding to output
        performedActions.add(new Output(gantryInput.getId(), clock, gantryInput.getCurrentX(), gantryInput.getCurrentY(), -1));
        updateClock(clock, problem.getPickupPlaceDuration());
        performedActions.add(new Output(gantryInput.getId(), clock, gantryInput.getCurrentX(), gantryInput.getCurrentY(), j.getItem().getId()));

    }

    /**
     * Function which executes the movement of the item from its pickup location to its place location.
     * Updates the clock where necessary. Adds movements to the performed actions list.
     *
     * @param @Slot toFillSlot
     * @param @Job j
     * */
    public void executeMoveJob(Slot toFillSlot, Job j){
        //calculate delivery time (moving from input slot to free slot
        timeToAdd = calculateGantryMoveTime(toFillSlot, gantryInput);
        //calculate delivery time (moving from input slot to free slot
        timeToAdd = calculateGantryMoveTime(toFillSlot, gantryInput);
        updateClock(clock, timeToAdd);
        //set new gantry coordinates
        gantrySetCoordinates(gantryInput, j.getItem().getSlot());  //new references between slot and item are both ways, so we can reference the slot via the item
        //action performed; adding to output
        performedActions.add(new Output(gantryInput.getId(), clock, gantryInput.getCurrentX(), gantryInput.getCurrentY(), j.getItem().getId()));
        updateClock(clock, problem.getPickupPlaceDuration());
        performedActions.add(new Output(gantryInput.getId(), clock, gantryInput.getCurrentX(), gantryInput.getCurrentY(), -1));

    }

    /**
     * Recursive method which digs out the necessary items.
     *
     * @param @Slot s
     */
    public void digSlotOut(Slot s) {

        if (s.getChild() != null) {
            if (s.getChild().getItem() != null) {
                digSlotOut(s.getChild());

            }
        }


        //Every move can be seen as an input job but with a different start slot.
        performInput(new Job(s.getId(), problem.getItems().get(s.getItem().getId()), s, null));
        s.setItem(null);

    }

    /**
     * Function which calculates the minimal amount of time required to go from the gantry's current position to the slot
     *
     * @param @Slot pickupSlot
     * @param @Gantry gantry
     */
    public double calculateGantryMoveTime(Slot pickupSlot, Gantry gantry) {

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
     *
     * @param @Gantry gantryInput
     * @param @Slot s
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
     *
     * @param @double clock
     * @param @double time
     */
    public void updateClock(double clock, double time) {
        setClock(clock + time);
    }

    /**
     * Function which updates the coordinates of the crane
     *
     * @param @Gantry gantry
     * @param @Slot slot
     */
    public void gantrySetCoordinates(Gantry gantry, Slot slot) {
        gantry.setCurrentX(slot.getCenterX());
        gantry.setCurrentY(slot.getCenterY());
    }

    /**
     * Function which converts all output objects into a .csv file
     */
    public void writeOutput(String output) throws IOException {

        PrintWriter fw = new PrintWriter(output);
        StringBuffer sb = new StringBuffer();

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

    public void setClock(double clock) {
        this.clock = clock;
    }

}
