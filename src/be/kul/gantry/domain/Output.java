package be.kul.gantry.domain;

public class Output {

    private int gantryID;
    private double timeStamp;
    private int xCoordinate;
    private int yCoordinate;
    private String itemInCraneID;

    public Output() {
    }

    public Output(int gantryID, double timeStamp, int xCoordinate, int yCoordinate, int itemInCraneID) {
        if(itemInCraneID != -1){
            this.gantryID = gantryID;
            this.timeStamp = timeStamp;
            this.xCoordinate = xCoordinate;
            this.yCoordinate = yCoordinate;
            this.itemInCraneID = String.valueOf(itemInCraneID);
        }else{
            this.gantryID = gantryID;
            this.timeStamp = timeStamp;
            this.xCoordinate = xCoordinate;
            this.yCoordinate = yCoordinate;
            this.itemInCraneID = "null";
        }

    }

    public int getGantryID() {
        return gantryID;
    }

    public void setGantryID(int gantryID) {
        this.gantryID = gantryID;
    }

    public double getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(double timeStamp) {
        this.timeStamp = timeStamp;
    }

    public int getxCoordinate() {
        return xCoordinate;
    }

    public void setxCoordinate(int xCoordinate) {
        this.xCoordinate = xCoordinate;
    }

    public int getyCoordinate() {
        return yCoordinate;
    }

    public void setyCoordinate(int yCoordinate) {
        this.yCoordinate = yCoordinate;
    }

    public String getItemInCraneID() {
        return itemInCraneID;
    }

    public void setItemInCraneID(String itemInCraneID) {
        this.itemInCraneID = itemInCraneID;
    }
}
