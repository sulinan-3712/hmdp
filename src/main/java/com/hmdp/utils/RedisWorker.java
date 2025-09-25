package com.hmdp.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Slf4j
@RequiredArgsConstructor
public class RedisWorker {

    private final StringRedisTemplate stringRedisTemplate;

    //设置起始时间，我这里设定的是2022.01.01 00:00:00
    public static final Long BEGIN_TIMESTAMP = 1640995200L;
    //序列号长度
    public static final Long COUNT_BIT = 32L;

    public long setNextId(String key) {
        long epochSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timestamp = epochSecond - BEGIN_TIMESTAMP;
        String date = new SimpleDateFormat("yyyy:MM:dd").format(new Date());
        long increment = stringRedisTemplate.opsForValue().increment("inc:" + key + ":" + date);
        return timestamp << COUNT_BIT | increment;
    }

}
