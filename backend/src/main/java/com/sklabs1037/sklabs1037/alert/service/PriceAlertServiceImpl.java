package com.sklabs1037.sklabs1037.alert.service;

import com.sklabs1037.sklabs1037.alert.dao.PriceAlertDao;
import com.sklabs1037.sklabs1037.alert.vo.PriceAlertVO;
import com.sklabs1037.sklabs1037.mock.service.MockPriceService;
import com.sklabs1037.sklabs1037.mock.vo.MockTradeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 목표가 알림 Service 구현
 *
 * 알림 흐름
 *   1. 사용자가 "삼성전자 220,000원 이상" 같은 알림을 걸어둠 (ACTIVE)
 *   2. 정규장 1분마다 PriceAlertScheduler → checkAndNotify()
 *      - 켜진 알림이 없으면 토스도 호출하지 않음
 *      - 현재가는 모의투자와 같은 MockPriceService (알림 걸린 종목만 200개씩 묶어서, 10초 캐시), 실시간 가격일 때만 확인
 *   3. 조건을 넘으면 먼저 SENT로 바꾸고(markSent = 1일 때만) 메일 발송
 *      → 서버가 두 대여도, 확인이 겹쳐도 같은 알림 메일은 1번만
 *      → 메일 발송이 실패하면 다시 ACTIVE로 (다음 확인 때 재시도)
 *      → 이메일이 없는 계정은 재시도하지 않음 (화면에서도 미리 안내)
 */
@Slf4j
@Service
public class PriceAlertServiceImpl implements IPriceAlertService {

    // 한 사람이 동시에 켜둘 수 있는 알림 수
    private static final int MAX_ACTIVE = 20;

    private static final Set<String> CONDITIONS = Set.of("ABOVE", "BELOW");

    @Autowired
    private PriceAlertDao priceAlertDao;

    @Autowired
    private MockPriceService mockPriceService;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String mailFrom;

    // 메일 안의 링크 주소 (운영: https://sklabs1037.com)
    @Value("${app.frontend-url}")
    private String siteUrl;

    @Override
    public List<Map<String, Object>> getMyAlerts(Long userId, Long stockId) {

        PriceAlertVO param = new PriceAlertVO();
        param.setUserId(userId);
        param.setStockId(stockId);

        List<PriceAlertVO> alerts = priceAlertDao.selectMyAlerts(param);
        Map<Long, MockTradeVO> prices = mockPriceService.getPrices(alerts.stream().map(PriceAlertVO::getStockId).toList());
        List<Map<String, Object>> list = new ArrayList<>();

        for (PriceAlertVO a : alerts) {

            MockTradeVO p = prices.get(a.getStockId());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("alertId", a.getAlertId());
            row.put("stockId", a.getStockId());
            row.put("symbol", a.getSymbol());
            row.put("name", a.getName());
            row.put("condition", a.getCondition());
            row.put("targetPrice", a.getTargetPrice());
            row.put("status", a.getStatus());
            row.put("sentPrice", a.getSentPrice());
            row.put("sentAt", a.getSentAt());
            row.put("insAt", a.getInsAt());
            row.put("price", p == null ? null : p.getPrice());   // 지금 가격 (목표까지 얼마 남았는지 화면에서 계산)
            list.add(row);

        }

        return list;

    }

    @Override
    public void create(Long userId, Long stockId, String condition, BigDecimal targetPrice) {

        if (stockId == null) throw new IllegalArgumentException("종목을 확인해주세요.");
        if (condition == null || !CONDITIONS.contains(condition)) throw new IllegalArgumentException("이상 / 이하를 선택해주세요.");
        if (targetPrice == null || targetPrice.signum() <= 0) throw new IllegalArgumentException("목표가를 입력해주세요.");

        // 이미 조건을 넘은 가격이면 걸자마자 메일이 가니까 막음
        MockTradeVO stock = mockPriceService.getPrice(stockId);

        if (stock != null) {

            BigDecimal now = stock.getPrice();

            if ("ABOVE".equals(condition) && now.compareTo(targetPrice) >= 0) {
                throw new IllegalArgumentException("지금 가격(" + won(now) + "원)이 이미 목표가 이상이에요. 더 높은 가격으로 정해주세요.");
            }

            if ("BELOW".equals(condition) && now.compareTo(targetPrice) <= 0) {
                throw new IllegalArgumentException("지금 가격(" + won(now) + "원)이 이미 목표가 이하예요. 더 낮은 가격으로 정해주세요.");
            }

        }

        if (priceAlertDao.countActiveAlerts(userId) >= MAX_ACTIVE) {
            throw new IllegalArgumentException("알림은 " + MAX_ACTIVE + "개까지 켜둘 수 있어요. 안 쓰는 알림을 지워주세요.");
        }

        PriceAlertVO vo = new PriceAlertVO();
        vo.setUserId(userId);
        vo.setStockId(stockId);
        vo.setCondition(condition);
        vo.setTargetPrice(targetPrice.setScale(0, java.math.RoundingMode.DOWN));

        if (priceAlertDao.insertAlert(vo) == 0) throw new IllegalArgumentException("지원하지 않는 종목입니다.");

    }

