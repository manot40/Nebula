package emu.nebula.game.tower.cases;

import emu.nebula.GameConstants;
import emu.nebula.data.GameData;
import emu.nebula.game.inventory.ItemParamMap;
import emu.nebula.game.player.PlayerChangeInfo;
import emu.nebula.game.quest.QuestCondition;
import emu.nebula.game.tower.room.RoomType;
import emu.nebula.proto.PublicStarTower.StarTowerRoomCase;
import emu.nebula.proto.StarTowerInteract.StarTowerInteractReq;
import emu.nebula.proto.StarTowerInteract.StarTowerInteractResp;
import emu.nebula.util.Utils;
import lombok.Getter;

@Getter
public class StarTowerBattleCase extends StarTowerBaseCase {
    private int subNoteDrops;
    private int expReward;

    @Override
    public CaseType getType() {
        return CaseType.Battle;
    }
    
    public RoomType getRoomType() {
        return this.getRoom().getType();
    }
    
    @Override
    public void onRegister() {
        // Get relevant floor exp data for the current stage
        int stage = this.getRoom().getStage().getStage();
        var floorExpData = GameData.getStarTowerFloorExpDataTable().stream()
                            .filter(f -> f.getStarTowerId() == this.getGame().getId() && f.getStage() == stage)
                            .findFirst()
                            .or(() -> GameData.getStarTowerFloorExpDataTable().stream()
                                .filter(f -> f.getStarTowerId() == this.getGame().getId())
                                .findFirst())
                            .orElseThrow();
        
        // Determine appropriate reward
        switch (this.getRoom().getType()) {
            // Regular battle room
            case RoomType.BattleRoom:
                double chance = this.getModifiers().getBattleSubNoteDropChance() + .4;
                this.subNoteDrops = Utils.randomChance(chance) ? 1 : 0;
                this.expReward = floorExpData.getNormalExp();
                break;
            // Elite battle room
            case RoomType.EliteBattleRoom:
                this.subNoteDrops = 1;
                this.expReward = floorExpData.getEliteExp();
                break;
            // Non-final boss room
            case RoomType.BossRoom:
                this.subNoteDrops = 2;
                this.expReward = floorExpData.getBossExp();
                break;
            // Final room
            case RoomType.FinalBossRoom:
                this.subNoteDrops = 2;
                this.expReward = floorExpData.getFinalBossExp();
                break;
            default:
                break;
        }
    }

    @Override
    public StarTowerInteractResp interact(StarTowerInteractReq req, StarTowerInteractResp rsp) {
        // Parse battle end
        var proto = req.getBattleEndReq();
        
        // Init change
        var change = new PlayerChangeInfo();
        
        // Handle victory/defeat
        if (proto.hasVictory()) {
            // Level up
            this.getGame().addExp(this.expReward);
            int picks = this.getGame().levelUp();
            
            // Handle rare potential picks (guarantee 2 per deployed character across a run)
            int remainingRare = this.getGame().getRemainingRarePotentialCount();
            int rareDrops = 0;

            if (remainingRare > 0) {
                int remainingBattlesAfterThis = this.getGame().getRemainingBattleRoomCount();

                if (this.getRoomType() == RoomType.FinalBossRoom || remainingBattlesAfterThis == 0) {
                    // Final battle: award all remaining rare potentials so every deployed char gets 2
                    rareDrops = remainingRare;
                } else if (picks > 0) {
                    if (this.getGame().getFloorCount() == 1 || this.getRoomType() == RoomType.BossRoom) {
                        rareDrops = 1;
                    } else {
                        double baseChance = this.getRoomType() == RoomType.EliteBattleRoom ? 0.50D : 0.25D;
                        double pacedChance = (double) remainingRare / (remainingBattlesAfterThis + 1);
                        if (Utils.randomChance(Math.max(baseChance, pacedChance))) {
                            rareDrops = 1;
                        }
                    }

                    // Catch up if remaining battles cannot cover the remaining rare potentials at 1 per room
                    if (remainingRare - rareDrops > remainingBattlesAfterThis) {
                        rareDrops = Math.min(picks, remainingRare - remainingBattlesAfterThis);
                    }
                }

                if (rareDrops > 0) {
                    this.getGame().addRarePotentialSelectors(rareDrops);
                    picks = Math.max(0, picks - rareDrops);
                }
            }

            // Add remaining normal picks
            this.getGame().addPotentialSelectors(picks);
            
            // Add clear time
            this.getGame().addBattleTime(proto.getVictory().getTime());
            
            // Handle victory
            rsp.getMutableBattleEndResp()
                .getMutableVictory()
                .setLv(this.getGame().getTeamLevel())
                .setBattleTime(this.getGame().getBattleTime());
            
            // Calculate amount of coins earned
            int coin = this.getRoom().getStage().getInteriorCurrencyQuantity();
            
            // Tower research talent
            if (Utils.randomChance(this.getModifiers().getBonusCoinChance())) {
                coin += this.getModifiers().getBonusCoinCount();
            }
            
            // Add coins
            this.getGame().addItem(GameConstants.TOWER_COIN_ITEM_ID, coin, change);
            
            // Handle pending potential selectors
            var nextCases = this.getGame().handlePendingPotentialSelectors();
            
            for (var towerCase : nextCases) {
                this.getGame().addCase(rsp.getMutableCases(), towerCase);
            }
            
            // Add sub note skills
            this.calculateSubNoteRewards(change);

            // Handle client events for achievements
            this.getGame().getPlayer().getAchievementManager().handleClientEvents(proto.getVictory().getEvents());
        } else {
            // Handle defeat
            // TODO
            return this.getGame().settle(rsp, false);
        }
        
        // Set change
        rsp.setChange(change.toProto());
        
        // Return response for the player
        return rsp;
    }
    
    // Sub notes
    
    private void calculateSubNoteRewards(PlayerChangeInfo change) {
        // Init rewards
        var rewards = new ItemParamMap();
        
        // Sub note drops
        int subNotes = this.getSubNoteDrops();
        
        // Add extra sub notes after a boss fight
        if (getRoom().getType() == RoomType.BossRoom && getModifiers().getBonusBossSubNotes() > 0) {
            // 50% chance to add extra sub notes
            if (Utils.randomChance(0.5)) {
                subNotes += getModifiers().getBonusBossSubNotes();
            }
        }
        
        // Bonus sub note chance (Note of Surprise)
        if (Utils.randomChance(this.getModifiers().getBonusSubNoteChance())) {
            subNotes += this.getModifiers().getBonusSubNotes();
            
            // Star Tower quests
            this.getGame().getPlayer().trigger(QuestCondition.TowerSpecificNoteActivateTotal, 1);
        }
        
        // Regular sub note drops
        for (int i = 0; i < subNotes; i++) {
            int id = this.getGame().getRandomSubNoteId();
            int count = 3;
            
            rewards.add(id, count);
        }
        
        // Add sub notes to inventory
        for (var item : rewards) {
            this.getGame().addItem(item.getIntKey(), item.getIntValue(), change);
        }
    }
    
    // Proto
    
    @Override
    public void encodeProto(StarTowerRoomCase proto) {
        proto.getMutableBattleCase()
            .setSubNoteSkillNum(this.getSubNoteDrops());
    }
}
