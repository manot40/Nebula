package emu.nebula.data.resources;

import java.util.List;

import emu.nebula.data.BaseDef;
import emu.nebula.data.ResourceType;
import lombok.Getter;

@Getter
@ResourceType(name = "SoldierStoryPersonalityOption.json")
public class SoldierStoryPersonalityOptionDef extends BaseDef {
    private int Id;
    private int OptionTypeId;
    private int ResultId;
    private int CheckDimension;
    private int CheckDifficulty;
    private int FailResultId;
    private int OptionalCond;
    private List<Integer> OptionalParam;
    private int UnlockCond1;
    private List<Integer> UnlockParam1;

    public int getRulePrefix() {
        return Id / 10000;
    }

    public int getSubgroup() {
        return (Id / 100) % 10;
    }

    @Override
    public int getId() {
        return Id;
    }
}