    @Override
    public void delete(Long userId, Long alertId) {

        PriceAlertVO vo = new PriceAlertVO();
        vo.setUserId(userId);
        vo.setAlertId(alertId);

        if (priceAlertDao.deleteAlert(vo) == 0) throw new IllegalArgumentException("알림을 찾을 수 없습니다.");

    }

    @Override
    public void reactivate(Long userId, Long alertId) {

        PriceAlertVO key = new PriceAlertVO();
        key.setUserId(userId);
        key.setAlertId(alertId);

        PriceAlertVO alert = priceAlertDao.selectMyAlert(key);
        if (alert == null) throw new IllegalArgumentException("알림을 찾을 수 없습니다.");
        if (!"SENT".equals(alert.getStatus())) throw new IllegalArgumentException("이미 켜져 있는 알림이에요.");

        // 지금 가격이 이미 조건을 넘었으면 다시 켜자마자 또 메일이 감 → 막음
        MockTradeVO stock = mockPriceService.getPrice(alert.getStockId());

        if (stock != null && crosses(alert.getCondition(), stock.getPrice(), alert.getTargetPrice())) {
            throw new IllegalArgumentException("지금 가격(" + won(stock.getPrice()) + "원)이 이미 조건을 넘었어요. 새 목표가로 알림을 만들어주세요.");
        }

        if (MAX_ACTIVE <= priceAlertDao.countActiveAlerts(userId)) {
            throw new IllegalArgumentException("알림은 " + MAX_ACTIVE + "개까지 켜둘 수 있어요.");
        }

        priceAlertDao.reactivateAlert(key);

    }

    @Override
    public int checkAndNotify() {

        List<PriceAlertVO> alerts = priceAlertDao.selectActiveAlerts();
        if (alerts.isEmpty()) return 0;

        Map<Long, MockTradeVO> prices = mockPriceService.getPrices(alerts.stream().map(PriceAlertVO::getStockId).toList());
        int sent = 0;

        for (PriceAlertVO a : alerts) {

            MockTradeVO p = prices.get(a.getStockId());

            // 실시간 가격일 때만 (토스 실패로 종가를 쓰는 중이면 기다림)
            if (p == null || !"LIVE".equals(p.getPriceSource())) continue;
            if (!crosses(a.getCondition(), p.getPrice(), a.getTargetPrice())) continue;

            // 먼저 "보냄"으로 잡은 경우에만 발송 (메일이 두 번 가지 않게)
            a.setSentPrice(p.getPrice());
            if (priceAlertDao.markSent(a) == 0) continue;

            // 이메일이 없는 계정(일부 소셜 로그인)은 보낼 곳이 없으니 재시도하지 않고 "보냄"으로 둠
            if (a.getEmail() == null || !a.getEmail().contains("@")) {
                log.info("가격 알림: 이메일이 없는 계정이라 메일 생략 (alert={})", a.getAlertId());
                continue;
            }

            try {

                sendAlertMail(a, p.getPrice());
                sent++;

            } catch (Exception e) {

                log.warn("가격 알림 메일 실패 (다음에 다시 시도): alert={} {}", a.getAlertId(), e.getMessage());
                priceAlertDao.revertSent(a.getAlertId());

            }

        }

        return sent;

    }

    // ============================================
    // 공통
    // ============================================

    // 조건 확인 (이상: 현재가 ≥ 목표가 / 이하: 현재가 ≤ 목표가)
    private boolean crosses(String condition, BigDecimal price, BigDecimal target) {

        return "ABOVE".equals(condition) ? price.compareTo(target) >= 0 : price.compareTo(target) <= 0;

    }

    private void sendAlertMail(PriceAlertVO a, BigDecimal price) {

        String condText = "ABOVE".equals(a.getCondition()) ? "이상" : "이하";
        String link = siteUrl + "/stock?symbol=" + a.getSymbol();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(a.getEmail());
        message.setSubject("[SKLabs] " + a.getName() + " " + won(a.getTargetPrice()) + "원 " + condText + " 도달 🔔");
        message.setText(
                a.getNickname() + "님, 걸어두신 가격 알림이 도착했어요.\n\n"
                        + "종목   : " + a.getName() + " (" + a.getSymbol() + ")\n"
                        + "조건   : " + won(a.getTargetPrice()) + "원 " + condText + "\n"
                        + "현재가 : " + won(price) + "원\n\n"
                        + "종목 보러 가기: " + link + "\n"
                        + "모의투자로 사고팔기: " + siteUrl + "/mock?symbol=" + a.getSymbol() + "\n\n"
                        + "이 알림은 한 번만 보내져요. 마이페이지 > 내 가격 알림에서 다시 켤 수 있어요.\n"
                        + "※ 투자 판단은 본인 책임입니다.");

        mailSender.send(message);

    }

    private String won(BigDecimal value) {

        return String.format("%,d", value.longValue());

    }
}
