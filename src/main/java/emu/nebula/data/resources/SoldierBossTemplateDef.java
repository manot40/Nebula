package emu.nebula.data.resources;

import emu.nebula.data.BaseDef;
import emu.nebula.data.ResourceType;
import lombok.Getter;

@Getter
@ResourceType(name = "SoldierBossTemplate.json")
public class SoldierBossTemplateDef extends BaseDef {
    private int Id;
    private int Personality_Intuition;
    private int Personality_Analysis;
    private int Personality_Chaos;
    private int SoldierRoleSkillType;
    private boolean IsUnlock;
    private int TechNodeId;

    @Override
    public int getId() {
        return Id;
    }
}
