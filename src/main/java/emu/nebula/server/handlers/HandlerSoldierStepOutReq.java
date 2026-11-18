package emu.nebula.server.handlers;

import emu.nebula.net.GameSession;
import emu.nebula.net.HandlerId;
import emu.nebula.net.NetHandler;
import emu.nebula.net.NetMsgId;

@HandlerId(NetMsgId.soldier_step_out_req)
public class HandlerSoldierStepOutReq extends NetHandler {
    @Override
    public byte[] handle(GameSession session, byte[] message) {
        boolean ok = session.getPlayer().getSoldierManager().stepOut();
        return session.encodeMsg(ok
                ? NetMsgId.soldier_step_out_succeed_ack
                : NetMsgId.soldier_step_out_failed_ack);
    }
}
