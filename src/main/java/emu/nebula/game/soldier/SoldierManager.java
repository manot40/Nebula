package emu.nebula.game.soldier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import emu.nebula.Nebula;
import emu.nebula.data.GameData;
import emu.nebula.data.resources.SoldierGradeChallengeDef;
import emu.nebula.data.resources.SoldierTotalRewardDef;
import emu.nebula.database.GameDatabaseObject;
import emu.nebula.game.inventory.ItemParamMap;
import emu.nebula.game.player.Player;
import emu.nebula.game.player.PlayerChangeInfo;
import emu.nebula.game.player.PlayerManager;
import emu.nebula.proto.Public;
import emu.nebula.proto.Public.NewbieInfo;
import emu.nebula.proto.PublicSoldier;
import emu.nebula.proto.SoldierApply.SoldierApplyResp;
import emu.nebula.proto.SoldierBattleReportFavoriteListOuterClass.SoldierBattleReportFavoriteList;
import emu.nebula.proto.SoldierBattleReportRecentListOuterClass.SoldierBattleReportRecentList;
import emu.nebula.proto.SoldierInteract.SoldierInteractReq;
import emu.nebula.proto.SoldierInteract.SoldierInteractResp;
import emu.nebula.proto.SoldierSeasonRewardReceive.SoldierSeasonRewardReceiveResp;
import emu.nebula.util.Bitset;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity(value = "soldier", useDiscriminator = false)
public class SoldierManager extends PlayerManager implements GameDatabaseObject {
    private static final int MAX_RECENT_REPORTS = 10;
    private static final int MAX_FAVORITE_REPORTS = 10;
    private static final String SOLDIER_VERSION = "v1.0.0";

    @Id
    private int uid;

    private Map<Integer, SeasonState> seasons;
    private SoldierGame activeRun;

    @Deprecated // Morphia only
    public SoldierManager() {
    }

    public SoldierManager(Player player) {
        super(player);
        this.uid = player.getUid();
        this.seasons = new HashMap<>();
        this.save();
    }

    @Override
    public void onLoad() {
        if (this.seasons == null) {
            this.seasons = new HashMap<>();
        }
        if (this.activeRun != null) {
            this.activeRun.postLoad(this);
        }
    }

    private Map<Integer, SeasonState> seasons() {
        if (this.seasons == null) {
            this.seasons = new HashMap<>();
        }
        return this.seasons;
    }

    private static int normalizeSeason(int seasonId) {
        return seasonId > 0 ? seasonId : 1;
    }

    private SeasonState getOrCreateSeason(int seasonId) {
        int normalized = normalizeSeason(seasonId);
        return seasons().computeIfAbsent(normalized, id -> {
            var state = new SeasonState();
            state.seasonId = id;
            state.unlockedTechNodes = new Bitset();
            state.newbies = new HashMap<>();
            state.completedChallenges = new HashSet<>();
            state.recentReports = new ArrayList<>();
            state.favoriteReports = new ArrayList<>();
            var firstChallenge = findFirstChallenge(id);
            if (firstChallenge != null) {
                state.curGradeChallengeId = firstChallenge.getId();
            }
            return state;
        });
    }

    private static SoldierGradeChallengeDef findFirstChallenge(int seasonId) {
        SoldierGradeChallengeDef first = null;
        for (var def : GameData.getSoldierGradeChallengeDataTable()) {
            if (def.getSeason() == seasonId
                    && (first == null || def.getGradeLevel() < first.getGradeLevel())) {
                first = def;
            }
        }
        return first;
    }

    public synchronized boolean hasTechNode(int seasonId, int nodeTableId) {
        var node = GameData.getSoldierTechNodeDataTable().get(nodeTableId);
        if (node == null || node.getNodeId() <= 0) {
            return false;
        }
        var state = seasons().get(seasonId);
        return state != null && state.unlockedTechNodes != null && state.unlockedTechNodes.isSet(node.getNodeId());
    }

    public synchronized PublicSoldier.SoldierSeasonInfo getSeasonInfoProto(int seasonId) {
        int targetSeason = normalizeSeason(seasonId);
        var state = getOrCreateSeason(targetSeason);
        int rewardLv = computeRewardLevel(targetSeason, state.rewardExp);

        var rsp = PublicSoldier.SoldierSeasonInfo.newInstance()
                .setSeasonId(targetSeason)
                .setRewardLv(rewardLv)
                .setRewardExp(state.rewardExp)
                .setCurGradeChallengeId(state.curGradeChallengeId)
                .setCurGradeExp(state.curGradeExp)
                .setCurRewardLv(state.curRewardLv);

        if (state.newbies != null) {
            for (var entry : state.newbies.entrySet()) {
                rsp.addNewbies(NewbieInfo.newInstance()
                        .setGroupId(entry.getKey())
                        .setStepId(entry.getValue()));
            }
        }
        return rsp;
    }

