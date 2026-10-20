package com.easemob.customer.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easemob.customer.ai.entity.CsAiProvider;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AiProviderMapper extends BaseMapper<CsAiProvider> {

    @Select("SELECT * FROM cs_ai_provider WHERE status = 1 ORDER BY weight DESC")
    List<CsAiProvider> selectEnabledByWeight();
}
