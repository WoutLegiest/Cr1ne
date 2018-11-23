package be.kul.gantry.domain;

import java.util.HashMap;
import java.util.Map;


/**
 * Klasse voor het opslaan van alle slots
 */
public class SlotStructure {


    private Map<String, Slot> slotStructureMap = new HashMap<>();

    public SlotStructure() {
    }

    public void setChild(Slot s){
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

}