    private static int computeRewardLevel(int seasonId, int totalExp) {
        int level = 0;
        int remaining = totalExp;
        for (var def : getSeasonRewardLevels(seasonId)) {
            if (def.getNeedExp() <= 0 || remaining < def.getNeedExp()) {
                break;
            }
            remaining -= def.getNeedExp();
            level = def.getLevel();
        }
        return level;
    }

    private static List<SoldierTotalRewardDef> getSeasonRewardLevels(int seasonId) {
        var list = new ArrayList<SoldierTotalRewardDef>();
        for (var def : GameData.getSoldierTotalRewardDataTable()) {
            if (def.getSeasonId() == seasonId) {
                list.add(def);
            }
        }
        list.sort(Comparator.comparingInt(SoldierTotalRewardDef::getLevel));
        return list;
    }

    public synchronized SoldierSeasonRewardReceiveResp receiveSeasonReward(int seasonId) {
        int targetSeason = normalizeSeason(seasonId);
        var state = getOrCreateSeason(targetSeason);
        int rewardLv = computeRewardLevel(targetSeason, state.rewardExp);
        if (rewardLv <= state.curRewardLv) {
            return null;
        }

        var rewards = new ItemParamMap();
        for (var def : getSeasonRewardLevels(targetSeason)) {
            if (def.getLevel() > state.curRewardLv && def.getLevel() <= rewardLv
                    && def.getRewardId() > 0 && def.getRewardQty() > 0) {
                rewards.add(def.getRewardId(), def.getRewardQty());
            }
        }
        if (rewards.isEmpty()) {
            return null;
        }

        state.curRewardLv = rewardLv;
        var change = getPlayer().getInventory().addItems(rewards);
        this.save();

        var rsp = SoldierSeasonRewardReceiveResp.newInstance()
                .setCurRewardLv(state.curRewardLv)
                .setChange(change.toProto());
        rewards.toItemTemplateStream().forEach(rsp::addRewards);
        return rsp;
    }

    public synchronized boolean learn(int seasonId, int groupId, int stepId) {
        var state = getOrCreateSeason(seasonId);
        if (state.newbies == null) {
            state.newbies = new HashMap<>();
        }
        state.newbies.put(groupId, stepId);
        this.save();
        return true;
    }

    public synchronized byte[] getTechInfoBytes(int seasonId) {
        var state = getOrCreateSeason(seasonId);
        if (state.unlockedTechNodes == null) {
            state.unlockedTechNodes = new Bitset();
        }
        return state.unlockedTechNodes.toByteArray();
    }

    public synchronized PlayerChangeInfo unlockTechNode(int nodeTableId) {
        var node = GameData.getSoldierTechNodeDataTable().get(nodeTableId);
        if (node == null || node.getNodeId() <= 0) {
            return null;
        }

        var state = getOrCreateSeason(node.getSeason());
        if (state.unlockedTechNodes == null) {
            state.unlockedTechNodes = new Bitset();
        }
        if (state.unlockedTechNodes.isSet(node.getNodeId())) {
            return null;
        }

        if (node.getPreNodes() != null && !node.getPreNodes().isEmpty()) {
            boolean allRequired = node.isPreNodesLogic();
            boolean ok = allRequired;
            for (int preId : node.getPreNodes()) {
                var preDef = GameData.getSoldierTechNodeDataTable().get(preId);
                boolean set = preDef != null && state.unlockedTechNodes.isSet(preDef.getNodeId());
                if (allRequired && !set) {
                    ok = false;
                    break;
                }
                if (!allRequired && set) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return null;
            }
        }

        var change = new PlayerChangeInfo();
        if (node.getItemId1() > 0 && node.getItemQty1() > 0) {
            var inv = getPlayer().getInventory();
            if (!inv.hasItem(node.getItemId1(), node.getItemQty1())) {
                return null;
            }
            inv.removeItem(node.getItemId1(), node.getItemQty1(), change);
        }

        state.unlockedTechNodes.setBit(node.getNodeId());
        this.save();
        return change;
    }

    public synchronized boolean inProgress() {
        return this.activeRun != null && this.activeRun.inProgress();
    }

    public synchronized Public.SoldierState getStateProto() {
        var state = Public.SoldierState.newInstance();
        if (inProgress()) {
            state.setCurGradeChallengeId(this.activeRun.getGradeChallengeId())
                    .setStage(this.activeRun.getStage())
                    .setNodeIndex(this.activeRun.getNodeIndex())
                    .setCurSeasonId(this.activeRun.getSeasonId());
        }
        return state;
    }

    public synchronized PublicSoldier.SoldierInfo getInfoProto() {
        if (!inProgress() || this.activeRun.currentNode() == null) {
            return null;
        }
        return this.activeRun.toInfoProto();
    }

