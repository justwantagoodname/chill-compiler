package top.voidc.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import top.voidc.service.ComponentsService;

import java.io.IOException;

@Controller
public class MainController {

    @Autowired
    ComponentsService componentsService;

    @GetMapping("/")
    public String home(Model model) throws IOException {
        model.addAttribute("componentsList", componentsService.getFilesWithComments("js/components"));
        return "index";
    }

    /**
     * 重定向editor
     */
    @GetMapping("/vs/{*path}")
    public void getEditorMain(HttpServletResponse response, @PathVariable("path") String path) throws IOException {
        response.sendRedirect("/js/monaco-editor/min/vs/" + path);
    }
}