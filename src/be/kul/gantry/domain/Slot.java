package be.kul.gantry.domain;

/**
 * Created by Wim on 27/04/2015.
 */
public class Slot {

    private final int id;
    private final int centerX, centerY, xMin, xMax, yMin, yMax,z;
    private Item item;
    private final SlotType type;

    //Toegevoegd
    private double pickupTime = 0;
    private Slot childLeft, childRight;
    //private Slot child;

    public Slot(int id, int centerX, int centerY, int xMin, int xMax, int yMin, int yMax, int z, SlotType type, Item item) {
        this.id = id;
        this.centerX = centerX;
        this.centerY = centerY;
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
        this.z = z;
        this.item = item;
        this.type = type;
    }

    public boolean isInputSlot() { return type == SlotType.INPUT; }

    public boolean isOutputSlot() { return type == SlotType.OUTPUT; }

    public boolean isStorageSlot() { return type == SlotType.STORAGE; }

    @Override
    public String toString() {
        return String.format("Slot %d (%d,%d,%d)",id,centerX,centerY,z);
    }

    public static enum SlotType {
        INPUT,
        OUTPUT,
        STORAGE
    }

    public int getId() {
        return id;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterY() {
        return centerY;
    }

    public int getZ() {
        return z;
    }

    public int getXMin() {
        return xMin;
    }

    public int getXMax() {
        return xMax;
    }

    public int getYMin() {
        return yMin;
    }

    public int getYMax() {
        return yMax;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public SlotType getType() {
        return type;
    }

    public double getPickupTime() {
        return pickupTime;
    }

    public void setPickupTime(double pickupTime) {
        this.pickupTime = pickupTime;
    }

    /*public Slot getChild() {
        return child;
    }

    public void setChild(Slot child) {
        this.child = child;
    }*/

    public Slot getChildLeft() {
        return childLeft;
    }

    public void setChildLeft(Slot childLeft) {
        this.childLeft = childLeft;
    }

    public Slot getChildRight() {
        return childRight;
    }

    public void setChildRight(Slot childRight) {
        this.childRight = childRight;
    }


}