    public synchronized SoldierApplyResp apply(int seasonId, int challengeId) {
        if (inProgress()) {
            return null;
        }
        var season = GameData.getSoldierSeasonDataTable().get(seasonId);
        var challenge = GameData.getSoldierGradeChallengeDataTable().get(challengeId);
        if (season == null || challenge == null || SoldierGame.nodes(challenge.getNodeGroupId()).isEmpty()) {
            return null;
        }

        getOrCreateSeason(seasonId);
        var run = new SoldierGame(this, seasonId, challengeId);
        run.initRun();
        this.activeRun = run;
        this.save();
        return run.toApplyResp();
    }

    public synchronized SoldierInteractResp interact(SoldierInteractReq req) {
        if (!inProgress()) {
            return null;
        }
        return this.activeRun.interact(req);
    }

    public synchronized PublicSoldier.SoldierSettleDataResp giveUp() {
        if (!inProgress()) {
            return null;
        }
        return settleRun(this.activeRun, false);
    }

    public synchronized PublicSoldier.SoldierSettleDataResp settleRun(SoldierGame run, boolean victory) {
        if (run == null || !run.inProgress()) {
            return null;
        }

        var seasonState = getOrCreateSeason(run.getSeasonId());
        var seasonDef = GameData.getSoldierSeasonDataTable().get(run.getSeasonId());
        var challenge = GameData.getSoldierGradeChallengeDataTable().get(run.getGradeChallengeId());

        int totalNodes = challenge == null ? 0 : SoldierGame.nodes(challenge.getNodeGroupId()).size();
        int passed = run.getPassedNodes();
        int baseScore = (challenge != null && totalNodes > 0 && passed > 0)
                ? challenge.getScore() * Math.min(passed, totalNodes) / totalNodes
                : 0;
        int totalScore = baseScore + run.getCheckStageDpsScore() + run.getCheckStageTimeScore();
        int rewardExpGain = totalScore;
        int techPointGain = (challenge != null && totalNodes > 0 && passed > 0)
                ? challenge.getTechPoint() * Math.min(passed, totalNodes) / totalNodes
                : 0;
        int gradeExpGain = victory && challenge != null ? challenge.getGradeScore() : 0;

        seasonState.score += totalScore;
        seasonState.rewardExp += rewardExpGain;
        if (victory && challenge != null) {
            if (seasonState.completedChallenges == null) {
                seasonState.completedChallenges = new HashSet<>();
            }
            seasonState.completedChallenges.add(challenge.getId());
            seasonState.curGradeExp += gradeExpGain;
            var nextChallenge = GameData.getSoldierGradeChallengeDataTable().get(challenge.getId() + 1);
            if (nextChallenge != null && seasonState.curGradeExp >= challenge.getGradeScore()) {
                seasonState.curGradeChallengeId = nextChallenge.getId();
                seasonState.curGradeExp = 0;
            }
        }

        var change = new PlayerChangeInfo();
        if (seasonDef != null && seasonDef.getTechPointTid() > 0 && techPointGain > 0) {
            getPlayer().getInventory().addItem(seasonDef.getTechPointTid(), techPointGain, change);
        }

        int chessTotalCost = 0;
        var report = new BattleReportData();
        report.reportId = run.getSeasonId() + "_" + run.getGradeChallengeId() + "_" + System.currentTimeMillis();
        report.gradeChallengeId = run.getGradeChallengeId();
        report.bossAbilityId = run.getBossAbilityId();
        report.nodeIndex = run.getNodeIndex();
        report.remainingHp = run.getHp();
        report.totalCoin = run.getTotalCoinEarned();
        report.cardIds = new ArrayList<>(run.getStrategyCards());
        report.chess = new ArrayList<>();
        for (var c : run.getChessList()) {
            var charDef = GameData.getSoldierCharacterDataTable().get(c.getTid());
            if (charDef != null) {
                chessTotalCost += charDef.getCost();
            }
            report.chess.add(new ReportChess(c.getTid(), c.getStar()));
        }
        report.result = victory;
        report.score = totalScore;
        report.time = Nebula.getCurrentServerTime();

        if (seasonState.recentReports == null) {
            seasonState.recentReports = new ArrayList<>();
        }
        seasonState.recentReports.add(0, report);
        while (seasonState.recentReports.size() > MAX_RECENT_REPORTS) {
            seasonState.recentReports.removeLast();
        }

        int endingId = victory ? 10102 : 10101;
        var settle = PublicSoldier.SoldierSettleDataResp.newInstance()
                .setResult(victory ? 1 : 0)
                .setPassedNode(passed)
                .setRemainingHp(run.getHp())
                .setCheckStageDps(run.getCheckStageDps())
                .setCheckStageTime(run.getCheckStageTime())
                .setChessTotalCost(chessTotalCost)
                .setGetSchedule(rewardExpGain)
                .setGetTechPoint(techPointGain)
                .setGetGradeExp(gradeExpGain)
                .setCurGradeChallengeId(seasonState.curGradeChallengeId)
                .setCurGradeExp(seasonState.curGradeExp)
                .setEndingId(endingId)
                .setTotalCoinQty(run.getTotalCoinEarned());
        if (!change.isEmpty()) {
            settle.setChange(change.toProto());
        }

        this.activeRun = null;
        this.save();
        return settle;
    }

