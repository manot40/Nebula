package emu.nebula.game.soldier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import dev.morphia.annotations.Entity;
import emu.nebula.data.GameData;
import emu.nebula.data.resources.SoldierCharacterDef;
import emu.nebula.data.resources.SoldierCheckStageAddScoreDef;
import emu.nebula.data.resources.SoldierEventBattlePoolDef;
import emu.nebula.data.resources.SoldierEventPersonalityStoryDef;
import emu.nebula.data.resources.SoldierFloorDef;
import emu.nebula.data.resources.SoldierGradeChallengeDef;
import emu.nebula.data.resources.SoldierNodePlanDef;
import emu.nebula.data.resources.SoldierShopPoolDef;
import emu.nebula.data.resources.SoldierStoryPersonalityOptionDef;
import emu.nebula.data.resources.SoldierStoryRewardDef;
import emu.nebula.data.resources.SoldierStrategyCardDef;
import emu.nebula.game.player.PlayerChangeInfo;
import emu.nebula.proto.Public;
import emu.nebula.proto.PublicSoldier;
import emu.nebula.proto.SoldierApply.SoldierApplyResp;
import emu.nebula.proto.SoldierInteract.SoldierInteractReq;
import emu.nebula.proto.SoldierInteract.SoldierInteractResp;
import emu.nebula.util.Utils;
import emu.nebula.util.WeightedList;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(useDiscriminator = false)
public class SoldierGame {
    public static final int INITIAL_COIN = 4;
    public static final int INITIAL_CHESS_COUNT = 3;
    public static final int DEFAULT_INITIAL_HP = 100;
    public static final int MAX_HP = 100;
    public static final int COIN_TID = 40;
    public static final int MAX_SKILL_COIN = 300;
    public static final int MAX_CLIENT_DATA = 64 * 1024;
    public static final int SHOP_SIZE = 5;
    public static final int BOARD_SLOT_MAX = 9;
    public static final int BENCH_SLOT_START = 10;
    public static final int BENCH_SLOT_COUNT = 8;
    public static final int POOL_COPIES_PER_UNIT = 9;
    public static final int SHOP_REFRESH_COST = 2;
    public static final int SHOP_EXP_COST = 4;
    public static final int SHOP_EXP_GAIN = 4;
    public static final int INITIAL_SHOP_LEVEL = 3;
    public static final int MAX_SHOP_LEVEL = 10;
    public static final int BASE_MAX_INTEREST = 2;

    public static final int PHASE_BOSS_ABILITY = 1;
    public static final int PHASE_FIGHT = 2;
    public static final int PHASE_BATTLE = 3;
    public static final int PHASE_EVENT_DISPUTE = 4;
    public static final int PHASE_EVENT_DISPUTE_BATTLE = 5;
    public static final int PHASE_STORY_PERSONALITY = 6;
    public static final int PHASE_STORY_COSTLY = 7;
    public static final int PHASE_CHECK_STAGE = 8;
    public static final int PHASE_SELECT_CARD = 9;

    private static final int AFTER_CARD_ADVANCE_NODE = 1;
    private static final int AFTER_CARD_ENTER_NODE = 2;

    private transient SoldierManager manager;

    private int seasonId;
    private int gradeChallengeId;
    private int bossAbilityId;
    private int stage;
    private int nodeIndex;
    private int passedNodes;
    private int hp;
    private int coin;
    private int totalCoinEarned;
    private int shopLevel;
    private int shopExp;
    private boolean shopLocked;
    private int freeRefreshes;
    private int interestCapBonus;
    private int winningStreak;
    private int losingStreak;
    private int deployVersion;
    private int nextChessUid;
    private int dataVersion;
    private String clientData;
    private boolean usedLastStand;

    private int lastCaseId;
    private int phase;
    private int returnPhaseAfterSelectCard;
    private int mainCaseId;
    private int shopCaseId;
    private int deployOptCaseId;
    private int clientEventCaseId;

    private int currentFloorId;
    private int eventBattleId;
    private int storyId;
    private int storySelectedIndex;
    private int storyDicePoint;
    private int pendingCardPicks;
    private int cardRerolls;
    private int checkStageDps;
    private int checkStageTime;
    private int checkStageDpsScore;
    private int checkStageTimeScore;

    private List<ChessEntry> chessList;
    private List<Integer> shopGoods;
    private List<Integer> eventChoices;
    private List<Integer> storyOptionIds;
    private Map<String, List<Integer>> storyPreviewCards;
    private Map<String, List<Integer>> storyPreviewChess;
    private List<Integer> cardChoices;
    private List<Integer> strategyCards;
    private Set<Integer> tracePartnerTypes;

    @Deprecated // Morphia only
    public SoldierGame() {
    }

    public SoldierGame(SoldierManager manager, int seasonId, int challengeId) {
        this.manager = manager;
        this.seasonId = seasonId;
        this.gradeChallengeId = challengeId;
        this.chessList = new ArrayList<>();
        this.shopGoods = new ArrayList<>();
        this.eventChoices = new ArrayList<>();
        this.storyOptionIds = new ArrayList<>();
        this.storyPreviewCards = new HashMap<>();
        this.storyPreviewChess = new HashMap<>();
        this.cardChoices = new ArrayList<>();
        this.strategyCards = new ArrayList<>();
        this.tracePartnerTypes = new HashSet<>();
    }

    public void initRun() {
        var season = GameData.getSoldierSeasonDataTable().get(this.seasonId);
        var challenge = GameData.getSoldierGradeChallengeDataTable().get(this.gradeChallengeId);
        var nodes = nodes(challenge.getNodeGroupId());

        this.stage = nodes.getFirst().getStage();
        this.nodeIndex = 1;
        this.passedNodes = 0;
        this.hp = challenge.getInitialHp() > 0 ? challenge.getInitialHp() : DEFAULT_INITIAL_HP;
        this.coin = INITIAL_COIN + (hasTechNode(10001) ? 4 : 0);
        this.totalCoinEarned = this.coin;
        this.shopLevel = INITIAL_SHOP_LEVEL;
        this.shopExp = 0;
        this.shopLocked = false;
        this.freeRefreshes = 0;
        this.interestCapBonus = 0;
        this.winningStreak = 0;
        this.losingStreak = 0;
        this.deployVersion = 1;
        this.nextChessUid = 1;
        this.dataVersion = 1;
        this.clientData = "";
        this.usedLastStand = false;

        var starterCandidates = new ArrayList<Integer>();
        for (var character : GameData.getSoldierCharacterDataTable()) {
            if (character.getGroupId() == season.getChessGroupId() && character.isBoardChess()
                    && starterWeightForCost(character.getCost()) > 0) {
                starterCandidates.add(character.getId());
            }
        }
        for (int i = 0; i < INITIAL_CHESS_COUNT && !starterCandidates.isEmpty(); i++) {
            var weighted = new WeightedList<Integer>();
            for (int tid : starterCandidates) {
                var c = GameData.getSoldierCharacterDataTable().get(tid);
                int w = c != null ? starterWeightForCost(c.getCost()) : 1;
                weighted.add(Math.max(1, w), tid);
            }
            int pickedTid = weighted.next();
            starterCandidates.remove((Integer) pickedTid);
            var entry = new ChessEntry();
            entry.uid = this.nextChessUid++;
            entry.tid = pickedTid;
            entry.star = 1;
            entry.slot = BENCH_SLOT_START + i;
            chessList().add(entry);
        }

        this.phase = PHASE_BOSS_ABILITY;
        this.mainCaseId = nextCaseId();
        this.clientEventCaseId = nextCaseId();
    }

    public void postLoad(SoldierManager manager) {
        this.manager = manager;
        chessList();
        shopGoods();
        eventChoices();
        storyOptionIds();
        storyPreviewCards();
        storyPreviewChess();
        cardChoices();
        strategyCards();
        tracePartnerTypes();
    }

    private List<ChessEntry> chessList() {
        if (this.chessList == null) this.chessList = new ArrayList<>();
        return this.chessList;
    }

    private List<Integer> shopGoods() {
        if (this.shopGoods == null) this.shopGoods = new ArrayList<>();
        return this.shopGoods;
    }

    private List<Integer> eventChoices() {
        if (this.eventChoices == null) this.eventChoices = new ArrayList<>();
        return this.eventChoices;
    }

    private List<Integer> storyOptionIds() {
        if (this.storyOptionIds == null) this.storyOptionIds = new ArrayList<>();
        return this.storyOptionIds;
    }

    private Map<String, List<Integer>> storyPreviewCards() {
        if (this.storyPreviewCards == null) this.storyPreviewCards = new HashMap<>();
        return this.storyPreviewCards;
    }

    private Map<String, List<Integer>> storyPreviewChess() {
        if (this.storyPreviewChess == null) this.storyPreviewChess = new HashMap<>();
        return this.storyPreviewChess;
    }

    private List<Integer> cardChoices() {
        if (this.cardChoices == null) this.cardChoices = new ArrayList<>();
        return this.cardChoices;
    }

    private List<Integer> strategyCards() {
        if (this.strategyCards == null) this.strategyCards = new ArrayList<>();
        return this.strategyCards;
    }

    private Set<Integer> tracePartnerTypes() {
        if (this.tracePartnerTypes == null) this.tracePartnerTypes = new HashSet<>();
        return this.tracePartnerTypes;
    }

    public boolean inProgress() {
        return this.gradeChallengeId != 0;
    }

    public SoldierApplyResp toApplyResp() {
        var rsp = SoldierApplyResp.newInstance()
                .setCoinQty(this.coin)
                .setInfo(toInfoProto());
        for (var entry : chessList()) {
            rsp.addChess(toChessProto(entry));
        }
        return rsp;
    }

    public SoldierInteractResp interact(SoldierInteractReq req) {
        if (!inProgress()) {
            return null;
        }
        int reqId = req.getId();

        if (this.phase == PHASE_FIGHT && reqId == this.shopCaseId && req.hasShopReq()) {
            return interactShop(req);
        }
        if (this.deployOptCaseId > 0 && reqId == this.deployOptCaseId && req.hasDeploySyncReq()) {
            return interactDeploySync(req);
        }
        if (reqId == this.clientEventCaseId && req.hasClientEventReq()) {
            return interactClientEvent(req);
        }
        if (reqId != this.mainCaseId) {
            return null;
        }

        return switch (this.phase) {
            case PHASE_BOSS_ABILITY -> interactSelectBossAbility(req);
            case PHASE_FIGHT -> interactFight(req);
            case PHASE_BATTLE -> interactBattle(req);
            case PHASE_EVENT_DISPUTE -> interactEventDispute(req);
            case PHASE_EVENT_DISPUTE_BATTLE -> interactEventDisputeBattle(req);
            case PHASE_STORY_PERSONALITY -> interactStoryPersonality(req);
            case PHASE_STORY_COSTLY -> interactStoryCostly(req);
            case PHASE_CHECK_STAGE -> interactCheckStage(req);
            case PHASE_SELECT_CARD -> interactSelectCard(req);
            default -> null;
        };
    }

