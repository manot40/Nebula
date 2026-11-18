package emu.nebula.data.resources;

import java.util.List;

import emu.nebula.data.BaseDef;
import emu.nebula.data.ResourceType;
import lombok.Getter;

@Getter
@ResourceType(name = "SoldierStoryReward.json")
public class SoldierStoryRewardDef extends BaseDef {
    private int Id;
    private int Type1;
    private List<Integer> Param1;
    private int Type2;
    private List<Integer> Param2;

    @Override
    public int getId() {
        return Id;
    }
}
