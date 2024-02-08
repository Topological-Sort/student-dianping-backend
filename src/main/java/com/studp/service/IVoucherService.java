package com.studp.service;

import com.studp.dto.Result;
import com.studp.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Voucher> {

    Result<List<Voucher>> queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
}
