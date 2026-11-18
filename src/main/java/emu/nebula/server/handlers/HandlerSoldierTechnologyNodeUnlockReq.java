package emu.nebula.server.handlers;

import emu.nebula.net.GameSession;
import emu.nebula.net.HandlerId;
import emu.nebula.net.NetHandler;
import emu.nebula.net.NetMsgId;
import emu.nebula.proto.Public.UI32;

@HandlerId(NetMsgId.soldier_technology_node_unlock_req)
public class HandlerSoldierTechnologyNodeUnlockReq extends NetHandler {
    @Override
    public byte[] handle(GameSession session, byte[] message) throws Exception {
        var req = UI32.parseFrom(message);
        var change = session.getPlayer().getSoldierManager().unlockTechNode(req.getValue());
        return change == null
                ? session.encodeMsg(NetMsgId.soldier_technology_node_unlock_failed_ack)
                : session.encodeMsg(NetMsgId.soldier_technology_node_unlock_succeed_ack, change.toProto());
    }
}
