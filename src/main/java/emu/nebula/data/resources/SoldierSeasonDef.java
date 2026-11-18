package emu.nebula.data.resources;

import emu.nebula.data.BaseDef;
import emu.nebula.data.ResourceType;
import lombok.Getter;

@Getter
@ResourceType(name = "SoldierSeason.json")
public class SoldierSeasonDef extends BaseDef {
    private int Id;
    private int Season;
    private int StarterGroupId;
    private int StrategyGroupId;
    private int ShopPoolGroupId;
    private int ChessGroupId;
    private int RewardGroupId;
    private int TechPointTid;

    @Override
    public int getId() {
        return Id;
    }
}
