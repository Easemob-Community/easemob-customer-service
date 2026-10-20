package com.easemob.customer.common.result;

import lombok.Data;

@Data
public class R<T> {

    private int code;
    private String msg;
    private T data;

    private R() {
    }

    private R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(0, "ok", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(0, "ok", data);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R<>(0, msg, data);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }

    public static <T> R<T> fail(String msg) {
        return new R<>(500, msg, null);
    }

    public boolean success() {
        return code == 0;
    }
}
