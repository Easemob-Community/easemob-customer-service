package com.easemob.customer.conversation.service;

import com.easemob.customer.common.enums.SessionStatus;
import com.easemob.customer.conversation.dto.EvaluateReq;
import com.easemob.customer.conversation.entity.CsEvaluate;
import com.easemob.customer.conversation.entity.CsSession;
import com.easemob.customer.conversation.mapper.EvaluateMapper;
import com.easemob.customer.conversation.mapper.SessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EvaluateService {

    private final EvaluateMapper evaluateMapper;
    private final SessionMapper sessionMapper;
    private final SessionService sessionService;

    @Transactional
    public void evaluate(EvaluateReq req) {
        CsSession session = sessionMapper.selectById(req.getSessionId());
        if (session == null) {
            return;
        }
        CsEvaluate evaluate = new CsEvaluate();
        evaluate.setSessionId(req.getSessionId());
        evaluate.setScore(req.getScore());
        evaluate.setTags(req.getTags() != null ? String.join(",", req.getTags()) : null);
        evaluate.setComment(req.getComment());
        evaluateMapper.insert(evaluate);

        session.setStatus(SessionStatus.EVALUATED.name());
        sessionMapper.updateById(session);
        sessionService.close(req.getSessionId(), "USER", "EVALUATED");
    }
}
