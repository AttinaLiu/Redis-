package com.powerpoint.redis_lock.controller;

import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 模型：多线程访问多个后端服务器，这几个后端服务器共用一个Redis
 */

@RestController //每个方法的返回值都会以 JSON 或 XML 的形式直接写入 HTTP 响应体中
public class SecKillController {
    @Autowired
    private StringRedisTemplate srt;

    public static final String REDIS_LOCK = "REDIS_LOCK";

    @Value("${sever.Port}")
    private String severPort;
    @Value("${spring.data.redis.port}")
    private int redisPort;
    @Value("${spring.data.redis.host}")
    private String redisHost;
    @Autowired
    private Redisson redisson;
    String Result = "没抢到";
    int amount;


    @GetMapping("/sc6")
    public String secKillHandeler6(){
        RLock rLock = redisson.getLock(REDIS_LOCK);

        try {
            boolean lockOK = rLock.tryLock();
            if(!lockOK){
                return Result;
            }
            String stock = srt.opsForValue().get("sc:0008");
            amount = stock == null ? 0 : Integer.parseInt(stock);
            if(amount>0) {
                srt.opsForValue().set("sc:0008", String.valueOf(--amount));

            }
        } finally {
            rLock.unlock();
        }
        return "还剩下" + amount;
    }

    /**
     * 仍然存在的问题：a的锁过期后业务还没执行完，此时b又拿到锁执行，修改共享数据，会出现数据不一致
     * 解决方法：使用可重入锁Redisson
     * @return
     */
    @GetMapping("/sc5")
    public String secKillHandeler5(){
        String uuid = UUID.randomUUID().toString();
        try {
            //这是一个原子性操作
            Boolean lock = srt.opsForValue().setIfAbsent(REDIS_LOCK, uuid,5, TimeUnit.SECONDS);
            if(!lock){
                return Result;
            }
            String stock = srt.opsForValue().get("sc:0008");
            amount = stock == null ? 0 : Integer.parseInt(stock);
            if(amount>0) {
                srt.opsForValue().set("sc:0008", String.valueOf(--amount));
            }
        } finally {
            JedisPool jedisPool = new JedisPool(redisHost,redisPort);
            try(Jedis jedis = jedisPool.getResource()) {
                //redis.call()在Lua脚本中执行redis命令
                String script = "if redis.call('get',KEYS[1]) == ARGV[1] " +
                        "then return redis.call('del',KEYS[1]) " +
                        "end " +
                        "return 0";
                //Collections.singletonList 生成一个只有一个元素的列表，减少内存浪费
                //redis使用eval执行lua脚本
                Object eval = jedis.eval(script, Collections.singletonList(REDIS_LOCK), Collections.singletonList(uuid));

                if("1".equals(eval.toString())){
                    System.out.println("释放成功");
                }else {
                    System.out.println("释放异常");
                }
            }
            //使用Lua脚本
            if(srt.opsForValue().get(REDIS_LOCK).equals(uuid)){
                srt.delete(REDIS_LOCK);
            }

        }
        return "还剩下" + amount;
    }

    /**
     * 先判断再删除存在的问题：这个过程是两条语句，不具有原子性，如果判断成功了，但是轮到别人执行了，恰好原来的锁过期了，新的线程拿到了锁，之后轮到原来的
     * 线程执行的时候，因为之前已经判断成功了，所以可以直接删除新的锁，这就会把别人的锁删除
     * 解决方法：使用LUA脚本让删除操作是原子性的
     * @return
     */
    @GetMapping("/sc4")
    public String secKillHandeler4(){
        String uuid = UUID.randomUUID().toString();
        try {
            //这是一个原子性操作
            Boolean lock = srt.opsForValue().setIfAbsent(REDIS_LOCK, uuid,5, TimeUnit.SECONDS);
            if(!lock){
                return Result;
            }
            String stock = srt.opsForValue().get("sc:0008");
            amount = stock == null ? 0 : Integer.parseInt(stock);
            if(amount>0) {
                srt.opsForValue().set("sc:0008", String.valueOf(--amount));

            }
        } finally {
            //先判断，再删除
            if(srt.opsForValue().get(REDIS_LOCK).equals(uuid)){
                srt.delete(REDIS_LOCK);
            }

        }
        return "还剩下" + amount;
    }

