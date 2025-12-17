package com.hmdp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA fallback: let Spring serve index.html for frontend routes on refresh/deep-link.
 * Static assets (e.g. /assets/**) are served by ResourceHandler and are not forwarded here.
 */
@Controller
public class SpaForwardController {

    @GetMapping({
            "/",
            "/login",
            "/profile",
            "/orders",
            "/blogs/**",
            "/shops/**",
            "/merchant/**",
            "/admin/**"
    })
    public String forward() {
        return "forward:/index.html";
    }
}

