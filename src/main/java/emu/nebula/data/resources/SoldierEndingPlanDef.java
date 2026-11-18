package emu.nebula.data.resources;

import emu.nebula.data.BaseDef;
import emu.nebula.data.ResourceType;
import lombok.Getter;

@Getter
@ResourceType(name = "SoldierEndingPlan.json")
public class SoldierEndingPlanDef extends BaseDef {
    private int Id;
    private int SeasonId;
    private int EndingCondition1;

    @Override
    public int getId() {
        return Id;
    }
}
