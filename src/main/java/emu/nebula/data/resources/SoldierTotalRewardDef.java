package emu.nebula.data.resources;

import emu.nebula.data.BaseDef;
import emu.nebula.data.ResourceType;
import lombok.Getter;

@Getter
@ResourceType(name = "SoldierTotalReward.json")
public class SoldierTotalRewardDef extends BaseDef {
    private int Id;
    private int SeasonId;
    private int Level;
    private int NeedExp;
    private int RewardId;
    private int RewardQty;
    private boolean KeyReward;

    @Override
    public int getId() {
        return Id;
    }
}
