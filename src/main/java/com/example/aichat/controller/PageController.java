package com.example.aichat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/chat")
    public String chat() {
        return "forward:/index.html";
    }

    @GetMapping("/prompt-hub")
    public String promptHub() {
        return "workshop";
    }

    @GetMapping("/workshop")
    public String workshop() {
        return "workshop";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }

    @GetMapping("/kb-manager")
    public String kbManager() {
        return "kbManager";
    }

    @GetMapping("/memory-manager")
    public String memoryManager() {
        return "memoryManager";
    }
}
