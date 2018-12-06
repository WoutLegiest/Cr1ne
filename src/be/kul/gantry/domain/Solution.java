package be.kul.gantry.domain;

import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import static java.lang.Math.abs;

public class Solution {

    public static boolean stacked = false;
    private double clock = 0;
    private Problem problem;

    private FreeSlots freeSlots;
    private List<Slot> tempFreeSlots = new ArrayList<>();
    private List<Output> performedActions = new ArrayList<>();
    private double timeToAdd = 0;
    private Gantry gantryInput;
    private Gantry gantryOutput;
    private SlotStructure slotStructure;

    private int pickupLevel;

    /**
     * Constructor, create problem from Json file, setup gantries, setup free slot Queue.
     * @param inputFileName
     */
    public Solution(String inputFileName) throws IOException, ParseException {
        //Reading the information from the jason file
        String pathname = "./1_10_100_4_FALSE_65_50_50.json";
        try {
            problem = Problem.fromJson(new File(pathname));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        //check which type of stacking we are dealing with
        List<String> file = Arrays.asList(pathname.split("\\s*_\\s*"));
        System.out.println(file.toString());
        if(file.get(4).equals("TRUE")){
            //crossed setup
        }else if(file.get(4).equals("FALSE")){
            //stacked setup
            stacked = true;
        }

        setupGantries();

        //Create new slot structure
        slotStructure = new SlotStructure();

        implementSlotStructure(slotStructure);

        //Create new freeSlots object
        freeSlots = new FreeSlots(tempFreeSlots);
        //Remove input slot from the queue (bug in file reader)
        freeSlots.getFreeSlots().remove();

        //add start position of gantry to output
        performedActions.add(new Output(gantryInput.getId(), clock, gantryInput.getCurrentX(), gantryInput.getCurrentY(), -1));


    }

    /**
     * Function which checks the amount of gantries and assigns them to variables for easy access
     * */
    public void setupGantries(){
        //Setup gantries
        gantryInput = problem.getGantries().get(0);
        if (problem.getGantries().size() == 2) {
            gantryOutput = problem.getGantries().get(1);
        }
    }

    /**
     * Function which fills the slot structure with slots
     * */
    public void implementSlotStructure(SlotStructure slotStructure){
        //Adding all the slots to the HashMap
        String slotCoordinate;
        for (Slot s : problem.getSlots()) {
            //create key by coordinate
            slotCoordinate = String.valueOf(s.getCenterX()) + "," + String.valueOf(s.getCenterY()) + "," + String.valueOf(s.getZ());
            System.out.print(slotCoordinate);
            if (s.getItem() == null) {
                tempFreeSlots.add(s);
            }
            //Calculate gantry move time between input and current slot (usable for optimalization)
            s.setPickupTime(calculateSlotInputMoveTime(gantryInput, s));
            slotStructure.getSlotStructureMap().put(slotCoordinate, s);

            //check whether we are dealing with a stacked or a crossed setup, assign child accordingly
            if(!stacked){
                //add slot to possible parent as a child
                slotStructure.setChildCrossed(s);

            }else{
                slotStructure.setChildStacked(s);
            }

        }
    }

    /**
     * Function which goes through the input job sequence
     */
    public void handleInputJobs() {
        for (Job j : problem.getInputJobSequence()) {


            // MOve the gantry to the correct position
            executeGantryMove(j);

            //Get free slot
            Slot freeSlotTemp;
            Slot toFillSlot = null;
            String slotCoordinate = "";

            boolean goodSlot = false;
            while (!goodSlot) {

                freeSlotTemp = freeSlots.getFreeSlots().remove();

                //map toFillSlot object to object from hashMap
                slotCoordinate = String.valueOf(freeSlotTemp.getCenterX()) + "," + String.valueOf(freeSlotTemp.getCenterY()) + "," + String.valueOf(freeSlotTemp.getZ());

                //Check whether the slot underneath is empty
                if(!stacked){
                    if(checkUnderneathCrossed(freeSlotTemp)){
                        break;
                    }
                }else{
                    if(checkUnderneathStacked(freeSlotTemp)){
                        break;
                    };
                }


                freeSlots.addSlot(freeSlotTemp);
            }
            toFillSlot = slotStructure.getSlotStructureMap().get(slotCoordinate);

            toFillSlot.setItem(j.getItem());  //add item to slot
            j.getItem().setSlot(toFillSlot);  //add slot to item

            // effectively execute the move job
            executeMoveJob(toFillSlot,j);

        }
    }

    /**
     * Function which checks whether the slot underneath the given slot contains an item in a crossed setup
     * @param freeSlotTemp The slot under which needs to be checked
     * @return boolean True if there is no item underneath, false if not
     * */
    public boolean checkUnderneathCrossed(Slot freeSlotTemp){
        String slotCoordinateUnderLeft = String.valueOf(freeSlotTemp.getCenterX() - 5) + "," + String.valueOf(freeSlotTemp.getCenterY()) + "," + String.valueOf(freeSlotTemp.getZ()-1);
        String slotCoordinateUnderRight = String.valueOf(freeSlotTemp.getCenterX() + 5) + "," + String.valueOf(freeSlotTemp.getCenterY()) + "," + String.valueOf(freeSlotTemp.getZ()-1);

        Slot underLeft = slotStructure.getSlotStructureMap().get(slotCoordinateUnderLeft);
        Slot underRight = slotStructure.getSlotStructureMap().get(slotCoordinateUnderRight);

        if(underLeft != null && underRight != null) {
            if (underLeft.getItem() != null && underRight.getItem() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Function which checks whether the slot underneath the given slot contains an item in a stacked setup
     * @param freeSlotTemp The slot under which needs to be checked
     * @return boolean True if there is no item underneath, false if not
     * */
    public boolean checkUnderneathStacked(Slot freeSlotTemp){
        String slotCoordinateStraightUnder = String.valueOf((freeSlotTemp.getCenterX()) + "," + String.valueOf(freeSlotTemp.getCenterY()) + "," + String.valueOf(freeSlotTemp.getZ()-1));

        Slot straightUnder = slotStructure.getSlotStructureMap().get(slotCoordinateStraightUnder);
        if(straightUnder != null){
            if(straightUnder.getItem() != null){
                return true;
            }
        }
        return false;
    }

    /**
     * Function which executes a move caused by digging out slots in a crossed setup
     * @param j The job which contains the info about the move
     *
     */
    public void performInputCrossed(Job j) {



        //move gantry to the pickup location

        executeGantryMove(j);

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
            minX = calculatePlacementRangeMinX(pickupLevel, problem.getMaxLevels(), j.getPickup().getSlot().getCenterX());
            maxX = calculatePlacementRangeMaxX(pickupLevel, problem.getMaxLevels(), j.getPickup().getSlot().getCenterX());

            //Get slot from the queue
            freeSlotTemp = freeSlots.getFreeSlots().remove();

            //Check if freeSlotTemp is not within the range
            if (freeSlotTemp.getCenterY() == j.getPickup().getSlot().getCenterY()) {
                if (freeSlotTemp.getCenterX() < minX && freeSlotTemp.getCenterX() > maxX) {
                    //Check if the slot has two filled underlying slots.
                    if(checkUnderneathCrossed(freeSlotTemp)){
                        break;
                    }

                }
            }
            //If slots has a different Y coordinate, no range check is needed
            else if (freeSlotTemp.getCenterY() != j.getPickup().getSlot().getCenterY()) {
                if(checkUnderneathCrossed(freeSlotTemp)){
                    break;
                }
            }

            //If slot is not feasible, add to the queue again and rerun the loop
            freeSlots.addSlot(freeSlotTemp);
        }

        //Loop ended, assign the slot to the item and vice versa
        freeSlotTemp.setItem(j.getItem());  //add item to slot
        j.getItem().setSlot(freeSlotTemp);  //add slot to item

        //Execute the move from the pickup location to the place location
        executeMoveJob(freeSlotTemp,j);

    }

    /**
     * Function which executes a move caused by digging out slots in a stacked setup
     * @param j The job which contains the information about the move to be performed
     */
    public void performInputStacked(Job j) {

        //Move the gantry to the pickup location
        executeGantryMove(j);

        //Initialize variables to find free slot
        Slot freeSlotTemp = null;
        String slotCoordinate = "";

        //Setup loop
        while(true){
            //Get slot from the queue
            freeSlotTemp = freeSlots.getFreeSlots().remove();

            //Check if place slot is not the same as the pickup slot
            if(freeSlotTemp.getCenterX() != j.getPickup().getSlot().getCenterX() && freeSlotTemp.getCenterY() != j.getPickup().getSlot().getCenterY()){
               break;
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
     * Function which moves the gantry according to the given job
     * @param j The job which contains the information about the move
     * */
    public void executeGantryMove(Job j){
        //calculate pickup time (moving gantry from current position to input slot)
        timeToAdd = calculateGantryMoveTime(j.getPickup().getSlot(), gantryInput);
        updateClock(clock, timeToAdd);
        /*set new gantry coordinates*/
        gantrySetCoordinates(gantryInput, j.getPickup().getSlot());
        /*action performed: adding to output*/
        performedActions.add(new Output(gantryInput.getId(), clock, gantryInput.getCurrentX(), gantryInput.getCurrentY(), -1));
        updateClock(clock, problem.getPickupPlaceDuration());
        performedActions.add(new Output(gantryInput.getId(), clock, gantryInput.getCurrentX(), gantryInput.getCurrentY(), j.getItem().getId()));

    }

    /**
     * Function which moves the gantry according to a given job
     * @param toFillSlot Slot which needs to be filled
     * @param j The job containing the information about the move
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
     * Function which handles the output job sequence
     * Deze methode handelt een output job af, het zal dus een item uitgraven als boven hem en dan naar de output plaats
     * brengen
     */
    public void handleOutputJobs() {
        for (Job j : problem.getOutputJobSequence()) {
            // First, get item and find slot from item
            Item i = problem.getItems().get(j.getItem().getId());
            //System.out.println(i.getId());
            Slot pickupSlot = i.getSlot();
            pickupLevel = pickupSlot.getZ();
            // Second recursively look for any children and reallocate them
            if(!stacked){
                if (pickupSlot.getItem() != null) {
                    digSlotOutCrossed(pickupSlot);

                }
            }else{
                if (pickupSlot.getItem() != null && pickupSlot.getChild() != null) {
                    if (pickupSlot.getChild().getItem() != null) {
                        digSlotOutStacked(pickupSlot.getChild());

                    }

                }
            }


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
     * Recursive method which digs slots out in a crossed setup
     * @param s The Slot which children need to be dug out
     */
    public void digSlotOutCrossed(Slot s) {

        if (s.getChildLeft() != null) {
            if (s.getChildLeft().getItem() != null) {
                digSlotOutCrossed(s.getChildLeft());

            }
        }

        //Every move can be seen as an input job but with a different start slot.


        if (s.getChildRight() != null) {
            if (s.getChildRight().getItem() != null) {
                digSlotOutCrossed(s.getChildRight());

            }
        }

        if (s.getZ() != pickupLevel) {
            performInputCrossed(new Job(s.getId(), problem.getItems().get(s.getItem().getId()), s, null));
            s.setItem(null);
        }

    }

    /**
     * Recursive method which digs out the necessary items in a stacked setup.
     * @param @Slot s
     */
    public void digSlotOutStacked(Slot s) {

        if (s.getChild() != null) {
            if (s.getChild().getItem() != null) {
                digSlotOutStacked(s.getChild());

            }
        }


        //Every move can be seen as an input job but with a different start slot.
        performInputStacked(new Job(s.getId(), problem.getItems().get(s.getItem().getId()), s, null));
        s.setItem(null);

    }

    /**
     * Function which calculates the minimum x-coordinate of the range in which no slots may be taken to ensure correct digging.
     * @param pickupLevel The Z level on which the current pickup is
     * @param maxLevels The maximum levels in the current problem
     * @param centerX the center X coordinate of the slot which needs to be moved to the output
     * @return int The minimum x coordinate from which no slots can be taken as a free slot
     */
    public int calculatePlacementRangeMinX(int pickupLevel, int maxLevels, int centerX) {
        int differenceInLevels = maxLevels - pickupLevel;
        int totalRangeWidth = differenceInLevels * 10;
        return centerX - (totalRangeWidth / 2);
    }

    /**
     * Function which calculates the maximum x-coordinate of the range in which no slots may be taken to ensure correct digging.
     * @param pickupLevel The Z level on which the current pickup is
     * @param maxLevels The maximum levels in the current problem
     * @param centerX the center X coordinate of the slot which needs to be moved to the output
     * @return int The minimum x coordinate from which no slots can be taken as a free slot
     */
    public int calculatePlacementRangeMaxX(int pickupLevel, int maxLevels, int centerX) {
        int differenceInLevels = maxLevels - pickupLevel;
        int totalRangeWidth = differenceInLevels * 10;
        return centerX + (totalRangeWidth / 2);
    }


    /**
     * Function which calculates the minimal amount of time required to go from the gantry's current position to the slot
     * @param pickupSlot The slot from where the pickup will be initiated
     * @param gantry The gantry which will execute the move
     * @return double The minimum time needed to move the gantry to the pickup position
     */
    public double calculateGantryMoveTime(Slot pickupSlot, Gantry gantry) {
        //calculate both x and y moving times, as this can be done in parallel, the shortest one is returned
        double timeX = abs(gantry.getCurrentX() - pickupSlot.getCenterX()) / gantry.getXSpeed();
        double timeY = abs(gantry.getCurrentY() - pickupSlot.getCenterY()) / gantry.getYSpeed();
        if (timeX < timeY) {
            return timeY;
        } else {
            return timeX;
        }
    }

    /**
     * Function which calculates the minimal time required to move an item from the input slot to the given slot.
     * @param gantryInput The gantry which will execute the move
     * @param s The slot from where to move to
     * @return double The minimum time needed to move the gantry to the pickup position
     */
    public double calculateSlotInputMoveTime(Gantry gantryInput, Slot s) {
        //calculate both x and y moving times, as this can be done in parallel, the shortest one is returned
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
     * @param clock The current clock
     * @param time The time to be added
     */
    public void updateClock(double clock, double time) {
        setClock(clock + time);
    }

    /**
     * Function which updates the coordinates of the crane
     * @param gantry The gantry of which the coordinates need to be changed
     * @param slot The slot coordinates to which the coordinates of the gantry need to be changed to
     */
    public void gantrySetCoordinates(Gantry gantry, Slot slot) {
        gantry.setCurrentX(slot.getCenterX());
        gantry.setCurrentY(slot.getCenterY());
    }

    /**
     * Function for debugging purposes
     * */
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
     * @param outputFileName
     */
    public void writeOutput(String outputFileName) throws IOException {
        PrintWriter fw = new PrintWriter("output.csv");
        StringBuffer sb = new StringBuffer();
        for (Output o : performedActions) {
            String s = "";
            s = o.getGantryID() + ";" + o.getTimeStamp() + ";" + o.getxCoordinate() + ";" + o.getyCoordinate() + ";" + o.getItemInCraneID() + "\n";
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
