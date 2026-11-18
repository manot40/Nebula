package emu.nebula.server.handlers;

import emu.nebula.game.tower.StarTowerBuild;
import emu.nebula.net.GameSession;
import emu.nebula.net.HandlerId;
import emu.nebula.net.NetHandler;
import emu.nebula.net.NetMsgId;
import emu.nebula.proto.StarTowerBuildPotentialModify.StarTowerBuildPotentialModifyReq;
import emu.nebula.proto.StarTowerBuildPotentialModify.StarTowerBuildPotentialModifyResp;

@HandlerId(NetMsgId.star_tower_build_potential_modify_req)
public class HandlerStarTowerBuildPotentialModifyReq extends NetHandler {

    @Override
    public byte[] handle(GameSession session, byte[] message) throws Exception {
        var req = StarTowerBuildPotentialModifyReq.parseFrom(message);

        var change = session.getPlayer().getStarTowerManager().modifyBuildPotentials(
                req.getBuildId(),
                req.getPotentialChange()
        );

        if (change == null) {
            return session.encodeMsg(NetMsgId.star_tower_build_potential_modify_failed_ack);
        }

        var build = (StarTowerBuild) change.getExtraData();
        var rsp = StarTowerBuildPotentialModifyResp.newInstance()
                .setChange(change.toProto())
                .setInfo(build.toProto());

        return session.encodeMsg(NetMsgId.star_tower_build_potential_modify_succeed_ack, rsp);
    }

}