    private SoldierInteractResp interactSelectBossAbility(SoldierInteractReq req) {
        if (!req.hasSelectBossAbilityReq()) {
            return null;
        }
        int abilityId = req.getSelectBossAbilityReq().getAbilityId();
        var boss = GameData.getSoldierBossTemplateDataTable().get(abilityId);
        if (boss == null || (!boss.isIsUnlock() && (boss.getTechNodeId() <= 0 || !hasTechNode(boss.getTechNodeId())))) {
            return null;
        }

        this.bossAbilityId = abilityId;
        var change = new PlayerChangeInfo();
        addBuffToChange(change, PublicSoldier.SoldierBuffType.AbilityBuff, abilityId, 4000 + abilityId);

        var rsp = SoldierInteractResp.newInstance().setId(req.getId());
        rsp.getMutableNilResp();

        if (abilityId == 101) {
            this.coin += 6;
            this.totalCoinEarned += 6;
            change.add(PublicSoldier.SoldierRes.newInstance().setTid(COIN_TID).setQty(6));
            var granted = grantRandomCharacters(1, 3, 1, change);
            if (!granted.isEmpty()) {
                var showChess = PublicSoldier.SoldierEffectAddChess.newInstance();
                for (var entry : granted) {
                    showChess.addChess(toChessProto(entry));
                }
                rsp.addEffects(PublicSoldier.SoldierEffect.newInstance().setShowChess(showChess));
            }
        }

        enterCurrentNode(rsp, change);
        rsp.setNode(nodeData()).setSyncData(syncData());
        if (!change.isEmpty()) {
            rsp.setChange(change.toProto());
        }
        this.manager.save();
        return rsp;
    }

    private SoldierInteractResp interactDeploySync(SoldierInteractReq req) {
        var sync = req.getDeploySyncReq();
        if (sync.hasDeployVersion() && sync.getDeployVersion() != this.deployVersion) {
            return deployRollbackResp(req.getId());
        }
        var change = new PlayerChangeInfo();
        if (sync.hasMove()) {
            var move = sync.getMove();
            var entry = findChessByUid(move.getUID());
            int targetSlot = move.getSlot();
            if (entry == null || targetSlot < 1 || targetSlot >= BENCH_SLOT_START + BENCH_SLOT_COUNT) {
                return deployRollbackResp(req.getId());
            }
            var existing = findChessBySlot(targetSlot);
            boolean movingBenchToBoard = entry.slot > BOARD_SLOT_MAX && targetSlot <= BOARD_SLOT_MAX;
            boolean swappingBoardToBench = entry.slot <= BOARD_SLOT_MAX && targetSlot > BOARD_SLOT_MAX && existing != null;
            if (movingBenchToBoard && existing == null && countBoardDeployed() >= maxBoardDeployCount()) {
                return deployRollbackResp(req.getId());
            }
            if (movingBenchToBoard && hasOtherBoardTid(entry.tid, existing)) {
                return deployRollbackResp(req.getId());
            }
            if (swappingBoardToBench && hasOtherBoardTid(existing.tid, entry)) {
                return deployRollbackResp(req.getId());
            }
            var transform = PublicSoldier.SoldierTransform.newInstance();
            if (existing != null && existing != entry) {
                existing.slot = entry.slot;
                transform.addUpdate(toChessProto(existing));
            }
            entry.slot = targetSlot;
            transform.addUpdate(toChessProto(entry));
            addChessChange(change, transform);
        }
        this.deployOptCaseId = nextCaseId();
        this.manager.save();

        var rsp = SoldierInteractResp.newInstance()
                .setId(req.getId())
                .setDeploySyncResp(PublicSoldier.InteractDeploySyncResp.newInstance()
                        .setSucceed(Public.Nil.newInstance()));
        addActiveCases(rsp);
        if (!change.isEmpty()) {
            rsp.setChange(change.toProto());
        }
        return rsp;
    }

    private SoldierInteractResp deployRollbackResp(int requestId) {
        this.deployOptCaseId = nextCaseId();
        this.manager.save();
        var rsp = SoldierInteractResp.newInstance()
                .setId(requestId)
                .setDeploySyncResp(PublicSoldier.InteractDeploySyncResp.newInstance()
                        .setDeploy(buildDeployProto()));
        addActiveCases(rsp);
        return rsp;
    }

    private SoldierInteractResp interactClientEvent(SoldierInteractReq req) {
        var ev = req.getClientEventReq();
        if (!validClientData(ev.getClientData(), ev.getDataVersion())) {
            return null;
        }
        this.clientData = ev.getClientData();
        this.dataVersion = ev.getDataVersion();
        this.clientEventCaseId = nextCaseId();
        this.manager.save();

        var rsp = SoldierInteractResp.newInstance().setId(req.getId());
        rsp.getMutableNilResp();
        addActiveCases(rsp);
        return rsp;
    }

    private SoldierInteractResp interactShop(SoldierInteractReq req) {
        var shop = req.getShopReq();
        var rsp = SoldierInteractResp.newInstance().setId(req.getId());
        var shopResp = PublicSoldier.InteractShopResp.newInstance();
        var change = new PlayerChangeInfo();

        if (shop.hasPurchase()) {
            int index = shop.getPurchase() - 1;
            if (index < 0 || index >= shopGoods().size()) {
                return null;
            }
            int characterId = shopGoods().get(index);
            SoldierShopPoolDef goods = shopPool(characterId);
            if (characterId == 0 || goods == null || this.coin < goods.getCost()) {
                return null;
            }
            int benchSlot = allocateBenchSlot();
            if (benchSlot == 0 && !canMergeWith(characterId)) {
                return null;
            }

            this.coin -= goods.getCost();
            shopGoods().set(index, 0);

            var obtained = new ChessEntry();
            obtained.uid = this.nextChessUid++;
            obtained.tid = characterId;
            obtained.star = 1;
            obtained.slot = benchSlot;

            var transforms = appendAndMergeChess(obtained);
            change.add(PublicSoldier.SoldierRes.newInstance().setTid(COIN_TID).setQty(-goods.getCost()));
            addChessChange(change, transforms);
            shopResp.setGoods(toChessProto(obtained));
        } else if (shop.hasSell()) {
            var sold = shop.getSell();
            var entry = findChessByUid(sold.getUID());
            if (entry == null) {
                return null;
            }
            var character = GameData.getSoldierCharacterDataTable().get(entry.tid);
            if (character == null || !character.isBoardChess()) {
                return null;
            }
            int value = Math.max(1, character.getCost() * entry.star);
            this.coin += value;
            this.totalCoinEarned += value;
            chessList().remove(entry);

            change.add(PublicSoldier.SoldierRes.newInstance().setTid(COIN_TID).setQty(value));
            addChessChange(change, PublicSoldier.SoldierTransform.newInstance().addConsume(toChessProto(entry)));
            shopResp.setRes(PublicSoldier.SoldierRes.newInstance().setTid(COIN_TID).setQty(value));
        } else if (shop.hasRefresh() && shop.getRefresh()) {
            int refreshCost = this.freeRefreshes > 0 ? 0 : SHOP_REFRESH_COST;
            if (this.coin < refreshCost) {
                return null;
            }
            if (this.freeRefreshes > 0) {
                this.freeRefreshes--;
            } else {
                this.coin -= refreshCost;
                change.add(PublicSoldier.SoldierRes.newInstance().setTid(COIN_TID).setQty(-refreshCost));
            }
            this.shopLocked = false;
            rollShop();
            shopResp.setShopData(shopData());
        } else if (shop.hasUpgrade() && shop.getUpgrade()) {
            if (this.coin < SHOP_EXP_COST || this.shopLevel >= MAX_SHOP_LEVEL) {
                return null;
            }
            this.coin -= SHOP_EXP_COST;
            this.shopExp += SHOP_EXP_GAIN;
            updateShopLevel();
            change.add(PublicSoldier.SoldierRes.newInstance().setTid(COIN_TID).setQty(-SHOP_EXP_COST));
            shopResp.setUpgrade(PublicSoldier.InteractShopResp.UpgradeResult.newInstance()
                    .setLevel(this.shopLevel)
                    .setExp(this.shopExp)
                    .addAllRarityUp(0, 0, 0, 0, 0));
        } else if (shop.hasLock()) {
            this.shopLocked = shop.getLock();
            shopResp.setSucceed(this.shopLocked);
        } else {
            return null;
        }

        this.shopCaseId = nextCaseId();
        rsp.setShopResp(shopResp).setSyncData(syncData());
        addActiveCases(rsp);
        if (!change.isEmpty()) {
            rsp.setChange(change.toProto());
        }
        this.manager.save();
        return rsp;
    }

    private SoldierInteractResp interactFight(SoldierInteractReq req) {
        if (!req.hasFightReq()) {
            return null;
        }
        int floorId = rollFloorId();
        if (floorId == 0) {
            return null;
        }
        this.phase = PHASE_BATTLE;
        this.currentFloorId = floorId;
        this.mainCaseId = nextCaseId();
        this.manager.save();

        var rsp = SoldierInteractResp.newInstance().setId(req.getId());
        rsp.getMutableNilResp();
        addActiveCases(rsp);
        return rsp;
    }

