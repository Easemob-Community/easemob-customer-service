package com.easemob.customer.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easemob.customer.ai.entity.CsTransferWord;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface TransferWordMapper extends BaseMapper<CsTransferWord> {

    @Select("SELECT word FROM cs_transfer_word WHERE status = 1")
    List<String> selectEnabledWords();
}
