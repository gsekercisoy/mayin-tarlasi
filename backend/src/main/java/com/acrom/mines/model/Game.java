package com.acrom.mines.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@Entity
@Table(name = "games")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty("id")
    public Long id;

    @NotBlank
    @Size(min = 1, max = 20)
    @Column(nullable = false, length = 20)
    @JsonProperty("player")
    public String player;

    @NotBlank
    @Column(nullable = false, length = 10)
    @JsonProperty("difficulty")
    public String difficulty;

    @Column(nullable = false)
    @JsonProperty("width")
    public int width;

    @Column(nullable = false)
    @JsonProperty("height")
    public int height;

    @Column(nullable = false)
    @JsonProperty("mineCount")
    public int mineCount;

    /** Mayin indeksleri CSV (r*w+c) — view'a sadece oyun bitince expose edilir. */
    @Column(nullable = false, length = 1200)
    @JsonProperty("mines")
    public String mines = "";

    @Column(nullable = false, length = 1600)
    @JsonProperty("revealed")
    public String revealed = "";

    @Column(nullable = false, length = 1200)
    @JsonProperty("flagged")
    public String flagged = "";

    @Column(nullable = false, length = 10)
    @JsonProperty("status")
    public String status = "ACTIVE";

    @JsonProperty("startedAt")
    public LocalDateTime startedAt = LocalDateTime.now();

    @JsonProperty("endedAt")
    public LocalDateTime endedAt;

    @JsonProperty("durationSec")
    public Long durationSec;
}
