package be.kul.gantry.domain;

import java.util.*;

public class FreeSlots{

   private Queue<Slot> freeSlotsQueue ;
   private boolean newSlotAdded = false;

    public FreeSlots(){}

    /**
     * Constructor creates new priority queue with a comparator and adds all elements from the list to the queue.
     *
     * @param @List freeSlots
     * */
    public FreeSlots(List<Slot> freeSlots){
        this.freeSlotsQueue = new LinkedList<>();
        for(Slot s : freeSlots){
            freeSlotsQueue.add(s);
        }
    }


    public void addSlot(Slot s){
        freeSlotsQueue.add(s);
    }

    /*Getters and Setters*/
    public Queue<Slot> getFreeSlots() {
        return freeSlotsQueue;
    }

    public void setFreeSlots(PriorityQueue<Slot> freeSlots) {
        this.freeSlotsQueue = freeSlots;
    }
}
