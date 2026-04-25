package com.game.web;

import com.game.domain.Direction;
import com.game.domain.GameState;
import com.game.domain.MazePoint;
import com.game.domain.PlayerId;
import com.game.service.MazeService;
import com.game.memory.MemoryGameState;
import com.game.service.MemoryService;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/game")
public class GameController {
    private static final String SESSION_MAZE_KEY = "maze-state";
    private static final String SESSION_MAZE_LEVEL_KEY = "maze-level";
    private static final String SESSION_GAME_MODE_KEY = "game-mode";
    private static final String SESSION_MEMORY_KEY = "memory-state";
    private final MazeService mazeService;
    private final MemoryService memoryService;

    public GameController(MazeService mazeService, MemoryService memoryService) {
        this.mazeService = mazeService;
        this.memoryService = memoryService;
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
        GameState state = mazeService.generate(cols, rows, level, appliedMode);
        int appliedLevel = MazeService.MODE_CHASE.equals(appliedMode) ? 1 : mazeService.clampLevel(level);
        session.setAttribute(SESSION_MAZE_KEY, state);
        session.setAttribute(SESSION_MAZE_LEVEL_KEY, appliedLevel);
        session.setAttribute(SESSION_GAME_MODE_KEY, appliedMode);
        return GameResponse.from(state, true, true, null, appliedLevel, appliedMode);
    }

    @PostMapping("/move")
    public GameResponse move(@RequestBody MoveRequest request, HttpSession session) {
        GameState state = (GameState) session.getAttribute(SESSION_MAZE_KEY);
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
            String gameMode = (String) session.getAttribute(SESSION_GAME_MODE_KEY);
            String appliedMode = normalizeGameMode(gameMode);
            MazeService.MoveResult moveResult = mazeService.tryMove(state, playerId, direction, appliedMode, chaseChaserId);
            Integer appliedLevel = (Integer) session.getAttribute(SESSION_MAZE_LEVEL_KEY);
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

    @PostMapping("/memory/start")
    public MemoryResponse startMemory(@RequestBody(required = false) MemoryStartRequest request, HttpSession session) {
        int rows = request == null || request.getRows() == null ? 4 : request.getRows();
        int cols = request == null || request.getCols() == null ? 6 : request.getCols();
        List<String> vocabulary;
        try {
            vocabulary = memoryService.loadVocabulary();
        } catch (IOException ex) {
            vocabulary = Collections.emptyList();
        }
        if (vocabulary.size() < 12) {
            vocabulary = getFallbackVocabulary();
        }
        try {
            MemoryGameState state = memoryService.start(rows, cols, vocabulary);
            session.setAttribute(SESSION_MEMORY_KEY, state);
            session.setAttribute(SESSION_GAME_MODE_KEY, "MEMORY_MATCH");
            return MemoryResponse.from(state, false);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/memory/state")
    public MemoryResponse memoryState(HttpSession session) {
        MemoryGameState state = (MemoryGameState) session.getAttribute(SESSION_MEMORY_KEY);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please start memory game first.");
        }
        return MemoryResponse.from(state, false);
    }

    @PostMapping("/memory/flip")
    public MemoryResponse flipMemory(@RequestBody MemoryFlipRequest request, HttpSession session) {
        MemoryGameState state = (MemoryGameState) session.getAttribute(SESSION_MEMORY_KEY);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please start memory game first.");
        }
        if (request == null || request.getIndex() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "index is required.");
        }
        MemoryService.TurnResult turnResult = memoryService.flip(state, request.getIndex());
        return MemoryResponse.from(state, turnResult.isMismatchNeedsResolve());
    }

    @PostMapping("/memory/resolve")
    public MemoryResponse resolveMemory(HttpSession session) {
        MemoryGameState state = (MemoryGameState) session.getAttribute(SESSION_MEMORY_KEY);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please start memory game first.");
        }
        memoryService.resolveMismatch(state);
        return MemoryResponse.from(state, false);
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
            return MazeService.MODE_TWIN_RACE;
        }
        String normalized = value.trim().toUpperCase();
        if (MazeService.MODE_CHASE.equals(normalized)) {
            return MazeService.MODE_CHASE;
        }
        return MazeService.MODE_TWIN_RACE;
    }

    private List<String> getFallbackVocabulary() {
        return java.util.Arrays.asList(
                "Apple", "Cat", "Dog", "Sun", "Book", "Fish", "Tree", "Milk", "Bird", "Cake",
                "Ball", "Star", "Moon", "Hand", "Duck", "Bear", "Lion", "Frog", "Ship", "Leaf",
                "Rain", "Cloud", "Smile", "Bread", "Flower"
        );
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

    public static class MemoryStartRequest {
        private Integer rows;
        private Integer cols;

        public Integer getRows() {
            return rows;
        }

        public void setRows(Integer rows) {
            this.rows = rows;
        }

        public Integer getCols() {
            return cols;
        }

        public void setCols(Integer cols) {
            this.cols = cols;
        }
    }

    public static class MemoryFlipRequest {
        private Integer index;

        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
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
            response.level = level == null ? MazeService.DEFAULT_LEVEL : level;
            response.gameMode = gameMode == null ? MazeService.MODE_TWIN_RACE : gameMode;
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

    public static class MemoryCardDto {
        private final String id;
        private final String word;
        private final boolean flipped;
        private final boolean matched;

        public MemoryCardDto(String id, String word, boolean flipped, boolean matched) {
            this.id = id;
            this.word = word;
            this.flipped = flipped;
            this.matched = matched;
        }

        public String getId() {
            return id;
        }

        public String getWord() {
            return word;
        }

        public boolean isFlipped() {
            return flipped;
        }

        public boolean isMatched() {
            return matched;
        }
    }

    public static class MemoryResponse {
        private List<MemoryCardDto> deck;
        private int rows;
        private int cols;
        private String currentPlayer;
        private Map<String, Integer> scoreBoard;
        private boolean gameOver;
        private String winner;
        private boolean inputLocked;
        private boolean pendingResolve;

        public static MemoryResponse from(MemoryGameState state, boolean pendingResolve) {
            MemoryResponse response = new MemoryResponse();
            response.deck = state.getDeck().stream()
                    .map(c -> new MemoryCardDto(c.getId(), c.getWord(), c.isFlipped(), c.isMatched()))
                    .collect(Collectors.toList());
            response.rows = state.getRows();
            response.cols = state.getCols();
            response.currentPlayer = state.getCurrentPlayer().name();
            response.scoreBoard = state.getScoreBoard().entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue));
            response.gameOver = state.isGameOver();
            response.winner = state.getWinner() == null ? null : state.getWinner().name();
            response.inputLocked = state.isInputLocked();
            response.pendingResolve = pendingResolve || state.isPendingResolve();
            return response;
        }

        public List<MemoryCardDto> getDeck() {
            return deck;
        }

        public int getRows() {
            return rows;
        }

        public int getCols() {
            return cols;
        }

        public String getCurrentPlayer() {
            return currentPlayer;
        }

        public Map<String, Integer> getScoreBoard() {
            return scoreBoard;
        }

        public boolean isGameOver() {
            return gameOver;
        }

        public String getWinner() {
            return winner;
        }

        public boolean isInputLocked() {
            return inputLocked;
        }

        public boolean isPendingResolve() {
            return pendingResolve;
        }
    }
}
