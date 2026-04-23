/**
 * 游戏主逻辑
 */
const Game = {
    // 游戏状态
    currentLevel: 1,
    currentGameMode: null,
    chaseChaserPlayerId: "A",
    maze: [],
    players: {},
    goal: null,
    breadcrumbs: {},
    inputLocked: false,
    winnerMessage: "",
    scoreBoard: {A: 0, B: 0},
    roundScoreRecorded: false,
    keyPressed: {},
    moveLoopId: null,
    activeTheme: buildActiveTheme(1),

    // DOM 元素引用
    elements: {},

    /**
     * 初始化游戏
     */
    init() {
        this.cacheElements();
        this.bindEvents();
        this.updateScoreDisplay();
        this.applyLevelTheme(1);
        this.updateNextLevelButton();
        this.startMoveLoop();
        this.showModeSelector();
    },

    /**
     * 缓存 DOM 元素
     */
    cacheElements() {
        this.elements = {
            canvas: document.getElementById("mazeCanvas"),
            playerAInput: document.getElementById("playerAName"),
            playerBInput: document.getElementById("playerBName"),
            playerAScore: document.getElementById("playerAScore"),
            playerBScore: document.getElementById("playerBScore"),
            levelBadge: document.getElementById("levelBadge"),
            resetLevelBtn: document.getElementById("resetLevelBtn"),
            nextLevelBtn: document.getElementById("nextLevelBtn"),
            modeSelectorOverlay: document.getElementById("modeSelectorOverlay"),
            modeTwinRaceBtn: document.getElementById("modeTwinRaceBtn"),
            modeChaseBtn: document.getElementById("modeChaseBtn"),
            chaseControls: document.getElementById("chaseControls"),
            toggleChaserBtn: document.getElementById("toggleChaserBtn"),
            chaseStatus: document.getElementById("chaseStatus")
        };
    },

    /**
     * 绑定事件
     */
    bindEvents() {
        this.elements.resetLevelBtn.addEventListener("click", () => this.onResetLevelClick());
        this.elements.nextLevelBtn.addEventListener("click", () => this.onNextLevelClick());
        this.elements.modeTwinRaceBtn.addEventListener("click", () => this.selectGameMode(GameConfig.MODE_TWIN_RACE));
        this.elements.modeChaseBtn.addEventListener("click", () => this.selectGameMode(GameConfig.MODE_CHASE));
        this.elements.toggleChaserBtn.addEventListener("click", () => this.onToggleChaserClick());

        window.addEventListener("keydown", (e) => this.onKeyDown(e));
        window.addEventListener("keyup", (e) => this.onKeyUp(e));
        window.addEventListener("resize", () => {
            if (this.maze && this.maze.length > 0) {
                this.render();
            }
        });
    },

    /**
     * 重置关卡
     */
    onResetLevelClick() {
        if (!this.currentGameMode) return;

        if (this.currentGameMode === GameConfig.MODE_CHASE) {
            this.chaseChaserPlayerId = Math.random() < 0.5 ? "A" : "B";
        }
        this.currentLevel = this.currentGameMode === GameConfig.MODE_CHASE ? GameConfig.CHASE_FIXED_LEVEL : 1;
        this.applyLevelTheme(this.currentLevel);
        this.updateNextLevelButton();
        this.loadMazeForCurrentLevel();
    },

    /**
     * 下一关
     */
    onNextLevelClick() {
        if (!this.currentGameMode) return;
        if (this.currentGameMode === GameConfig.MODE_CHASE) return;
        if (this.currentLevel >= GameConfig.MAX_LEVEL) return;

        this.currentLevel += 1;
        this.applyLevelTheme(this.currentLevel);
        this.updateNextLevelButton();
        this.loadMazeForCurrentLevel();
    },

    /**
     * 更新下一关按钮状态
     */
    updateNextLevelButton() {
        if (this.currentGameMode === GameConfig.MODE_CHASE) {
            this.elements.nextLevelBtn.classList.add("hidden");
            this.elements.resetLevelBtn.textContent = "重置游戏";
            this.elements.chaseControls.classList.remove("hidden");
            this.updateChaseStatusText();
            return;
        }
        this.elements.nextLevelBtn.classList.remove("hidden");
        this.elements.resetLevelBtn.textContent = "重置关卡";
        this.elements.chaseControls.classList.add("hidden");
        this.elements.nextLevelBtn.disabled = this.currentLevel >= GameConfig.MAX_LEVEL;
    },

    /**
     * 应用关卡主题
     */
    applyLevelTheme(level) {
        const lv = level < 1 ? 1 : (level > GameConfig.MAX_LEVEL ? GameConfig.MAX_LEVEL : level);
        this.activeTheme = buildActiveTheme(lv);
        const root = document.documentElement;
        root.style.setProperty("--page-bg", this.activeTheme.pageBg);
        root.style.setProperty("--canvas-bg", this.activeTheme.canvasBg);
        root.style.setProperty("--text-main", this.activeTheme.textMain);
        root.style.setProperty("--label-color", this.activeTheme.labelColor);
        root.style.setProperty("--input-border", this.activeTheme.inputBorder);
        root.style.setProperty("--canvas-border", this.activeTheme.canvasBorder);
        root.style.setProperty("--badge-bg", this.activeTheme.badgeBg);
        root.style.setProperty("--badge-text", this.activeTheme.badgeText);

        if (this.currentGameMode === GameConfig.MODE_CHASE) {
            this.elements.levelBadge.textContent = "抓人模式 | 固定地图 19";
        } else {
            this.elements.levelBadge.textContent = "Level: " + lv + "/" + GameConfig.MAX_LEVEL;
        }
    },

    /**
     * 键盘按下
     */
    onKeyDown(event) {
        if (this.inputLocked) return;
        if (GameConfig.KEY_MAPPING[event.key]) {
            event.preventDefault();
            this.keyPressed[event.key] = true;
        }
    },

    /**
     * 键盘释放
     */
    onKeyUp(event) {
        if (this.keyPressed[event.key]) {
            this.keyPressed[event.key] = false;
        }
    },

    /**
     * 启动移动循环
     */
    startMoveLoop() {
        if (this.moveLoopId) {
            clearInterval(this.moveLoopId);
        }
        this.moveLoopId = setInterval(() => {
            if (this.inputLocked) return;

            const actionA = this.currentDirectionForPlayer("A");
            const actionB = this.currentDirectionForPlayer("B");

            if (!actionA && !actionB) return;

            if (actionA && actionB) {
                Promise.all([this.move(actionA), this.move(actionB)]).catch(() => {});
            } else if (actionA) {
                this.move(actionA);
            } else {
                this.move(actionB);
            }
        }, 95);
    },

    /**
     * 获取玩家当前方向
     */
    currentDirectionForPlayer(playerId) {
        const keys = playerId === "A"
            ? ["w", "W", "s", "S", "a", "A", "d", "D"]
            : ["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight"];
        for (let i = 0; i < keys.length; i++) {
            if (this.keyPressed[keys[i]]) {
                return GameConfig.KEY_MAPPING[keys[i]];
            }
        }
        return null;
    },

    /**
     * 加载当前关卡迷宫
     */
    loadMazeForCurrentLevel() {
        if (!this.currentGameMode) return;

        this.inputLocked = false;
        this.winnerMessage = "";
        this.roundScoreRecorded = false;
        this.keyPressed = {};

        ApiService.generateMaze(
            this.currentGameMode === GameConfig.MODE_CHASE ? GameConfig.CHASE_FIXED_LEVEL : this.currentLevel,
            this.currentGameMode
        ).then(data => this.applyState(data)).catch(() => {});
    },

    /**
     * 执行移动
     */
    move(action) {
        return ApiService.move(action, this.currentGameMode, this.chaseChaserPlayerId)
            .then(data => {
                if (data.gameOver) {
                    this.inputLocked = true;
                    this.updateScoreIfNeeded(data.winner);
                    this.winnerMessage = this.getWinnerMessage(data.winner);
                }
                this.applyState(data);
            }).catch(() => {});
    },

    /**
     * 应用服务器返回的状态
     */
    applyState(data) {
        if (typeof data.level === "number") {
            this.currentLevel = data.level;
            this.applyLevelTheme(this.currentLevel);
        }
        if (typeof data.gameMode === "string" && data.gameMode) {
            this.currentGameMode = data.gameMode;
        }
        this.updateNextLevelButton();
        if (data.maze) {
            this.maze = data.maze;
        }
        this.players = data.players || {};
        this.goal = data.goal;
        this.breadcrumbs = data.breadcrumbs || {};
        this.render();
    },

    /**
     * 渲染游戏画面
     */
    render() {
        MazeRenderer.init("mazeCanvas");
        MazeRenderer.render(
            {maze: this.maze, players: this.players, goal: this.goal, breadcrumbs: this.breadcrumbs},
            this.activeTheme,
            this.winnerMessage
        );
    },

    /**
     * 获取获胜消息
     */
    getWinnerMessage(winnerId) {
        const name = this.getPlayerDisplayName(winnerId);
        if (this.currentGameMode === GameConfig.MODE_CHASE) {
            if (winnerId === this.chaseChaserPlayerId) {
                return name + " 成功抓获目标，获得胜利！";
            }
            return name + " 成功夺取火焰杯，获得胜利！";
        }
        return name + " 获得了胜利！";
    },

    /**
     * 获取玩家显示名称
     */
    getPlayerDisplayName(playerId) {
        const name = playerId === "A" ? this.elements.playerAInput.value : this.elements.playerBInput.value;
        const safeName = (name || "").trim();
        return safeName || (playerId === "A" ? "玩家A" : "玩家B");
    },

    /**
     * 更新分数
     */
    updateScoreIfNeeded(winnerId) {
        if (this.roundScoreRecorded) return;
        if (winnerId !== "A" && winnerId !== "B") return;

        this.scoreBoard[winnerId] += 1;
        this.roundScoreRecorded = true;
        this.updateScoreDisplay();
    },

    /**
     * 更新分数显示
     */
    updateScoreDisplay() {
        this.elements.playerAScore.textContent = "胜场：" + this.scoreBoard.A;
        this.elements.playerBScore.textContent = "胜场：" + this.scoreBoard.B;
    },

    /**
     * 显示模式选择器
     */
    showModeSelector() {
        this.elements.modeSelectorOverlay.classList.remove("hidden");
        this.inputLocked = true;
    },

    /**
     * 选择游戏模式
     */
    selectGameMode(mode) {
        this.currentGameMode = mode;
        if (mode === GameConfig.MODE_CHASE) {
            this.chaseChaserPlayerId = Math.random() < 0.5 ? "A" : "B";
        }
        this.elements.modeSelectorOverlay.classList.add("hidden");
        this.inputLocked = false;
        this.currentLevel = mode === GameConfig.MODE_CHASE ? GameConfig.CHASE_FIXED_LEVEL : 1;
        this.applyLevelTheme(this.currentLevel);
        this.updateNextLevelButton();
        this.loadMazeForCurrentLevel();
    },

    /**
     * 切换抓人方
     */
    onToggleChaserClick() {
        if (this.currentGameMode !== GameConfig.MODE_CHASE) return;
        this.chaseChaserPlayerId = this.chaseChaserPlayerId === "A" ? "B" : "A";
        this.updateChaseStatusText();
    },

    /**
     * 更新抓人状态文本
     */
    updateChaseStatusText() {
        if (this.currentGameMode !== GameConfig.MODE_CHASE) return;

        this.elements.toggleChaserBtn.classList.remove("is-blue");
        this.elements.toggleChaserBtn.classList.remove("is-red");

        if (this.chaseChaserPlayerId === "A") {
            this.elements.toggleChaserBtn.classList.add("is-blue");
            this.elements.chaseStatus.textContent = "当前抓人方：蓝色方";
            this.elements.toggleChaserBtn.textContent = "切换为红色方抓人";
        } else {
            this.elements.toggleChaserBtn.classList.add("is-red");
            this.elements.chaseStatus.textContent = "当前抓人方：红色方";
            this.elements.toggleChaserBtn.textContent = "切换为蓝色方抓人";
        }
    }
};

// 启动游戏
document.addEventListener("DOMContentLoaded", () => Game.init());
