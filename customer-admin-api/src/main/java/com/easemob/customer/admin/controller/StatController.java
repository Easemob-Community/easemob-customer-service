package com.easemob.customer.admin.controller;

import com.easemob.customer.common.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/stat")
public class StatController {

    @GetMapping("/overview")
    public R<Map<String, Object>> overview(@RequestParam(required = false) String range) {
        return R.ok(Map.of(
                "sessionCount", 0,
                "aiResolved", 0,
                "transferred", 0,
                "onlineAgents", 0,
                "queueSize", 0));
    }

    @GetMapping("/trend")
    public R<List<Map<String, Object>>> trend(@RequestParam(required = false) String range) {
        return R.ok(Collections.emptyList());
    }

    @GetMapping("/agent-rank")
    public R<List<Map<String, Object>>> agentRank(@RequestParam(required = false) String range) {
        return R.ok(Collections.emptyList());
    }

    @GetMapping("/transfer-words-top")
    public R<List<Map<String, Object>>> transferWordsTop() {
        return R.ok(Collections.emptyList());
    }
}
