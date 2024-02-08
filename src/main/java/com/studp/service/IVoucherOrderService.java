package com.studp.service;

import com.studp.dto.Result;
import com.studp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result<Long> saveSeckillVoucherOrder(Long voucherId) throws InterruptedException;

    void createVoucherOrder(VoucherOrder voucherOrder);


    // 悲观锁实现子方法
//    Result<Long> createVoucherOrder(Long voucherId, Long id);
}
