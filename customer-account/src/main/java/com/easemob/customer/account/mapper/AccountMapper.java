package com.easemob.customer.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easemob.customer.account.entity.CsAccount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AccountMapper extends BaseMapper<CsAccount> {

    @Select("SELECT * FROM cs_account WHERE type = 'AI' AND group_id = #{groupId} AND status = 1")
    List<CsAccount> selectEnabledByGroup(@Param("groupId") Long groupId);

    @Select("<script>SELECT * FROM cs_account WHERE type = 'AGENT' AND status = 1 " +
            "<if test='skillGroupId != null'>AND group_id = #{skillGroupId}</if></script>")
    List<CsAccount> selectAgents(@Param("skillGroupId") Long skillGroupId);

    @Select("SELECT * FROM cs_account WHERE easemob_id = #{easemobId} LIMIT 1")
    CsAccount selectByEasemobId(@Param("easemobId") String easemobId);
}
