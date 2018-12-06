package be.kul.gantry.domain;

import java.util.HashMap;
import java.util.Map;

public class SlotStructure {


    private Map<String, Slot> slotStructureMap = new HashMap<>();

    public SlotStructure() {
    }

    public void setChildCrossed(Slot s){
        String parentLeftCoordinate = String.valueOf(s.getCenterX()-5) + "," + String.valueOf(s.getCenterY()) + "," + String.valueOf(s.getZ()-1);
        String parentRightCoordinate = String.valueOf(s.getCenterX()+5) + "," + String.valueOf(s.getCenterY()) + "," + String.valueOf(s.getZ()-1);

        Slot parentLeft = slotStructureMap.get(parentLeftCoordinate);
        Slot parentRight = slotStructureMap.get(parentRightCoordinate);

        if(parentLeft != null) {
            parentLeft.setChildRight(s);
        }
        if(parentRight != null){
            parentRight.setChildLeft(s);
        }
    }

    public void setChildStacked(Slot s){
        String parentCoordinate = String.valueOf(s.getCenterX()) + "," + String.valueOf(s.getCenterY()) + "," + String.valueOf(s.getZ()-1);
        Slot parent = slotStructureMap.get(parentCoordinate);
        if(parent != null) parent.setChild(s);
    }

    public Map<String, Slot> getSlotStructureMap() {
        return slotStructureMap;
    }

    public void setSlotStructureMap(Map<String, Slot> slotStructureMap) {
        this.slotStructureMap = slotStructureMap;
    }

    public void printSlotStructure(){
        for(Map.Entry<String, Slot> entry : slotStructureMap.entrySet()){
            print(entry.getValue());
        }
    }

    public void print(Slot s){
        if(s != null){
            System.out.print(s.toString()+ " //// ");
            print(s.getChildLeft());
            print(s.getChildRight());
        }
        System.out.println();

    }



}
