package com.findus.backend.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("텍스트 헬스체크의 연결 실패 처리")
class HealthTests {
	// region 일부 연결 실패와 전체 실패
	@Test
	@DisplayName("DB 또는 Redis가 실패해도 두 항목을 모두 확인하고 실패 항목만 표시한다")
	void 실패한_연결만_fail로_표시한다() {
		for (boolean database : new boolean[]{true, false}) {
			for (boolean redisAvailable : new boolean[]{true, false}) {
				var jdbc = mock(JdbcTemplate.class);
				var redis = mock(StringRedisTemplate.class);
				if (database) {
					when(jdbc.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Boolean>>any())).thenReturn(true);
				} else {
					when(jdbc.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Boolean>>any()))
							.thenThrow(new DataAccessResourceFailureException("테스트용 내부 DB 오류"));
				}
				if (redisAvailable) {
					when(redis.execute(org.mockito.ArgumentMatchers.<RedisCallback<String>>any())).thenReturn("PONG");
				} else {
					when(redis.execute(org.mockito.ArgumentMatchers.<RedisCallback<String>>any()))
							.thenThrow(new DataAccessResourceFailureException("테스트용 내부 Redis 오류"));
				}
				var result = new HealthServiceImpl(jdbc, redis).check();
				assertThat(result.database()).isEqualTo(database);
				assertThat(result.redis()).isEqualTo(redisAvailable);
				assertThat(result.healthy()).isEqualTo(database && redisAvailable);
				assertThat(result.text()).doesNotContain("내부", "오류");
				verify(jdbc).execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Boolean>>any());
				verify(redis).execute(org.mockito.ArgumentMatchers.<RedisCallback<String>>any());
			}
		}
	}
	// endregion

	// region 프론트에서 받는 실제 응답 형식
	@Test
	@DisplayName("정상은 200, 연결 실패는 503으로 응답하고 JSON 대신 세 줄의 텍스트를 제공한다")
	void 연결_상태에_맞는_HTTP응답을_반환한다() throws Exception {
		for (boolean database : new boolean[]{true, false}) {
			for (boolean redis : new boolean[]{true, false}) {
				HealthService service = () -> new HealthStatus(database, redis);
				var mvc = MockMvcBuilders.standaloneSetup(new HealthController(service)).build();
				mvc.perform(get("/health")).andExpect(status().is(database && redis ? 200 : 503))
						.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
						.andExpect(header().string("Cache-Control", "no-store"))
						.andExpect(content().string("Spring Boot: ok\nPostgreSQL: " + (database ? "ok" : "fail")
								+ "\nRedis: " + (redis ? "ok" : "fail")));
			}
		}
	}
	// endregion
}
