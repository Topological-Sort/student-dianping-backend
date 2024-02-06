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

    Result<Long> saveSeckillVoucher(Long voucherId);

    Result<Long> createVoucherOrder(Long voucherId);
}
