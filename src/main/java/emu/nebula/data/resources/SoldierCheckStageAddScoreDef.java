package emu.nebula.data.resources;

import emu.nebula.data.BaseDef;
import emu.nebula.data.ResourceType;
import lombok.Getter;

@Getter
@ResourceType(name = "SoldierCheckStageAddScore.json")
public class SoldierCheckStageAddScoreDef extends BaseDef {
    private int Id;
    private int Group;
    private int Index;
    private int DPS;
    private int DPSAddScore;
    private int Duration;
    private int TankingAddScore;

    @Override
    public int getId() {
        return Id;
    }
}
