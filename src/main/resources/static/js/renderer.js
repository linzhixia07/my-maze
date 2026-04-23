/**
 * 迷宫渲染器
 */
const MazeRenderer = {
    canvas: null,
    ctx: null,
    cellSize: 24,

    init(canvasId) {
        this.canvas = document.getElementById(canvasId);
        this.ctx = this.canvas.getContext("2d");
    },

    /**
     * 对齐 Canvas 缓冲区到显示尺寸
     */
    alignCanvasBufferToDisplay() {
        const rect = this.canvas.getBoundingClientRect();
        let cssW = Math.floor(rect.width);
        let cssH = Math.floor(rect.height);
        if (cssW < 16 || cssH < 16) {
            cssW = 700;
            cssH = 700;
        }
        this.canvas._layoutCssW = cssW;
        this.canvas._layoutCssH = cssH;
        const dpr = window.devicePixelRatio || 1;
        const bufW = Math.max(1, Math.floor(cssW * dpr));
        const bufH = Math.max(1, Math.floor(cssH * dpr));
        if (this.canvas.width !== bufW || this.canvas.height !== bufH) {
            this.canvas.width = bufW;
            this.canvas.height = bufH;
        }
        this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    },

    /**
     * 渲染完整游戏画面
     */
    render(gameState, theme, winnerMessage) {
        if (!gameState.maze || gameState.maze.length === 0) {
            return;
        }

        const rows = gameState.maze.length;
        const cols = gameState.maze[0].length;

        this.alignCanvasBufferToDisplay();
        const lw = this.canvas._layoutCssW;
        const lh = this.canvas._layoutCssH;
        this.cellSize = Math.floor(Math.min(lw / cols, lh / rows));
        this.ctx.clearRect(0, 0, lw, lh);

        this.drawMazeSeamless(gameState.maze, rows, cols, lw, lh, theme);
        this.drawBreadcrumbs(gameState.breadcrumbs, theme);
        this.drawGoal(gameState.goal);
        this.drawPlayers(gameState.players);
        this.drawWinnerOverlay(winnerMessage, lw, lh);
    },

    /**
     * 绘制迷宫（无缝墙体）
     */
    drawMazeSeamless(maze, rows, cols, lw, lh, theme) {
        this.ctx.fillStyle = theme.cellRoad;
        this.ctx.fillRect(0, 0, lw, lh);
        this.ctx.fillStyle = theme.cellWall;
        const wallSpan = this.cellSize + 1;
        for (let y = 0; y < rows; y++) {
            for (let x = 0; x < cols; x++) {
                if ((maze[y][x] & 2) === 2) {
                    const px = Math.floor(x * this.cellSize);
                    const py = Math.floor(y * this.cellSize);
                    this.ctx.fillRect(px, py, wallSpan, wallSpan);
                }
            }
        }
    },

    /**
     * 绘制面包屑轨迹
     */
    drawBreadcrumbs(breadcrumbs, theme) {
        this.drawPlayerBreadcrumb(breadcrumbs.A, theme.breadcrumbA);
        this.drawPlayerBreadcrumb(breadcrumbs.B, theme.breadcrumbB);
    },

    drawPlayerBreadcrumb(path, color) {
        if (!path) return;
        this.ctx.fillStyle = color;
        for (let i = 0; i < path.length; i++) {
            const p = path[i];
            this.ctx.beginPath();
            this.ctx.arc(
                p.x * this.cellSize + this.cellSize / 2,
                p.y * this.cellSize + this.cellSize / 2,
                this.cellSize / 8,
                0,
                Math.PI * 2
            );
            this.ctx.fill();
        }
    },

    /**
     * 绘制终点奖杯
     */
    drawGoal(goal) {
        if (!goal) return;
        const cx = goal.x * this.cellSize + this.cellSize / 2;
        const cy = goal.y * this.cellSize + this.cellSize / 2;
        const trophySize = this.cellSize * 0.9;

        // 金色发光背景
        this.ctx.save();
        this.ctx.shadowColor = "rgba(255, 215, 0, 0.8)";
        this.ctx.shadowBlur = 15;
        this.ctx.fillStyle = "rgba(255, 215, 0, 0.3)";
        this.ctx.beginPath();
        this.ctx.arc(cx, cy, this.cellSize / 2, 0, Math.PI * 2);
        this.ctx.fill();
        this.ctx.restore();

        // 奖杯 emoji
        this.ctx.font = Math.floor(trophySize) + "px serif";
        this.ctx.textAlign = "center";
        this.ctx.textBaseline = "middle";
        this.ctx.fillText("🏆", cx, cy + this.cellSize * 0.05);
    },

    /**
     * 绘制玩家
     */
    drawPlayers(players) {
        const playerA = players.A;
        const playerB = players.B;
        if (playerA && playerB && playerA.x === playerB.x && playerA.y === playerB.y) {
            this.drawOverlappedPlayers(playerA);
            return;
        }
        this.drawPlayer(playerA, "#2f80ed");
        this.drawPlayer(playerB, "#eb5757");
    },

    drawPlayer(player, color) {
        if (!player) return;
        this.ctx.fillStyle = color;
        this.ctx.beginPath();
        this.ctx.arc(
            player.x * this.cellSize + this.cellSize / 2,
            player.y * this.cellSize + this.cellSize / 2,
            this.cellSize / 2.5,
            0,
            Math.PI * 2
        );
        this.ctx.fill();
    },

    drawOverlappedPlayers(position) {
        const cx = position.x * this.cellSize + this.cellSize / 2;
        const cy = position.y * this.cellSize + this.cellSize / 2;
        const splitOffset = this.cellSize * 0.18;
        const radius = this.cellSize / 3.3;

        this.ctx.fillStyle = "#2f80ed";
        this.ctx.beginPath();
        this.ctx.arc(cx - splitOffset, cy, radius, 0, Math.PI * 2);
        this.ctx.fill();

        this.ctx.fillStyle = "#eb5757";
        this.ctx.beginPath();
        this.ctx.arc(cx + splitOffset, cy, radius, 0, Math.PI * 2);
        this.ctx.fill();
    },

    /**
     * 绘制获胜覆盖层
     */
    drawWinnerOverlay(winnerMessage, w, h) {
        if (!winnerMessage) return;
        const cx = w / 2;
        const cy = h / 2;
        const fontSize = Math.max(26, Math.min(42, Math.floor(Math.min(w, h) / 18)));

        this.ctx.save();
        this.ctx.fillStyle = "rgba(6, 8, 14, 0.78)";
        this.ctx.fillRect(0, 0, w, h);

        this.ctx.textAlign = "center";
        this.ctx.textBaseline = "middle";
        this.ctx.font = "bold " + fontSize + "px Arial, sans-serif";
        this.ctx.lineJoin = "round";
        this.ctx.lineWidth = 6;
        this.ctx.strokeStyle = "rgba(0, 0, 0, 0.62)";
        this.ctx.strokeText(winnerMessage, cx, cy);
        this.ctx.shadowColor = "rgba(255, 213, 79, 0.85)";
        this.ctx.shadowBlur = 20;
        this.ctx.fillStyle = "#fffde7";
        this.ctx.fillText(winnerMessage, cx, cy);
        this.ctx.shadowBlur = 0;
        this.ctx.restore();
    }
};
