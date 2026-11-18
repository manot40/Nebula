package emu.nebula.server.handlers;

import emu.nebula.net.GameSession;
import emu.nebula.net.HandlerId;
import emu.nebula.net.NetHandler;
import emu.nebula.net.NetMsgId;
import emu.nebula.proto.Public.UI32;

@HandlerId(NetMsgId.soldier_season_reward_receive_req)
public class HandlerSoldierSeasonRewardReceiveReq extends NetHandler {
    @Override
    public byte[] handle(GameSession session, byte[] message) throws Exception {
        int seasonId = message.length > 0 ? UI32.parseFrom(message).getValue() : 1;
        var rsp = session.getPlayer().getSoldierManager().receiveSeasonReward(seasonId);
        return rsp == null
                ? session.encodeMsg(NetMsgId.soldier_season_reward_receive_failed_ack)
                : session.encodeMsg(NetMsgId.soldier_season_reward_receive_succeed_ack, rsp);
    }
}
