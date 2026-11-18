package emu.nebula.data.resources;

import emu.nebula.data.BaseDef;
import emu.nebula.data.ResourceType;
import emu.nebula.game.character.ElementType;
import emu.nebula.util.Utils;
import emu.nebula.util.WeightedList;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import lombok.Getter;

@Getter
@ResourceType(name = "SubNoteSkillDropGroup.json")
public class SubNoteSkillDropGroupDef extends BaseDef {
    private int Id;
    private int GroupId;
    private int SubNoteSkillId;
    
    private static Int2ObjectMap<IntList> GROUPS = new Int2ObjectOpenHashMap<>();
    
    @Override
    public int getId() {
        return Id;
    }
    
    @Override
    public void onLoad() {
        var packageList = GROUPS.computeIfAbsent(this.GroupId, i -> new IntArrayList());
        packageList.add(this.SubNoteSkillId);
    }
    
    public static int getRandomDrop(int groupId) {
        return getRandomDrop(groupId, null, null);
    }

    /**
     * Picks a random sub note skill from the drop group using a two-stage roll:
     * Stage 1 preserves the group's generic vs. elemental candidate ratio, and
     * Stage 2 applies optional per-element bonus weights within the elemental pool.
     */
    public static int getRandomDrop(int groupId, Int2IntMap bonusWeights, IntSet excludeIds) {
        var dropList = GROUPS.get(groupId);
        if (dropList == null || dropList.isEmpty()) {
            return 0;
        }

        boolean filterExcluded = false;
        if (excludeIds != null && !excludeIds.isEmpty()) {
            for (int i = 0; i < dropList.size(); i++) {
                if (!excludeIds.contains(dropList.getInt(i))) {
                    filterExcluded = true;
                    break;
                }
            }
        }

        if ((bonusWeights == null || bonusWeights.isEmpty()) && !filterExcluded) {
            return Utils.randomElement(dropList);
        }

        IntList genericIds = new IntArrayList();
        IntList elementIds = new IntArrayList();

        for (int i = 0; i < dropList.size(); i++) {
            int id = dropList.getInt(i);
            if (filterExcluded && excludeIds.contains(id)) {
                continue;
            }

            if (ElementType.isElementSubNote(id)) {
                elementIds.add(id);
            } else {
                genericIds.add(id);
            }
        }

        if (elementIds.isEmpty()) {
            return genericIds.isEmpty() ? Utils.randomElement(dropList) : Utils.randomElement(genericIds);
        }

        // Preserve the generic vs. elemental drop share before applying element preference
        double elementChance = (double) elementIds.size() / (genericIds.size() + elementIds.size());
        if (!genericIds.isEmpty() && !Utils.randomChance(elementChance)) {
            return Utils.randomElement(genericIds);
        }

        var weighted = new WeightedList<Integer>();
        for (int i = 0; i < elementIds.size(); i++) {
            int id = elementIds.getInt(i);
            double weight = 1.0 + (bonusWeights != null ? Math.max(0, bonusWeights.get(id)) : 0);
            weighted.add(weight, id);
        }

        return weighted.size() > 0 ? weighted.next() : Utils.randomElement(elementIds);
    }

    public static IntList getGroup(int groupId) {
        return GROUPS.get(groupId);
    }
}
