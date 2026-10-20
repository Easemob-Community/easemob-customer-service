package com.easemob.customer.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easemob.customer.conversation.entity.CsMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface MessageMapper extends BaseMapper<CsMessage> {

    @Select("SELECT * FROM cs_message WHERE session_id = #{sessionId} ORDER BY id DESC LIMIT #{limit}")
    List<CsMessage> selectRecentBySession(@Param("sessionId") Long sessionId, @Param("limit") int limit);

    @Select("SELECT * FROM cs_message WHERE msg_id = #{msgId} LIMIT 1")
    CsMessage selectByMsgId(@Param("msgId") String msgId);
}