    public synchronized boolean stepOut() {
        if (!inProgress()) {
            return false;
        }
        this.save();
        return true;
    }

    public synchronized boolean setPartnerTrace(int partnerType, boolean trace) {
        if (!inProgress() || !this.activeRun.setPartnerTrace(partnerType, trace)) {
            return false;
        }
        this.save();
        return true;
    }

    public synchronized SoldierBattleReportRecentList getRecentReportsProto(int seasonId) {
        var state = getOrCreateSeason(seasonId);
        var rsp = SoldierBattleReportRecentList.newInstance();
        if (state.recentReports != null) {
            for (var r : state.recentReports) {
                rsp.addReports(toReportProto(r));
            }
        }
        return rsp;
    }

    public synchronized SoldierBattleReportFavoriteList getFavoriteReportsProto(int seasonId) {
        var state = getOrCreateSeason(seasonId);
        var rsp = SoldierBattleReportFavoriteList.newInstance();
        if (state.favoriteReports != null) {
            for (var r : state.favoriteReports) {
                rsp.addReports(toReportProto(r));
            }
        }
        return rsp;
    }

    public synchronized boolean favoriteReport(String reportId) {
        if (reportId == null || reportId.isEmpty()) {
            return false;
        }
        for (var state : seasons().values()) {
            if (state.recentReports == null) continue;
            for (var r : state.recentReports) {
                if (reportId.equals(r.reportId)) {
                    if (state.favoriteReports == null) {
                        state.favoriteReports = new ArrayList<>();
                    }
                    if (state.favoriteReports.size() >= MAX_FAVORITE_REPORTS) {
                        return false;
                    }
                    if (state.favoriteReports.stream().noneMatch(f -> reportId.equals(f.reportId))) {
                        state.favoriteReports.add(0, r);
                        this.save();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized boolean unfavoriteReport(String reportId) {
        if (reportId == null || reportId.isEmpty()) {
            return false;
        }
        boolean removed = false;
        for (var state : seasons().values()) {
            if (state.favoriteReports != null && state.favoriteReports.removeIf(r -> reportId.equals(r.reportId))) {
                removed = true;
            }
        }
        if (removed) {
            this.save();
        }
        return removed;
    }

    private static Public.SoldierBattleReport toReportProto(BattleReportData r) {
        var proto = Public.SoldierBattleReport.newInstance()
                .setReportId(r.reportId)
                .setGradeChallengeId(r.gradeChallengeId)
                .setBossAbilityId(r.bossAbilityId)
                .setNodeIndex(r.nodeIndex)
                .setRemainingHP(r.remainingHp)
                .setTotalCoin(r.totalCoin)
                .setResult(r.result)
                .setScore(r.score)
                .setVersion(SOLDIER_VERSION)
                .setTime(r.time);
        if (r.cardIds != null) {
            for (int cid : r.cardIds) {
                proto.addCardIds(cid);
            }
        }
        if (r.chess != null) {
            for (var c : r.chess) {
                proto.addChess(Public.SoldierBattleReportChess.newInstance().setId(c.tid).setStar(c.star));
            }
        }
        return proto;
    }

    @Getter
    @Setter
    @Entity(useDiscriminator = false)
    public static class SeasonState {
        private int seasonId;
        private int rewardExp;
        private int curRewardLv;
        private int curGradeChallengeId;
        private int curGradeExp;
        private int score;
        private Bitset unlockedTechNodes;
        private Map<Integer, Integer> newbies;
        private Set<Integer> completedChallenges;
        private List<BattleReportData> recentReports;
        private List<BattleReportData> favoriteReports;
    }

    @Getter
    @Setter
    @Entity(useDiscriminator = false)
    public static class ReportChess {
        private int tid;
        private int star;

        @Deprecated
        public ReportChess() {
        }

        public ReportChess(int tid, int star) {
            this.tid = tid;
            this.star = star;
        }
    }

    @Getter
    @Setter
    @Entity(useDiscriminator = false)
    public static class BattleReportData {
        private String reportId;
        private int gradeChallengeId;
        private int bossAbilityId;
        private int nodeIndex;
        private int remainingHp;
        private int totalCoin;
        private List<Integer> cardIds;
        private List<ReportChess> chess;
        private boolean result;
        private int score;
        private long time;
    }
}
