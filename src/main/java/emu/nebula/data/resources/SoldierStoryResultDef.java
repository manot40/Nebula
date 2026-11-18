package emu.nebula.data.resources;

import emu.nebula.data.BaseDef;
import emu.nebula.data.ResourceType;
import lombok.Getter;

@Getter
@ResourceType(name = "SoldierStoryResult.json")
public class SoldierStoryResultDef extends BaseDef {
    private int Id;
    private int RewardId;

    @Override
    public int getId() {
        return Id;
    }
}
