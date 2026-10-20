package com.easemob.customer.admin.controller;

import com.easemob.customer.common.result.R;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/assignment")
public class AssignmentConfigController {

    @GetMapping("/strategy")
    public R<Map<String, Object>> getStrategy() {
        return R.ok(Map.of(
                "defaultStrategy", "FAMILIAR_FIRST",
                "familiarValidDays", 30,
                "queueLimit", 50,
                "noAgentPolicy", "QUEUE_WITH_AI",
                "groupOverrides", Collections.emptyList()));
    }

    @PutMapping("/strategy")
    public R<Void> updateStrategy(@RequestBody Map<String, Object> config) {
        return R.ok();
    }
}
