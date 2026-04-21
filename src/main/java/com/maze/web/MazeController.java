package com.maze.web;

import com.maze.game.Direction;
import com.maze.game.MazePoint;
import com.maze.game.MazeService;
import com.maze.game.MazeSessionState;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpSession;
import java.util.List;

@RestController
@RequestMapping("/api/maze")
public class MazeController {
    private static final String SESSION_KEY = "maze-state";
    private final MazeService mazeService;

    public MazeController(MazeService mazeService) {
        this.mazeService = mazeService;
    }

    @PostMapping("/generate")
    public MazeResponse generate(@RequestBody(required = false) GenerateRequest request, HttpSession session) {
        Integer cols = request == null ? null : request.getCols();
        Integer rows = request == null ? null : request.getRows();
        MazeSessionState state = mazeService.generate(cols, rows);
        session.setAttribute(SESSION_KEY, state);
        return MazeResponse.from(state, true, true);
    }

    @PostMapping("/move")
    public MazeResponse move(@RequestBody MoveRequest request, HttpSession session) {
        MazeSessionState state = (MazeSessionState) session.getAttribute(SESSION_KEY);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please generate maze first.");
        }
        if (request == null || request.getDirection() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Direction is required.");
        }
        Direction direction = parseDirection(request.getDirection());
        boolean moved = mazeService.tryMove(state, direction);
        return MazeResponse.from(state, moved, false);
    }

    private Direction parseDirection(String value) {
        try {
            return Direction.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid direction.");
        }
    }

    public static class GenerateRequest {
        private Integer cols;
        private Integer rows;

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
    }

    public static class MoveRequest {
        private String direction;

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }
    }

    public static class MazeResponse {
        private int[][] maze;
        private PointDto player;
        private PointDto start;
        private PointDto goal;
        private List<MazePoint> breadcrumbs;
        private boolean moved;
        private boolean won;

        public static MazeResponse from(MazeSessionState state, boolean moved, boolean includeMaze) {
            MazeResponse response = new MazeResponse();
            response.maze = includeMaze ? state.getMaze() : null;
            response.player = new PointDto(state.getPlayer().getX(), state.getPlayer().getY());
            response.start = new PointDto(state.getStart().getX(), state.getStart().getY());
            response.goal = new PointDto(state.getGoal().getX(), state.getGoal().getY());
            response.breadcrumbs = state.getBreadcrumbs();
            response.moved = moved;
            response.won = state.getPlayer().getX() == state.getGoal().getX()
                    && state.getPlayer().getY() == state.getGoal().getY();
            return response;
        }

        public int[][] getMaze() {
            return maze;
        }

        public PointDto getPlayer() {
            return player;
        }

        public PointDto getStart() {
            return start;
        }

        public PointDto getGoal() {
            return goal;
        }

        public List<MazePoint> getBreadcrumbs() {
            return breadcrumbs;
        }

        public boolean isMoved() {
            return moved;
        }

        public boolean isWon() {
            return won;
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