    /**
     * 存在的问题：如果业务执行时间比锁的过期时间长，就是还没执行完锁就释放了，其他线程就能拿到锁，然后这个线程执行完后又会删除锁，就把别人的锁删掉了
     * 解决方法：给每个锁加标识，谁加的谁删
     * @return
     */
    @GetMapping("/sc3")
    public String secKillHandeler3(){
        String uuid = UUID.randomUUID().toString();
        try {
            //这是一个原子性操作
            Boolean lock = srt.opsForValue().setIfAbsent(REDIS_LOCK, uuid,5, TimeUnit.SECONDS);
            if(!lock){
                return Result;
            }
            String stock = srt.opsForValue().get("sc:0008");
            amount = stock == null ? 0 : Integer.parseInt(stock);
            if(amount>0) {
                srt.opsForValue().set("sc:0008", String.valueOf(--amount));

            }
        } finally {
            //先判断，再删除
            if(srt.opsForValue().get(REDIS_LOCK).equals(uuid)){
                srt.delete(REDIS_LOCK);
            }

        }
        return "还剩下" + amount;
    }


    /**
     * 无法删除锁的解决方法：为锁添加过期时间
     * @return
     */
    @GetMapping("/sc2")
    public String secKillHandeler2(){
        int amount;
        try {
            //这是一个原子性操作
            Boolean lock = srt.opsForValue().setIfAbsent(REDIS_LOCK, "lock",5, TimeUnit.SECONDS);
            if(!lock){
                return Result;
            }
            String stock = srt.opsForValue().get("sc:0008");
            amount = stock == null ? 0 : Integer.parseInt(stock);
            if(amount>0) {
                srt.opsForValue().set("sc:0008", String.valueOf(--amount));

            }
        } finally {
            srt.delete(REDIS_LOCK);//这里会删除别人的锁
        }
        return "还剩下" + amount;
    }

    /**
     * 添加了锁
     * 添加方法：在Redis中写入一个锁字段，仅在这个字段不存在的时候才能写入成功
     * 锁的作用：如果一个线程添加成功了，说明这个线程拿到了锁，就可以抢购；如果没有添加成功，说明已经有一个线程提前完成了写入拿到了锁。
     * 存在的问题：如果一个线程拿到了锁，但是他在执行抢购的时候服务器宕机了，无法执行释放锁的操作；而且可能删除别人的锁
     * @return
     */
    @GetMapping("/sc1")
    public String secKillHandeler1(){
        int amount;
        try {
            Boolean lock = srt.opsForValue().setIfAbsent(REDIS_LOCK, "lock");
            if(!lock){
                return Result;
            }
            String stock = srt.opsForValue().get("sc:0008");
            amount = stock == null ? 0 : Integer.parseInt(stock);
            if(amount>0) {
                srt.opsForValue().set("sc:0008", String.valueOf(--amount));

            }
        } finally {
            srt.delete(REDIS_LOCK);//这里会删除别人的锁
        }
        return "还剩下" + amount;
    }

    /**
     * 没有锁
     * @return
     */
    @GetMapping("/sc")
    public String secKillHandeler(){
        String stock = srt.opsForValue().get("sc:0008");
        int amount = stock == null ? 0 : Integer.parseInt(stock);

        if(amount>0) {
            srt.opsForValue().set("sc:0008", String.valueOf(--amount));
            return severPort+"还剩下" + amount;
        }else {
            return "来晚了，没有了";
        }
    }

}
