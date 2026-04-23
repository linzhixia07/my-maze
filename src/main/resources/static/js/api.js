/**
 * API 服务
 */
const ApiService = {
    /**
     * 生成迷宫
     */
    generateMaze(level, gameMode) {
        return fetch("/api/maze/generate", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                level: level,
                gameMode: gameMode
            })
        }).then(this.parseJson);
    },

    /**
     * 移动玩家
     */
    move(action, currentGameMode, chaseChaserPlayerId) {
        return fetch("/api/maze/move", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                playerId: action.playerId,
                direction: action.direction,
                chaseChaserId: currentGameMode === GameConfig.MODE_CHASE ? chaseChaserPlayerId : null
            })
        }).then(this.parseJson);
    },

    /**
     * 解析 JSON 响应
     */
    parseJson(response) {
        if (!response.ok) {
            throw new Error("request failed");
        }
        return response.json();
    }
};
