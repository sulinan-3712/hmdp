package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;

@Component
public class RedisClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     *  缓存穿透
     */
    public <R, ID> R cachePenetration(String keyPrefix, ID id, Class<R> type,
                                      Function<ID, R> dbFallBack, Long time, TimeUnit timeUnit) {
        // 1. 从redis查询缓存，存在返回数据，不存在查询数据库
        String json = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        if (StrUtil.isNotEmpty(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }
        R r = dbFallBack.apply(id);
        // 数据库没查询到，就存储空值，防止穿透
        if (r == null) {
            stringRedisTemplate.opsForValue().set(keyPrefix + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 2. 保存数据到redis，返回数据库查询的数据
        stringRedisTemplate.opsForValue().set(keyPrefix + id, JSONUtil.toJsonStr(r), time, timeUnit);
        return r;
    }

    /**
     *  缓存击穿(互斥锁)
     */
    public <R, ID> R cacheBreakdownWithMutex(String keyPrefix, ID id, Class<R> type,
                                      Function<ID, R> dbFallBack, Long time, TimeUnit timeUnit) {
        // 1. 从redis查询缓存，存在返回数据，不存在查询数据库
        String json = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        if (StrUtil.isNotEmpty(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }
        // 缓存没查询到数据就加锁
        R r = null;
        try {
            boolean lock = tryLock(RedisConstants.LOCK_SHOP_KEY + id);
            if (!lock) {
                Thread.sleep(30);
                return cacheBreakdownWithMutex(keyPrefix, id, type, dbFallBack, time, timeUnit);
            }
            r = dbFallBack.apply(id);
            if (r == null) {
                stringRedisTemplate.opsForValue().set(keyPrefix + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 2. 保存数据到redis，返回数据库查询的数据
            stringRedisTemplate.opsForValue().set(keyPrefix + id, JSONUtil.toJsonStr(r), time, timeUnit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock();
        }
        return r;
    }

    /**
     *  缓存击穿(逻辑过期)
     */
    private static final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public <R, ID> R cacheBreakdownWithExpire(String keyPrefix, ID id, Class<R> type,
                                             Function<ID, R> dbFallBack, Long time, TimeUnit timeUnit) {
        // 1. 从redis查询缓存，存在返回数据，不存在查询数据库
        String json = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        if (StrUtil.isEmpty(json)) {
            return null;
        }
        // 查询到解析对象返回
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        // 获取互斥锁
        try {
            boolean lock = tryLock(RedisConstants.LOCK_SHOP_KEY + id);
            if (lock) {
                executorService.submit(() -> {
                    R result = dbFallBack.apply(id);
                    RedisData data = new RedisData();
                    data.setExpireTime(LocalDateTime.now().plusSeconds(time));
                    data.setData(result);
                    stringRedisTemplate.opsForValue().set(keyPrefix + id, JSONUtil.toJsonStr(data), time, timeUnit);
                });
                return r;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            unLock();
        }
        return r;
    }

    private boolean tryLock(String key) {
        Boolean absent = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(absent);
    }

    private void  unLock() {
        stringRedisTemplate.delete(RedisConstants.LOCK_SHOP_KEY);
    }

}
