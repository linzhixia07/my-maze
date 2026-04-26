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
    memoryDeck: [],
    memoryCurrentPlayer: "A",
    memoryInputLocked: false,
    memoryRows: 4,
    memoryCols: 6,

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
            modeMemoryBtn: document.getElementById("modeMemoryBtn"),
            chaseControls: document.getElementById("chaseControls"),
            toggleChaserBtn: document.getElementById("toggleChaserBtn"),
            chaseStatus: document.getElementById("chaseStatus"),
            memoryBoard: document.getElementById("memoryBoard"),
            memorySizeOverlay: document.getElementById("memorySizeOverlay"),
            memoryRowsInput: document.getElementById("memoryRowsInput"),
            memoryColsInput: document.getElementById("memoryColsInput"),
            memorySizeCancelBtn: document.getElementById("memorySizeCancelBtn"),
            memorySizeConfirmBtn: document.getElementById("memorySizeConfirmBtn"),
            memorySizeError: document.getElementById("memorySizeError")
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
        this.elements.modeMemoryBtn.addEventListener("click", () => this.selectGameMode(GameConfig.MODE_MEMORY));
        this.elements.toggleChaserBtn.addEventListener("click", () => this.onToggleChaserClick());
        this.elements.memorySizeCancelBtn.addEventListener("click", () => this.hideMemorySizeDialog());
        this.elements.memorySizeConfirmBtn.addEventListener("click", () => this.onConfirmMemorySize());

        window.addEventListener("keydown", (e) => this.onKeyDown(e));
        window.addEventListener("keyup", (e) => this.onKeyUp(e));
        window.addEventListener("resize", () => {
            if (this.currentGameMode !== GameConfig.MODE_MEMORY && this.maze && this.maze.length > 0) {
                this.render();
            }
        });
    },

    /**
     * 重置关卡
     */
    onResetLevelClick() {
        if (!this.currentGameMode) return;
        if (this.currentGameMode === GameConfig.MODE_MEMORY) {
            this.showMemorySizeDialog();
            return;
        }

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
        if (this.currentGameMode === GameConfig.MODE_CHASE || this.currentGameMode === GameConfig.MODE_MEMORY) return;
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
            this.elements.canvas.classList.remove("hidden");
            this.elements.memoryBoard.classList.add("hidden");
            this.updateChaseStatusText();
            return;
        }
        if (this.currentGameMode === GameConfig.MODE_MEMORY) {
            this.elements.nextLevelBtn.classList.add("hidden");
            this.elements.resetLevelBtn.textContent = "重置游戏";
            this.elements.chaseControls.classList.add("hidden");
            this.elements.canvas.classList.add("hidden");
            this.elements.memoryBoard.classList.remove("hidden");
            return;
        }
        this.elements.nextLevelBtn.classList.remove("hidden");
        this.elements.resetLevelBtn.textContent = "重置关卡";
        this.elements.chaseControls.classList.add("hidden");
        this.elements.canvas.classList.remove("hidden");
        this.elements.memoryBoard.classList.add("hidden");
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
        } else if (this.currentGameMode === GameConfig.MODE_MEMORY) {
            this.elements.levelBadge.textContent = this.buildMemoryBadgeText();
        } else {
            this.elements.levelBadge.textContent = "Level: " + lv + "/" + GameConfig.MAX_LEVEL;
        }
    },

    /**
     * 键盘按下
     */
    onKeyDown(event) {
        if (this.inputLocked || this.currentGameMode === GameConfig.MODE_MEMORY) return;
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
            if (this.inputLocked || this.currentGameMode === GameConfig.MODE_MEMORY) return;

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
        if (!this.currentGameMode || this.currentGameMode === GameConfig.MODE_MEMORY) return;

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
        const label = this.currentGameMode === GameConfig.MODE_MEMORY ? "得分：" : "胜场：";
        this.elements.playerAScore.textContent = label + this.scoreBoard.A;
        this.elements.playerBScore.textContent = label + this.scoreBoard.B;
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
        this.winnerMessage = "";
        if (mode === GameConfig.MODE_CHASE) {
            this.chaseChaserPlayerId = Math.random() < 0.5 ? "A" : "B";
        }
        this.elements.modeSelectorOverlay.classList.add("hidden");
        this.inputLocked = false;
        this.currentLevel = mode === GameConfig.MODE_CHASE ? GameConfig.CHASE_FIXED_LEVEL : 1;
        this.scoreBoard = {A: 0, B: 0};
        this.updateScoreDisplay();
        this.applyLevelTheme(this.currentLevel);
        this.updateNextLevelButton();

        switch (mode) {
            case GameConfig.MODE_TWIN_RACE:
            case GameConfig.MODE_CHASE:
                this.loadMazeForCurrentLevel();
                break;
            case GameConfig.MODE_MEMORY:
                this.initMemoryGame();
                break;
            default:
                break;
        }
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
    },

    initMemoryGame() {
        this.resetMemoryRuntimeState();
        this.startMemoryGame();
    },

    resetMemoryRuntimeState() {
        this.memoryDeck = [];
        this.memoryCurrentPlayer = "A";
        this.memoryInputLocked = false;
        this.winnerMessage = "";
        this.scoreBoard = {A: 0, B: 0};
        this.updateScoreDisplay();
    },

    resetMemoryGame() {
        if (this.currentGameMode !== GameConfig.MODE_MEMORY) return;
        this.startMemoryGame();
    },

    startMemoryGame() {
        ApiService.startMemoryGame(this.memoryRows, this.memoryCols)
            .then((state) => this.applyMemoryState(state))
            .catch(() => {});
    },

    applyMemoryState(state) {
        this.memoryDeck = state.deck || [];
        this.memoryRows = typeof state.rows === "number" ? state.rows : this.memoryRows;
        this.memoryCols = typeof state.cols === "number" ? state.cols : this.memoryCols;
        this.memoryCurrentPlayer = state.currentPlayer || "A";
        this.scoreBoard = state.scoreBoard || {A: 0, B: 0};
        this.memoryInputLocked = !!state.inputLocked;
        this.winnerMessage = state.gameOver && state.winner ? this.getWinnerMessage(state.winner) : "";
        this.applyMemoryGridSize();
        this.renderMemoryBoard();
        this.applyLevelTheme(1);
        this.elements.canvas.classList.add("hidden");
    },

    renderMemoryBoard() {
        const board = this.elements.memoryBoard;
        board.innerHTML = "";
        board.style.pointerEvents = this.winnerMessage ? "none" : "auto";

        for (let i = 0; i < this.memoryDeck.length; i++) {
            const card = this.memoryDeck[i];
            const button = document.createElement("button");
            button.type = "button";
            button.className = "memory-card";
            if (card.flipped || card.matched) button.classList.add("is-flipped");
            if (card.matched) button.classList.add("is-matched");
            button.disabled = card.matched;
            const cardColor = card.matched ? this.getWordColor(card.word) : this.getUnmatchedFlipColor();
            const cardBorder = card.matched ? this.getWordBorderColor(card.word) : this.getUnmatchedFlipBorderColor();
            const wordFontSize = this.getMemoryWordFontSize(card.word);
            button.innerHTML = "<div class=\"memory-card-inner\"><div class=\"memory-card-face memory-card-back\">?</div><div class=\"memory-card-face memory-card-front\" style=\"background:" + cardColor + ";border-color:" + cardBorder + ";\"><span class=\"memory-card-word\" data-base-font-size=\"" + wordFontSize + "\" style=\"font-size:" + wordFontSize + "px;\">" + card.word + "</span></div></div>";
            button.addEventListener("click", () => this.onMemoryCardClick(i));
            board.appendChild(button);
        }

        if (this.winnerMessage) {
            const overlay = document.createElement("div");
            overlay.className = "memory-winner-overlay";
            overlay.innerHTML = "<div class=\"memory-winner-message\">" + this.winnerMessage + "</div>";
            board.appendChild(overlay);
        }

        this.updateScoreDisplay();
        this.applyLevelTheme(1);
        this.fitMemoryCardWords();
    },

    onMemoryCardClick(index) {
        if (this.currentGameMode !== GameConfig.MODE_MEMORY || this.memoryInputLocked) return;
        const card = this.memoryDeck[index];
        if (!card || card.matched || card.flipped) return;
        ApiService.flipMemoryCard(index)
            .then((state) => {
                this.applyMemoryState(state);
                if (state.pendingResolve) {
                    setTimeout(() => {
                        if (this.currentGameMode !== GameConfig.MODE_MEMORY) return;
                        ApiService.resolveMemoryTurn()
                            .then((resolvedState) => this.applyMemoryState(resolvedState))
                            .catch(() => {});
                    }, 2000);
                }
            })
            .catch(() => {});
    },

    buildMemoryBadgeText() {
        return "单词配对 | " + this.getPlayerDisplayName("A") + " " + this.scoreBoard.A + " vs " + this.scoreBoard.B + " " + this.getPlayerDisplayName("B") + " | 当前：" + this.getPlayerDisplayName(this.memoryCurrentPlayer);
    },

    getWordColor(word) {
        let hash = 0;
        for (let i = 0; i < word.length; i++) {
            hash = (hash * 31 + word.charCodeAt(i)) >>> 0;
        }
        const hue = hash % 360;
        return "hsl(" + hue + ", 70%, 90%)";
    },

    getWordBorderColor(word) {
        let hash = 0;
        for (let i = 0; i < word.length; i++) {
            hash = (hash * 37 + word.charCodeAt(i)) >>> 0;
        }
        const hue = hash % 360;
        return "hsl(" + hue + ", 45%, 76%)";
    },

    getUnmatchedFlipColor() {
        return "#e9f4ff";
    },

    getUnmatchedFlipBorderColor() {
        return "#b8d6f2";
    },

    getMemoryWordFontSize(word) {
        const lettersOnlyCount = (word || "").replace(/[^a-zA-Z]/g, "").length;
        if (lettersOnlyCount <= 6) return 22;
        if (lettersOnlyCount <= 8) return 20;
        if (lettersOnlyCount <= 10) return 18;
        if (lettersOnlyCount <= 12) return 16;
        return 14;
    },

    fitMemoryCardWords() {
        const wordNodes = this.elements.memoryBoard.querySelectorAll(".memory-card-word");
        for (let i = 0; i < wordNodes.length; i++) {
            this.fitSingleMemoryWord(wordNodes[i]);
        }
    },

    fitSingleMemoryWord(wordNode) {
        if (!wordNode) return;
        const frontFace = wordNode.closest(".memory-card-front");
        if (!frontFace) return;

        const baseSize = Number(wordNode.dataset.baseFontSize) || 16;
        let fontSize = baseSize;
        const minFontSize = 9;
        const safetyGap = 2;
        wordNode.style.fontSize = fontSize + "px";

        while (fontSize > minFontSize && wordNode.scrollWidth > (frontFace.clientWidth - safetyGap)) {
            fontSize -= 1;
            wordNode.style.fontSize = fontSize + "px";
        }
    },

    applyMemoryGridSize() {
        this.elements.memoryBoard.style.gridTemplateRows = "repeat(" + this.memoryRows + ", minmax(126px, 1fr))";
        this.elements.memoryBoard.style.gridTemplateColumns = "repeat(" + this.memoryCols + ", minmax(120px, 1fr))";
    },

    showMemorySizeDialog() {
        this.elements.memoryRowsInput.value = String(this.memoryRows);
        this.elements.memoryColsInput.value = String(this.memoryCols);
        this.elements.memorySizeError.textContent = "";
        this.elements.memorySizeError.classList.add("hidden");
        this.elements.memorySizeOverlay.classList.remove("hidden");
    },

    hideMemorySizeDialog() {
        this.elements.memorySizeOverlay.classList.add("hidden");
    },

    onConfirmMemorySize() {
        const rows = Number(this.elements.memoryRowsInput.value);
        const cols = Number(this.elements.memoryColsInput.value);
        if (!Number.isInteger(rows) || !Number.isInteger(cols) || rows < 2 || cols < 2) {
            this.showMemorySizeError("请输入 2 到 10 之间的整数行列。");
            return;
        }

        const total = rows * cols;
        if (total % 2 !== 0) {
            this.showMemorySizeError("总格子数必须是偶数，才能两两配对。");
            return;
        }

        this.memoryRows = rows;
        this.memoryCols = cols;
        this.hideMemorySizeDialog();
        this.resetMemoryGame();
    },

    showMemorySizeError(message) {
        this.elements.memorySizeError.textContent = message;
        this.elements.memorySizeError.classList.remove("hidden");
    }
};

// 启动游戏
document.addEventListener("DOMContentLoaded", () => Game.init());
