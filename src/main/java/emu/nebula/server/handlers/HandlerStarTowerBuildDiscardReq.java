package emu.nebula.server.handlers;

import emu.nebula.net.GameSession;
import emu.nebula.net.HandlerId;
import emu.nebula.net.NetHandler;
import emu.nebula.net.NetMsgId;
import emu.nebula.proto.StarTowerBuildDiscard.StarTowerBuildDiscardReq;

@HandlerId(NetMsgId.star_tower_build_discard_req)
public class HandlerStarTowerBuildDiscardReq extends NetHandler {

    @Override
    public byte[] handle(GameSession session, byte[] message) throws Exception {
        var req = StarTowerBuildDiscardReq.parseFrom(message);

        var build = session.getPlayer().getStarTowerManager().getBuildById(req.getBuildId());
        if (build == null) {
            return session.encodeMsg(NetMsgId.star_tower_build_discard_failed_ack);
        }

        if (req.getDiscard() && (build.isLock() || build.isPreference())) {
            return session.encodeMsg(NetMsgId.star_tower_build_discard_failed_ack);
        }

        build.setDiscard(req.getDiscard());

        return session.encodeMsg(NetMsgId.star_tower_build_discard_succeed_ack);
    }

}
