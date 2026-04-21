package com.maze.web;

import com.maze.game.Direction;
import com.maze.game.GameState;
import com.maze.game.MazePoint;
import com.maze.game.MazeService;
import com.maze.game.PlayerId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/maze")
public class MazeController {
    private static final String SESSION_KEY = "maze-state";
    private static final String SESSION_LEVEL_KEY = "maze-level";
    private final MazeService mazeService;

    public MazeController(MazeService mazeService) {
        this.mazeService = mazeService;
    }

    @PostMapping("/generate")
    public MazeResponse generate(@RequestBody(required = false) GenerateRequest request, HttpSession session) {
        return reset(request, session);
    }

    @PostMapping("/reset")
    public MazeResponse reset(@RequestBody(required = false) GenerateRequest request, HttpSession session) {
        Integer cols = request == null ? null : request.getCols();
        Integer rows = request == null ? null : request.getRows();
        Integer level = request == null ? null : request.getLevel();
        GameState state = mazeService.generate(cols, rows, level);
        int appliedLevel = mazeService.clampLevel(level);
        session.setAttribute(SESSION_KEY, state);
        session.setAttribute(SESSION_LEVEL_KEY, appliedLevel);
        return MazeResponse.from(state, true, true, null, appliedLevel);
    }

    @PostMapping("/move")
    public MazeResponse move(@RequestBody MoveRequest request, HttpSession session) {
        GameState state = (GameState) session.getAttribute(SESSION_KEY);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please generate maze first.");
        }
        if (request == null || request.getDirection() == null || request.getPlayerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Direction and playerId are required.");
        }
        Direction direction = parseDirection(request.getDirection());
        PlayerId playerId = parsePlayerId(request.getPlayerId());
        state.getLock().lock();
        try {
            MazeService.MoveResult moveResult = mazeService.tryMove(state, playerId, direction);
            Integer appliedLevel = (Integer) session.getAttribute(SESSION_LEVEL_KEY);
            return MazeResponse.from(state, moveResult.isMoved(), false, moveResult.getWinner(), appliedLevel);
        } finally {
            state.getLock().unlock();
        }
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

    public static class GenerateRequest {
        private Integer cols;
        private Integer rows;
        private Integer level;

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
    }

    public static class MoveRequest {
        private String direction;
        private String playerId;

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
    }

    public static class MazeResponse {
        private int[][] maze;
        private Map<String, PointDto> players;
        private PointDto goal;
        private Map<String, List<MazePoint>> breadcrumbs;
        private boolean moved;
        private boolean gameOver;
        private String winner;
        private int level;

        public static MazeResponse from(GameState state, boolean moved, boolean includeMaze, PlayerId winnerId,
                                        Integer level) {
            MazeResponse response = new MazeResponse();
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
            response.level = level == null ? MazeService.DEFAULT_LEVEL : level;
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
