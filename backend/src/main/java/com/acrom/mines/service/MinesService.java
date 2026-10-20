package com.acrom.mines.service;

import com.acrom.mines.model.Game;
import com.acrom.mines.repository.GameRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

@Service
public class MinesService {

    private static final Map<String, int[]> DIFFS = new LinkedHashMap<>();
    static {
        DIFFS.put("EASY", new int[]{9, 9, 10});      // 9x9, 10 mayin
        DIFFS.put("MEDIUM", new int[]{12, 12, 25});  // 12x12, 25 mayin
        DIFFS.put("HARD", new int[]{16, 16, 45});    // 16x16, 45 mayin
    }

    private final GameRepository repo;

    public MinesService(GameRepository repo) {
        this.repo = repo;
    }

    public Map<String, Object> create(String player, String difficulty) {
        String name = player == null ? "" : player.trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Oyuncu adi gerekli");
        }
        if (name.length() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Oyuncu adi en fazla 20 karakter");
        }
        String d = difficulty == null ? "EASY" : difficulty.trim().toUpperCase();
        int[] spec = DIFFS.get(d);
        if (spec == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zorluk EASY|MEDIUM|HARD olmali");
        }

        Game g = new Game();
        g.player = name;
        g.difficulty = d;
        g.width = spec[0];
        g.height = spec[1];
        g.mineCount = spec[2];
        g.mines = "";
        g.revealed = "";
        g.flagged = "";
        g.status = "ACTIVE";
        repo.save(g);
        return view(g);
    }

    public Map<String, Object> state(Long id) {
        return view(get(id));
    }

    public Map<String, Object> reveal(Long id, int row, int col) {
        Game g = get(id);
        if (!"ACTIVE".equals(g.status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Oyun bitti (status=" + g.status + ")");
        }
        int idx = checkCell(g, row, col);

        Set<Integer> mines = parse(g.mines);
        Set<Integer> revealed = parse(g.revealed);
        Set<Integer> flagged = parse(g.flagged);

        if (flagged.contains(idx)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Isaretli hucreyi acamazsin; once isareti kaldir");
        }
        if (revealed.contains(idx)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hucre zaten acik");
        }

        // ilk acilis guvenli: mayinlar ilk tikhlamada yerlesir (tikhlanan + komsulari haric)
        if (mines.isEmpty()) {
            mines = placeMines(g, idx);
            g.mines = csv(mines);
        }

        if (mines.contains(idx)) {
            g.status = "LOST";
            for (int i = 0; i < g.width * g.height; i++) revealed.add(i);
            flagged.clear();
            finish(g);
        } else {
            flood(g, idx, mines, revealed);
            if (revealed.size() >= g.width * g.height - g.mineCount) {
                g.status = "WON";
                for (int i = 0; i < g.width * g.height; i++) revealed.add(i);
                finish(g);
            }
        }

        g.revealed = csv(revealed);
        g.flagged = csv(flagged);
        repo.save(g);
        return view(g);
    }

    public Map<String, Object> flag(Long id, int row, int col) {
        Game g = get(id);
        if (!"ACTIVE".equals(g.status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Oyun bitti (status=" + g.status + ")");
        }
        int idx = checkCell(g, row, col);

        Set<Integer> mines = parse(g.mines);
        Set<Integer> revealed = parse(g.revealed);
        Set<Integer> flagged = parse(g.flagged);

        if (revealed.contains(idx)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acik hucre isaretlenemez");
        }
        if (flagged.contains(idx)) {
            flagged.remove(idx);
        } else {
            if (mines.isEmpty()) {
                // mayinlar henuz yerlesmedi; sadece isearet Sakla
                flagged.add(idx);
            } else {
                if (flagged.size() >= g.mineCount) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Butun bayraklar kullanildi");
                }
                flagged.add(idx);
            }
        }
        g.flagged = csv(flagged);
        repo.save(g);
        return view(g);
    }

    public List<Map<String, Object>> leaderboard(String difficulty) {
        List<Game> games;
        if (difficulty == null || difficulty.trim().isEmpty()) {
            games = repo.findTop10ByStatusOrderByDurationSecAsc("WON");
        } else {
            String d = difficulty.trim().toUpperCase();
            if (!DIFFS.containsKey(d)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zorluk EASY|MEDIUM|HARD olmali");
            }
            games = repo.findTop10ByStatusAndDifficultyOrderByDurationSecAsc("WON", d);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Game g : games) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("player", g.player);
            m.put("difficulty", g.difficulty);
            m.put("durationSec", g.durationSec);
            m.put("endedAt", g.endedAt);
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalGames", repo.count());
        out.put("active", repo.countByStatus("ACTIVE"));
        out.put("won", repo.countByStatus("WON"));
        out.put("lost", repo.countByStatus("LOST"));
        Game best = repo.findFirstByStatusOrderByDurationSecAsc("WON");
        out.put("fastestWinSec", best == null ? null : best.durationSec);
        return out;
    }

    // ---- yardimcilar ----

    private Game get(Long id) {
        Game g = repo.findById(id).orElse(null);
        if (g == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Oyun bulunamadi: " + id);
        }
        return g;
    }

    private int checkCell(Game g, int row, int col) {
        if (row < 0 || row >= g.height || col < 0 || col >= g.width) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Hucre sinir disi: satir 0-" + (g.height - 1) + ", sutun 0-" + (g.width - 1) + " olmali");
        }
        return row * g.width + col;
    }

    private Set<Integer> placeMines(Game g, int safeIdx) {
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < g.width * g.height; i++) {
            if (i == safeIdx) continue;
            boolean isNeighbor = false;
            for (int n : neighbors(safeIdx, g.width, g.height)) {
                if (n == i) { isNeighbor = true; break; }
            }
            if (!isNeighbor) pool.add(i);
        }
        Collections.shuffle(pool, new Random());
        Set<Integer> mines = new TreeSet<>();
        for (int i = 0; i < g.mineCount && i < pool.size(); i++) mines.add(pool.get(i));
        return mines;
    }

    private void flood(Game g, int start, Set<Integer> mines, Set<Integer> revealed) {
        Deque<Integer> q = new ArrayDeque<>();
        q.add(start);
        while (!q.isEmpty()) {
            int cur = q.poll();
            if (revealed.contains(cur)) continue;
            revealed.add(cur);
            if (adjacentMines(g, mines, cur) == 0) {
                for (int n : neighbors(cur, g.width, g.height)) {
                    if (!revealed.contains(n) && !mines.contains(n) && !flaggedNot(g, n)) {
                        q.add(n);
                    }
                }
            }
        }
    }

    private boolean flaggedNot(Game g, int idx) {
        return parse(g.flagged).contains(idx);
    }

    private int adjacentMines(Game g, Set<Integer> mines, int idx) {
        int c = 0;
        for (int n : neighbors(idx, g.width, g.height)) {
            if (mines.contains(n)) c++;
        }
        return c;
    }

    private List<Integer> neighbors(int idx, int w, int h) {
        int r = idx / w, c = idx % w;
        List<Integer> out = new ArrayList<>(8);
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = r + dr, nc = c + dc;
                if (nr >= 0 && nr < h && nc >= 0 && nc < w) out.add(nr * w + nc);
            }
        }
        return out;
    }

    private void finish(Game g) {
        g.endedAt = LocalDateTime.now();
        long sec = Duration.between(g.startedAt, g.endedAt).getSeconds();
        g.durationSec = Math.max(1, sec);
    }

    private Set<Integer> parse(String csv) {
        Set<Integer> out = new TreeSet<>();
        if (csv == null || csv.isEmpty()) return out;
        for (String s : csv.split(",")) {
            try {
                out.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignore) {
                // bozuk deger yoksayilir
            }
        }
        return out;
    }

    private String csv(Set<Integer> set) {
        if (set == null || set.isEmpty()) return "";
        Set<Integer> sorted = new TreeSet<>(set);
        StringBuilder sb = new StringBuilder();
        for (Integer i : sorted) {
            if (sb.length() > 0) sb.append(',');
            sb.append(i);
        }
        return sb.toString();
    }

    private Map<String, Object> view(Game g) {
        boolean expose = !"ACTIVE".equals(g.status);
        Set<Integer> mines = parse(g.mines);
        Set<Integer> revealed = parse(g.revealed);
        Set<Integer> flagged = parse(g.flagged);

        List<List<Map<String, Object>>> board = new ArrayList<>();
        for (int r = 0; r < g.height; r++) {
            List<Map<String, Object>> row = new ArrayList<>();
            for (int c = 0; c < g.width; c++) {
                int i = r * g.width + c;
                Map<String, Object> cell = new LinkedHashMap<>();
                boolean rev = revealed.contains(i);
                cell.put("revealed", rev);
                if (rev && expose && mines.contains(i)) {
                    cell.put("mine", true);
                } else if (rev) {
                    cell.put("v", adjacentMines(g, mines, i));
                }
                cell.put("flag", flagged.contains(i));
                row.add(cell);
            }
            board.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", g.id);
        out.put("player", g.player);
        out.put("difficulty", g.difficulty);
        out.put("width", g.width);
        out.put("height", g.height);
        out.put("mineCount", g.mineCount);
        int flagsLeft = g.mineCount - flagged.size();
        out.put("flagsLeft", Math.max(0, flagsLeft));
        out.put("status", g.status);
        out.put("durationSec", g.durationSec);
        out.put("board", board);
        return out;
    }
}
