package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        List<ShopType> result = new ArrayList<>();
        // 1. 从redis查询缓存，存在返回数据，不存在查询数据库
        List<String> list = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE_KEY, 0, -1);
        if (CollectionUtil.isNotEmpty(list)) {
            assert list != null;
            for (String json :list) {
                ShopType shopType;
                shopType = JSONUtil.toBean(json, ShopType.class);
                result.add(shopType);
            }
            return Result.ok(result);

        }
        List<ShopType> typeList = this
                .query().orderByAsc("sort").list();
        if (CollectionUtil.isEmpty(typeList)) {
            return Result.fail("店铺为空");
        }
        List<String> stringList = new ArrayList<>();
        typeList.forEach(shopType -> {
            String json = JSONUtil.toJsonStr(shopType);
            stringList.add(json);
        });
        // 2. 保存数据到redis，返回数据库查询的数据
        stringRedisTemplate.opsForList().leftPushAll(RedisConstants.CACHE_SHOP_TYPE_KEY, stringList);
        return Result.ok(typeList);
    }
}
