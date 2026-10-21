package com.easemob.customer.common.exception;

import com.easemob.customer.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e, HttpServletRequest req) {
        log.warn("[{}] BizException: code={}, msg={}", req.getRequestURI(), e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({BindException.class, IllegalArgumentException.class})
    public R<Void> handleParamException(Exception e, HttpServletRequest req) {
        log.warn("[{}] Param error: {}", req.getRequestURI(), e.getMessage());
        return R.fail(400, "请求参数错误：" + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleUnknown(Exception e, HttpServletRequest req) {
        log.error("[{}] unhandled", req.getRequestURI(), e);
        return R.fail(500, "系统繁忙，请稍后再试");
    }
}
