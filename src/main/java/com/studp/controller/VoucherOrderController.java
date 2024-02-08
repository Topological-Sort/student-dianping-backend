package com.studp.controller;


import com.studp.dto.Result;
import com.studp.service.IVoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@Slf4j
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    /**
     * 用户下单秒杀券
     * @param voucherId 代金券id（秒杀券）
     * @return 订单id
     */
    @PostMapping("seckill/{id}")
    public Result<Long> seckillVoucher(@PathVariable("id") Long voucherId) throws InterruptedException {
        log.info("【VoucherOrder/seckillVoucher】用户抢购秒杀券：id={}", voucherId);
        return voucherOrderService.saveSeckillVoucherOrder(voucherId);
    }
}
