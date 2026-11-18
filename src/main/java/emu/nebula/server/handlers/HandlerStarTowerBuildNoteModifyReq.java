package emu.nebula.server.handlers;

import emu.nebula.game.tower.StarTowerBuild;
import emu.nebula.net.GameSession;
import emu.nebula.net.HandlerId;
import emu.nebula.net.NetHandler;
import emu.nebula.net.NetMsgId;
import emu.nebula.proto.StarTowerBuildNoteModify.StarTowerBuildNoteModifyReq;
import emu.nebula.proto.StarTowerBuildNoteModify.StarTowerBuildNoteModifyResp;

@HandlerId(NetMsgId.star_tower_build_note_modify_req)
public class HandlerStarTowerBuildNoteModifyReq extends NetHandler {

    @Override
    public byte[] handle(GameSession session, byte[] message) throws Exception {
        var req = StarTowerBuildNoteModifyReq.parseFrom(message);

        var change = session.getPlayer().getStarTowerManager().modifyBuildNotes(
                req.getBuildId(),
                req.getNoteChange()
        );

        if (change == null) {
            return session.encodeMsg(NetMsgId.star_tower_build_note_modify_failed_ack);
        }

        var build = (StarTowerBuild) change.getExtraData();
        var rsp = StarTowerBuildNoteModifyResp.newInstance()
                .setChange(change.toProto())
                .setInfo(build.toProto());

        return session.encodeMsg(NetMsgId.star_tower_build_note_modify_succeed_ack, rsp);
    }

}
