package com.itheima.reggie.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itheima.reggie.common.R;
import com.itheima.reggie.entity.User;
import com.itheima.reggie.service.UserService;
import com.itheima.reggie.utils.SMSUtils;
import com.itheima.reggie.utils.ValidateCodeUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户登录
 */
@RestController
@RequestMapping("/user")
@Slf4j
@Api(tags = "用户登录相关接口")
public class UserController {
    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 发送手机验证码
     * @param user
     * @return
     */
    @PostMapping("/sendMsg")
    @ApiOperation("发送手机验证码接口")
    public R<String> sendMsg(@RequestBody User user, HttpSession session){
        //获取手机号
        String phone = user.getPhone();
        if(StringUtils.isNotEmpty(phone)){
            //生成随机验证码
            String code = ValidateCodeUtils.generateValidateCode(4).toString();
            log.info("code = {}",code);
            //调用阿里云提供的短信服务API完成发送短信
            //SMSUtils.sendMessage("瑞吉外卖",phone,code);

            //需要将生产的验证码保存到Session
            //session.setAttribute(phone,code);

            //将生成的验证码缓存到Redis中，并设置有效期为5分钟
            redisTemplate.opsForValue().set(phone,code,5, TimeUnit.MINUTES);

            return R.success("手机验证码发送成功");
        }
        return R.error("手机验证码发送失败");
    }

    /**
     * 移动端用户登录
     * @param user
     * @param session
     * @return
     */
    @PostMapping("/login")
    @ApiOperation("移动端用户登录接口")
    public R<User> login(@RequestBody Map map, HttpSession session){
        log.info(map.toString());
        //获取手机号
        String phone = map.get("phone").toString();
        //获取验证码
        String code = map.get("code").toString();
        //从session中获取保存的验证码
        //Object codeInSession = session.getAttribute(phone);

        //从Redis中获取缓存的验证码
        Object codeInSession = redisTemplate.opsForValue().get(phone);

        //进行验证码的比对（页面提交的验证码和session中保存的验证码比对）
        if(codeInSession!=null && codeInSession.equals(code)){
            //如果比对成功，登录成功
            LambdaQueryWrapper<User> queryWrapper =new LambdaQueryWrapper<>();
            queryWrapper.eq(User::getPhone,phone);
            User user = userService.getOne(queryWrapper);
            if(user == null){
                //判断当前手机号对应用户是否是新用户，如果是新用户自动完成注册
                user = new User();
                user.setPhone(phone);
                user.setStatus(1);
                userService.save(user);
            }
            session.setAttribute("user",user.getId());

            //如果登录成功，验证码失效
            redisTemplate.delete(phone);
            return R.success(user);
        }
        return R.error("登陆失败，请重试");
    }
}
