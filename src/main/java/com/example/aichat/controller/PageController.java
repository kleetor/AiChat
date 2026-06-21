package com.example.aichat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/chat")
    public String chat() {
        return "index";
    }

    @GetMapping("/prompt-hub")
    public String promptHub() {
        return "promptHub";
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
