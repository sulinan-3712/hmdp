package com.hmdp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DataXConfig {

    @Value("${datax.home:D://datax}")
    private String dataxHome;

    @Value("${datax.job.path:D://datax//job}")
    private String jobPath;

    public String getDataxHome() {
        return dataxHome;
    }

    public String getJobPath() {
        return jobPath;
    }
}