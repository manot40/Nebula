package emu.nebula.server.handlers;

import emu.nebula.net.GameSession;
import emu.nebula.net.HandlerId;
import emu.nebula.net.NetHandler;
import emu.nebula.net.NetMsgId;

@HandlerId(NetMsgId.soldier_info_req)
public class HandlerSoldierInfoReq extends NetHandler {
    @Override
    public byte[] handle(GameSession session, byte[] message) {
        var info = session.getPlayer().getSoldierManager().getInfoProto();
        return info == null
                ? session.encodeMsg(NetMsgId.soldier_info_failed_ack)
                : session.encodeMsg(NetMsgId.soldier_info_succeed_ack, info);
    }
}
