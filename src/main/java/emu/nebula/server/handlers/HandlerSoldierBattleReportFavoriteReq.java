package emu.nebula.server.handlers;

import emu.nebula.net.GameSession;
import emu.nebula.net.HandlerId;
import emu.nebula.net.NetHandler;
import emu.nebula.net.NetMsgId;
import emu.nebula.proto.Public;

@HandlerId(NetMsgId.soldier_battle_report_favorite_req)
public class HandlerSoldierBattleReportFavoriteReq extends NetHandler {
    @Override
    public byte[] handle(GameSession session, byte[] message) throws Exception {
        var req = Public.String.parseFrom(message);
        boolean ok = session.getPlayer().getSoldierManager().favoriteReport(req.getValue());
        return session.encodeMsg(ok
                ? NetMsgId.soldier_battle_report_favorite_succeed_ack
                : NetMsgId.soldier_battle_report_favorite_failed_ack);
    }
}
