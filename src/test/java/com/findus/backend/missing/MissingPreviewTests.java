package com.findus.backend.missing;

import com.findus.backend.missing.controller.MissingPreviewController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MissingPreviewTests {
    @Test void 로컬_확인화면은_실제데이터_없이_DB조회코드만_제공하고_캐시하지않는다() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new MissingPreviewController()).build();
        var response=mvc.perform(get("/test/safe182")).andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
        assertThat(response.getContentAsString()).contains("/api/missing-persons?page=", "/api/admin/missing-sync", "credentials:'omit'")
                .doesNotContain("data:image/", "{{CARDS}}", "tknphotoFile", "authKey", "innerHTML");
    }
    @Test void 확인페이지는_local프로필에서만_등록된다() {
        for (String profile:new String[]{"local","prod"}) {
            try (var context=new AnnotationConfigApplicationContext()) {
                context.getEnvironment().setActiveProfiles(profile);
                context.register(MissingPreviewController.class);
                context.refresh();
                assertThat(context.getBeansOfType(MissingPreviewController.class)).hasSize(profile.equals("local")?1:0);
            }
        }
    }
}
