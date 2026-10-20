package com.easemob.customer.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easemob.customer.account.entity.CsFamiliarRelation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface FamiliarRelationMapper extends BaseMapper<CsFamiliarRelation> {

    @Select("SELECT * FROM cs_familiar_relation " +
            "WHERE user_id = #{userId} AND agent_id = #{agentId} " +
            "AND last_serve_time >= DATE_SUB(NOW(), INTERVAL #{validDays} DAY) " +
            "LIMIT 1")
    CsFamiliarRelation selectValid(@Param("userId") String userId,
                                   @Param("agentId") String agentId,
                                   @Param("validDays") int validDays);

    @Update("UPDATE cs_familiar_relation SET serve_count = serve_count + 1, last_serve_time = NOW() " +
            "WHERE user_id = #{userId} AND agent_id = #{agentId}")
    int incrementServeCount(@Param("userId") String userId, @Param("agentId") String agentId);
}
