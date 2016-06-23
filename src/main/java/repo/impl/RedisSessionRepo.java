package repo.impl;

import objects.SerializableFormSession;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import repo.SessionRepo;

import java.util.Map;

/**
 * Created by willpride on 1/19/16.
 */
public class RedisSessionRepo implements SessionRepo{

    @Autowired
    private RedisTemplate<String, SerializableFormSession> redisTemplate;

    private final Log log = LogFactory.getLog(RedisSessionRepo.class);
    private static final String SESSION_KEY = "formplayer-session";

    @Override
    public void save(SerializableFormSession session) {
        log.info("Saving Session " +  session.getInstanceXml());
        this.redisTemplate.opsForHash().put(SESSION_KEY, session.getId(), session);
    }

    @Override
    public SerializableFormSession find(String id) {
        SerializableFormSession ret = (SerializableFormSession) this.redisTemplate.opsForHash().get(SESSION_KEY, id);
        log.info("Returning Session: " + ret.getInstanceXml());
        return ret;
    }

    @Override
    public Map<Object, Object> findAll() {
        log.info("Getting all keys");
        return this.redisTemplate.opsForHash().entries(SESSION_KEY);

    }

    @Override
    public void delete(String id) {
        this.redisTemplate.opsForHash().delete(SESSION_KEY,id);
    }
}