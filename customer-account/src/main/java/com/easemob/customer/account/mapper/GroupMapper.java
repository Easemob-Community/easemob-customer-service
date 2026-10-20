package com.easemob.customer.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easemob.customer.account.entity.CsGroup;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface GroupMapper extends BaseMapper<CsGroup> {

    @Select("SELECT * FROM cs_group WHERE type = #{type} AND status = 1")
    List<CsGroup> selectByType(@Param("type") String type);
}
