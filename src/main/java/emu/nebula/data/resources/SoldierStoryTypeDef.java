package emu.nebula.data.resources;

import emu.nebula.data.BaseDef;
import emu.nebula.data.ResourceType;
import lombok.Getter;

@Getter
@ResourceType(name = "SoldierStoryType.json")
public class SoldierStoryTypeDef extends BaseDef {
    private int Id;
    private int StoryType;

    @Override
    public int getId() {
        return Id;
    }
}