    private SoldierInteractResp interactBattle(SoldierInteractReq req) {
        if (!req.hasBattleReq()) {
            return null;
        }
        var battle = req.getBattleReq();
        boolean victory = battle.hasVictory();
        boolean defeat = battle.hasDefeat() && battle.getDefeat();
        SoldierNodePlanDef node = currentNode();
        if (node == null || victory == defeat || battle.getChessSkillAddGold() > MAX_SKILL_COIN
                || !validClientData(battle.getClientData(), battle.getDataVersion())) {
            return null;
        }

        int battleTime = 90;
        var floor = GameData.getSoldierFloorDataTable().get(this.currentFloorId);
        if (floor != null && floor.getBattleTime() > 0) {
            battleTime = floor.getBattleTime();
        }
        if (victory && battle.getVictory().hasTime() && battle.getVictory().getTime() > battleTime + 120) {
            return null;
        }

        this.clientData = battle.getClientData();
        this.dataVersion = battle.getDataVersion();

        int baseReward = node.getCoin();
        int interest = Math.min(this.coin / 10, maxInterest());
        int streakReward;
        if (victory) {
            this.winningStreak++;
            this.losingStreak = 0;
            streakReward = streakReward(this.winningStreak);
            this.passedNodes++;
        } else {
            this.losingStreak++;
            this.winningStreak = 0;
            streakReward = streakReward(this.losingStreak);
        }
        applyBattleHpResult(node, victory);

        int extraGold = battle.getChessSkillAddGold();
        if (victory && hasStrategyCard(2010113)) {
            extraGold += 1;
        }
        int coinDelta = baseReward + interest + streakReward + extraGold;
        this.coin += coinDelta;
        this.totalCoinEarned += Math.max(0, coinDelta);

        int expGain = node.getExperience();
        if (!victory && hasStrategyCard(2010112)) {
            expGain += 2;
        }
        this.shopExp += expGain;
        updateShopLevel();

        var change = soldierResChange(coinDelta);
        var rsp = SoldierInteractResp.newInstance()
                .setId(req.getId())
                .setBattleResp(PublicSoldier.InteractSoldierBattleResp.newInstance()
                        .setBaseReward(baseReward)
                        .setInterest(interest)
                        .setWinningStreak(victory ? streakReward : 0)
                        .setLosingStreak(victory ? 0 : streakReward));

        return advanceOrFinish(rsp, victory, change);
    }

    private SoldierInteractResp interactEventDispute(SoldierInteractReq req) {
        if (!req.hasEventSettlementOfDisputeReq()) {
            return null;
        }
        int index = req.getEventSettlementOfDisputeReq().getIndex();
        if (index < 1 || index > eventChoices().size()) {
            return null;
        }
        var event = GameData.getSoldierEventBattlePoolDataTable().get(eventChoices().get(index - 1));
        if (event == null) {
            return null;
        }
        int floorId = rollFloorForGroup(event.getFloorGroup());
        if (floorId == 0) {
            return null;
        }

        this.eventBattleId = event.getId();
        this.currentFloorId = floorId;
        this.phase = PHASE_EVENT_DISPUTE_BATTLE;
        this.mainCaseId = nextCaseId();
        this.manager.save();

        var rsp = SoldierInteractResp.newInstance().setId(req.getId());
        rsp.getMutableNilResp();
        addActiveCases(rsp);
        return rsp;
    }

    private SoldierInteractResp interactEventDisputeBattle(SoldierInteractReq req) {
        if (!req.hasEventSettlementOfDisputeBattleReq()) {
            return null;
        }
        var battle = req.getEventSettlementOfDisputeBattleReq();
        boolean victory = battle.hasVictory();
        boolean defeat = battle.hasDefeat() && battle.getDefeat();
        var event = GameData.getSoldierEventBattlePoolDataTable().get(this.eventBattleId);
        var node = currentNode();
        if (event == null || node == null || victory == defeat || battle.getChessSkillAddGold() > MAX_SKILL_COIN
                || !validClientData(battle.getClientData(), battle.getDataVersion())) {
            return null;
        }
        var floor = GameData.getSoldierFloorDataTable().get(this.currentFloorId);
        if (victory && battle.getVictory().hasTime()
                && floor != null && floor.getBattleTime() > 0 && battle.getVictory().getTime() > floor.getBattleTime() + 120) {
            return null;
        }

        this.clientData = battle.getClientData();
        this.dataVersion = battle.getDataVersion();
        int interest = Math.min(this.coin / 10, maxInterest());
        int baseReward = victory ? event.getCoin() : 0;
        int coinDelta = baseReward + interest + battle.getChessSkillAddGold();
        this.coin += coinDelta;
        this.totalCoinEarned += Math.max(0, coinDelta);

        var change = soldierResChange(coinDelta);
        var battleResp = PublicSoldier.InteractEventSettlementOfDisputeBattleResp.newInstance()
                .setCoin(baseReward)
                .setBaseReward(baseReward)
                .setInterest(interest);

        if (victory) {
            for (var entry : grantRandomCharacters(event.getCharacterCount(), 0, 1, change)) {
                battleResp.addChess(toChessProto(entry));
            }
            this.pendingCardPicks += Math.max(0, event.getStrategyCardCount());
            this.passedNodes++;
        }
        applyBattleHpResult(node, victory);

        this.shopExp += node.getExperience();
        updateShopLevel();

        var rsp = SoldierInteractResp.newInstance()
                .setId(req.getId())
                .setEventSettlementOfDisputeBattleResp(battleResp);
        return advanceOrFinish(rsp, victory, change);
    }

    private void applyBattleHpResult(SoldierNodePlanDef node, boolean victory) {
        if (victory) {
            int healBonus = hasTechNode(10006) ? 2 : 0;
            this.hp = Math.min(MAX_HP, this.hp + node.getAddHp() + healBonus);
            return;
        }
        this.hp = Math.max(0, this.hp - node.getLoseHp());
        if (this.hp == 0 && hasTechNode(10023) && !this.usedLastStand) {
            this.usedLastStand = true;
            this.hp = 1;
        }
    }

    private SoldierInteractResp interactStoryPersonality(SoldierInteractReq req) {
        if (!req.hasStoryEventOfPersonalitySelectReq()) {
            return null;
        }
        var storyReq = req.getStoryEventOfPersonalitySelectReq();
        var selectResp = PublicSoldier.InteractStoryEventOfPersonalitySelectResp.newInstance();
        var change = new PlayerChangeInfo();

        if (!storyReq.getThrowDice()) {
            int idx = storyReq.getIndex();
            if (idx < 1 || idx > storyOptionIds().size()) {
                return null;
            }
            int optionId = storyOptionIds().get(idx - 1);
            var optionDef = GameData.getSoldierStoryPersonalityOptionDataTable().get(optionId);
            if (optionDef == null || !isOptionSelectable(optionDef)) {
                return null;
            }

            if (optionDef.getCheckDifficulty() > 0) {
                this.storySelectedIndex = idx;
                this.mainCaseId = nextCaseId();
                this.manager.save();
                selectResp.setSuccess(Public.Nil.newInstance());
                var rsp = SoldierInteractResp.newInstance()
                        .setId(req.getId())
                        .setStoryEventOfPersonalitySelectResp(selectResp);
                addActiveCases(rsp);
                return rsp;
            }

            // No dice check required -> resolve immediately
            var rsp = SoldierInteractResp.newInstance().setId(req.getId());
            var rewardBuilder = selectResp.getMutableReward().setPoint(0).setResult(true);
            applyStoryResult(optionId, optionDef.getResultId(), rewardBuilder::addRewards, change, rsp);
            return completeStoryNode(rsp, selectResp, change);
        }

        int idx = storyReq.hasIndex() && storyReq.getIndex() > 0 ? storyReq.getIndex() : this.storySelectedIndex;
        if (idx < 1 || idx > storyOptionIds().size()) {
            idx = 1;
        }
        int optionId = storyOptionIds().get(idx - 1);
        var optionDef = GameData.getSoldierStoryPersonalityOptionDataTable().get(optionId);

        int rawRoll = Utils.randomRange(1, 20);
        int diceBonus = hasTechNode(10018) ? 2 : 0;
        int point = Math.min(20, rawRoll + diceBonus);
        int stat = optionDef == null ? 0 : bossStatForDimension(optionDef.getCheckDimension());
        boolean success = optionDef == null || optionDef.getCheckDifficulty() <= 0
                || (point + stat) >= optionDef.getCheckDifficulty();

        this.storyDicePoint = point;
        var rsp = SoldierInteractResp.newInstance().setId(req.getId());
        var rewardBuilder = selectResp.getMutableReward().setPoint(point).setResult(success);
        if (optionDef != null) {
            int resultId = success ? optionDef.getResultId() : optionDef.getFailResultId();
            applyStoryResult(optionId, resultId, rewardBuilder::addRewards, change, rsp);
        }
        return completeStoryNode(rsp, selectResp, change);
    }

    private SoldierInteractResp completeStoryNode(SoldierInteractResp rsp,
            PublicSoldier.InteractStoryEventOfPersonalitySelectResp selectResp, PlayerChangeInfo change) {
        var node = currentNode();
        if (node != null) {
            this.shopExp += node.getExperience();
            updateShopLevel();
        }
        this.passedNodes++;

        rsp.setStoryEventOfPersonalitySelectResp(selectResp);
        return advanceOrFinish(rsp, this.hp > 0, change);
    }

    private SoldierInteractResp interactStoryCostly(SoldierInteractReq req) {
        int idx = storyCostlyOptionIndex(req);
        if (idx < 1 || idx > storyOptionIds().size()) {
            return null;
        }

        int optionId = storyOptionIds().get(idx - 1);
        var optionDef = GameData.getSoldierStoryPersonalityOptionDataTable().get(optionId);
        if (optionDef == null || !isOptionSelectable(optionDef)) {
            return null;
        }

        var change = new PlayerChangeInfo();
        var rsp = SoldierInteractResp.newInstance().setId(req.getId());
        var costlyResp = PublicSoldier.InteractStoryEventOfCostlySelectResp.newInstance();
        applyStoryResult(optionId, optionDef.getResultId(), costlyResp::addRewards, change, rsp);

        var node = currentNode();
        if (node != null) {
            this.shopExp += node.getExperience();
            updateShopLevel();
        }
        this.passedNodes++;

        rsp.setStoryEventOfCostlySelectResp(costlyResp);
        return advanceOrFinish(rsp, this.hp > 0, change);
    }

    private static int storyCostlyOptionIndex(SoldierInteractReq req) {
        if (req.hasSelectReq()) {
            return req.getSelectReq().getIndex();
        }
        if (req.hasEventSettlementOfDisputeReq()) {
            return req.getEventSettlementOfDisputeReq().getIndex();
        }
        if (req.hasStoryEventOfPersonalitySelectReq()) {
            return req.getStoryEventOfPersonalitySelectReq().getIndex();
        }
        return 0;
    }

