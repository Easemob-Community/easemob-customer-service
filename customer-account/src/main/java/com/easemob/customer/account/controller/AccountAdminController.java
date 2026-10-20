package com.easemob.customer.account.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.easemob.customer.account.entity.CsAccount;
import com.easemob.customer.account.entity.CsGroup;
import com.easemob.customer.account.mapper.AccountMapper;
import com.easemob.customer.account.mapper.GroupMapper;
import com.easemob.customer.account.service.AccountService;
import com.easemob.customer.common.enums.AccountType;
import com.easemob.customer.common.exception.BizException;
import com.easemob.customer.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/admin/account")
@RequiredArgsConstructor
public class AccountAdminController {

    private final AccountMapper accountMapper;
    private final GroupMapper groupMapper;
    private final AccountService accountService;

    @GetMapping("/agents")
    public R<List<CsAccount>> listAgents() {
        return R.ok(listByType(AccountType.AGENT));
    }

    @PostMapping("/agents")
    public R<Void> createAgent(@RequestBody CsAccount body) {
        create(body, AccountType.AGENT);
        return R.ok();
    }

    @PutMapping("/agents/{id}")
    public R<Void> updateAgent(@PathVariable Long id, @RequestBody CsAccount body) {
        update(id, body, AccountType.AGENT);
        return R.ok();
    }

    @DeleteMapping("/agents/{id}")
    public R<Void> deleteAgent(@PathVariable Long id) {
        CsAccount agent = delete(id, AccountType.AGENT);
        accountService.agentOffline(agent.getEasemobId());
        return R.ok();
    }

    @GetMapping("/ai-accounts")
    public R<List<CsAccount>> listAiAccounts() {
        return R.ok(listByType(AccountType.AI));
    }

    @PostMapping("/ai-accounts")
    public R<Void> createAiAccount(@RequestBody CsAccount body) {
        create(body, AccountType.AI);
        return R.ok();
    }

    @PutMapping("/ai-accounts/{id}")
    public R<Void> updateAiAccount(@PathVariable Long id, @RequestBody CsAccount body) {
        update(id, body, AccountType.AI);
        return R.ok();
    }

    @DeleteMapping("/ai-accounts/{id}")
    public R<Void> deleteAiAccount(@PathVariable Long id) {
        delete(id, AccountType.AI);
        return R.ok();
    }

    @GetMapping("/groups")
    public R<List<CsGroup>> listGroups(@RequestParam(required = false) AccountType type) {
        QueryWrapper<CsGroup> wrapper = new QueryWrapper<CsGroup>()
                .eq("status", 1)
                .orderByAsc("id");
        if (type != null) {
            wrapper.eq("type", type);
        }
        return R.ok(groupMapper.selectList(wrapper));
    }

    @PostMapping("/groups")
    public R<Void> createGroup(@RequestBody CsGroup body) {
        if (!StringUtils.hasText(body.getName()) || body.getType() == null) {
            throw new BizException("组名和类型不能为空");
        }
        body.setId(null);
        body.setStatus(body.getStatus() == null ? 1 : body.getStatus());
        body.setCreateTime(LocalDateTime.now());
        groupMapper.insert(body);
        return R.ok();
    }

    @PutMapping("/groups/{id}")
    public R<Void> updateGroup(@PathVariable Long id, @RequestBody CsGroup body) {
        if (groupMapper.selectById(id) == null) {
            throw new BizException("组不存在");
        }
        body.setId(id);
        groupMapper.updateById(body);
        return R.ok();
    }

    @DeleteMapping("/groups/{id}")
    public R<Void> deleteGroup(@PathVariable Long id) {
        Long count = accountMapper.selectCount(
                new QueryWrapper<CsAccount>().eq("group_id", id));
        if (count != null && count > 0) {
            throw new BizException("组下存在账号，无法删除");
        }
        groupMapper.deleteById(id);
        return R.ok();
    }

    private List<CsAccount> listByType(AccountType type) {
        return accountMapper.selectList(new QueryWrapper<CsAccount>()
                .eq("type", type)
                .orderByDesc("create_time"));
    }

    private void create(CsAccount body, AccountType type) {
        if (!StringUtils.hasText(body.getEasemobId())) {
            throw new BizException("环信ID不能为空");
        }
        Long exists = accountMapper.selectCount(
                new QueryWrapper<CsAccount>().eq("easemob_id", body.getEasemobId()));
        if (exists != null && exists > 0) {
            throw new BizException("环信ID已存在: " + body.getEasemobId());
        }
        body.setId(null);
        body.setType(type);
        body.setStatus(body.getStatus() == null ? 1 : body.getStatus());
        if (type == AccountType.AGENT && body.getMaxSessions() == null) {
            body.setMaxSessions(5);
        }
        body.setCreateTime(LocalDateTime.now());
        accountMapper.insert(body);
    }

    private void update(Long id, CsAccount body, AccountType type) {
        CsAccount existing = accountMapper.selectById(id);
        if (existing == null || existing.getType() != type) {
            throw new BizException("账号不存在");
        }
        body.setId(id);
        body.setType(null);
        body.setEasemobId(null);
        body.setCreateTime(null);
        accountMapper.updateById(body);
    }

    private CsAccount delete(Long id, AccountType type) {
        CsAccount existing = accountMapper.selectById(id);
        if (existing == null || existing.getType() != type) {
            throw new BizException("账号不存在");
        }
        accountMapper.deleteById(id);
        return existing;
    }
}
