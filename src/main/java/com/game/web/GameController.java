package com.game.web;

import com.game.domain.Direction;
import com.game.domain.GameState;
import com.game.domain.MazePoint;
import com.game.domain.PlayerId;
import com.game.service.GameService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/game")
public class GameController {
    private static final String SESSION_KEY = "maze-state";
    private static final String SESSION_LEVEL_KEY = "maze-level";
    private static final String SESSION_MODE_KEY = "maze-mode";
    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/generate")
    public GameResponse generate(@RequestBody(required = false) GenerateRequest request, HttpSession session) {
        return reset(request, session);
    }

    @PostMapping("/reset")
    public GameResponse reset(@RequestBody(required = false) GenerateRequest request, HttpSession session) {
        Integer cols = request == null ? null : request.getCols();
        Integer rows = request == null ? null : request.getRows();
        Integer level = request == null ? null : request.getLevel();
        String gameMode = request == null ? null : request.getGameMode();
        String appliedMode = normalizeGameMode(gameMode);
        GameState state = gameService.generate(cols, rows, level, appliedMode);
        int appliedLevel = GameService.MODE_CHASE.equals(appliedMode) ? 1 : gameService.clampLevel(level);
        session.setAttribute(SESSION_KEY, state);
        session.setAttribute(SESSION_LEVEL_KEY, appliedLevel);
        session.setAttribute(SESSION_MODE_KEY, appliedMode);
        return GameResponse.from(state, true, true, null, appliedLevel, appliedMode);
    }

    @PostMapping("/move")
    public GameResponse move(@RequestBody MoveRequest request, HttpSession session) {
        GameState state = (GameState) session.getAttribute(SESSION_KEY);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please generate maze first.");
        }
        if (request == null || request.getDirection() == null || request.getPlayerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Direction and playerId are required.");
        }
        Direction direction = parseDirection(request.getDirection());
        PlayerId playerId = parsePlayerId(request.getPlayerId());
        PlayerId chaseChaserId = request.getChaseChaserId() == null ? null : parsePlayerId(request.getChaseChaserId());
        state.getLock().lock();
        try {
            String gameMode = (String) session.getAttribute(SESSION_MODE_KEY);
            String appliedMode = normalizeGameMode(gameMode);
            GameService.MoveResult moveResult = gameService.tryMove(state, playerId, direction, appliedMode, chaseChaserId);
            Integer appliedLevel = (Integer) session.getAttribute(SESSION_LEVEL_KEY);
            return GameResponse.from(state, moveResult.isMoved(), false, moveResult.getWinner(), appliedLevel, appliedMode);
        } finally {
            state.getLock().unlock();
        }
    }

    @GetMapping(value = "/vocabulary", produces = MediaType.TEXT_PLAIN_VALUE)
    public String vocabulary() throws IOException {
        ClassPathResource resource = new ClassPathResource("config/dict.txt");
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    private Direction parseDirection(String value) {
        try {
            return Direction.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid direction.");
        }
    }

    private PlayerId parsePlayerId(String value) {
        try {
            return PlayerId.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid playerId.");
        }
    }

    private String normalizeGameMode(String value) {
        if (value == null) {
            return GameService.MODE_TWIN_RACE;
        }
        String normalized = value.trim().toUpperCase();
        if (GameService.MODE_CHASE.equals(normalized)) {
            return GameService.MODE_CHASE;
        }
        return GameService.MODE_TWIN_RACE;
    }

    public static class GenerateRequest {
        private Integer cols;
        private Integer rows;
        private Integer level;
        private String gameMode;

        public Integer getCols() {
            return cols;
        }

        public void setCols(Integer cols) {
            this.cols = cols;
        }

        public Integer getRows() {
            return rows;
        }

        public void setRows(Integer rows) {
            this.rows = rows;
        }

        public Integer getLevel() {
            return level;
        }

        public void setLevel(Integer level) {
            this.level = level;
        }

        public String getGameMode() {
            return gameMode;
        }

        public void setGameMode(String gameMode) {
            this.gameMode = gameMode;
        }
    }

    public static class MoveRequest {
        private String direction;
        private String playerId;
        private String chaseChaserId;

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public String getPlayerId() {
            return playerId;
        }

        public void setPlayerId(String playerId) {
            this.playerId = playerId;
        }

        public String getChaseChaserId() {
            return chaseChaserId;
        }

        public void setChaseChaserId(String chaseChaserId) {
            this.chaseChaserId = chaseChaserId;
        }
    }

    public static class GameResponse {
        private int[][] maze;
        private Map<String, PointDto> players;
        private PointDto goal;
        private Map<String, List<MazePoint>> breadcrumbs;
        private boolean moved;
        private boolean gameOver;
        private String winner;
        private int level;
        private String gameMode;

        public static GameResponse from(GameState state, boolean moved, boolean includeMaze, PlayerId winnerId,
                                        Integer level, String gameMode) {
            GameResponse response = new GameResponse();
            response.maze = includeMaze ? state.getMaze() : null;
            response.goal = new PointDto(state.getGoal().getX(), state.getGoal().getY());
            response.players = state.getPlayers().entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> e.getKey().name(),
                            e -> new PointDto(e.getValue().getX(), e.getValue().getY())
                    ));
            response.breadcrumbs = state.getBreadcrumbs().entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue));
            response.moved = moved;
            response.gameOver = state.isGameOver();
            response.winner = winnerId == null && state.getWinner() != null ? state.getWinner().name()
                    : winnerId == null ? null : winnerId.name();
            response.level = level == null ? GameService.DEFAULT_LEVEL : level;
            response.gameMode = gameMode == null ? GameService.MODE_TWIN_RACE : gameMode;
            return response;
        }

        public int[][] getMaze() {
            return maze;
        }

        public Map<String, PointDto> getPlayers() {
            return players;
        }

        public PointDto getGoal() {
            return goal;
        }

        public Map<String, List<MazePoint>> getBreadcrumbs() {
            return breadcrumbs;
        }

        public boolean isMoved() {
            return moved;
        }

        public boolean isGameOver() {
            return gameOver;
        }

        public String getWinner() {
            return winner;
        }

        public int getLevel() {
            return level;
        }

        public String getGameMode() {
            return gameMode;
        }
    }

    public static class PointDto {
        private final int x;
        private final int y;

        public PointDto(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }
}
