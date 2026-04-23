/**
 * 游戏配置常量
 */
const GameConfig = {
    MODE_TWIN_RACE: "TWIN_RACE",
    MODE_CHASE: "CHASE",
    MAX_LEVEL: 5,
    CHASE_FIXED_LEVEL: 1,

    /**
     * 难度配色：绿→黄→橙→红
     */
    LEVEL_THEMES: {
        1: {
            cellRoad: "#f0faf4",
            cellWall: "#0f3d2a",
            breadcrumbA: "rgba(25, 118, 210, 0.55)",
            breadcrumbB: "rgba(229, 57, 53, 0.55)",
            pageBg: "#e4f3eb",
            canvasBg: "#f0faf4",
            textMain: "#0f291c",
            labelColor: "#1b4332",
            inputBorder: "#8fd4b0",
            canvasBorder: "#2e7d56",
            badgeBg: "rgba(255, 255, 255, 0.95)",
            badgeText: "#0f291c"
        },
        2: {
            cellRoad: "#f6fcec",
            cellWall: "#2d4a18",
            breadcrumbA: "rgba(25, 118, 210, 0.55)",
            breadcrumbB: "rgba(229, 57, 53, 0.55)",
            pageBg: "#edf6df",
            canvasBg: "#f6fcec",
            textMain: "#1e3310",
            labelColor: "#2d4a18",
            inputBorder: "#aed581",
            canvasBorder: "#558b2f",
            badgeBg: "rgba(255, 255, 255, 0.95)",
            badgeText: "#1e3310"
        },
        3: {
            cellRoad: "#fffdf7",
            cellWall: "#6a4a00",
            breadcrumbA: "rgba(21, 101, 192, 0.56)",
            breadcrumbB: "rgba(211, 47, 47, 0.56)",
            pageBg: "#fff6e0",
            canvasBg: "#fffdf7",
            textMain: "#4a3500",
            labelColor: "#5c4200",
            inputBorder: "#ffd54f",
            canvasBorder: "#f9a825",
            badgeBg: "rgba(255, 255, 255, 0.96)",
            badgeText: "#4a3500"
        },
        4: {
            cellRoad: "#fffaf6",
            cellWall: "#7a3a00",
            breadcrumbA: "rgba(13, 71, 161, 0.56)",
            breadcrumbB: "rgba(211, 47, 47, 0.56)",
            pageBg: "#fff0e6",
            canvasBg: "#fffaf6",
            textMain: "#4a2100",
            labelColor: "#5c2800",
            inputBorder: "#ffcc80",
            canvasBorder: "#ef6c00",
            badgeBg: "rgba(255, 255, 255, 0.96)",
            badgeText: "#4a2100"
        },
        5: {
            cellRoad: "#fff8f8",
            cellWall: "#5c1018",
            breadcrumbA: "rgba(25, 118, 210, 0.58)",
            breadcrumbB: "rgba(229, 57, 53, 0.58)",
            pageBg: "#fceaea",
            canvasBg: "#fff8f8",
            textMain: "#3d0a10",
            labelColor: "#6b141c",
            inputBorder: "#ef9a9a",
            canvasBorder: "#c62828",
            badgeBg: "rgba(255, 255, 255, 0.96)",
            badgeText: "#3d0a10"
        }
    },

    /**
     * 按键映射
     */
    KEY_MAPPING: {
        w: {playerId: "A", direction: "UP"},
        W: {playerId: "A", direction: "UP"},
        s: {playerId: "A", direction: "DOWN"},
        S: {playerId: "A", direction: "DOWN"},
        a: {playerId: "A", direction: "LEFT"},
        A: {playerId: "A", direction: "LEFT"},
        d: {playerId: "A", direction: "RIGHT"},
        D: {playerId: "A", direction: "RIGHT"},
        ArrowUp: {playerId: "B", direction: "UP"},
        ArrowDown: {playerId: "B", direction: "DOWN"},
        ArrowLeft: {playerId: "B", direction: "LEFT"},
        ArrowRight: {playerId: "B", direction: "RIGHT"}
    }
};

/**
 * 构建当前关卡的主题
 */
function buildActiveTheme(level) {
    const lv = level < 1 ? 1 : (level > GameConfig.MAX_LEVEL ? GameConfig.MAX_LEVEL : level);
    return Object.assign({}, GameConfig.LEVEL_THEMES[lv] || GameConfig.LEVEL_THEMES[1]);
}
