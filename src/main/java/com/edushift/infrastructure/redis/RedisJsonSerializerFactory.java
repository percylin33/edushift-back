package com.edushift.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * JSON serializers for Redis values with Java 8 date/time support.
 */
public final class RedisJsonSerializerFactory {

	private RedisJsonSerializerFactory() {
	}

	public static RedisSerializer<Object> jsonSerializer() {
		return new GenericJackson2JsonRedisSerializer(redisObjectMapper());
	}

	static ObjectMapper redisObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return mapper;
	}

}
