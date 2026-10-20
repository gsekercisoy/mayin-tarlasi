package com.acrom.mines.controller;

import com.acrom.mines.model.Game;
import com.acrom.mines.service.MinesService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MinesController {

    private final MinesService service;

    public MinesController(MinesService service) {
        this.service = service;
    }

    @PostMapping("/games")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody Game body) {
        return service.create(body.player, body.difficulty);
    }

    @GetMapping("/games/{id}")
    public Map<String, Object> state(@PathVariable Long id) {
        return service.state(id);
    }

    @PostMapping("/games/{id}/reveal")
    public Map<String, Object> reveal(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.reveal(id, intOf(body.get("row"), -1), intOf(body.get("col"), -1));
    }

    @PostMapping("/games/{id}/flag")
    public Map<String, Object> flag(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.flag(id, intOf(body.get("row"), -1), intOf(body.get("col"), -1));
    }

    @GetMapping("/leaderboard")
    public List<Map<String, Object>> leaderboard(@RequestParam(required = false) String difficulty) {
        return service.leaderboard(difficulty);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return service.stats();
    }

    private static int intOf(Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
