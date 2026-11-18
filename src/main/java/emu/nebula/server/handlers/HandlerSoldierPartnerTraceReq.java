package emu.nebula.server.handlers;

import emu.nebula.net.GameSession;
import emu.nebula.net.HandlerId;
import emu.nebula.net.NetHandler;
import emu.nebula.net.NetMsgId;
import emu.nebula.proto.SoldierPartnerTrace.SoldierPartnerTraceReq;

@HandlerId(NetMsgId.soldier_partner_trace_req)
public class HandlerSoldierPartnerTraceReq extends NetHandler {
    @Override
    public byte[] handle(GameSession session, byte[] message) throws Exception {
        var req = SoldierPartnerTraceReq.parseFrom(message);
        boolean ok = session.getPlayer().getSoldierManager()
                .setPartnerTrace(req.getPartnerType(), req.getTrace());
        return session.encodeMsg(ok
                ? NetMsgId.soldier_partner_trace_succeed_ack
                : NetMsgId.soldier_partner_trace_failed_ack);
    }
}