    private SoldierInteractResp interactSelectCard(SoldierInteractReq req) {
        if (!req.hasSelectReq()) {
            return null;
        }
        var select = req.getSelectReq();
        var rsp = SoldierInteractResp.newInstance().setId(req.getId());
        var selectResp = PublicSoldier.InteractSoldierSelectResp.newInstance();

        if (select.hasReRoll() && select.getReRoll()) {
            if (this.cardRerolls <= 0) {
                return null;
            }
            this.cardRerolls--;
            rollStrategyCardChoices();
            this.mainCaseId = nextCaseId();
            this.manager.save();
            selectResp.setSelectCard(selectCardData());
            rsp.setSelectResp(selectResp);
            addActiveCases(rsp);
            return rsp;
        }

        int idx = select.getIndex();
        if (idx < 1 || idx > cardChoices().size()) {
            return null;
        }

        int cardId = cardChoices().get(idx - 1);
        var cardDef = GameData.getSoldierStrategyCardDataTable().get(cardId);
        if (cardDef == null) {
            return null;
        }

        var change = new PlayerChangeInfo();
        strategyCards().add(cardId);
        addBuffToChange(change, PublicSoldier.SoldierBuffType.CardBuff, cardId, cardDef.getCardEffectId());
        applyStrategyCardImmediate(cardDef, change, rsp);
        selectResp.getMutableResp().setId(cardId);
        rsp.setSelectResp(selectResp);

        this.pendingCardPicks = Math.max(0, this.pendingCardPicks - 1);
        if (this.pendingCardPicks > 0) {
            rollStrategyCardChoices();
            if (!cardChoices().isEmpty()) {
                this.mainCaseId = nextCaseId();
                addActiveCases(rsp);
                rsp.setSyncData(syncData());
                if (!change.isEmpty()) {
                    rsp.setChange(change.toProto());
                }
                this.manager.save();
                return rsp;
            }
            this.pendingCardPicks = 0;
        }

        if (this.returnPhaseAfterSelectCard == AFTER_CARD_ENTER_NODE) {
            this.returnPhaseAfterSelectCard = 0;
            cardChoices().clear();
            enterCurrentNode(rsp, change);
            rsp.setNode(nodeData()).setSyncData(syncData());
            if (!change.isEmpty()) {
                rsp.setChange(change.toProto());
            }
            this.manager.save();
            return rsp;
        }
        if (this.returnPhaseAfterSelectCard == AFTER_CARD_ADVANCE_NODE) {
            this.returnPhaseAfterSelectCard = 0;
            return advanceToNextNodeOrFinish(rsp, this.hp > 0, change);
        }

        addActiveCases(rsp);
        rsp.setSyncData(syncData());
        if (!change.isEmpty()) {
            rsp.setChange(change.toProto());
        }
        this.manager.save();
        return rsp;
    }

    private SoldierInteractResp interactCheckStage(SoldierInteractReq req) {
        if (!req.hasCheckStageSettleReq()) {
            return null;
        }
        var checkReq = req.getCheckStageSettleReq();
        var challenge = GameData.getSoldierGradeChallengeDataTable().get(this.gradeChallengeId);
        int groupId = challenge != null && challenge.getCheckStageGroupId() > 0 ? challenge.getCheckStageGroupId() : 101;

        SoldierCheckStageAddScoreDef dpsDef = GameData.getSoldierCheckStageAddScoreDataTable().get(checkReq.getDpsId());
        SoldierCheckStageAddScoreDef timeDef = GameData.getSoldierCheckStageAddScoreDataTable().get(checkReq.getTimeId());
        int dpsScore = dpsDef != null && dpsDef.getGroup() == groupId ? dpsDef.getDPSAddScore() : 0;
        int timeScore = timeDef != null && timeDef.getGroup() == groupId ? timeDef.getTankingAddScore() : 0;

        this.checkStageDps = dpsDef != null ? dpsDef.getDPS() : 0;
        this.checkStageTime = timeDef != null ? timeDef.getDuration() : 0;
        this.checkStageDpsScore = dpsScore;
        this.checkStageTimeScore = timeScore;
        this.passedNodes++;

        var rsp = SoldierInteractResp.newInstance()
                .setId(req.getId())
                .setCheckStageSettleResp(PublicSoldier.InteractCheckStageSettleResp.newInstance()
                        .setDpsScore(dpsScore)
                        .setTimeScore(timeScore));
        return finishRun(rsp, true, new PlayerChangeInfo());
    }

    private SoldierInteractResp advanceOrFinish(SoldierInteractResp rsp, boolean victory, PlayerChangeInfo change) {
        if (this.hp <= 0) {
            return finishRun(rsp, false, change);
        }
        if (victory && this.pendingCardPicks > 0) {
            rollStrategyCardChoices();
            if (!cardChoices().isEmpty()) {
                // Advance the node before entering SelectCard so battle results receive a valid Node change (tbNodeChange).
                if (!stepToNextNode(change)) {
                    this.pendingCardPicks = 0;
                    return finishRun(rsp, true, change);
                }
                this.phase = PHASE_SELECT_CARD;
                this.returnPhaseAfterSelectCard = AFTER_CARD_ENTER_NODE;
                this.cardRerolls = 1;
                this.mainCaseId = nextCaseId();
                this.shopCaseId = 0;
                addActiveCases(rsp);
                rsp.setNode(nodeData()).setSyncData(syncData());
                if (!change.isEmpty()) {
                    rsp.setChange(change.toProto());
                }
                this.manager.save();
                return rsp;
            }
            this.pendingCardPicks = 0;
        }
        return advanceToNextNodeOrFinish(rsp, victory, change);
    }

    private SoldierInteractResp advanceToNextNodeOrFinish(SoldierInteractResp rsp, boolean victory,
            PlayerChangeInfo change) {
        if (!stepToNextNode(change)) {
            return finishRun(rsp, this.hp > 0 && victory, change);
        }
        cardChoices().clear();
        enterCurrentNode(rsp, change);
        rsp.setNode(nodeData()).setSyncData(syncData());
        if (!change.isEmpty()) {
            rsp.setChange(change.toProto());
        }
        this.manager.save();
        return rsp;
    }

    private boolean stepToNextNode(PlayerChangeInfo change) {
        var nodes = nodesForCurrentChallenge();
        boolean finalNode = this.nodeIndex >= nodes.size();
        if (this.hp <= 0 || finalNode) {
            return false;
        }

        int prevStage = this.stage;
        this.nodeIndex++;
        SoldierNodePlanDef next = currentNode();
        if (next == null) {
            return false;
        }

        this.stage = next.getStage();
        this.currentFloorId = 0;
        this.eventBattleId = 0;
        this.storyId = 0;
        this.storySelectedIndex = 0;
        this.storyDicePoint = 0;
        eventChoices().clear();
        storyOptionIds().clear();
        storyPreviewCards().clear();
        storyPreviewChess().clear();

        onEnterNewNode(prevStage, this.stage, change);
        return true;
    }

    private void onEnterNewNode(int prevStage, int newStage, PlayerChangeInfo change) {
        if (this.bossAbilityId == 101 && this.nodeIndex == 4) {
            this.coin += 6;
            this.totalCoinEarned += 6;
            change.add(PublicSoldier.SoldierRes.newInstance().setTid(COIN_TID).setQty(6));
        }
        if (this.bossAbilityId == 102) {
            this.freeRefreshes += 1 + (newStage > prevStage ? 2 : 0);
        }
        if (this.bossAbilityId == 103) {
            this.shopExp += 2;
            updateShopLevel();
        }
        if (newStage > prevStage) {
            int stageGold = (this.bossAbilityId == 104 ? 15 : 0) + (hasTechNode(10009) ? 5 : 0);
            if (stageGold > 0) {
                this.coin += stageGold;
                this.totalCoinEarned += stageGold;
                change.add(PublicSoldier.SoldierRes.newInstance().setTid(COIN_TID).setQty(stageGold));
            }
        }
    }

    private void enterCurrentNode(SoldierInteractResp rsp, PlayerChangeInfo change) {
        prepareCurrentNodeCase();
        this.mainCaseId = nextCaseId();
        if (this.phase == PHASE_FIGHT) {
            this.shopCaseId = nextCaseId();
            this.deployOptCaseId = nextCaseId();
        } else {
            this.shopCaseId = 0;
            if (this.deployOptCaseId == 0) {
                this.deployOptCaseId = nextCaseId();
            }
        }
        addActiveCases(rsp);
    }

    private void addActiveCases(SoldierInteractResp rsp) {
        if (this.mainCaseId > 0) {
            rsp.addCases(buildMainCaseProto());
        }
        if (this.phase == PHASE_FIGHT && this.shopCaseId > 0) {
            rsp.addCases(PublicSoldier.SoldierCase.newInstance().setId(this.shopCaseId).setShopCase(shopData()));
        }
        if (this.deployOptCaseId > 0) {
            rsp.addCases(PublicSoldier.SoldierCase.newInstance().setId(this.deployOptCaseId)
                    .setDeployOptCase(PublicSoldier.DeploySyncCaseData.newInstance()));
        }
        if (this.clientEventCaseId > 0) {
            rsp.addCases(PublicSoldier.SoldierCase.newInstance().setId(this.clientEventCaseId)
                    .setClientEventCase(PublicSoldier.ClientEventCaseData.newInstance()));
        }
    }

    private SoldierInteractResp finishRun(SoldierInteractResp rsp, boolean victory, PlayerChangeInfo change) {
        rsp.setNode(nodeData()).setSyncData(syncData());
        if (!change.isEmpty()) {
            rsp.setChange(change.toProto());
        }
        var settle = this.manager.settleRun(this, victory);
        if (settle == null) {
            return null;
        }
        rsp.setSettle(settle);
        return rsp;
    }

    private void prepareCurrentNodeCase() {
        var node = currentNode();
        if (node == null) {
            return;
        }
        if (node.getNodeType() == 5) {
            this.phase = PHASE_CHECK_STAGE;
            this.currentFloorId = 99;
            return;
        }

        int eventType = pickEventTypeForNode(node);
        if (eventType == 4) {
            this.phase = PHASE_EVENT_DISPUTE;
            this.eventBattleId = 0;
            rollEventChoices();
            return;
        }
        if (eventType == 5 || eventType == 6) {
            this.phase = eventType == 5 ? PHASE_STORY_PERSONALITY : PHASE_STORY_COSTLY;
            rollStoryEvent(node.getStage(), eventType);
            return;
        }

        this.phase = PHASE_FIGHT;
        if (!this.shopLocked) {
            rollShop();
        }
    }

    private int pickEventTypeForNode(SoldierNodePlanDef node) {
        if (node.getNodeType() == 4) {
            return 1;
        }
        var types = new ArrayList<Integer>();
        for (var ep : GameData.getSoldierEventPlanDataTable()) {
            if (ep.getEventGroupId() == node.getEventGroupId()) {
                types.add(ep.getEventType());
            }
        }
        if (types.isEmpty()) {
            return switch (node.getNodeType()) {
                case 1 -> 5;
                case 5 -> 7;
                default -> 3;
            };
        }
        return Utils.randomElement(types);
    }

