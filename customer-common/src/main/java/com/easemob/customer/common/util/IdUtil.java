package com.easemob.customer.common.util;

import java.util.UUID;

public final class IdUtil {

    private IdUtil() {
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String fastSimpleUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
