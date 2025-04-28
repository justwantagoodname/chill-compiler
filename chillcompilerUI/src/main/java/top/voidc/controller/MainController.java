package top.voidc.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class MainController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("componentsList", List.of(new String[]{"sysY.js", "llvm.js"}));
        return "index";
    }

    // 配置组件目录（根据实际路径调整）
    private static final String COMP_DIR = "static/js/components/";

    @GetMapping("/api/sysycompiler")
    @ResponseBody
    public String sysycompiler(String content) {
        return content;
    }

    @GetMapping("/vs/{*path}")
    public void getEditorMain(HttpServletResponse response, @PathVariable("path") String path) throws IOException {
        System.out.println(path);
        response.sendRedirect("/js/monaco-editor/min/vs/" + path);
    }
}