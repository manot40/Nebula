package emu.nebula.server.handlers;

import emu.nebula.net.GameSession;
import emu.nebula.net.HandlerId;
import emu.nebula.net.NetHandler;
import emu.nebula.net.NetMsgId;
import emu.nebula.proto.Public;

@HandlerId(NetMsgId.soldier_battle_report_unfavorite_req)
public class HandlerSoldierBattleReportUnfavoriteReq extends NetHandler {
    @Override
    public byte[] handle(GameSession session, byte[] message) throws Exception {
        var req = Public.String.parseFrom(message);
        boolean ok = session.getPlayer().getSoldierManager().unfavoriteReport(req.getValue());
        return session.encodeMsg(ok
                ? NetMsgId.soldier_battle_report_unfavorite_succeed_ack
                : NetMsgId.soldier_battle_report_unfavorite_failed_ack);
    }
}
