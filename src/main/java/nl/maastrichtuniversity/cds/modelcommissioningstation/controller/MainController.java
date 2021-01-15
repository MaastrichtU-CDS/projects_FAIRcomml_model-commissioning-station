package nl.maastrichtuniversity.cds.modelcommissioningstation.controller;

import nl.maastrichtuniversity.cds.modelcommissioningstation.services.IndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {
    @Autowired
    private IndexService indexService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

}
