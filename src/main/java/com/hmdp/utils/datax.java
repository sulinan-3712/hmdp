package com.hmdp.utils;

import com.alibaba.datax.core.Engine;

public class datax {
    public static void main(String[] args) {
        String getCurrentClasspath = "D:\\datax";
        System.setProperty("datax.home", getCurrentClasspath);
        String[] datxArgs = {"-job", "D:\\datax\\job\\job.json", "-mode", "standalone", "-jobid", "-1"};
        try {
            Engine.entry(datxArgs);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}

