package emu.nebula.server.handlers;

import emu.nebula.net.GameSession;
import emu.nebula.net.HandlerId;
import emu.nebula.net.NetHandler;
import emu.nebula.net.NetMsgId;
import emu.nebula.proto.Public.UI32;

@HandlerId(NetMsgId.soldier_battle_report_favorite_list_req)
public class HandlerSoldierBattleReportFavoriteListReq extends NetHandler {
    @Override
    public byte[] handle(GameSession session, byte[] message) throws Exception {
        int seasonId = message.length > 0 ? UI32.parseFrom(message).getValue() : 1;
        var rsp = session.getPlayer().getSoldierManager().getFavoriteReportsProto(seasonId);
        return session.encodeMsg(NetMsgId.soldier_battle_report_favorite_list_succeed_ack, rsp);
    }
}
