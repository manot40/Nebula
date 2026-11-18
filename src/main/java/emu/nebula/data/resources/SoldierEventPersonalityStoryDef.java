package emu.nebula.data.resources;

import emu.nebula.data.BaseDef;
import emu.nebula.data.ResourceType;
import lombok.Getter;

@Getter
@ResourceType(name = "SoldierEventPersonalityStory.json")
public class SoldierEventPersonalityStoryDef extends BaseDef {
    private int Id;
    private int GroupId;
    private String Weight;
    private int StoryTypeId;
    private int OptionRule;

    @Override
    public int getId() {
        return Id;
    }
}
