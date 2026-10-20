package com.easemob.customer.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easemob.customer.conversation.entity.CsTransferRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface TransferRecordMapper extends BaseMapper<CsTransferRecord> {

    @Select("SELECT * FROM cs_transfer_record WHERE session_id = #{sessionId} ORDER BY id DESC")
    List<CsTransferRecord> selectBySession(@Param("sessionId") Long sessionId);
}
