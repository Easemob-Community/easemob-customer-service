package com.easemob.customer.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easemob.customer.conversation.entity.CsSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface SessionMapper extends BaseMapper<CsSession> {

    @Select("SELECT * FROM cs_session WHERE user_account_id = #{userId} AND status NOT IN ('CLOSED','EVALUATED') LIMIT 1")
    CsSession selectActiveByUser(@Param("userId") String userId);

    @Select("SELECT * FROM cs_session WHERE group_id = #{groupId} LIMIT 1")
    CsSession selectByGroupId(@Param("groupId") String groupId);

    @Update("UPDATE cs_session SET status = #{status}, agent_account_id = #{agentId}, " +
            "current_handler_type = 'HUMAN', transfer_reason = #{reason}, update_time = NOW() " +
            "WHERE id = #{id}")
    int updateToHuman(@Param("id") Long id, @Param("status") String status,
                      @Param("agentId") String agentId, @Param("reason") String reason);

    @Update("UPDATE cs_session SET status = 'CLOSED', close_reason = #{reason}, update_time = NOW() " +
            "WHERE id = #{id}")
    int updateStatusClosed(@Param("id") Long id, @Param("reason") String reason);

    @Update("UPDATE cs_session SET current_handler_type = 'HUMAN', transfer_reason = #{reason}, " +
            "update_time = NOW() WHERE id = #{id}")
    int updateWaiting(@Param("id") Long id, @Param("reason") String reason);

    @Select("SELECT * FROM cs_session WHERE status IN ('AI_SERVING','HUMAN_SERVING') " +
            "AND last_msg_time < #{before} LIMIT 100")
    List<CsSession> selectTimeoutSessions(@Param("before") LocalDateTime before);

    @Select("SELECT * FROM cs_session WHERE agent_account_id = #{agentId} " +
            "AND status NOT IN ('CLOSED','EVALUATED') ORDER BY last_msg_time DESC")
    List<CsSession> selectAgentSessions(@Param("agentId") String agentId);

    @Select("SELECT * FROM cs_session WHERE status = 'AI_SERVING' " +
            "AND agent_account_id IS NULL " +
            "AND transfer_reason IS NOT NULL ORDER BY last_msg_time DESC LIMIT 100")
    List<CsSession> selectWaitingSessions();
}
