package com.hmdp;

import com.alibaba.datax.core.Engine;
import com.hmdp.config.DataXConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
public class HmDianPingApplicationTests {

    @Resource
    private DataXConfig dataXConfig;

    @Test
    public void contextLoads() {
        String jobConfigPath = dataXConfig.getJobPath() + "//job.json";
        //这个datax.home一定要配，就是解压的那个文件的datax目录，执行的时候会去这底下找对应的同步插件
        System.setProperty("datax.home",dataXConfig.getDataxHome());
        // 构建参数数组，模拟命令行调用
        String[] args = {"-job", jobConfigPath, "-mode", "standalone", "-jobid", "-1"};

        try {
            // 调用DataX引擎
            Engine.entry(args);
        } catch (Throwable e) {
            log.error(e.getMessage());
        }
    }
}