    private void rollStoryEvent(int currentStage, int storyType) {
        this.storyId = 0;
        this.storySelectedIndex = 0;
        this.storyDicePoint = 0;
        storyOptionIds().clear();
        storyPreviewCards().clear();
        storyPreviewChess().clear();

        int prefix = storyType == 6 ? 2 : 1;
        int minGroup = prefix * 1000 + currentStage * 100;
        int maxGroup = minGroup + 99;

        var validStories = new ArrayList<SoldierEventPersonalityStoryDef>();
        var validOptionsMap = new HashMap<Integer, List<SoldierStoryPersonalityOptionDef>>();
        for (var s : GameData.getSoldierEventPersonalityStoryDataTable()) {
            if (s.getGroupId() < minGroup || s.getGroupId() > maxGroup) {
                continue;
            }
            var options = findMatchingOptions(s.getOptionRule());
            if (!options.isEmpty()) {
                validStories.add(s);
                validOptionsMap.put(s.getId(), options);
            }
        }
        if (validStories.isEmpty()) {
            this.phase = PHASE_FIGHT;
            if (!this.shopLocked) rollShop();
            return;
        }

        var chosen = Utils.randomElement(validStories);
        this.storyId = chosen.getId();
        var bySubgroup = new TreeMap<Integer, List<SoldierStoryPersonalityOptionDef>>();
        for (var opt : validOptionsMap.get(chosen.getId())) {
            bySubgroup.computeIfAbsent(opt.getSubgroup(), k -> new ArrayList<>()).add(opt);
        }
        // Client SoldierEventCtrl only renders 3 option slots (1..3); sending 4 crashes RefreshEventOptions.
        var selectedSubgroups = selectStorySubgroups(bySubgroup.keySet(), 3);
        for (int subgroup : selectedSubgroups) {
            var subgroupList = bySubgroup.get(subgroup);
            var unlocked = new ArrayList<SoldierStoryPersonalityOptionDef>();
            for (var opt : subgroupList) {
                if (isOptionUnlocked(opt)) {
                    unlocked.add(opt);
                }
            }
            var pool = unlocked.isEmpty() ? subgroupList : unlocked;
            storyOptionIds().add(Utils.randomElement(pool).getId());
        }
    }

    private static List<Integer> selectStorySubgroups(Set<Integer> available, int maxOptions) {
        if (available.size() <= maxOptions) {
            return new ArrayList<>(available);
        }
        var chosen = new TreeMap<Integer, Boolean>();
        var extra = new ArrayList<Integer>();
        for (int sg : available) {
            if (sg == 0 && chosen.isEmpty()) {
                chosen.put(sg, Boolean.TRUE);
            } else {
                extra.add(sg);
            }
        }
        Collections.shuffle(extra);
        for (int sg : extra) {
            if (chosen.size() >= maxOptions) {
                break;
            }
            chosen.put(sg, Boolean.TRUE);
        }
        return new ArrayList<>(chosen.keySet());
    }

    private List<SoldierStoryPersonalityOptionDef> findMatchingOptions(int optionRule) {
        int rulePrefix = optionRule >= 100000 ? optionRule / 10 : optionRule;
        var options = new ArrayList<SoldierStoryPersonalityOptionDef>();
        for (var opt : GameData.getSoldierStoryPersonalityOptionDataTable()) {
            if (opt.getRulePrefix() == rulePrefix) {
                options.add(opt);
            }
        }
        options.sort(Comparator.comparingInt(SoldierStoryPersonalityOptionDef::getId));
        return options;
    }

    private boolean isOptionUnlocked(SoldierStoryPersonalityOptionDef opt) {
        if (opt.getUnlockCond1() == 0) {
            return true;
        }
        var param = opt.getUnlockParam1();
        if (opt.getUnlockCond1() == 3 && param != null && !param.isEmpty()) {
            return this.bossAbilityId == param.getFirst();
        }
        if (opt.getUnlockCond1() == 4 && param != null && !param.isEmpty()) {
            return countBoardDeployed() >= param.getFirst() || chessList().size() >= param.getFirst();
        }
        return true;
    }

    private boolean isOptionSelectable(SoldierStoryPersonalityOptionDef opt) {
        if (opt == null || opt.getOptionalCond() == 0) {
            return true;
        }
        var param = opt.getOptionalParam();
        if (param == null || param.isEmpty()) {
            return true;
        }
        return switch (opt.getOptionalCond()) {
            case 1 -> param.size() >= 2 && bossStatForDimension(param.get(0)) >= param.get(1);
            case 5 -> this.coin >= param.getFirst();
            case 6 -> this.hp > param.getFirst();
            default -> true;
        };
    }

    private void applyStoryResult(int optionId, int resultId,
            java.util.function.Consumer<PublicSoldier.SoldierStoryEventEffectReward> out,
            PlayerChangeInfo change, SoldierInteractResp rsp) {
        var resultDef = GameData.getSoldierStoryResultDataTable().get(resultId);
        if (resultDef == null || resultDef.getRewardId() <= 0) {
            return;
        }
        var rewardDef = GameData.getSoldierStoryRewardDataTable().get(resultDef.getRewardId());
        if (rewardDef == null) {
            return;
        }
        if (rewardDef.getType1() > 0) {
            out.accept(applyStoryRewardEffect(optionId, rewardDef.getType1(), rewardDef.getParam1(), change, rsp));
        }
        if (rewardDef.getType2() > 0) {
            out.accept(applyStoryRewardEffect(optionId, rewardDef.getType2(), rewardDef.getParam2(), change, rsp));
        }
    }

    private PublicSoldier.SoldierStoryEventEffectReward applyStoryRewardEffect(
            int optionId, int type, List<Integer> param, PlayerChangeInfo change, SoldierInteractResp rsp) {
        var reward = PublicSoldier.SoldierStoryEventEffectReward.newInstance();
        switch (type) {
            case 1 -> {
                int qty = paramAt(param, 1, 2);
                this.coin += qty;
                this.totalCoinEarned += qty;
                reward.setRewardType(PublicSoldier.SoldierEventEffectRewardType.GainCoin);
                reward.getMutableRes().addResRewards(PublicSoldier.SoldierRes.newInstance().setTid(COIN_TID).setQty(qty));
                change.add(PublicSoldier.SoldierRes.newInstance().setTid(COIN_TID).setQty(qty));
            }
            case 10 -> {
                int qty = paramAt(param, 1, 5);
                int loss = Math.min(this.coin, qty);
                this.coin -= loss;
                reward.setRewardType(PublicSoldier.SoldierEventEffectRewardType.LossCoin);
                reward.getMutableRes().addResRewards(PublicSoldier.SoldierRes.newInstance().setTid(COIN_TID).setQty(-loss));
                change.add(PublicSoldier.SoldierRes.newInstance().setTid(COIN_TID).setQty(-loss));
            }
            case 11 -> {
                int loss = this.coin;
                this.coin = 0;
                reward.setRewardType(PublicSoldier.SoldierEventEffectRewardType.LoseAllCoin);
                if (loss > 0) {
                    reward.getMutableRes().addResRewards(PublicSoldier.SoldierRes.newInstance().setTid(COIN_TID).setQty(-loss));
                    change.add(PublicSoldier.SoldierRes.newInstance().setTid(COIN_TID).setQty(-loss));
                }
            }
            case 7, 9 -> {
                int hpDelta = paramAt(param, 0, 10);
                if (type == 9) hpDelta = -hpDelta;
                this.hp = Math.max(1, Math.min(MAX_HP, this.hp + hpDelta));
                reward.setRewardType(PublicSoldier.SoldierEventEffectRewardType.Hp).setHp(hpDelta);
            }
            case 2 -> {
                int count = paramAt(param, 0, 1);
                int star = paramAt(param, 1, 1);
                int costFilter = paramAt(param, 2, 0);
                reward.setRewardType(PublicSoldier.SoldierEventEffectRewardType.Chess);
                var tids = getOrRollStoryChessTids(optionId, Math.min(count, 3), costFilter);
                for (var c : grantCharactersByTids(tids, star, change)) {
                    reward.getMutableChessList().addChessList(toChessProto(c));
                }
            }
            case 6 -> {
                int count = paramAt(param, 0, 1);
                int tier = paramAt(param, 1, 1);
                reward.setRewardType(PublicSoldier.SoldierEventEffectRewardType.Card);
                var pickedIds = getOrRollStoryCardIds(optionId, Math.max(1, count), tier);
                for (int cardId : pickedIds) {
                    var cardDef = GameData.getSoldierStrategyCardDataTable().get(cardId);
                    if (cardDef == null) continue;
                    strategyCards().add(cardId);
                    addBuffToChange(change, PublicSoldier.SoldierBuffType.CardBuff, cardId, cardDef.getCardEffectId());
                    applyStrategyCardImmediate(cardDef, change, rsp);
                    reward.getMutableCardIds().addValues(cardId);
                    reward.getMutableCardEffectIds().addValues(cardDef.getCardEffectId());
                }
            }
            default -> reward.setRewardType(PublicSoldier.SoldierEventEffectRewardType.Next);
        }
        return reward;
    }

    private static int paramAt(List<Integer> param, int index, int fallback) {
        return param != null && index >= 0 && index < param.size() ? param.get(index) : fallback;
    }

    private void populateRewardShows(PublicSoldier.SoldierStoryEventOption optionProto,
            SoldierStoryPersonalityOptionDef optDef) {
        var resultDef = GameData.getSoldierStoryResultDataTable().get(optDef.getResultId());
        if (resultDef == null || resultDef.getRewardId() <= 0) {
            return;
        }
        var rewardDef = GameData.getSoldierStoryRewardDataTable().get(resultDef.getRewardId());
        if (rewardDef == null) {
            return;
        }
        if (rewardDef.getType1() > 0) {
            optionProto.addRewards(buildRewardShow(optDef.getId(), rewardDef.getType1(), rewardDef.getParam1()));
        }
        if (rewardDef.getType2() > 0) {
            optionProto.addRewards(buildRewardShow(optDef.getId(), rewardDef.getType2(), rewardDef.getParam2()));
        }
    }

