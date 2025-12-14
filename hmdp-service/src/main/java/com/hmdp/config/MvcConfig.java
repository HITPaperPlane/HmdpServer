package com.hmdp.config;

import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);

        registry.addInterceptor(new LoginInterceptor()).
                excludePathPatterns("/user/login",
                                    "/user/code",
                                    "/admin/login",
                                    "/merchant/login",
                                    "/merchant/code",
                                    "/user/uv",
                                    "/blog/hot",
                                    "/shop/**",
                                    "/shop-type/**",
                                    "/upload/**",
                                    "/imgs/**",
                                    "/voucher/**").order(1);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absoluteDir = new File(SystemConstants.IMAGE_UPLOAD_DIR).getAbsolutePath();
        String location = "file:" + (absoluteDir.endsWith(File.separator) ? absoluteDir : absoluteDir + File.separator);
        registry.addResourceHandler("/imgs/**").addResourceLocations(location);
    }
}
