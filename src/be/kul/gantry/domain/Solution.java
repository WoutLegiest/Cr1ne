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
    private Gantry gantryInput, gantryOutput;
    private SlotStructure slotStructure;
    //todo interval aanpassen aan traagste speed
    private final static  int timeInterval = 5;
    private static int safetyDistance = 0;

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
        gantryInput.setItemId(-1);
        safetyDistance = problem.getSafetyDistance();

        if (problem.getGantries().size() == 2) {
            gantryOutput = problem.getGantries().get(1);
            gantryOutput.setItemId(-1);
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
        Job inputJob = problem.getInputJobSequence().get(0);
        //get first item of output queue
        Job outputJob = problem.getOutputJobSequence().get(0);
        int outputJobCount = 0;
        int inputJobCount = 0;
        while(true){
            //print current position gantries
            performedActions.add(new Output(gantryInput.getId(), clock,
                    gantryInput.getCurrentX(), gantryInput.getCurrentY(), gantryInput.getItemId()));
            performedActions.add(new Output(gantryOutput.getId(), clock,
                    gantryOutput.getCurrentX(), gantryOutput.getCurrentY(), gantryOutput.getItemId()));

            // When moving a gantry, update x position (based on how many x positions we can move during the time interval of 5)
            // and update the Y coordinate until the y coordinate of the pickup/drop slot is reached

            //check if output gantry can continue based on current position of input gantry
            // if so move the output gantry
            // if not move input gantry as far as needed to let the output gantry reach the pickup slot
            // and update clock so that the rest of the output job can be performed as a whole without interruption
            if(outputJobCount != problem.getOutputJobSequence().size()){
                if(outputJob.getItem().getSlot() != null){
                    pickupLevel = outputJob.getItem().getSlot().getZ();
                    boolean feasibleMove = checkIfOutputGantryMoveIsFeasible(gantryOutput,gantryInput,outputJob);
                    if(feasibleMove){
                        executeOutputGantryMove(gantryOutput, outputJob);
                    }else{
                        //TODO: move input gantry as far as needed (maybe keep dig-out zone in mind)
                    }
                    // if the gantry is directly above the slot it has to pickup, check for possible dig out and give absolute priority
                    if(gantryOutput.getCurrentX() == outputJob.getItem().getSlot().getCenterX() && gantryOutput.getCurrentY() == outputJob.getItem().getSlot().getCenterY()){
                        if(crossed){
                            // if necessary move input gantry out of the way
                            digSlotOutCrossed(outputJob.getItem().getSlot());
                        }else{
                            digSlotOutStacked(outputJob.getItem().getSlot());
                        }
                        outputJobCount++;
                        // assign new output job
                        outputJob = problem.getOutputJobSequence().get(outputJobCount);
                    }
                }
            }


            //check if input gantry can continue based on current position of the output gantry
            // if so move the input gantry

            // update the clock, add the time interval
        }
    }

    public boolean checkIfOutputGantryMoveIsFeasible(Gantry outputGantry, Gantry inputGantry, Job job){
        int outputXCoordinate = outputGantry.getCurrentX();
        int outputGantryPossibleXMove = (int)outputGantry.getXSpeed() * timeInterval;
        int inputGantryCurrentXPosition = inputGantry.getCurrentX();

        int pickupXCoordinate = job.getItem().getSlot().getCenterX();

        if(pickupXCoordinate < outputXCoordinate){
            return outputXCoordinate - outputGantryPossibleXMove > inputGantryCurrentXPosition + safetyDistance;
        }
        else{
            return true;
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
    private void executeOutputGantryMove(Gantry gantry, Job j){
        int outputXCoordinate = gantry.getCurrentX();
        int outputGantryPossibleXMove = (int)gantry.getXSpeed() * timeInterval;
        int pickupXCoordinate = j.getItem().getSlot().getCenterX();
        int pickupYCoordinate = j.getItem().getSlot().getCenterY();


        // check if we have to move to the left
        if(outputXCoordinate > pickupXCoordinate){

            // check if we need less distance then foreseen feasible distance by the time interval
            // this means we can reach the x coordinate of the pickup slot
            if(outputXCoordinate - pickupXCoordinate < outputGantryPossibleXMove){

                // voor de output rekening houden met de mindere klok tijd die we nodig hadden! (done)
                // set the x coordinate as we can reach ths lot within the time interval
                gantry.setCurrentX(pickupXCoordinate);

                // calculate the actual time required
                double timeRequired = (outputXCoordinate - pickupXCoordinate)/gantry.getXSpeed();


                // check whether we could reach the Y coordinate within this smaller time frame
                int outputYCoordinate = gantry.getCurrentY();
                int outputGantryPossibleYMove = (int) (gantry.getYSpeed() * timeRequired);
                //check if the y coordinate of the pickup is on a higher level than the current y coordinate of the gantry
                // if it is equal we do not execute
                if(gantry.getCurrentY() > pickupYCoordinate){
                    // now we have to move down on the y axis
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate - outputGantryPossibleYMove <= pickupYCoordinate){
                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(pickupYCoordinate);
                        performedActions.add(new Output(gantry.getId(), clock + timeRequired,
                                gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{
                        // y coordinate of the pickup slot could not be reached within the new time interval
                        // as the y move can be done while the gantry is stationary x-wise, we can set the
                        // y coordinate and add the action to the output
                        timeRequired = (outputYCoordinate - pickupYCoordinate )/ gantry.getYSpeed();
                        gantry.setCurrentY(pickupYCoordinate);
                        performedActions.add(new Output(gantry.getId(), clock + timeRequired,
                                gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }
                }else if(gantry.getCurrentY() < pickupYCoordinate){
                    // now we have to move up
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate + outputGantryPossibleYMove >= pickupYCoordinate){
                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(pickupYCoordinate);
                        performedActions.add(new Output(gantry.getId(), clock + timeRequired,
                                gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{
                        // y coordinate of the pickup slot could not be reached within the new time interval
                        // as the y move can be done while the gantry is stationary x-wise, we can set the
                        // y coordinate and add the action to the output
                        timeRequired = (outputYCoordinate + pickupYCoordinate )/ gantry.getYSpeed();
                        gantry.setCurrentY(pickupYCoordinate);
                        performedActions.add(new Output(gantry.getId(), clock + timeRequired,
                                gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }

                }
            }
            // We cannot reach this pickup slot within the time frame
            else if(outputXCoordinate - pickupXCoordinate >= outputGantryPossibleXMove){
                gantry.setCurrentX(outputXCoordinate - outputGantryPossibleXMove);

                // check if the Y coordinate can be reached
                int outputYCoordinate = gantry.getCurrentY();
                int outputGantryPossibleYMove = (int) (gantry.getYSpeed() * timeInterval);
                //check if the y coordinate of the pickup is on a higher level than the current y coordinate of the gantry
                if(gantry.getCurrentY() > pickupYCoordinate){
                    // now we have to move down on the y axis
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate - outputGantryPossibleYMove <= pickupYCoordinate){
                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(pickupYCoordinate);
                        double timeRequired = (outputYCoordinate - pickupYCoordinate)/gantry.getYSpeed();
                        performedActions.add(new Output(gantry.getId(), clock + timeRequired,
                                gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{
                        // y coordinate of the pickup slot could not be reached within the new time interval
                        gantry.setCurrentY(outputYCoordinate - outputGantryPossibleYMove);
                        performedActions.add(new Output(gantry.getId(), clock + timeInterval,
                                gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }
                }else if(gantry.getCurrentY() < pickupYCoordinate){
                    // now we have to move up
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate + outputGantryPossibleYMove >= pickupYCoordinate){
                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(pickupYCoordinate);
                        double timeRequired = (outputYCoordinate - pickupYCoordinate)/gantry.getYSpeed();
                        performedActions.add(new Output(gantry.getId(), clock + timeRequired,
                                gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{
                        // y coordinate of the pickup slot could not be reached within the new time interval
                        gantry.setCurrentY(outputYCoordinate + outputGantryPossibleYMove);
                        performedActions.add(new Output(gantry.getId(), clock + timeInterval,
                                gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }

                }
            }
        }
        // move to the right
        else if(outputXCoordinate < pickupXCoordinate){
            // check if we need less distance then foreseen feasible distance by the time interval
            // this means we reach x coordinate of the pickup slot
            if(pickupXCoordinate - outputXCoordinate < outputGantryPossibleXMove){

                // voor de output rekening houden met de mindere klok tijd die we nodig hadden! (done)
                // set the x coordinate as we can reach ths lot within tht time interval
                gantry.setCurrentX(pickupXCoordinate);

                // calculate the actual time required
                double timeRequired = (pickupXCoordinate - outputXCoordinate)/gantry.getXSpeed();


                // check whether we could reach the Y coordinate within this smaller time frame
                int outputYCoordinate = gantry.getCurrentY();
                int outputGantryPossibleYMove = (int) (gantry.getYSpeed() * timeRequired);
                //check if the y coordinate of the pickup is on a higher level than the current y coordinate of the gantry
                // if it is equal we do not execute
                if(gantry.getCurrentY() > pickupYCoordinate){
                    // now we have to move down on the y axis
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate - outputGantryPossibleYMove <= pickupYCoordinate){
                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(pickupYCoordinate);
                        performedActions.add(new Output(gantry.getId(), clock + timeRequired,
                                gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{
                        // y coordinate of the pickup slot could not be reached within the new time interval
                        // as the y move can be done while the gantry is stationary x-wise, we can set the
                        // y coordinate and add the action to the output
                        timeRequired = (outputYCoordinate - pickupYCoordinate )/ gantry.getYSpeed();
                        gantry.setCurrentY(pickupYCoordinate);
                        performedActions.add(new Output(gantry.getId(), clock + timeRequired,
                                gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }
                }else if(gantry.getCurrentY() < pickupYCoordinate){
                    // now we have to move up
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate + outputGantryPossibleYMove >= pickupYCoordinate){
                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(pickupYCoordinate);
                        performedActions.add(new Output(gantry.getId(), clock + timeRequired,
                                gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{
                        // y coordinate of the pickup slot could not be reached within the new time interval
                        // as the y move can be done while the gantry is stationary x-wise, we can set the
                        // y coordinate and add the action to the output
                        timeRequired = (outputYCoordinate + pickupYCoordinate )/ gantry.getYSpeed();
                        gantry.setCurrentY(pickupYCoordinate);
                        performedActions.add(new Output(gantry.getId(), clock + timeRequired,
                                gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }

                }
            }
            // We cannot reach this pickup slot within the time frame
            else if(outputXCoordinate - pickupXCoordinate >= outputGantryPossibleXMove){
                gantry.setCurrentX(outputXCoordinate - outputGantryPossibleXMove);

                // check if the Y coordinate can be reached
                int outputYCoordinate = gantry.getCurrentY();
                int outputGantryPossibleYMove = (int) (gantry.getYSpeed() * timeInterval);
                //check if the y coordinate of the pickup is on a higher level than the current y coordinate of the gantry
                if(gantry.getCurrentY() > pickupYCoordinate){
                    // now we have to move down on the y axis
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate - outputGantryPossibleYMove <= pickupYCoordinate){
                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(pickupYCoordinate);
                        double timeRequired = (outputYCoordinate - pickupYCoordinate)/gantry.getYSpeed();
                        performedActions.add(new Output(gantry.getId(), clock + timeRequired,
                                gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{
                        // y coordinate of the pickup slot could not be reached within the new time interval
                        gantry.setCurrentY(outputYCoordinate - outputGantryPossibleYMove);
                        performedActions.add(new Output(gantry.getId(), clock + timeInterval,
                                gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }
                }else if(gantry.getCurrentY() < pickupYCoordinate){
                    // now we have to move up
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate + outputGantryPossibleYMove >= pickupYCoordinate){
                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(pickupYCoordinate);
                        double timeRequired = (outputYCoordinate - pickupYCoordinate)/gantry.getYSpeed();
                        performedActions.add(new Output(gantry.getId(), clock + timeRequired,
                                gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{
                        // y coordinate of the pickup slot could not be reached within the new time interval
                        gantry.setCurrentY(outputYCoordinate + outputGantryPossibleYMove);
                        performedActions.add(new Output(gantry.getId(), clock + timeInterval,
                                gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }

                }
            }
        }
        // if we are already on the right x coordinate
        else if(outputXCoordinate == pickupXCoordinate){

            // check if the Y coordinate can be reached
            int outputYCoordinate = gantry.getCurrentY();
            int outputGantryPossibleYMove = (int) (gantry.getYSpeed() * timeInterval);
            //check if the y coordinate of the pickup is on a higher level than the current y coordinate of the gantry
            if(gantry.getCurrentY() > pickupYCoordinate){
                // now we have to move down on the y axis
                // can we reach te y coordinate within the new time interval
                if(outputYCoordinate - outputGantryPossibleYMove <= pickupYCoordinate){
                    //we can reach the pickup slot y coordinate within the new time interval
                    //set y coordinate
                    gantry.setCurrentY(pickupYCoordinate);
                    // calculate the actual time required to perform the move
                    double timeRequired = (outputYCoordinate - pickupYCoordinate)/gantry.getYSpeed();
                    performedActions.add(new Output(gantry.getId(), clock + timeRequired,
                            gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                }else{
                    // y coordinate of the pickup slot could not be reached within the new time interval
                    gantry.setCurrentY(outputYCoordinate - outputGantryPossibleYMove);
                    performedActions.add(new Output(gantry.getId(), clock + timeInterval,
                            gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                }
            }else if(gantry.getCurrentY() < pickupYCoordinate){
                // now we have to move up
                // can we reach te y coordinate within the new time interval
                if(outputYCoordinate + outputGantryPossibleYMove >= pickupYCoordinate){
                    //we can reach the pickup slot y coordinate within the new time interval
                    //set y coordinate
                    gantry.setCurrentY(pickupYCoordinate);
                    double timeRequired = (outputYCoordinate - pickupYCoordinate)/gantry.getYSpeed();
                    performedActions.add(new Output(gantry.getId(), clock + timeRequired,
                            gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                }else{
                    // y coordinate of the pickup slot could not be reached within the new time interval
                    gantry.setCurrentY(outputYCoordinate + outputGantryPossibleYMove);
                    performedActions.add(new Output(gantry.getId(), clock + timeInterval,
                            gantryInput.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                }

            }
        }

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
     * Resurive methode die een effectief een item zal uitgraven
     */
    private void digSlotOutCrossed(Slot s) {
        //TODO check gantry move feasibility
        if (s.getChildLeft() != null)
            if (s.getChildLeft().getItem() != null)
                digSlotOutCrossed(s.getChildLeft());

        //Every move can be seen as an input job but with a different start slot.

        if (s.getChildRight() != null)
            if (s.getChildRight().getItem() != null)
                digSlotOutCrossed(s.getChildRight());

        if (s.getZ() != pickupLevel) {
            // we check the Z level to make sure we can do the pickup and delivery of the slot which has to go towards the output slot, outside this function
            performInputCrossed(new Job(s.getId(), problem.getItems().get(s.getItem().getId()), s, null));
            s.setItem(null);
        }

    }

    /**
     * Recursive method that will dig out a slot
     * @param s the to dig out slot
     */
    private void digSlotOutStacked(Slot s) {
        //TODO: check gantry move feasibility
        if (s.getChildLeft() != null) {
            if (s.getChildLeft().getItem() != null){
                System.out.println("Recursie voor ");
                digSlotOutStacked(s.getChildLeft());
            }


        }

        //Every move can be seen as an input job but with a different start slot.
        //performInputStacked(new Job(s.getId(), problem.getItems().get(s.getItem().getId()), s, null));
        s.setItem(null);

    }

    public void performInputCrossed(Job j){
        // first make sure the input gantry is not in the way

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
