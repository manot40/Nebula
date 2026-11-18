package emu.nebula.data.resources;

import java.util.List;

import emu.nebula.data.BaseDef;
import emu.nebula.data.ResourceType;
import lombok.Getter;

@Getter
@ResourceType(name = "SoldierTechNode.json")
public class SoldierTechNodeDef extends BaseDef {
    private int Id;
    private int NodeId;
    private int Season;
    private int Type;
    private int Priority;
    private int ItemId1;
    private int ItemQty1;
    private List<Integer> PreNodes;
    private boolean PreNodesLogic;
    private int Param;
    private List<Integer> EffectClient;
    private int EffectBattle;

    @Override
    public int getId() {
        return Id;
    }
}