    private PublicSoldier.SoldierStoryEventRewardShow buildRewardShow(int optionId, int type, List<Integer> param) {
        var show = PublicSoldier.SoldierStoryEventRewardShow.newInstance();
        switch (type) {
            case 1 -> show.setRewardType(PublicSoldier.SoldierEventEffectRewardType.GainCoin)
                    .getMutableResource()
                    .addResRewards(PublicSoldier.SoldierRes.newInstance()
                            .setTid(COIN_TID).setQty(paramAt(param, 1, 2)));
            case 10 -> show.setRewardType(PublicSoldier.SoldierEventEffectRewardType.LossCoin)
                    .getMutableResource()
                    .addResRewards(PublicSoldier.SoldierRes.newInstance()
                            .setTid(COIN_TID).setQty(-paramAt(param, 1, 5)));
            case 11 -> show.setRewardType(PublicSoldier.SoldierEventEffectRewardType.LoseAllCoin)
                    .setNil(Public.Nil.newInstance());
            case 7, 9 -> {
                int hpDelta = paramAt(param, 0, 10);
                if (type == 9) hpDelta = -hpDelta;
                show.setRewardType(PublicSoldier.SoldierEventEffectRewardType.Hp).setHp(hpDelta);
            }
            case 2 -> {
                int count = paramAt(param, 0, 1);
                int star = paramAt(param, 1, 1);
                int costFilter = paramAt(param, 2, 0);
                show.setRewardType(PublicSoldier.SoldierEventEffectRewardType.Chess);
                var chess = show.getMutableChess();
                for (int tid : getOrRollStoryChessTids(optionId, Math.min(count, 3), costFilter)) {
                    chess.addChessRewards(PublicSoldier.SoldierEventChessReward.newInstance()
                            .setTid(tid).setStar(star).setQty(1));
                }
            }
            case 6 -> {
                int count = paramAt(param, 0, 1);
                int tier = paramAt(param, 1, 1);
                show.setRewardType(PublicSoldier.SoldierEventEffectRewardType.Card);
                var ids = show.getMutableIds();
                for (int cardId : getOrRollStoryCardIds(optionId, Math.max(1, count), tier)) {
                    ids.addValues(cardId);
                }
            }
            default -> show.setRewardType(PublicSoldier.SoldierEventEffectRewardType.Next)
                    .setNil(Public.Nil.newInstance());
        }
        return show;
    }

