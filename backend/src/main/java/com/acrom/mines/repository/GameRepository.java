package com.acrom.mines.repository;

import com.acrom.mines.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GameRepository extends JpaRepository<Game, Long> {
    List<Game> findTop10ByStatusAndDifficultyOrderByDurationSecAsc(String status, String difficulty);
    List<Game> findTop10ByStatusOrderByDurationSecAsc(String status);
    Game findFirstByStatusOrderByDurationSecAsc(String status);
    long countByStatus(String status);
}
