package be.kul.gantry.domain;

/**
 * Created by Wim on 12/05/2015.
 */
public class Item {

    private final int id;

    //Toegevoegd
    private Slot slot;

    public Item(int id) {
        this.id = id;
        this.slot = null;
    }

    public int getId() {
        return id;
    }

    public Slot getSlot() {
        return slot;
    }

    public void setSlot(Slot slot){
        this.slot = slot;
    }
}
