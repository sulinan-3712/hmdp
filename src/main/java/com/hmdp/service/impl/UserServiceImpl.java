package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 2.生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 3.保存验证码到redis,设置有效期
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //4.发送验证码
        log.debug("验证码为: " + code);
        return Result.ok(code);
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        log.debug("进入login方法");
        String phone = loginForm.getPhone();
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 2.校验验证码
        String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (code == null || !code.equals(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }
        // 3.判断用户是否存在,不存在新增用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone, phone);
        User user = baseMapper.selectOne(queryWrapper);
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        log.debug("user: " + user);
        // 4.保存用户到redis，设置有效期
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        CopyOptions copyOptions = CopyOptions.create()
                // 设置字段值编辑器：如果字段值是Long类型，则转换为String
                .setFieldValueEditor((fieldName, fieldValue) -> {
                    if (fieldValue instanceof Long) {
                        // 使用Hutool的Convert类将Long转换为String
                        return Convert.toStr(fieldValue);
                    }
                    // 如果不是Long类型，保持原值
                    return fieldValue;
                });
        HashMap<String, Object> userMap = new HashMap<>();
        BeanUtil.beanToMap(userDTO, userMap, copyOptions);
        String token = UUID.randomUUID().toString();
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, userMap);
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        // 5.返回token,而不是返回用户
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setPassword("123456");
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        this.save(user);
        return user;
    }
}
