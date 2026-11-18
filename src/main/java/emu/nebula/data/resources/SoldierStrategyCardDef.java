package emu.nebula.data.resources;

import java.util.List;

import emu.nebula.data.BaseDef;
import emu.nebula.data.ResourceType;
import lombok.Getter;

@Getter
@ResourceType(name = "SoldierStrategyCard.json")
public class SoldierStrategyCardDef extends BaseDef {
    private int Id;
    private int GroupId;
    private int Rarity;
    private int CardEffectId;
    private int GradeLevelCond;
    private List<Integer> CharacterShow;

    @Override
    public int getId() {
        return Id;
    }
}