    private List<Integer> getOrRollStoryCardIds(int optionId, int count, int tier) {
        String key = String.valueOf(optionId);
        List<Integer> existing = storyPreviewCards().get(key);
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }
        var season = GameData.getSoldierSeasonDataTable().get(this.seasonId);
        int groupId = season != null ? season.getStrategyGroupId() : 101;
        int targetRarity = switch (tier) {
            case 3 -> 3;
            case 2 -> 4;
            default -> 5;
        };
        var preferred = new ArrayList<Integer>();
        var unowned = new ArrayList<Integer>();
        var all = new ArrayList<Integer>();
        for (var card : GameData.getSoldierStrategyCardDataTable()) {
            if (card.getGroupId() != groupId) continue;
            if (card.getGradeLevelCond() != 0 && this.gradeChallengeId < card.getGradeLevelCond()) continue;
            all.add(card.getId());
            if (!strategyCards().contains(card.getId())) {
                unowned.add(card.getId());
                if (card.getRarity() == targetRarity) {
                    preferred.add(card.getId());
                }
            }
        }
        var source = !preferred.isEmpty() ? preferred : unowned;
        if (source.isEmpty()) {
            source = all;
        }
        Collections.shuffle(source);
        var picked = new ArrayList<Integer>();
        for (int i = 0; i < Math.min(count, source.size()); i++) {
            picked.add(source.get(i));
        }
        storyPreviewCards().put(key, picked);
        return picked;
    }

    private List<Integer> getOrRollStoryChessTids(int optionId, int count, int costFilter) {
        String key = String.valueOf(optionId);
        List<Integer> existing = storyPreviewChess().get(key);
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }
        var picked = pickRandomChessTids(count, costFilter);
        storyPreviewChess().put(key, picked);
        return picked;
    }

    private List<Integer> pickRandomChessTids(int count, int costFilter) {
        var season = GameData.getSoldierSeasonDataTable().get(this.seasonId);
        if (season == null || count <= 0) return List.of();
        int groupId = season.getChessGroupId();
        var pool = new ArrayList<Integer>();
        for (var c : GameData.getSoldierCharacterDataTable()) {
            if (c.getGroupId() == groupId && c.isBoardChess() && (costFilter <= 0 || c.getCost() == costFilter)) {
                pool.add(c.getId());
            }
        }
        if (pool.isEmpty() && costFilter > 0) {
            for (var c : GameData.getSoldierCharacterDataTable()) {
                if (c.getGroupId() == groupId && c.isBoardChess()) {
                    pool.add(c.getId());
                }
            }
        }
        var picked = new ArrayList<Integer>();
        if (!pool.isEmpty()) {
            for (int i = 0; i < count; i++) {
                picked.add(Utils.randomElement(pool));
            }
        }
        return picked;
    }

    private List<ChessEntry> grantCharactersByTids(List<Integer> tids, int star, PlayerChangeInfo change) {
        if (tids == null || tids.isEmpty()) return List.of();
        var granted = new ArrayList<ChessEntry>();
        var transforms = new ArrayList<PublicSoldier.SoldierTransform>();
        int targetStar = Math.max(1, Math.min(3, star));
        for (int tid : tids) {
            int slot = allocateBenchSlot();
            if (slot == 0 && (targetStar != 1 || !canMergeWith(tid))) {
                break;
            }
            var entry = new ChessEntry();
            entry.uid = this.nextChessUid++;
            entry.tid = tid;
            entry.star = targetStar;
            entry.slot = slot;
            transforms.addAll(appendAndMergeChess(entry));
            granted.add(entry);
        }
        if (!transforms.isEmpty()) {
            addChessChange(change, transforms);
        }
        return granted;
    }

    private void rollEventChoices() {
        eventChoices().clear();
        var node = currentNode();
        if (node == null) return;

        var candidates = new ArrayList<SoldierEventBattlePoolDef>();
        int groupPrefix = node.getNodeGroupId() == 103 ? 102 : node.getNodeGroupId();
        for (int stagePool = node.getStage(); stagePool >= 1 && candidates.isEmpty(); stagePool--) {
            int poolId = groupPrefix * 100000 + 40100 + stagePool;
            for (var event : GameData.getSoldierEventBattlePoolDataTable()) {
                if (event.getPoolId() == poolId) {
                    candidates.add(event);
                }
            }
        }
        while (eventChoices().size() < 3 && !candidates.isEmpty()) {
            var weighted = new WeightedList<SoldierEventBattlePoolDef>();
            for (var event : candidates) {
                weighted.add(Math.max(1, event.getWeight()), event);
            }
            var selected = weighted.next();
            eventChoices().add(selected.getId());
            candidates.remove(selected);
        }
        eventChoices().sort((a, b) -> {
            var left = GameData.getSoldierEventBattlePoolDataTable().get(a);
            var right = GameData.getSoldierEventBattlePoolDataTable().get(b);
            return Integer.compare(left.getLevelName(), right.getLevelName());
        });
    }

    private void rollStrategyCardChoices() {
        cardChoices().clear();
        var season = GameData.getSoldierSeasonDataTable().get(this.seasonId);
        if (season == null) return;

        var pool = new ArrayList<Integer>();
        for (var card : GameData.getSoldierStrategyCardDataTable()) {
            if (card.getGroupId() == season.getStrategyGroupId()
                    && !strategyCards().contains(card.getId())
                    && (card.getGradeLevelCond() == 0 || this.gradeChallengeId >= card.getGradeLevelCond())) {
                pool.add(card.getId());
            }
        }
        Collections.shuffle(pool);
        for (int i = 0; i < Math.min(3, pool.size()); i++) {
            cardChoices().add(pool.get(i));
        }
    }

    private void applyStrategyCardImmediate(SoldierStrategyCardDef card, PlayerChangeInfo change,
            SoldierInteractResp rsp) {
        switch (card.getId()) {
            case 2010101 -> addCoin(8, change);
            case 2010102 -> {
                this.shopExp += 8;
                updateShopLevel();
            }
            case 2010103 -> this.freeRefreshes += 5;
            case 2010104 -> {
                this.hp = Math.min(MAX_HP, this.hp + 10);
                addCoin(5, change);
            }
            case 2010206 -> addCoin(20, change);
            case 2010208 -> {
                this.hp = 20;
                addCoin(30, change);
            }
            case 2010303 -> {
                this.hp = 1;
                addCoin(50, change);
            }
            case 2010305 -> {
                int delta = 20 - this.coin;
                this.interestCapBonus += 1;
                if (delta != 0) addCoin(delta, change);
            }
            case 2010306 -> {
                this.hp = 1;
                if (this.coin > 0) addCoin(this.coin, change);
            }
            case 2010307 -> {
                addCoin(5, change);
                int prevCoin = this.coin;
                int newCoin = this.hp;
                this.hp = Math.max(1, Math.min(MAX_HP, prevCoin));
                if (newCoin != prevCoin) {
                    addCoin(newCoin - prevCoin, change);
                }
            }
            case 2010311 -> this.freeRefreshes += 3;
            default -> {
                if (card.getCharacterShow() != null && !card.getCharacterShow().isEmpty()) {
                    grantSpecificCharacter(card.getCharacterShow().getFirst(), 1, change, rsp);
                }
            }
        }
    }

    private void addCoin(int delta, PlayerChangeInfo change) {
        this.coin = Math.max(0, this.coin + delta);
        if (delta > 0) {
            this.totalCoinEarned += delta;
        }
        change.add(PublicSoldier.SoldierRes.newInstance().setTid(COIN_TID).setQty(delta));
    }

    private List<ChessEntry> grantRandomCharacters(int count, int costFilter, int star, PlayerChangeInfo change) {
        return grantCharactersByTids(pickRandomChessTids(count, costFilter), star, change);
    }

    private void grantSpecificCharacter(int tid, int star, PlayerChangeInfo change, SoldierInteractResp rsp) {
        var character = GameData.getSoldierCharacterDataTable().get(tid);
        if (character == null || !character.isBoardChess()) {
            return;
        }
        int slot = allocateBenchSlot();
        if (slot == 0 && (star != 1 || !canMergeWith(tid))) {
            return;
        }
        var entry = new ChessEntry();
        entry.uid = this.nextChessUid++;
        entry.tid = tid;
        entry.star = Math.max(1, Math.min(3, star));
        entry.slot = slot;

        var transforms = appendAndMergeChess(entry);
        addChessChange(change, transforms);
        if (rsp != null) {
            rsp.addEffects(PublicSoldier.SoldierEffect.newInstance()
                    .setShowChess(PublicSoldier.SoldierEffectAddChess.newInstance().addChess(toChessProto(entry))));
        }
    }

    /**
     * Adds an obtained chess entry and separates any resulting triple-merges into ordered
     * SoldierTransform steps so the client applies Obtain before Consume.
     */
    private List<PublicSoldier.SoldierTransform> appendAndMergeChess(ChessEntry entry) {
        chessList().add(entry);
        var transforms = new ArrayList<PublicSoldier.SoldierTransform>();
        ChessEntry unobtainedTransient = null;
        if (entry.slot > 0) {
            transforms.add(PublicSoldier.SoldierTransform.newInstance().addObtain(toChessProto(entry)));
        } else {
            unobtainedTransient = entry;
        }
        mergeTriples(entry, transforms, unobtainedTransient);
        return transforms;
    }

    private void mergeTriples(ChessEntry triggerEntry, List<PublicSoldier.SoldierTransform> transforms,
            ChessEntry unobtainedTransient) {
        SoldierCharacterDef charDef = GameData.getSoldierCharacterDataTable().get(triggerEntry.tid);
        int maxStar = charDef != null && charDef.getMaxStar() > 0 ? charDef.getMaxStar() : 3;

        boolean merged = true;
        while (merged) {
            merged = false;
            for (int star = 1; star < maxStar; star++) {
                var matches = new ArrayList<ChessEntry>();
                for (var entry : chessList()) {
                    if (entry.tid == triggerEntry.tid && entry.star == star) {
                        matches.add(entry);
                    }
                }
                if (matches.size() >= 3) {
                    matches.sort(Comparator.comparingInt(e -> mergeKeepPriority(e, triggerEntry)));
                    ChessEntry kept = matches.get(0);
                    ChessEntry c1 = matches.get(1);
                    ChessEntry c2 = matches.get(2);
                    chessList().remove(c1);
                    chessList().remove(c2);
                    if (kept.slot == 0) {
                        kept.slot = allocateBenchSlot();
                    }
                    kept.star++;

                    var mergeTransform = PublicSoldier.SoldierTransform.newInstance();
                    if (c1 != unobtainedTransient) {
                        mergeTransform.addConsume(toChessProto(c1));
                    }
                    if (c2 != unobtainedTransient) {
                        mergeTransform.addConsume(toChessProto(c2));
                    }
                    mergeTransform.addUpdate(toChessProto(kept));
                    transforms.add(mergeTransform);
                    merged = true;
                    break;
                }
            }
        }
    }

    private static int mergeKeepPriority(ChessEntry candidate, ChessEntry triggerEntry) {
        if (candidate.slot == 0) return Integer.MAX_VALUE;
        if (candidate == triggerEntry) return 1000 + candidate.slot;
        return candidate.slot;
    }

    private void addChessChange(PlayerChangeInfo change, PublicSoldier.SoldierTransform transform) {
        addChessChange(change, List.of(transform));
    }

    private void addChessChange(PlayerChangeInfo change, List<PublicSoldier.SoldierTransform> transforms) {
        if (transforms == null || transforms.isEmpty()) {
            return;
        }
        int baseVersion = this.deployVersion;
        int nextVersion = ++this.deployVersion;
        var chessChange = PublicSoldier.SoldierChessChange.newInstance()
                .setBaseVersion(baseVersion)
                .setNextVersion(nextVersion);
        for (var transform : transforms) {
            chessChange.addTransforms(transform);
        }
        change.add(chessChange);
    }

    private void rollShop() {
        var season = GameData.getSoldierSeasonDataTable().get(this.seasonId);
        var level = GameData.getSoldierShopLevelDataTable().get(this.shopLevel);
        shopGoods().clear();
        if (season == null || level == null) return;

        var rolledCounts = new HashMap<Integer, Integer>();
        for (int i = 0; i < SHOP_SIZE; i++) {
            var byRarity = new HashMap<Integer, WeightedList<SoldierShopPoolDef>>();
            for (var goods : GameData.getSoldierShopPoolDataTable()) {
                var character = GameData.getSoldierCharacterDataTable().get(goods.getChessCharacterId());
                if (goods.getGroupId() != season.getShopPoolGroupId() || character == null || !character.isBoardChess()) {
                    continue;
                }
                if (copiesUsedForCharacter(goods.getChessCharacterId(), rolledCounts) >= POOL_COPIES_PER_UNIT) {
                    continue;
                }
                byRarity.computeIfAbsent(goods.getRarity(), k -> new WeightedList<>())
                        .add(Math.max(1, goods.getWeight()), goods);
            }
            if (byRarity.isEmpty()) break;

            var rarityChoices = new WeightedList<Integer>();
            for (int r : byRarity.keySet()) {
                int w = level.rarityWeight(r);
                if (w > 0) {
                    rarityChoices.add(w, r);
                }
            }
            if (rarityChoices.size() == 0) {
                for (int r : byRarity.keySet()) {
                    rarityChoices.add(1, r);
                }
            }

            int pickedRarity = rarityChoices.next();
            var selected = byRarity.get(pickedRarity).next();
            int tid = selected.getChessCharacterId();
            shopGoods().add(tid);
            rolledCounts.merge(tid, 1, Integer::sum);
        }
    }

    private SoldierShopPoolDef shopPool(int characterId) {
        var season = GameData.getSoldierSeasonDataTable().get(this.seasonId);
        if (season == null) return null;
        for (var goods : GameData.getSoldierShopPoolDataTable()) {
            if (goods.getGroupId() == season.getShopPoolGroupId()
                    && goods.getChessCharacterId() == characterId) {
                return goods;
            }
        }
        return null;
    }

    private void updateShopLevel() {
        while (this.shopLevel < MAX_SHOP_LEVEL) {
            var def = GameData.getSoldierShopLevelDataTable().get(this.shopLevel);
            if (def == null || def.getExp() <= 0 || this.shopExp < def.getExp()) break;
            this.shopExp -= def.getExp();
            this.shopLevel++;
            if (hasTechNode(10013)) {
                this.freeRefreshes += 3;
            }
            if (hasStrategyCard(2010311)) {
                this.freeRefreshes += 3;
            }
        }
    }

    private int rollFloorId() {
        var node = currentNode();
        if (node == null) return 0;

        int targetGroup = switch (node.getNodeType()) {
            case 4 -> 303;
            case 3 -> node.getStage() * 100 + 3;
            default -> node.getStage() * 100 + (node.getIndex() % 7 >= 4 ? 2 : 1);
        };
        int exact = rollFloorForGroup(targetGroup);
        if (exact != 0) return exact;

        var fallback = new ArrayList<Integer>();
        for (SoldierFloorDef floor : GameData.getSoldierFloorDataTable()) {
            if (!floor.isIsDpsCheckStage()) {
                fallback.add(floor.getId());
            }
        }
        return fallback.isEmpty() ? 0 : Utils.randomElement(fallback);
    }

    private int rollFloorForGroup(int groupId) {
        var candidates = new ArrayList<Integer>();
        for (var floor : GameData.getSoldierFloorDataTable()) {
            if (floor.getDefault() == groupId) {
                candidates.add(floor.getId());
            }
        }
        return candidates.isEmpty() ? 0 : Utils.randomElement(candidates);
    }

    private int countBoardDeployed() {
        int count = 0;
        for (var c : chessList()) {
            if (c.slot >= 1 && c.slot <= BOARD_SLOT_MAX) {
                count++;
            }
        }
        return count;
    }

    private boolean hasOtherBoardTid(int tid, ChessEntry replacedEntry) {
        for (var c : chessList()) {
            if (c.slot >= 1 && c.slot <= BOARD_SLOT_MAX && c != replacedEntry && c.tid == tid) {
                return true;
            }
        }
        return false;
    }

    private int maxBoardDeployCount() {
        var level = GameData.getSoldierShopLevelDataTable().get(this.shopLevel);
        return level != null && level.getCount() > 0 ? level.getCount() : this.shopLevel;
    }

    private int maxInterest() {
        int bonus = this.interestCapBonus
                + (this.bossAbilityId == 104 ? 2 : 0)
                + (hasTechNode(10002) ? 3 : 0);
        return BASE_MAX_INTEREST + bonus;
    }

    private boolean hasTechNode(int nodeTableId) {
        return this.manager != null && this.manager.hasTechNode(this.seasonId, nodeTableId);
    }

    private boolean hasStrategyCard(int cardId) {
        return strategyCards().contains(cardId);
    }

    private int bossStatForDimension(int dimension) {
        var boss = GameData.getSoldierBossTemplateDataTable().get(this.bossAbilityId);
        if (boss == null) return 4;
        return switch (dimension) {
            case 1 -> boss.getPersonality_Intuition();
            case 2 -> boss.getPersonality_Analysis();
            default -> boss.getPersonality_Chaos();
        };
    }

    private int allocateBenchSlot() {
        for (int slot = BENCH_SLOT_START; slot < BENCH_SLOT_START + BENCH_SLOT_COUNT; slot++) {
            if (findChessBySlot(slot) == null) {
                return slot;
            }
        }
        return 0;
    }

    private boolean canMergeWith(int tid) {
        int count = 0;
        for (var entry : chessList()) {
            if (entry.tid == tid && entry.star == 1) {
                count++;
                if (count >= 2) return true;
            }
        }
        return false;
    }

    private int copiesUsedForCharacter(int tid, Map<Integer, Integer> rolledCounts) {
        int used = rolledCounts.getOrDefault(tid, 0);
        for (var entry : chessList()) {
            if (entry.tid == tid) {
                used += switch (entry.star) {
                    case 1 -> 1;
                    case 2 -> 3;
                    default -> 9;
                };
            }
        }
        return used;
    }

    private ChessEntry findChessByUid(int chessUid) {
        for (var c : chessList()) {
            if (c.uid == chessUid) return c;
        }
        return null;
    }

    private ChessEntry findChessBySlot(int slot) {
        for (var c : chessList()) {
            if (c.slot == slot) return c;
        }
        return null;
    }

    public List<SoldierNodePlanDef> nodesForCurrentChallenge() {
        var challenge = GameData.getSoldierGradeChallengeDataTable().get(this.gradeChallengeId);
        return challenge == null ? List.of() : nodes(challenge.getNodeGroupId());
    }

    public static List<SoldierNodePlanDef> nodes(int groupId) {
        var result = new ArrayList<SoldierNodePlanDef>();
        for (var node : GameData.getSoldierNodePlanDataTable()) {
            if (node.getNodeGroupId() == groupId) {
                result.add(node);
            }
        }
        result.sort(Comparator.comparingInt(SoldierNodePlanDef::getIndex));
        return result;
    }

    public SoldierNodePlanDef currentNode() {
        if (!inProgress()) return null;
        var nodes = nodesForCurrentChallenge();
        return this.nodeIndex > 0 && this.nodeIndex <= nodes.size() ? nodes.get(this.nodeIndex - 1) : null;
    }

    private int nextCaseId() {
        this.lastCaseId = this.lastCaseId == Integer.MAX_VALUE ? 1 : this.lastCaseId + 1;
        if (this.lastCaseId == 0) this.lastCaseId = 1;
        return this.lastCaseId;
    }

    private static int starterWeightForCost(int cost) {
        return switch (cost) {
            case 1, 2 -> 180;
            case 3 -> 90;
            default -> 0;
        };
    }

    private static int streakReward(int streak) {
        if (streak >= 5) return 3;
        if (streak >= 2) return 2;
        return 1;
    }

    private static boolean validClientData(String value, int version) {
        return value != null && value.length() <= MAX_CLIENT_DATA && version >= 0;
    }

    private static PlayerChangeInfo soldierResChange(int delta) {
        var change = new PlayerChangeInfo();
        change.add(PublicSoldier.SoldierRes.newInstance().setTid(COIN_TID).setQty(delta));
        return change;
    }

    public boolean setPartnerTrace(int partnerType, boolean trace) {
        if (!inProgress() || partnerType <= 0 || partnerType > 50) {
            return false;
        }
        if (trace) {
            tracePartnerTypes().add(partnerType);
        } else {
            tracePartnerTypes().remove(partnerType);
        }
        return true;
    }

    private void addBuffToChange(PlayerChangeInfo change, PublicSoldier.SoldierBuffType type,
            int bindId, int effectGroupId) {
        var buff = buildBuffProto(type, PublicSoldier.SoldierBuffAction.Add, bindId, effectGroupId);
        if (buff != null) {
            change.add(buff);
        }
    }

    private PublicSoldier.SoldierBuff buildBuffProto(PublicSoldier.SoldierBuffType type,
            PublicSoldier.SoldierBuffAction action, int bindId, int effectGroupId) {
        var effectDef = GameData.getSoldierCardEffectDataTable().get(effectGroupId);
        if (effectDef == null || effectDef.getBuffIds() == null || effectDef.getBuffIds().isEmpty()) {
            return null;
        }
        var proto = PublicSoldier.SoldierBuff.newInstance()
                .setBindId(bindId)
                .setEffectGroup(effectGroupId)
                .setType(type)
                .setAction(action);
        for (int buffId : effectDef.getBuffIds()) {
            proto.addBuffIds(buffId);
        }
        return proto;
    }

    public PublicSoldier.SoldierInfo toInfoProto() {
        var info = PublicSoldier.SoldierInfo.newInstance();
        var meta = info.getMutableMeta()
                .setId(this.seasonId)
                .setHp(this.hp)
                .setWinningStreak(this.winningStreak)
                .setLosingStreak(this.losingStreak)
                .setShopLevel(this.shopLevel)
                .setShopExp(this.shopExp)
                .setDataLen(this.clientData == null ? 0 : this.clientData.length())
                .setDataVersion(this.dataVersion)
                .setClientData(this.clientData == null ? "" : this.clientData);
        for (int type : tracePartnerTypes()) {
            meta.addTracePartnerTypes(type);
        }

        info.getMutableNode().setData(nodeData());
        info.addCases(buildMainCaseProto());
        if (this.phase == PHASE_FIGHT && this.shopCaseId > 0) {
            info.addCases(PublicSoldier.SoldierCase.newInstance().setId(this.shopCaseId).setShopCase(shopData()));
        }
        if (this.deployOptCaseId > 0) {
            info.addCases(PublicSoldier.SoldierCase.newInstance().setId(this.deployOptCaseId)
                    .setDeployOptCase(PublicSoldier.DeploySyncCaseData.newInstance()));
        }
        if (this.clientEventCaseId > 0) {
            info.addCases(PublicSoldier.SoldierCase.newInstance().setId(this.clientEventCaseId)
                    .setClientEventCase(PublicSoldier.ClientEventCaseData.newInstance()));
        }

        var bag = info.getMutableBag();
        bag.setDeploy(buildDeployProto());
        bag.addRes(PublicSoldier.SoldierRes.newInstance().setTid(COIN_TID).setQty(this.coin));

        if (this.bossAbilityId > 0) {
            var bossBuff = buildBuffProto(PublicSoldier.SoldierBuffType.AbilityBuff,
                    PublicSoldier.SoldierBuffAction.Sync, this.bossAbilityId, 4000 + this.bossAbilityId);
            if (bossBuff != null) bag.addBuffs(bossBuff);
        }
        for (var nodeDef : GameData.getSoldierTechNodeDataTable()) {
            if (nodeDef.getSeason() == this.seasonId && hasTechNode(nodeDef.getId())) {
                var techBuff = buildBuffProto(PublicSoldier.SoldierBuffType.TechNodeBuff,
                        PublicSoldier.SoldierBuffAction.Sync, nodeDef.getId(), 100000 + nodeDef.getId());
                if (techBuff != null) bag.addBuffs(techBuff);
            }
        }
        for (int cardId : strategyCards()) {
            var cardDef = GameData.getSoldierStrategyCardDataTable().get(cardId);
            if (cardDef != null) {
                var cardBuff = buildBuffProto(PublicSoldier.SoldierBuffType.CardBuff,
                        PublicSoldier.SoldierBuffAction.Sync, cardId, cardDef.getCardEffectId());
                if (cardBuff != null) bag.addBuffs(cardBuff);
            }
        }
        return info;
    }

    private PublicSoldier.SoldierDeploy buildDeployProto() {
        var deploy = PublicSoldier.SoldierDeploy.newInstance().setVersion(this.deployVersion);
        for (var entry : chessList()) {
            deploy.addChess(toChessProto(entry));
        }
        return deploy;
    }

    private PublicSoldier.SoldierNodeData nodeData() {
        var node = currentNode();
        if (node == null) return PublicSoldier.SoldierNodeData.newInstance();
        return PublicSoldier.SoldierNodeData.newInstance()
                .setStageId(node.getStage())
                .setNodeId(node.getId())
                .setType(node.getNodeType());
    }

    private PublicSoldier.SoldierSyncData syncData() {
        return PublicSoldier.SoldierSyncData.newInstance()
                .setHp(this.hp)
                .setLevel(this.shopLevel)
                .setExp(this.shopExp)
                .setWinningStreak(this.winningStreak)
                .setLosingStreak(this.losingStreak);
    }

    private PublicSoldier.ShopCaseData shopData() {
        var proto = PublicSoldier.ShopCaseData.newInstance()
                .setLevel(this.shopLevel)
                .setExp(this.shopExp)
                .setLock(this.shopLocked)
                .setFree(this.freeRefreshes)
                .setReRollPrice(SHOP_REFRESH_COST)
                .setPurchaseExpPrice(SHOP_EXP_COST)
                .addAllRarityUp(0, 0, 0, 0, 0);
        for (int id : shopGoods()) {
            var goods = id == 0 ? null : shopPool(id);
            proto.addGoods(PublicSoldier.SoldierGoods.newInstance()
                    .setId(id)
                    .setStar(id == 0 ? 0 : 1)
                    .setPrice(goods == null ? 0 : goods.getCost()));
        }
        while (proto.getGoods().length() < SHOP_SIZE) {
            proto.addGoods(PublicSoldier.SoldierGoods.newInstance().setId(0).setStar(0).setPrice(0));
        }
        return proto;
    }

    private PublicSoldier.SelectCardData selectCardData() {
        var data = PublicSoldier.SelectCardData.newInstance().setReRoll(this.cardRerolls);
        for (int id : cardChoices()) {
            data.addIds(id);
        }
        return data;
    }

    private PublicSoldier.SoldierCase buildMainCaseProto() {
        var proto = PublicSoldier.SoldierCase.newInstance().setId(this.mainCaseId);
        switch (this.phase) {
            case PHASE_BOSS_ABILITY -> proto.getMutableSelectBossAbilityCase();
            case PHASE_FIGHT -> proto.getMutableFightCase();
            case PHASE_BATTLE -> proto.getMutableBattleCase().setFloorId(this.currentFloorId);
            case PHASE_EVENT_DISPUTE -> proto.getMutableEventSettlementOfDispute()
                    .addAllIds(eventChoices().stream().mapToInt(Integer::intValue).toArray());
            case PHASE_EVENT_DISPUTE_BATTLE -> proto.getMutableEventSettlementOfDisputeBattle()
                    .setBattleId(this.eventBattleId)
                    .setFloorId(this.currentFloorId);
            case PHASE_STORY_PERSONALITY -> {
                var story = proto.getMutableStoryEventOfPersonalityCase()
                        .setPersonalityStoryId(this.storyId)
                        .setIndex(this.storySelectedIndex)
                        .setDicePoint(this.storyDicePoint);
                populateStoryOptions(story.getMutableOptions());
            }
            case PHASE_STORY_COSTLY -> {
                var story = proto.getMutableStoryEventOfCostlyCase()
                        .setPersonalityStoryId(this.storyId);
                populateStoryOptions(story.getMutableOptions());
            }
            case PHASE_CHECK_STAGE -> proto.getMutableCheckStageCase().setFloorId(this.currentFloorId);
            case PHASE_SELECT_CARD -> proto.setSelectCard(selectCardData());
            default -> proto.getMutableFightCase();
        }
        return proto;
    }

    private void populateStoryOptions(PublicSoldier.SoldierStoryEventOptions opts) {
        while (storyOptionIds().size() > 3) {
            storyOptionIds().removeLast();
        }
        for (int oid : storyOptionIds()) {
            var optDef = GameData.getSoldierStoryPersonalityOptionDataTable().get(oid);
            var optProto = PublicSoldier.SoldierStoryEventOption.newInstance()
                    .setPersonalityOptionId(oid)
                    .setSelect(isOptionSelectable(optDef));
            if (optDef != null) {
                populateRewardShows(optProto, optDef);
            }
            opts.addOptions(optProto);
        }
    }

    public static PublicSoldier.SoldierChess toChessProto(ChessEntry entry) {
        return PublicSoldier.SoldierChess.newInstance()
                .setUID(entry.uid)
                .setTid(entry.tid)
                .setStar(entry.star)
                .setSlot(entry.slot);
    }

    @Getter
    @Setter
    @Entity(useDiscriminator = false)
    public static class ChessEntry {
        private int uid;
        private int tid;
        private int star;
        private int slot;
    }
}
