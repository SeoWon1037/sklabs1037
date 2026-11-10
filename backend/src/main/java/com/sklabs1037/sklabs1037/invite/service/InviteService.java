package com.sklabs1037.sklabs1037.invite.service;

import com.sklabs1037.sklabs1037.invite.dao.InviteDao;
import com.sklabs1037.sklabs1037.me.service.BadgeService;
import com.sklabs1037.sklabs1037.me.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 🤝 친구 초대 — 내 링크(/?ref=코드)로 들어와 가입하면 두 사람 모두 배지
 * - 흐름: 링크로 들어옴 → 브라우저가 코드를 기억 → 가입 · 로그인(소셜 포함)한 뒤 화면이 코드를 서버로 보냄 (accept)
 *   → 가입 화면 · 소셜 로그인 코드를 건드리지 않고 어떤 가입 방법에서도 됨
 * - 받을 수 있는 사람: 가입한 지 3일 안, 아직 초대받은 적 없음, 체험 계정 아님 (InviteRules.refuse)
 * - 보상은 배지만 (가상머니를 주면 모의투자 수익률 랭킹이 틀어짐)
 *   초대한 사람: 🤝 첫 초대, 5명이면 📣 / 초대받은 사람: 🎈
 */
@Service
public class InviteService {

    @Autowired
    private InviteDao inviteDao;

    @Autowired
    private BadgeService badgeService;

    @Autowired
    private NotificationService notificationService;

    // 내 초대 { code, count, invited[], inviterName }
    public Map<String, Object> my(Long me) {

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", InviteRules.code(me));
        out.put("count", inviteDao.countInvited(me));
        out.put("invited", inviteDao.selectInvited(me));
        out.put("inviterName", inviteDao.selectInviterName(me));
        out.put("many", InviteRules.MANY);
        return out;

    }

    // 초대 받기 → { inviterName }
    @Transactional
    public Map<String, Object> accept(Long me, String code) {

        Map<String, Object> user = inviteDao.selectUser(me);
        if (user == null) throw new NoSuchElementException("회원 정보를 찾지 못했어요.");

        Long inviterId = InviteRules.userId(code);
        String no = InviteRules.refuse(inviterId, me, (String) user.get("provider"),
                user.get("joinedAt") instanceof Number n ? n.longValue() : null, System.currentTimeMillis());
        if (no != null) throw new IllegalArgumentException(no);

        Map<String, Object> inviter = inviteDao.selectUser(inviterId);
        if (inviter == null || "DEMO".equals(inviter.get("provider")) || "AI".equals(inviter.get("provider"))) throw new IllegalArgumentException("초대 코드가 맞지 않아요.");

        if (inviteDao.insertInvite(me, inviterId) == 0) throw new IllegalArgumentException("이미 초대를 받았어요.");

        badgeService.awardOne(me, "INVITED");
        badgeService.awardOne(inviterId, "INVITE_1");
        if (inviteDao.countInvited(inviterId) >= InviteRules.MANY) badgeService.awardOne(inviterId, "INVITE_5");

        notificationService.notify(inviterId, "INVITE", "🎉 " + user.get("nickname") + "님이 내 초대로 가입했어요",
                "초대해줘서 고마워요. 지금까지 " + inviteDao.countInvited(inviterId) + "명을 초대했어요.", "/activity", "INVITE:" + me);

        return Map.of("inviterName", String.valueOf(inviter.get("nickname")));

    }
}
