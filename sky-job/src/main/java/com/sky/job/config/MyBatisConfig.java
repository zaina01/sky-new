package com.sky.job.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan({
        "com.sky.job.audit.mapper",
        "com.sky.job.notification.mapper",
        "com.sky.job.plugin.storage.mapper",
        "com.sky.job.plugin.callback.mapper",
        "com.sky.job.plugin.artifact.mapper",
        "com.sky.job.scheduler.mapper",
        "com.sky.job.system.mapper",
        "com.sky.job.user.mapper"
})
public class MyBatisConfig {
}
