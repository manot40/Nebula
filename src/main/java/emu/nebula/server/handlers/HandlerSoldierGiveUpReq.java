package emu.nebula.server.handlers;

import emu.nebula.net.GameSession;
import emu.nebula.net.HandlerId;
import emu.nebula.net.NetHandler;
import emu.nebula.net.NetMsgId;

@HandlerId(NetMsgId.soldier_give_up_req)
public class HandlerSoldierGiveUpReq extends NetHandler {
    @Override
    public byte[] handle(GameSession session, byte[] message) {
        var rsp = session.getPlayer().getSoldierManager().giveUp();
        return rsp == null
                ? session.encodeMsg(NetMsgId.soldier_give_up_failed_ack)
                : session.encodeMsg(NetMsgId.soldier_give_up_succeed_ack, rsp);
    }
}
