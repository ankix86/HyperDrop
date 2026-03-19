import QtQuick

QtObject {
    id: root

    readonly property bool isDark: backend.effectiveTheme === "dark"

    // Discord-inspired surfaces
    readonly property color windowBg: isDark ? "#1e1f22" : "#f2f3f5"
    readonly property color sidebarBg: isDark ? "#2b2d31" : "#e3e5e8"
    readonly property color sidebarBorder: isDark ? "#1a1b1e" : "#c7ccd1"
    readonly property color cardSurface: isDark ? "#313338" : "#ffffff"
    readonly property color cardBorder: isDark ? "#3f4147" : "#d4d7dc"
    readonly property color panelBg: isDark ? "#2b2d31" : "#ebedef"
    readonly property color panelBorder: isDark ? "#404249" : "#d4d7dc"
    readonly property color elevatedSurface: isDark ? "#232428" : "#ffffff"
    readonly property color insetSurface: isDark ? "#1e1f22" : "#f2f3f5"
    readonly property color fieldBg: isDark ? "#1e1f22" : "#f2f3f5"
    readonly property color fieldBorder: isDark ? "#404249" : "#c7ccd1"
    readonly property color fieldFocus: isDark ? "#5cad73" : "#4f8f67"
    readonly property color itemBg: isDark ? "#2b2d31" : "#f8f9fb"
    readonly property color itemHoverBg: isDark ? "#35373c" : "#eef1f6"
    readonly property color itemSelectedBg: isDark ? "#3a3c43" : "#e8ebff"
    readonly property color itemBorder: isDark ? "#3f4147" : "#d4d7dc"
    readonly property color itemSelectedBorder: isDark ? "#5cad73" : "#4f8f67"
    readonly property color badgeBg: isDark ? "#41434a" : "#dfe3e7"
    readonly property color badgeText: isDark ? "#f2f3f5" : "#313338"
    readonly property color avatarBg: isDark ? "#5cad73" : "#5b9a72"
    readonly property color avatarText: "#ffffff"

    // Text
    readonly property color textPrimary: isDark ? "#f2f3f5" : "#2e3338"
    readonly property color textSecondary: isDark ? "#b5bac1" : "#4e5058"
    readonly property color textMuted: isDark ? "#949ba4" : "#747f8d"
    readonly property color buttonText: isDark ? "#f2f3f5" : "#2e3338"
    readonly property color iconPrimary: isDark ? "#f2f3f5" : "#2e3338"
    readonly property color iconSecondary: isDark ? "#b5bac1" : "#5b616a"
    readonly property color iconOnAccent: "#ffffff"

    // Buttons
    readonly property color buttonBase: isDark ? "#404249" : "#e3e5e8"
    readonly property color buttonHover: isDark ? "#4e5058" : "#d7dade"
    readonly property color buttonPressed: isDark ? "#3a3c43" : "#cfd3d8"
    readonly property color primaryButton: isDark ? "#5cad73" : "#5b9a72"
    readonly property color primaryButtonHover: isDark ? "#6bb884" : "#4f8f67"
    readonly property color primaryButtonPressed: isDark ? "#4a8b5f" : "#457d59"
    readonly property color primaryButtonText: "#ffffff"
    readonly property color ghostButton: isDark ? "#2b2d31" : "#f2f3f5"
    readonly property color ghostButtonHover: isDark ? "#35373c" : "#e8ebef"
    readonly property color ghostButtonPressed: isDark ? "#404249" : "#dfe3e7"
    readonly property color ghostButtonText: textPrimary
    readonly property color dangerButton: "#da373c"
    readonly property color dangerButtonHover: "#c23035"
    readonly property color dangerButtonPressed: "#a12828"
    readonly property color successButton: "#248046"
    readonly property color successButtonHover: "#1f6f3d"
    readonly property color successButtonPressed: "#1a5c33"
    readonly property color successButtonText: "#ffffff"

    // Accents & states
    readonly property color accentPurple: isDark ? "#6fba86" : "#5b9a72"
    readonly property color accentBlue: isDark ? "#5cad73" : "#5b9a72"
    readonly property color accentCyan: isDark ? "#8dc7a0" : "#6aa883"
    readonly property color accentPink: isDark ? "#c77d52" : "#bf7a54"
    readonly property color accentOrange: isDark ? "#d1a35c" : "#c79352"
    readonly property color statusError: "#da373c"
    readonly property color statusSuccess: "#248046"
    readonly property color statusWarn: "#f0b232"

    // Specific components
    readonly property color overlayBg: isDark ? "#80000000" : "#4d23272a"
    readonly property color progressBg: isDark ? "#232428" : "#dfe3e7"
    readonly property color scrollbarHandle: isDark ? "#4e5058" : "#c7ccd1"
    readonly property color divider: isDark ? "#404249" : "#d4d7dc"

    // Tab accents
    readonly property color tabReceive: isDark ? "#5cad73" : "#5b9a72"
    readonly property color tabSend: isDark ? "#8dc7a0" : "#6aa883"
    readonly property color tabSettings: isDark ? "#d1a35c" : "#c79352"

    // Decorative background
    readonly property color glowPrimary: isDark ? "#294132" : "#d8eadc"
    readonly property color glowSecondary: isDark ? "#2b332d" : "#e4ece6"
    readonly property color sceneTintTop: isDark ? "#232428" : "#f7f8fa"
    readonly property color sceneTintMid: isDark ? "#2b2d31" : "#eef1f6"
    readonly property color sceneTintBottom: isDark ? "#1e1f22" : "#e8ebef"
    readonly property color outlineSoft: isDark ? "#7eb68a55" : "#5b9a7255"
    readonly property color vignette: isDark ? "#22000000" : "#08000000"
}
