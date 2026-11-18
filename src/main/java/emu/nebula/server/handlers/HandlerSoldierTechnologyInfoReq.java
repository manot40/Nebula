package emu.nebula.server.handlers;

import emu.nebula.net.GameSession;
import emu.nebula.net.HandlerId;
import emu.nebula.net.NetHandler;
import emu.nebula.net.NetMsgId;
import emu.nebula.proto.Public.Bytes;
import emu.nebula.proto.Public.UI32;

@HandlerId(NetMsgId.soldier_technology_info_req)
public class HandlerSoldierTechnologyInfoReq extends NetHandler {
    @Override
    public byte[] handle(GameSession session, byte[] message) throws Exception {
        int seasonId = message.length > 0 ? UI32.parseFrom(message).getValue() : 1;
        var rsp = Bytes.newInstance()
                .setValue(session.getPlayer().getSoldierManager().getTechInfoBytes(seasonId));
        return session.encodeMsg(NetMsgId.soldier_technology_info_succeed_ack, rsp);
    }
}
