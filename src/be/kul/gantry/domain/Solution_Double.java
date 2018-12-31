package be.kul.gantry.domain;

import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import static java.lang.Math.abs;

@SuppressWarnings("Duplicates")
public class Solution_Double {

    private double clock = 0;
    private Problem problem;

    private FreeSlots freeSlots;
    private List<Output> performedActions = new ArrayList<>();
    private double timeToAdd = 0;
    private Gantry gantryInput, gantryOutput;
    private SlotStructure slotStructure;
    private final static  int timeInterval = 5;
    private static int safetyDistance = 0;

    private int pickupLevel;

    static boolean crossed = true;

    /**
     * Constructor, create problem from Json file, setup gantries, setup free slot Queue.
     * @param inputFileName Name of the inputfile
     */
    Solution_Double(String inputFileName) {

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
            //System.out.print(slotCoordinate);

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

                    // set the pickup slot in the job
                    outputJob.getPickup().setSlot(outputJob.getItem().getSlot());

                    // set the pickup level
                    pickupLevel = outputJob.getItem().getSlot().getZ();

                    // check for feasible move
                    boolean feasibleMove = checkIfOutputGantryMoveIsFeasibleForPickup(gantryOutput,gantryInput,outputJob);

                    if(!feasibleMove){
                        //move input gantry as far as needed (maybe keep dig-out zone in mind)
                        moveInputGantry(gantryInput, outputJob);
                        executeOutputGantryMove(gantryOutput, outputJob);
                    }else
                        executeOutputGantryMove(gantryOutput, outputJob);


                    // if the gantry is directly above the slot it has to pickup, check for possible dig out and give absolute priority
                    if(gantryOutput.getCurrentX() == outputJob.getItem().getSlot().getCenterX() &&
                            gantryOutput.getCurrentY() == outputJob.getItem().getSlot().getCenterY()){
                        if(crossed){
                            // if necessary move input gantry out of the way
                            digSlotOutCrossed(outputJob.getItem().getSlot());
                        }else
                            digSlotOutStacked(outputJob.getItem().getSlot());

                        // get clock value of the latest move
                        clock = performedActions.get(performedActions.size() - 1).getTimeStamp();
                        // now move the item to the output slot
                        // print current position
                        performedActions.add(new Output(gantryOutput.getId(), clock,
                                gantryOutput.getCurrentX(), gantryOutput.getCurrentY(), gantryOutput.getItemId()));

                        //move towards the pickup slot (unnecessary, is already done)
                        int outputXCoordinate = gantryOutput.getCurrentX();
                        int outputYCoordinate = gantryOutput.getCurrentY();
                        // calculate the time needed (maximum of x and y axis time)
                        double timeRequiredXAxis = abs(outputXCoordinate -
                                outputJob.getItem().getSlot().getCenterX())/gantryOutput.getXSpeed();
                        double timeRequiredYAxis = abs(outputYCoordinate -
                                outputJob.getItem().getSlot().getCenterY())/gantryOutput.getYSpeed();
                        double timeRequired;

                        if(timeRequiredXAxis > timeRequiredYAxis)
                            timeRequired = timeRequiredXAxis;
                        else
                            timeRequired = timeRequiredYAxis;

                        //move the output gantry
                        gantryOutput.setCurrentX(outputJob.getItem().getSlot().getCenterX());
                        gantryOutput.setCurrentY(outputJob.getItem().getSlot().getCenterY());

                        //update the clock
                        updateClock(clock, timeRequired);
                        //add to output
                        performedActions.add(new Output(gantryOutput.getId(), clock,
                                gantryOutput.getCurrentX(), gantryOutput.getCurrentY(), gantryOutput.getItemId()));

                        //add the item
                        gantryOutput.setItemId(outputJob.getItem().getId());
                        outputJob.getPickup().getSlot().setItem(null);

                        updateClock(clock, problem.getPickupPlaceDuration());
                        performedActions.add(new Output(gantryOutput.getId(), clock ,
                                gantryOutput.getCurrentX(), gantryOutput.getCurrentY(), gantryOutput.getItemId()));

                        outputXCoordinate = gantryOutput.getCurrentX();
                        outputYCoordinate = gantryOutput.getCurrentY();

                        // calculate the time required to move to the output slot
                        timeRequiredXAxis = abs(outputXCoordinate -
                                problem.getOutputSlot().getCenterX())/gantryOutput.getXSpeed();
                        timeRequiredYAxis = abs(outputYCoordinate -
                                problem.getOutputSlot().getCenterY())/gantryOutput.getYSpeed();

                        if(timeRequiredXAxis > timeRequiredYAxis)
                            timeRequired = timeRequiredXAxis;
                        else
                            timeRequired = timeRequiredYAxis;

                        // move the output gantry
                        gantryOutput.setCurrentX(problem.getOutputSlot().getCenterX());
                        gantryOutput.setCurrentY(problem.getOutputSlot().getCenterY());

                        // update the clock
                        updateClock(clock, timeRequired);
                        performedActions.add(new Output(gantryOutput.getId(), clock ,
                                gantryOutput.getCurrentX(), gantryOutput.getCurrentY(), gantryOutput.getItemId()));
                        gantryOutput.setItemId(-1);

                        updateClock(clock, problem.getPickupPlaceDuration());
                        performedActions.add(new Output(gantryOutput.getId(), clock,
                                gantryOutput.getCurrentX(), gantryOutput.getCurrentY(), gantryOutput.getItemId()));

                        outputJobCount++;
                        // assign new output job
                        if(outputJobCount < problem.getOutputJobSequence().size())
                            outputJob = problem.getOutputJobSequence().get(outputJobCount);
                    }
                }
            }
            //input handling
            // print out current position input gantry
            performedActions.add(new Output(gantryInput.getId(), clock,
                    gantryInput.getCurrentX(), gantryInput.getCurrentY(), gantryInput.getItemId()));

            if(inputJobCount != problem.getInputJobSequence().size()){

                // if there is no place slot assigned yet, we assign one
                if(inputJob.getPlace().getSlot() == null){

                    // move the input gantry to the input slot, once for every input job
                    moveInputGantryToInput(gantryInput, problem.getInputSlot(), inputJob);

                    System.out.println("IN " + inputJob.getId());
                    //get free slot first, than check for feasibility of the move

                    //Get free slot when the input crane is above the input slot
                    Slot freeSlotTemp;
                    while (true) {
                        //Get slot from the queue
                        freeSlotTemp = freeSlots.getFreeSlots().remove();

                        if (crossed) {
                            //Check if the slot has two filled underlying slots.
                            if (checkUnderneathCrossed(freeSlotTemp))
                                break;
                        } else {
                            // Check if the slot has one filled underlying slot
                            if (checkUnderneathStacked(freeSlotTemp))
                                break;
                        }
                    }
                    freeSlotTemp.setItem(inputJob.getItem());  //add item to slot

                    inputJob.getPlace().setSlot(freeSlotTemp); // add the free slot to the job
                }

                // check whether the output job item is assigned to a slot, if not it is in the input queue and this queue should proceed, which means moving the output gantry if necessary
                boolean outputNotReady = outputJob.getItem().getSlot() == null;

                boolean feasibleMove = checkIfInputGantryMoveIsFeasible(gantryOutput,gantryInput, inputJob);

                if(!feasibleMove && outputNotReady){
                    moveOutputGantry(gantryOutput,inputJob);
                    executeInputGantryMove(gantryInput, inputJob);
                }else if(feasibleMove)
                    executeInputGantryMove(gantryInput, inputJob);


                if(gantryInput.getCurrentX() == inputJob.getPlace().getSlot().getCenterX()
                        && gantryInput.getCurrentY() == inputJob.getPlace().getSlot().getCenterY()){

                    //we have reached the place slot
                    performedActions.add(new Output(gantryInput.getId(), clock,
                            gantryInput.getCurrentX(), gantryInput.getCurrentY(), gantryInput.getItemId()));

                    // only now set the slot to ensure that the ouptut gantry would not start moving to soon to pick this item up
                    inputJob.getItem().setSlot(inputJob.getPlace().getSlot());  //add slot to item
                    inputJob.getPlace().getSlot().setItem(inputJob.getItem());  // add the item to the slot

                    gantryInput.setItemId(-1);

                    //update the clock
                    updateClock(clock, problem.getPickupPlaceDuration());
                    // write out
                    performedActions.add(new Output(gantryInput.getId(), clock ,
                            gantryInput.getCurrentX(), gantryInput.getCurrentY(), gantryInput.getItemId()));

                    // assign new input job
                    inputJobCount++;
                    if(inputJobCount < problem.getInputJobSequence().size())
                        inputJob = problem.getInputJobSequence().get(inputJobCount);
                }
            }

            //check if input gantry can continue based on current position of the output gantry
            // if so move the input gantry

            // update the clock, add the time interval

            if(inputJobCount == problem.getInputJobSequence().size()
                    && outputJobCount == problem.getOutputJobSequence().size())
                break;

            //updateClock(clock, timeInterval);
        }
    }

    private boolean checkIfOutputGantryMoveIsFeasibleForPickup(Gantry outputGantry, Gantry inputGantry, Job job){
        int outputXCoordinate = outputGantry.getCurrentX();
        int outputGantryPossibleXMove = (int)outputGantry.getXSpeed() * timeInterval;
        int inputGantryCurrentXPosition = inputGantry.getCurrentX();

        int pickupXCoordinate = job.getItem().getSlot().getCenterX();

        // if we move to the left, we need to check safety distance constraints
        if(pickupXCoordinate < outputXCoordinate){
            // check whether we need the whole move
            if(outputXCoordinate - pickupXCoordinate < outputGantryPossibleXMove)
                return pickupXCoordinate > inputGantryCurrentXPosition + safetyDistance;
            else
                return outputXCoordinate - outputGantryPossibleXMove > inputGantryCurrentXPosition + safetyDistance;
        }

        // if we move to the right, no check is needed
        else{
            return true;
        }
    }

    private boolean checkIfInputGantryMoveIsFeasible(Gantry outputGantry, Gantry inputGantry, Job j){

        int inputXCoordinate = inputGantry.getCurrentX();
        int inputGantryPossibleXMove = (int)inputGantry.getXSpeed() * timeInterval;
        int outputGantryCurrentXPosition = outputGantry.getCurrentX();

        int placeXCoordinate = j.getPlace().getSlot().getCenterX();

        // if we move to the right, we need to check safety distance constraints
        if( placeXCoordinate > inputXCoordinate){
            //check whether we need the whole move
            if(placeXCoordinate - inputXCoordinate < inputGantryPossibleXMove)
                return placeXCoordinate < outputGantryCurrentXPosition - safetyDistance;

            return inputXCoordinate + inputGantryPossibleXMove < outputGantryCurrentXPosition - safetyDistance;
        }
        else{
            return true;
        }
    }

    private void moveInputGantry( Gantry inputGantry, Job outputJob){

        //Variables needed for range calculation
        int minX;

        //Calculate placement ranges
        minX = calculatePlacementRangeMinX(pickupLevel, problem.getMaxLevels(),
                outputJob.getPickup().getSlot().getCenterX());

        // print current position
        performedActions.add(new Output(inputGantry.getId(), clock,
                inputGantry.getCurrentX(), inputGantry.getCurrentY(), inputGantry.getItemId()));

        // calculate the time required to move far enough
        int inputCurrentXCoordinate = inputGantry.getCurrentX();
        int xCoordinateToGoTo = minX - safetyDistance;

        double timeRequired = abs(inputCurrentXCoordinate - xCoordinateToGoTo)/inputGantry.getXSpeed();

        if( xCoordinateToGoTo < inputGantry.getXMin())
            inputGantry.setCurrentX(inputGantry.getXMin());
        else
            inputGantry.setCurrentX(xCoordinateToGoTo);

        // no need to update the clock as this can be done in parallel
        //updateClock(clock, timeRequired);

        // print new position
        updateClock(clock, timeRequired);
        performedActions.add(new Output(inputGantry.getId(), clock ,
                inputGantry.getCurrentX(), inputGantry.getCurrentY(), inputGantry.getItemId()));

    }

    private void moveInputGantryToInput(Gantry inputGantry, Slot inputSlot, Job j){

        int currentInputXCoordinate = inputGantry.getCurrentX();
        int currentInputYCoordinate = inputGantry.getCurrentY();
        double timeRequiredX = abs(currentInputXCoordinate - inputSlot.getCenterX()) / inputGantry.getXSpeed();
        double timeRequiredY = abs(currentInputYCoordinate - inputSlot.getCenterY()) / inputGantry.getYSpeed();
        double timeRequired;
        if(timeRequiredX > timeRequiredY) timeRequired = timeRequiredX;
        else timeRequired = timeRequiredY;

        // print current position for security
        performedActions.add(new Output(inputGantry.getId(), clock ,
                inputGantry.getCurrentX(), inputGantry.getCurrentY(), inputGantry.getItemId()));

        // set new x and y coordinate
        inputGantry.setCurrentX(inputSlot.getCenterX());
        inputGantry.setCurrentY(inputSlot.getCenterY());

        // no need to update the clock as this can be done in parallel
        updateClock(clock, timeRequired);
        performedActions.add(new Output(inputGantry.getId(), clock ,
                inputGantry.getCurrentX(), inputGantry.getCurrentY(), inputGantry.getItemId()));

        // pickup the item
        inputGantry.setItemId(j.getItem().getId());
        updateClock(clock, problem.getPickupPlaceDuration());
        performedActions.add(new Output(inputGantry.getId(), clock,
                inputGantry.getCurrentX(), inputGantry.getCurrentY(), inputGantry.getItemId()));
    }

    private void moveOutputGantry( Gantry outputGantry, Job inputJob){
        int inputPlaceXCoordinate = inputJob.getPlace().getSlot().getCenterX();

        // calculate time needed to move the output gantry
        int currentOutputXCoordinate = outputGantry.getCurrentX();
        double timeRequired = abs((inputPlaceXCoordinate + safetyDistance) - currentOutputXCoordinate)/outputGantry.getXSpeed();

        // print current position
        performedActions.add(new Output(outputGantry.getId(), clock ,
                outputGantry.getCurrentX(), outputGantry.getCurrentY(), outputGantry.getItemId()));

        // no need to update the clock as this can be done in parallel

        gantryOutput.setCurrentX(inputPlaceXCoordinate + safetyDistance);

        updateClock(clock, timeRequired);
        performedActions.add(new Output(outputGantry.getId(), clock,
                outputGantry.getCurrentX(), outputGantry.getCurrentY(), outputGantry.getItemId()));

    }

    /**
     * This method checks if there are items under the slot
     *
     * @param freeSlotTemp The to check slot
     * @return Boolean that is true if there are items under the current item
     */
    private boolean checkUnderneathCrossed(Slot freeSlotTemp){

        String slotCoordinateUnderLeft = String.valueOf(freeSlotTemp.getCenterX() - 5) + ","
                + String.valueOf(freeSlotTemp.getCenterY()) + "," + String.valueOf(freeSlotTemp.getZ() - 1);
        String slotCoordinateUnderRight = String.valueOf(freeSlotTemp.getCenterX() + 5) + ","
                + String.valueOf(freeSlotTemp.getCenterY()) + "," + String.valueOf(freeSlotTemp.getZ() - 1);

        Slot underLeft = slotStructure.getSlotStructureMap().get(slotCoordinateUnderLeft);
        Slot underRight = slotStructure.getSlotStructureMap().get(slotCoordinateUnderRight);

        if (underLeft != null && underRight != null)
            return underLeft.getItem() != null && underRight.getItem() != null;
        else if(underLeft == null && underRight == null)
            return true;
        return false;
    }

    private boolean checkUnderneathStacked(Slot freeSlotTemp){

        String slotCoordinateUnderLeft = String.valueOf(freeSlotTemp.getCenterX()) + ","
                + String.valueOf(freeSlotTemp.getCenterY()) + "," + String.valueOf(freeSlotTemp.getZ() - 1);

        Slot underLeft = slotStructure.getSlotStructureMap().get(slotCoordinateUnderLeft);

        if (underLeft != null )
            return underLeft.getItem() != null;
        else if(underLeft == null)
            return true;
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
                double timeRequired = abs(outputXCoordinate - pickupXCoordinate)/gantry.getXSpeed();

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
                        updateClock(clock, timeRequired);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{

                        // y coordinate of the pickup slot could not be reached within the new time interval
                        // as the y move can be done while the gantry is stationary x-wise, we can set the
                        // y coordinate and add the action to the output
                        timeRequired = abs(outputYCoordinate - pickupYCoordinate )/ gantry.getYSpeed();
                        gantry.setCurrentY(pickupYCoordinate);
                        updateClock(clock, timeRequired);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }
                }else if(gantry.getCurrentY() < pickupYCoordinate){

                    // now we have to move up
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate + outputGantryPossibleYMove >= pickupYCoordinate){

                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(pickupYCoordinate);
                        updateClock(clock, timeRequired);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{

                        // y coordinate of the pickup slot could not be reached within the new time interval
                        // as the y move can be done while the gantry is stationary x-wise, we can set the
                        // y coordinate and add the action to the output
                        timeRequired = abs(outputYCoordinate + pickupYCoordinate )/ gantry.getYSpeed();
                        gantry.setCurrentY(pickupYCoordinate);
                        updateClock(clock, timeRequired);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }

                }else if( gantry.getCurrentY() == pickupYCoordinate){
                    updateClock(clock, timeRequired);
                    performedActions.add(new Output(gantry.getId(), clock,
                            gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));
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
                        //double timeRequired = abs(outputYCoordinate - pickupYCoordinate)/gantry.getYSpeed();
                        updateClock(clock, timeInterval);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{
                        // y coordinate of the pickup slot could not be reached within the new time interval
                        gantry.setCurrentY(outputYCoordinate - outputGantryPossibleYMove);
                        updateClock(clock, timeInterval);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }
                }else if(gantry.getCurrentY() < pickupYCoordinate){

                    // now we have to move up
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate + outputGantryPossibleYMove >= pickupYCoordinate){

                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(pickupYCoordinate);

                        //double timeRequired = abs(outputYCoordinate - pickupYCoordinate)/gantry.getYSpeed();
                        updateClock(clock, timeInterval);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{
                        // y coordinate of the pickup slot could not be reached within the new time interval
                        gantry.setCurrentY(outputYCoordinate + outputGantryPossibleYMove);
                        updateClock(clock, timeInterval);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }

                }else if( gantry.getCurrentY() == pickupYCoordinate){
                    updateClock(clock, timeInterval);
                    performedActions.add(new Output(gantry.getId(), clock ,
                            gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));
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
                double timeRequired = abs(pickupXCoordinate - outputXCoordinate)/gantry.getXSpeed();


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
                        updateClock(clock,timeRequired);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{
                        // y coordinate of the pickup slot could not be reached within the new time interval
                        // as the y move can be done while the gantry is stationary x-wise, we can set the
                        // y coordinate and add the action to the output
                        timeRequired = abs(outputYCoordinate - pickupYCoordinate )/ gantry.getYSpeed();
                        gantry.setCurrentY(pickupYCoordinate);
                        updateClock(clock,timeRequired);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }
                }else if(gantry.getCurrentY() < pickupYCoordinate){

                    // now we have to move up
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate + outputGantryPossibleYMove >= pickupYCoordinate){

                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(pickupYCoordinate);
                        updateClock(clock,timeRequired);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{
                        // y coordinate of the pickup slot could not be reached within the new time interval
                        // as the y move can be done while the gantry is stationary x-wise, we can set the
                        // y coordinate and add the action to the output
                        timeRequired = abs(outputYCoordinate + pickupYCoordinate )/ gantry.getYSpeed();
                        gantry.setCurrentY(pickupYCoordinate);
                        updateClock(clock,timeRequired);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }

                }else if( gantry.getCurrentY() == pickupYCoordinate){
                    updateClock(clock,timeRequired);
                    performedActions.add(new Output(gantry.getId(), clock ,
                            gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));
                }
            }
            // We cannot reach this pickup slot within the time frame
            else if( pickupXCoordinate - outputXCoordinate >= outputGantryPossibleXMove){
                gantry.setCurrentX(outputXCoordinate + outputGantryPossibleXMove);

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
                        //double timeRequired = abs(outputYCoordinate - pickupYCoordinate)/gantry.getYSpeed();
                        updateClock(clock,timeInterval);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{
                        // y coordinate of the pickup slot could not be reached within the new time interval
                        gantry.setCurrentY(outputYCoordinate - outputGantryPossibleYMove);
                        updateClock(clock,timeInterval);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }
                }else if(gantry.getCurrentY() < pickupYCoordinate){

                    // now we have to move up
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate + outputGantryPossibleYMove >= pickupYCoordinate){

                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(pickupYCoordinate);

                        //double timeRequired = abs(outputYCoordinate - pickupYCoordinate)/gantry.getYSpeed();
                        updateClock(clock,timeInterval);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{
                        // y coordinate of the pickup slot could not be reached within the new time interval
                        gantry.setCurrentY(outputYCoordinate + outputGantryPossibleYMove);
                        updateClock(clock,timeInterval);
                        performedActions.add(new Output(gantry.getId(), clock,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

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
                    double timeRequired = abs(outputYCoordinate - pickupYCoordinate)/gantry.getYSpeed();
                    updateClock(clock,timeRequired);
                    performedActions.add(new Output(gantry.getId(), clock ,
                            gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                }else{

                    // y coordinate of the pickup slot could not be reached within the new time interval
                    gantry.setCurrentY(outputYCoordinate - outputGantryPossibleYMove);
                    updateClock(clock,timeInterval);
                    performedActions.add(new Output(gantry.getId(), clock ,
                            gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                }
            }else if(gantry.getCurrentY() < pickupYCoordinate){

                // now we have to move up
                // can we reach te y coordinate within the new time interval
                if(outputYCoordinate + outputGantryPossibleYMove >= pickupYCoordinate){

                    //we can reach the pickup slot y coordinate within the new time interval
                    //set y coordinate
                    gantry.setCurrentY(pickupYCoordinate);
                    double timeRequired = abs(outputYCoordinate - pickupYCoordinate)/gantry.getYSpeed();
                    updateClock(clock,timeRequired);
                    performedActions.add(new Output(gantry.getId(), clock,
                            gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                }else{
                    // y coordinate of the pickup slot could not be reached within the new time interval
                    gantry.setCurrentY(outputYCoordinate + outputGantryPossibleYMove);
                    updateClock(clock,timeInterval);
                    performedActions.add(new Output(gantry.getId(), clock ,
                            gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                }

            }else if( gantry.getCurrentY() == pickupYCoordinate){
                updateClock(clock,timeInterval);
                performedActions.add(new Output(gantry.getId(), clock ,
                        gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));
            }
        }

    }

    private void executeInputGantryMove(Gantry gantry, Job j){

        int inputXCoordinate = gantry.getCurrentX();
        int inputGantryPossibleXMove = (int)gantry.getXSpeed() * timeInterval;
        int placeXCoordinate = j.getPlace().getSlot().getCenterX(); // is the general pickup slot
        int placeYCoordinate = j.getPlace().getSlot().getCenterY();

        // check if we have to move to the left
        if(inputXCoordinate > placeXCoordinate){

            // check if we need less distance then foreseen feasible distance by the time interval
            // this means we can reach the x coordinate of the pickup slot
            if(inputXCoordinate - placeXCoordinate < inputGantryPossibleXMove){

                // voor de output rekening houden met de mindere klok tijd die we nodig hadden! (done)
                // set the x coordinate as we can reach the slot within the time interval
                gantry.setCurrentX(placeXCoordinate);

                // calculate the actual time required
                double timeRequired = abs(inputXCoordinate - placeXCoordinate)/gantry.getXSpeed();

                // check whether we could reach the Y coordinate within this smaller time frame
                int outputYCoordinate = gantry.getCurrentY();
                int outputGantryPossibleYMove = (int) (gantry.getYSpeed() * timeRequired);

                //check if the y coordinate of the pickup is on a higher level than the current y coordinate of the gantry
                // if it is equal we do not execute
                if(gantry.getCurrentY() > placeYCoordinate){

                    // now we have to move down on the y axis
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate - outputGantryPossibleYMove <= placeYCoordinate){

                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(placeYCoordinate);
                        updateClock(clock,timeRequired);
                        performedActions.add(new Output(gantry.getId(), clock,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{

                        // y coordinate of the pickup slot could not be reached within the new time interval
                        // as the y move can be done while the gantry is stationary x-wise, we can set the
                        // y coordinate and add the action to the output
                        timeRequired = abs(outputYCoordinate - placeYCoordinate )/ gantry.getYSpeed();
                        gantry.setCurrentY(placeYCoordinate);
                        updateClock(clock,timeRequired);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }

                }else if(gantry.getCurrentY() < placeYCoordinate){

                    // now we have to move up
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate + outputGantryPossibleYMove >= placeYCoordinate){

                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(placeYCoordinate);
                        updateClock(clock,timeRequired);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{

                        // y coordinate of the pickup slot could not be reached within the new time interval
                        // as the y move can be done while the gantry is stationary x-wise, we can set the
                        // y coordinate and add the action to the output
                        timeRequired = abs(outputYCoordinate + placeYCoordinate )/ gantry.getYSpeed();
                        gantry.setCurrentY(placeYCoordinate);
                        updateClock(clock,timeRequired);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }

                }else if( gantry.getCurrentY() == placeYCoordinate){
                    updateClock(clock,timeRequired);
                    performedActions.add(new Output(gantry.getId(), clock ,
                            gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));
                }
            }

            // We cannot reach this pickup slot within the time frame
            else if(inputXCoordinate - placeXCoordinate >= inputGantryPossibleXMove){
                gantry.setCurrentX(inputXCoordinate - inputGantryPossibleXMove);

                // check if the Y coordinate can be reached
                int outputYCoordinate = gantry.getCurrentY();
                int outputGantryPossibleYMove = (int) (gantry.getYSpeed() * timeInterval);

                //check if the y coordinate of the pickup is on a higher level than the current y coordinate of the gantry
                if(gantry.getCurrentY() > placeYCoordinate){

                    // now we have to move down on the y axis
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate - outputGantryPossibleYMove <= placeYCoordinate){

                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(placeYCoordinate);

                        //double timeRequired = abs(outputYCoordinate - placeYCoordinate)/gantry.getYSpeed();
                        updateClock(clock,timeInterval);
                        performedActions.add(new Output(gantry.getId(), clock,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{

                        // y coordinate of the pickup slot could not be reached within the new time interval
                        gantry.setCurrentY(outputYCoordinate - outputGantryPossibleYMove);
                        updateClock(clock,timeInterval);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }
                }else if(gantry.getCurrentY() < placeYCoordinate){

                    // now we have to move up
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate + outputGantryPossibleYMove >= placeYCoordinate){

                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(placeYCoordinate);

                        //double timeRequired = abs(outputYCoordinate - placeYCoordinate)/gantry.getYSpeed();
                        updateClock(clock,timeInterval);
                        performedActions.add(new Output(gantry.getId(), clock,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{

                        // y coordinate of the pickup slot could not be reached within the new time interval
                        gantry.setCurrentY(outputYCoordinate + outputGantryPossibleYMove);
                        updateClock(clock,timeInterval);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }

                }else if( gantry.getCurrentY() == placeYCoordinate){
                    updateClock(clock,timeInterval);
                    performedActions.add(new Output(gantry.getId(), clock ,
                            gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));
                }
            }
        }

        // move to the right
        else if(inputXCoordinate < placeXCoordinate){

            // check if we need less distance then foreseen feasible distance by the time interval
            // this means we reach x coordinate of the pickup slot
            if(placeXCoordinate - inputXCoordinate < inputGantryPossibleXMove){

                // voor de output rekening houden met de mindere klok tijd die we nodig hadden! (done)
                // set the x coordinate as we can reach ths lot within tht time interval
                gantry.setCurrentX(placeXCoordinate);

                // calculate the actual time required
                double timeRequired = abs(placeXCoordinate - inputXCoordinate)/gantry.getXSpeed();


                // check whether we could reach the Y coordinate within this smaller time frame
                int outputYCoordinate = gantry.getCurrentY();
                int outputGantryPossibleYMove = (int) (gantry.getYSpeed() * timeRequired);

                //check if the y coordinate of the pickup is on a higher level than the current y coordinate of the gantry
                // if it is equal we do not execute
                if(gantry.getCurrentY() > placeYCoordinate){

                    // now we have to move down on the y axis
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate - outputGantryPossibleYMove <= placeYCoordinate){

                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(placeYCoordinate);
                        updateClock(clock,timeRequired);
                        performedActions.add(new Output(gantry.getId(), clock,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{

                        // y coordinate of the pickup slot could not be reached within the new time interval
                        // as the y move can be done while the gantry is stationary x-wise, we can set the
                        // y coordinate and add the action to the output
                        timeRequired = abs(outputYCoordinate - placeYCoordinate )/ gantry.getYSpeed();
                        gantry.setCurrentY(placeYCoordinate);
                        updateClock(clock,timeRequired);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }

                }else if(gantry.getCurrentY() < placeYCoordinate){

                    // now we have to move up
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate + outputGantryPossibleYMove >= placeYCoordinate){

                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(placeYCoordinate);
                        updateClock(clock,timeRequired);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{

                        // y coordinate of the pickup slot could not be reached within the new time interval
                        // as the y move can be done while the gantry is stationary x-wise, we can set the
                        // y coordinate and add the action to the output
                        timeRequired = abs(outputYCoordinate + placeYCoordinate )/ gantry.getYSpeed();
                        gantry.setCurrentY(placeYCoordinate);

                        updateClock(clock,timeRequired);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }

                }else if( gantry.getCurrentY() == placeYCoordinate){
                    updateClock(clock,timeRequired);
                    performedActions.add(new Output(gantry.getId(), clock ,
                            gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));
                }
            }

            // We cannot reach this pickup slot within the time frame
            else if(placeXCoordinate - inputXCoordinate >= inputGantryPossibleXMove){
                gantry.setCurrentX(inputXCoordinate + inputGantryPossibleXMove);

                // check if the Y coordinate can be reached
                int outputYCoordinate = gantry.getCurrentY();
                int outputGantryPossibleYMove = (int) (gantry.getYSpeed() * timeInterval);

                //check if the y coordinate of the pickup is on a higher level than the current y coordinate of the gantry
                if(gantry.getCurrentY() > placeYCoordinate){

                    // now we have to move down on the y axis
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate - outputGantryPossibleYMove <= placeYCoordinate){

                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(placeYCoordinate);

                        //double timeRequired = abs(outputYCoordinate - placeYCoordinate)/gantry.getYSpeed();
                        updateClock(clock,timeInterval);
                        performedActions.add(new Output(gantry.getId(), clock,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{
                        // y coordinate of the pickup slot could not be reached within the new time interval
                        gantry.setCurrentY(outputYCoordinate - outputGantryPossibleYMove);
                        updateClock(clock,timeInterval);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }
                }else if(gantry.getCurrentY() < placeYCoordinate){

                    // now we have to move up
                    // can we reach te y coordinate within the new time interval
                    if(outputYCoordinate + outputGantryPossibleYMove >= placeYCoordinate){

                        //we can reach the pickup slot y coordinate within the new time interval
                        //set y coordinate
                        gantry.setCurrentY(placeYCoordinate);

                        //double timeRequired = abs(outputYCoordinate - placeYCoordinate)/gantry.getYSpeed();
                        updateClock(clock,timeInterval);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }else{

                        // y coordinate of the pickup slot could not be reached within the new time interval
                        gantry.setCurrentY(outputYCoordinate + outputGantryPossibleYMove);
                        updateClock(clock,timeInterval);
                        performedActions.add(new Output(gantry.getId(), clock ,
                                gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                    }

                }else if( gantry.getCurrentY() == placeYCoordinate){
                    updateClock(clock,timeInterval);
                    performedActions.add(new Output(gantry.getId(), clock ,
                            gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));
                }
            }
        }

        // if we are already on the right x coordinate
        else if(inputXCoordinate == placeXCoordinate){

            // check if the Y coordinate can be reached
            int outputYCoordinate = gantry.getCurrentY();
            int outputGantryPossibleYMove = (int) (gantry.getYSpeed() * timeInterval);

            //check if the y coordinate of the pickup is on a higher level than the current y coordinate of the gantry
            if(gantry.getCurrentY() > placeYCoordinate){

                // now we have to move down on the y axis
                // can we reach te y coordinate within the new time interval
                if(outputYCoordinate - outputGantryPossibleYMove <= placeYCoordinate){

                    //we can reach the pickup slot y coordinate within the new time interval
                    //set y coordinate
                    gantry.setCurrentY(placeYCoordinate);

                    // calculate the actual time required to perform the move
                    double timeRequired = abs(outputYCoordinate - placeYCoordinate)/gantry.getYSpeed();
                    updateClock(clock,timeRequired);
                    performedActions.add(new Output(gantry.getId(), clock ,
                            gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                }else{
                    // y coordinate of the pickup slot could not be reached within the new time interval
                    gantry.setCurrentY(outputYCoordinate - outputGantryPossibleYMove);
                    updateClock(clock,timeInterval);
                    performedActions.add(new Output(gantry.getId(), clock ,
                            gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                }

            }else if(gantry.getCurrentY() < placeYCoordinate){

                // now we have to move up
                // can we reach te y coordinate within the new time interval
                if(outputYCoordinate + outputGantryPossibleYMove >= placeYCoordinate){

                    //we can reach the pickup slot y coordinate within the new time interval
                    //set y coordinate
                    gantry.setCurrentY(placeYCoordinate);
                    double timeRequired = abs(outputYCoordinate - placeYCoordinate)/gantry.getYSpeed();
                    updateClock(clock,timeRequired);
                    performedActions.add(new Output(gantry.getId(), clock ,
                            gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                }else{
                    // y coordinate of the pickup slot could not be reached within the new time interval
                    gantry.setCurrentY(outputYCoordinate + outputGantryPossibleYMove);
                    updateClock(clock,timeInterval);
                    performedActions.add(new Output(gantry.getId(), clock ,
                            gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));

                }

            }else if( gantry.getCurrentY() == placeYCoordinate){
                updateClock(clock,timeInterval);
                performedActions.add(new Output(gantry.getId(), clock ,
                        gantry.getCurrentX(), gantry.getCurrentY(), gantry.getItemId()));
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

        // pickup the item
        gantryOutput.setItemId(j.getItem().getId());
        updateClock(clock, problem.getPickupPlaceDuration());
        performedActions.add(new Output(gantryOutput.getId(), clock,
                gantryOutput.getCurrentX(), gantryOutput.getCurrentY(), j.getItem().getId()));

        //calculate delivery time (moving from input slot to free slot
        timeToAdd = calculateGantryMoveTime(toFillSlot, gantryOutput);
        updateClock(clock, timeToAdd);
        //set new gantry coordinates
        gantrySetCoordinates(gantryOutput, j.getItem().getSlot());
        //new references between slot and item are both ways, so we can reference the slot via the item

        //action performed; adding to output
        performedActions.add(new Output(gantryOutput.getId(), clock,
                gantryOutput.getCurrentX(), gantryOutput.getCurrentY(), j.getItem().getId()));
        updateClock(clock, problem.getPickupPlaceDuration());

        gantryOutput.setItemId(-1);
        performedActions.add(new Output(gantryOutput.getId(), clock,
                gantryOutput.getCurrentX(), gantryOutput.getCurrentY(), -1));

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
            // we check the Z level to make sure we can do the pickup and delivery of the slot which has to go
            // towards the output slot, outside this function
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

        if(s.getZ() != pickupLevel) {

            //Every move can be seen as an input job but with a different start slot.
            performInputStacked(new Job(s.getId(), problem.getItems().get(s.getItem().getId()), s, null));
            s.setItem(null);
        }
    }

    private void performInputCrossed(Job j){

        // move the output gantry in place

        // print out previous coordinates
        performedActions.add(new Output(gantryOutput.getId(), clock,
                gantryOutput.getCurrentX(), gantryOutput.getCurrentY(), gantryOutput.getItemId()));

        int outputXCoordinate = gantryOutput.getCurrentX();
        int outputYCoordinate = gantryOutput.getCurrentY();
        double timeRequired =  0;
        double timeRequiredX = abs(outputXCoordinate - j.getItem().getSlot().getCenterX())/gantryOutput.getXSpeed();
        double timeRequiredY = abs(outputYCoordinate - j.getItem().getSlot().getCenterY())/gantryOutput.getYSpeed();

        if(timeRequiredX > timeRequiredY)
            timeRequired = timeRequiredX;
        else
            timeRequired = timeRequiredY;

        gantryOutput.setCurrentX(j.getItem().getSlot().getCenterX());
        gantryOutput.setCurrentY(j.getItem().getSlot().getCenterY());
        // update the clock
        updateClock(clock, timeRequired);
        performedActions.add(new Output(gantryOutput.getId(), clock ,
                gantryOutput.getCurrentX(), gantryOutput.getCurrentY(), gantryOutput.getItemId()));

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
                if (freeSlotTemp.getCenterX() < minX || freeSlotTemp.getCenterX() > maxX) {

                    //Check if the slot has two filled underlying slots.
                    if(checkUnderneathCrossed(freeSlotTemp))
                        break;
                }
            }
            //If slots has a different Y coordinate, no range check is needed
            else if (freeSlotTemp.getCenterY() != j.getPickup().getSlot().getCenterY())
                if(checkUnderneathCrossed(freeSlotTemp))
                    break;

            //If slot is not feasible, add to the queue again and rerun the loop
            freeSlots.addSlot(freeSlotTemp);

            System.out.println("Lus lus");
        }

        // now that we have a free slot, check whether the input crane is in the way
        if(gantryInput.getCurrentX() > freeSlotTemp.getCenterX() || !checkSafetyDistance(gantryInput, freeSlotTemp)){
            //if false, move input gantry as far as needed

            // print out current input gantry coordinates for safety
            performedActions.add(new Output(gantryInput.getId(), clock,
                    gantryInput.getCurrentX(), gantryInput.getCurrentY(), gantryInput.getItemId()));
            /*if(freeSlotTemp.getCenterX() > gantryInput.getCurrentX()){
                // we have to move the gantry to the left
                int inputXCoordinate = gantryInput.getCurrentX();
                timeRequired = abs(inputXCoordinate - (inputXCoordinate - 20))/gantryInput.getXSpeed();
                updateClock(clock, timeRequired);
                performedActions.add(new Output(gantryInput.getId(), clock ,
                        gantryInput.getCurrentX(), gantryInput.getCurrentY(), gantryInput.getItemId()));
            }else if (freeSlotTemp.getCenterX() < gantryInput.getCurrentX()){
                // we have to move the gantry to the right
                int inputXCoordinate = gantryInput.getCurrentX();
                timeRequired = abs((inputXCoordinate + 20) - inputXCoordinate)/gantryInput.getXSpeed();
                updateClock(clock, timeRequired);
                performedActions.add(new Output(gantryInput.getId(), clock ,
                        gantryInput.getCurrentX(), gantryInput.getCurrentY(), gantryInput.getItemId()));
            }*/
            int inputXCoordinate = gantryInput.getCurrentX();
            timeRequired = abs(inputXCoordinate - (freeSlotTemp.getCenterX() - 20))/gantryInput.getXSpeed();
            gantryInput.setCurrentX(freeSlotTemp.getCenterX() - 20);
            updateClock(clock, timeRequired);
            performedActions.add(new Output(gantryInput.getId(), clock ,
                    gantryInput.getCurrentX(), gantryInput.getCurrentY(), gantryInput.getItemId()));
        }

       // input gantry is moved, assign the slot to the item and vice versa
        freeSlotTemp.setItem(j.getItem());  //add item to slot
        j.getItem().setSlot(freeSlotTemp);  //add slot to item

        //Execute the move from the pickup location to the place location
        executeMoveJob(freeSlotTemp,j);

    }

    private void performInputStacked(Job j){

        if(j.getItem().getId() == 1885)
            System.out.println();
        // move the output gantry in place

        // print out previous coordinates
        performedActions.add(new Output(gantryOutput.getId(), clock,
                gantryOutput.getCurrentX(), gantryOutput.getCurrentY(), gantryOutput.getItemId()));

        int outputXCoordinate = gantryOutput.getCurrentX();
        int outputYCoordinate = gantryOutput.getCurrentY();
        double timeRequired =  0;
        double timeRequiredX = abs(outputXCoordinate - j.getItem().getSlot().getCenterX())/gantryOutput.getXSpeed();
        double timeRequiredY = abs(outputYCoordinate - j.getItem().getSlot().getCenterY())/gantryOutput.getYSpeed();

        if(timeRequiredX > timeRequiredY)
            timeRequired = timeRequiredX;
        else
            timeRequired = timeRequiredY;

        gantryOutput.setCurrentX(j.getItem().getSlot().getCenterX());
        gantryOutput.setCurrentY(j.getItem().getSlot().getCenterY());

        // update the clock
        updateClock(clock, timeRequired);
        performedActions.add(new Output(gantryOutput.getId(), clock ,
                gantryOutput.getCurrentX(), gantryOutput.getCurrentY(), gantryOutput.getItemId()));

        //Get free slot
        Slot freeSlotTemp;

        //Variables needed for range calculation
        int minX;
        int maxX;

        //Initiate loop to find a correct slot
        while (true) {

            //Get slot from the queue
            freeSlotTemp = freeSlots.getFreeSlots().remove();

            //Check if freeSlotTemp is not within the range
            if(freeSlotTemp.getCenterX() != j.getPickup().getSlot().getCenterX()){
                //Check if the slot has two filled underlying slots.
                if(checkUnderneathCrossed(freeSlotTemp))
                    break;
            }

            //If slot is not feasible, add to the queue again and rerun the loop
            freeSlots.addSlot(freeSlotTemp);

            System.out.println("Lus lus");
        }

        // now that we have a free slot, check whether the input crane is in the way
        if(gantryInput.getCurrentX() > freeSlotTemp.getCenterX() || !checkSafetyDistance(gantryInput, freeSlotTemp)){
            //if false, move input gantry as far as needed

            // print out current input gantry coordinates for safety
            performedActions.add(new Output(gantryInput.getId(), clock,
                    gantryInput.getCurrentX(), gantryInput.getCurrentY(), gantryInput.getItemId()));

            int inputXCoordinate = gantryInput.getCurrentX();
            timeRequired = abs(inputXCoordinate - (freeSlotTemp.getCenterX() - 20))/gantryInput.getXSpeed();
            gantryInput.setCurrentX(freeSlotTemp.getCenterX() - 20);

            updateClock(clock, timeRequired);
            performedActions.add(new Output(gantryInput.getId(), clock ,
                    gantryInput.getCurrentX(), gantryInput.getCurrentY(), gantryInput.getItemId()));
        }

        // input gantry is moved, assign the slot to the item and vice versa
        freeSlotTemp.setItem(j.getItem());  //add item to slot
        j.getItem().setSlot(freeSlotTemp);  //add slot to item

        j.getPickup().getSlot().setItem(null);

        //Execute the move from the pickup location to the place location
        executeMoveJob(freeSlotTemp,j);

    }

    private boolean checkSafetyDistance(Gantry gantry, Slot s){
        int currentXCoordinateGantry = gantry.getCurrentX();
        int slotXCoordinate = s.getCenterX();

        return abs(currentXCoordinateGantry - slotXCoordinate) > 20;
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
        Solution_Double.crossed = crossed;
    }
}
