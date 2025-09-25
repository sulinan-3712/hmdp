package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService  seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getOne(new LambdaQueryWrapper<SeckillVoucher>()
                .eq(SeckillVoucher::getVoucherId,  voucherId));
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀还未开始，请耐心等待");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }
        if (voucher.getStock() < 1) {
            return Result.fail("优惠券已被抢光了哦，下次记得手速快点");
        }
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.getResult(voucherId);
        }
    }

    public Result getResult(Long voucherId) {
        // 一人一单
        Long userId = UserHolder.getUser().getId();
        int count = this.count(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getVoucherId, voucherId)
                .eq(VoucherOrder::getUserId, userId));
        if (count > 0) {
            return Result.fail("你已经抢过优惠券了哦");
        }


        LambdaUpdateWrapper<SeckillVoucher> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.setSql("stock = stock - 1")
                .eq(SeckillVoucher::getVoucherId, voucherId)
                //防止超卖(乐观锁)
//                .eq(SeckillVoucher::getStock, voucher.getStock())
                .gt(SeckillVoucher::getStock, 0);
        boolean update = seckillVoucherService.update(updateWrapper);
        if (!update) {
            return Result.fail("优惠券已被抢光了哦，下次记得手速快点");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(new RedisWorker(stringRedisTemplate).setNextId("order"));
        save(voucherOrder);
        return Result.ok(voucherOrder);
    }
}
