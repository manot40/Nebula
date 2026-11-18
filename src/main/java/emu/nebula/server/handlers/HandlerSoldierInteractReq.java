package emu.nebula.server.handlers;

import emu.nebula.net.GameSession;
import emu.nebula.net.HandlerId;
import emu.nebula.net.NetHandler;
import emu.nebula.net.NetMsgId;
import emu.nebula.proto.SoldierInteract.SoldierInteractReq;

@HandlerId(NetMsgId.soldier_interact_req)
public class HandlerSoldierInteractReq extends NetHandler {
    @Override
    public byte[] handle(GameSession session, byte[] message) throws Exception {
        var req = SoldierInteractReq.parseFrom(message);
        var rsp = session.getPlayer().getSoldierManager().interact(req);
        return rsp == null
                ? session.encodeMsg(NetMsgId.soldier_interact_failed_ack)
                : session.encodeMsg(NetMsgId.soldier_interact_succeed_ack, rsp);
    }
}
