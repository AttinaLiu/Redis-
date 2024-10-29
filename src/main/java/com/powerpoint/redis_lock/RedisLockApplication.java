package com.powerpoint.redis_lock;

import org.redisson.Redisson;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RedisLockApplication {

    @Value("${spring.data.redis.port}")
    private int redisPort;
    @Value("${spring.data.redis.host}")
    private String redisHost;


    public static void main(String[] args) {

        SpringApplication.run(RedisLockApplication.class, args);

    }

    @Bean
    public Redisson redisson(){
        Config config = new Config();
        config.useSingleServer()
                .setAddress(redisHost + ":" + redisPort)
                .setDatabase(0);
        return (Redisson) Redisson.create(config);
    }
}
