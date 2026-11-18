package emu.nebula.server.handlers;

import emu.nebula.net.GameSession;
import emu.nebula.net.HandlerId;
import emu.nebula.net.NetHandler;
import emu.nebula.net.NetMsgId;
import emu.nebula.proto.SoldierLearn.SoldierLearnReq;

@HandlerId(NetMsgId.soldier_learn_req)
public class HandlerSoldierLearnReq extends NetHandler {
    @Override
    public byte[] handle(GameSession session, byte[] message) throws Exception {
        var req = SoldierLearnReq.parseFrom(message);
        session.getPlayer().getSoldierManager().learn(req.getSeason(), req.getGroupId(), req.getStepId());
        return session.encodeMsg(NetMsgId.soldier_learn_succeed_ack);
    }
}
