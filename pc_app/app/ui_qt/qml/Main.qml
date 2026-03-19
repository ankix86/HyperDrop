import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

ApplicationWindow {
    id: window
    width: 1320
    height: 860
    minimumWidth: 960
    minimumHeight: 700
    visible: true
    title: "HyperDrop"
    color: appTheme.windowBg

    Theme { id: appTheme }

    property int currentTab: 0
    readonly property int receiveStatusTabIndex: 3
    readonly property int spaceXs: 6
    readonly property int spaceSm: 10
    readonly property int spaceMd: 14
    readonly property int spaceLg: 18
    readonly property int radiusSm: 14
    readonly property int radiusMd: 20
    readonly property int radiusLg: 28
    readonly property int motionFast: 140
    readonly property int motionMed: 220
    readonly property int motionSlow: 280
    readonly property color textPrimary: appTheme.textPrimary
    readonly property color textSecondary: appTheme.textSecondary
    readonly property color textMuted: appTheme.textMuted
    readonly property color buttonBase: appTheme.buttonBase
    readonly property color buttonHover: appTheme.buttonHover
    readonly property color buttonPressed: appTheme.buttonPressed
    readonly property color buttonText: appTheme.buttonText
    readonly property color cardSurface: appTheme.cardSurface
    readonly property color cardBorder: appTheme.cardBorder
    readonly property color wavePurple: appTheme.accentPurple
    readonly property color waveBlue: appTheme.accentBlue
    readonly property color waveCyan: appTheme.accentCyan
    readonly property bool compactLayout: width < 1320
    readonly property bool settingsNarrow: width < 1180
    readonly property int contentTopInset: 86
    readonly property var tabs: [
        { label: "Receive", accent: appTheme.tabReceive, iconSource: appTheme.isDark ? "../../../assets/icons/tab-activity-dark.svg" : "../../../assets/icons/tab-activity-light.svg" },
        { label: "Send", accent: appTheme.tabSend, iconSource: appTheme.isDark ? "../../../assets/icons/tab-send-dark.svg" : "../../../assets/icons/tab-send-light.svg" },
        { label: "Setting", accent: appTheme.tabSettings, iconSource: appTheme.isDark ? "../../../assets/icons/tab-control-dark.svg" : "../../../assets/icons/tab-control-light.svg" }
    ]

    function statusColor(level) {
        if (level === "error") return appTheme.statusError
        if (level === "success") return appTheme.statusSuccess
        if (level === "warn") return appTheme.statusWarn
        return appTheme.textMuted
    }

    Rectangle {
        anchors.fill: parent
        gradient: Gradient {
            GradientStop { position: 0.0; color: appTheme.sceneTintTop }
            GradientStop { position: 0.55; color: appTheme.sceneTintMid }
            GradientStop { position: 1.0; color: appTheme.sceneTintBottom }
        }
    }
    

    Item {
        id: noiseField
        anchors.fill: parent
        clip: true

        Rectangle {
            id: ambientOrbLeft
            width: 560
            height: 560
            radius: 280
            x: -220
            y: 260
            color: appTheme.glowPrimary
            opacity: appTheme.isDark ? 0.16 : 0.24

            SequentialAnimation on x {
                loops: Animation.Infinite
                NumberAnimation { from: -240; to: -170; duration: 22000; easing.type: Easing.InOutSine }
                NumberAnimation { from: -170; to: -240; duration: 22000; easing.type: Easing.InOutSine }
            }
        }

        Rectangle {
            id: ambientOrbRight
            width: 460
            height: 460
            radius: 230
            x: window.width - 210
            y: -120
            color: appTheme.glowSecondary
            opacity: appTheme.isDark ? 0.14 : 0.22

            SequentialAnimation on y {
                loops: Animation.Infinite
                NumberAnimation { from: -140; to: -80; duration: 19000; easing.type: Easing.InOutSine }
                NumberAnimation { from: -80; to: -140; duration: 19000; easing.type: Easing.InOutSine }
            }
        }

        // Moving geometric accents distributed across the scene.
        Item {
            id: geoField
            anchors.fill: parent
            z: 2
            opacity: 0.62

            Repeater {
                model: [
                    { x: 18, y: 20, kind: "triangle", size: 44, drift: 18, spin: 7 },
                    { x: window.width * 0.1, y: window.height * 0.76, kind: "cross", size: 40, drift: 16, spin: -6 },
                    { x: window.width * 0.2, y: window.height * 0.5, kind: "diamond", size: 42, drift: 14, spin: 8 },
                    { x: window.width * 0.32, y: window.height * 0.16, kind: "circle", size: 36, drift: 15, spin: -5 },
                    { x: window.width * 0.42, y: window.height * 0.62, kind: "triangle", size: 40, drift: 20, spin: 6 },
                    { x: window.width * 0.54, y: window.height * 0.44, kind: "cross", size: 44, drift: 18, spin: -7 },
                    { x: window.width * 0.64, y: window.height * 0.8, kind: "diamond", size: 38, drift: 16, spin: 6 },
                    { x: window.width * 0.74, y: window.height * 0.22, kind: "circle", size: 34, drift: 14, spin: -6 },
                    { x: window.width * 0.82, y: window.height * 0.68, kind: "triangle", size: 42, drift: 20, spin: 9 },
                    { x: window.width * 0.92, y: window.height * 0.38, kind: "circle", size: 36, drift: 15, spin: -8 }
                ]

                delegate: Item {
                    required property var modelData

                    width: modelData.size
                    height: modelData.size
                    x: modelData.x
                    y: modelData.y
                    opacity: 0.62

                    SequentialAnimation on x {
                        loops: Animation.Infinite
                        NumberAnimation { from: modelData.x - modelData.drift; to: modelData.x + modelData.drift; duration: 23000; easing.type: Easing.InOutSine }
                        NumberAnimation { from: modelData.x + modelData.drift; to: modelData.x - modelData.drift; duration: 23000; easing.type: Easing.InOutSine }
                    }
                    SequentialAnimation on y {
                        loops: Animation.Infinite
                        NumberAnimation { from: modelData.y - modelData.drift * 0.6; to: modelData.y + modelData.drift * 0.6; duration: 19000; easing.type: Easing.InOutSine }
                        NumberAnimation { from: modelData.y + modelData.drift * 0.6; to: modelData.y - modelData.drift * 0.6; duration: 19000; easing.type: Easing.InOutSine }
                    }
                    SequentialAnimation on rotation {
                        loops: Animation.Infinite
                        NumberAnimation { from: -modelData.spin; to: modelData.spin; duration: 25000; easing.type: Easing.InOutSine }
                        NumberAnimation { from: modelData.spin; to: -modelData.spin; duration: 25000; easing.type: Easing.InOutSine }
                    }

                    Loader {
                        anchors.fill: parent
                        sourceComponent: modelData.kind === "triangle"
                            ? triangleShape
                            : modelData.kind === "diamond"
                                ? diamondShape
                                : modelData.kind === "cross"
                                    ? crossShape
                                    : circleShape
                    }
                }
            }

            Component {
                id: triangleShape
                Canvas {
                    anchors.fill: parent
                    antialiasing: true
                    onPaint: {
                        var ctx = getContext("2d")
                        ctx.reset()
                        ctx.beginPath()
                        ctx.moveTo(width * 0.5, 2)
                        ctx.lineTo(width - 3, height - 3)
                        ctx.lineTo(3, height - 3)
                        ctx.closePath()
                        ctx.strokeStyle = appTheme.outlineSoft
                        ctx.lineWidth = 2.6
                        ctx.stroke()
                    }
                }
            }

            Component {
                id: diamondShape
                Rectangle {
                    anchors.centerIn: parent
                    width: parent.width * 0.66
                    height: parent.height * 0.66
                    color: "transparent"
                    border.width: 2.6
                    border.color: appTheme.outlineSoft
                    rotation: 45
                }
            }

            Component {
                id: crossShape
                Item {
                    anchors.fill: parent
                    Rectangle {
                        anchors.centerIn: parent
                        width: 2.6
                        height: parent.height * 0.74
                        color: appTheme.outlineSoft
                    }
                    Rectangle {
                        anchors.centerIn: parent
                        width: parent.width * 0.74
                        height: 2.6
                        color: appTheme.outlineSoft
                    }
                }
            }

            Component {
                id: circleShape
                Rectangle {
                    anchors.centerIn: parent
                    width: parent.width * 0.68
                    height: parent.height * 0.68
                    radius: width / 2
                    color: "transparent"
                    border.width: 2.6
                    border.color: appTheme.outlineSoft
                }
            }
        }

        // Soft vignette to keep center readable and push glow toward edges.
        Rectangle {
            anchors.fill: parent
            gradient: Gradient {
                orientation: Gradient.Vertical
                GradientStop { position: 0.0; color: appTheme.vignette }
                GradientStop { position: 0.35; color: "transparent" }
                GradientStop { position: 0.72; color: "transparent" }
                GradientStop { position: 1.0; color: appTheme.vignette }
            }
        }

    }

    RowLayout {
        anchors.fill: parent
        anchors.margins: 0
        spacing: 0

        // â”€â”€ Sidebar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Rectangle {
            Layout.fillHeight: true
            Layout.preferredWidth: 220
            color: appTheme.sidebarBg
            border.width: 0
            
            Rectangle { 
                anchors.right: parent.right
                width: 1
                height: parent.height
                color: appTheme.sidebarBorder
            }

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 20
                spacing: 30

                // Logo/Title
                Text {
                    text: "HyperDrop"
                    color: window.textPrimary
                    font.pixelSize: 24
                    font.bold: true
                    Layout.alignment: Qt.AlignHCenter
                    Layout.topMargin: 20
                }

                ColumnLayout {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    spacing: 12
                    Layout.topMargin: 20

                    Repeater {
                        model: window.tabs
                        delegate: Rectangle {
                            readonly property bool selectedState: window.currentTab === index || (window.currentTab === window.receiveStatusTabIndex && index === 0)
                            Layout.fillWidth: true
                            Layout.preferredHeight: 54
                            radius: 14
                            color: selectedState ? appTheme.buttonPressed : "transparent"
                             
                            Behavior on color { ColorAnimation { duration: 150 } }

                            RowLayout {
                                anchors.fill: parent
                                anchors.margins: 12
                                spacing: 14

                                Image {
                                    Layout.preferredWidth: 20
                                    Layout.preferredHeight: 20
                                    source: modelData.iconSource
                                    fillMode: Image.PreserveAspectFit
                                    smooth: true
                                    mipmap: true
                                    opacity: selectedState ? 1.0 : 0.72
                                }

                                Text {
                                    text: modelData.label
                                    color: selectedState ? window.textPrimary : window.textSecondary
                                    font.pixelSize: 15
                                    font.bold: selectedState
                                    Layout.fillWidth: true
                                }
                            }

                            MouseArea {
                                anchors.fill: parent
                                cursorShape: Qt.PointingHandCursor
                                onClicked: {
                                    if (backend.receiveSessionVisible || backend.incomingTransferVisible) {
                                        return
                                    }
                                    window.currentTab = index
                                }
                            }
                        }
                    }

                    Item { Layout.fillHeight: true } // Spacer
                }
            }
        }

        // â”€â”€ Main Content Area â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        StackLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            currentIndex: window.currentTab

            ReceivePage { }
            SendPage { }
            SettingsPage { }
            ReceiveStatusPage { }
        }
    }

    Connections {
        target: backend
        function onReceiveSessionChanged() {
            if (backend.receiveSessionVisible) {
                window.currentTab = window.receiveStatusTabIndex
                return
            }
            if (window.currentTab === window.receiveStatusTabIndex) {
                window.currentTab = 0
            }
        }
        function onIncomingTransferChanged() {
            if (backend.incomingTransferVisible) {
                window.currentTab = 0
            }
        }
    }

    Rectangle {
        anchors.fill: parent
        z: 49
        visible: backend.outgoingTransferRequestVisible && !backend.incomingTransferVisible
        enabled: visible
        color: appTheme.overlayBg

        MouseArea {
            anchors.fill: parent
        }

        Rectangle {
            id: outgoingRequestPanel
            width: Math.min(window.width * 0.56, 760)
            height: Math.min(window.height * 0.82, 620)
            clip: true
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.verticalCenter: parent.verticalCenter
            radius: 30
            color: appTheme.cardSurface
            border.width: 1
            border.color: appTheme.cardBorder

            Rectangle {
                anchors.fill: parent
                radius: parent.radius
                gradient: Gradient {
                    GradientStop { position: 0.0; color: appTheme.elevatedSurface }
                    GradientStop { position: 0.35; color: appTheme.cardSurface }
                    GradientStop { position: 1.0; color: appTheme.insetSurface }
                }
            }

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 24
                spacing: 12

                Rectangle {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 104
                    radius: 18
                    color: appTheme.itemBg
                    border.width: 1
                    border.color: appTheme.itemBorder

                    RowLayout {
                        anchors.fill: parent
                        anchors.margins: 14
                        spacing: 14

                        Rectangle {
                            width: 44
                            height: 44
                            radius: 22
                            color: appTheme.avatarBg

                            Text {
                                anchors.centerIn: parent
                                text: "S"
                                color: appTheme.avatarText
                                font.pixelSize: 18
                                font.bold: true
                            }
                        }

                        ColumnLayout {
                            Layout.fillWidth: true
                            spacing: 4

                            Text {
                                text: backend.outgoingTransferRequestSenderName
                                color: window.textPrimary
                                font.pixelSize: 24
                                font.bold: true
                                elide: Text.ElideRight
                                maximumLineCount: 1
                                Layout.fillWidth: true
                            }

                            RowLayout {
                                spacing: 8

                                Rectangle {
                                    radius: 12
                                    color: appTheme.badgeBg
                                    implicitWidth: 88
                                    implicitHeight: 26

                                    Text {
                                        anchors.centerIn: parent
                                        text: "PC"
                                        color: appTheme.badgeText
                                        font.pixelSize: 12
                                        font.bold: true
                                    }
                                }
                            }
                        }
                    }
                }

                Text {
                    Layout.fillWidth: true
                    text: "â†“"
                    color: window.textPrimary
                    horizontalAlignment: Text.AlignHCenter
                    font.pixelSize: 38
                    font.bold: true
                }

                Rectangle {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 104
                    radius: 18
                    color: appTheme.itemBg
                    border.width: 1
                    border.color: appTheme.itemBorder

                    RowLayout {
                        anchors.fill: parent
                        anchors.margins: 14
                        spacing: 14

                        Rectangle {
                            width: 44
                            height: 44
                            radius: 22
                            color: appTheme.avatarBg

                            Text {
                                anchors.centerIn: parent
                                text: "R"
                                color: appTheme.avatarText
                                font.pixelSize: 18
                                font.bold: true
                            }
                        }

                        ColumnLayout {
                            Layout.fillWidth: true
                            spacing: 4

                            Text {
                                text: backend.outgoingTransferRequestReceiverName
                                color: window.textPrimary
                                font.pixelSize: 24
                                font.bold: true
                                elide: Text.ElideRight
                                maximumLineCount: 1
                                Layout.fillWidth: true
                            }

                            RowLayout {
                                spacing: 8

                                Rectangle {
                                    radius: 12
                                    color: appTheme.badgeBg
                                    implicitWidth: 112
                                    implicitHeight: 26

                                    Text {
                                        anchors.centerIn: parent
                                        text: "Receiver"
                                        color: appTheme.badgeText
                                        font.pixelSize: 12
                                        font.bold: true
                                    }
                                }
                            }
                        }
                    }
                }

                Item { Layout.fillHeight: true }

                Text {
                    Layout.fillWidth: true
                    text: backend.outgoingTransferRequestMessage
                    color: appTheme.textPrimary
                    horizontalAlignment: Text.AlignHCenter
                    font.pixelSize: 27
                    font.bold: true
                    wrapMode: Text.WordWrap
                }

                Item { Layout.preferredHeight: 8 }

                NeonButton {
                    Layout.alignment: Qt.AlignHCenter
                    text: backend.outgoingTransferRequestActionLabel
                    role: backend.outgoingTransferRequestRejected ? "secondary" : "primary"
                    compact: false
                    width: 220
                    onClicked: function() { backend.cancelOrCloseOutgoingTransferRequest() }
                }
            }
        }
    }

    component IncomingTransferPage: Item {
        Rectangle {
            anchors.fill: parent
            color: appTheme.isDark ? appTheme.windowBg : "transparent"
        }

        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 18
            spacing: 12

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 4

                Text {
                    text: backend.incomingTransferSenderName || "Unknown device"
                    color: window.textPrimary
                    font.pixelSize: 30
                    font.bold: true
                }
                Text {
                    text: backend.incomingTransferSenderClosed
                          ? "Sender closed the session"
                          : "Incoming transfer request"
                    color: appTheme.textSecondary
                    font.pixelSize: 16
                }
                Text {
                    text: "Save to folder: " + backend.incomingTransferTargetDir
                    color: appTheme.textSecondary
                    font.pixelSize: 16
                    elide: Text.ElideMiddle
                    maximumLineCount: 1
                    Layout.fillWidth: true
                }
            }

            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                radius: 16
                color: appTheme.cardSurface
                border.width: 1
                border.color: appTheme.cardBorder
                clip: true

                Item {
                    anchors.fill: parent
                    anchors.margins: 12

                    ListView {
                        id: incomingFileList
                        anchors.fill: parent
                        clip: true
                        spacing: 8
                        model: backend.incomingTransferItems
                        ScrollBar.vertical: ThemedVScrollBar { }

                        delegate: Rectangle {
                            width: ListView.view.width
                            height: modelData.editable && modelData.selected && !backend.incomingTransferSenderClosed ? 108 : 74
                            radius: 14
                            color: modelData.selected || !modelData.selectable ? appTheme.itemSelectedBg : appTheme.itemBg
                            border.width: 1
                            border.color: modelData.selected || !modelData.selectable ? appTheme.itemSelectedBorder : appTheme.itemBorder
                            opacity: modelData.selected || !modelData.selectable ? 1.0 : 0.72

                            ColumnLayout {
                                anchors.fill: parent
                                anchors.margins: 10
                                spacing: 8

                                RowLayout {
                                    Layout.fillWidth: true
                                    spacing: 10

                                    Rectangle {
                                        width: 42
                                        height: 42
                                        radius: 11
                                        color: appTheme.buttonBase

                                        Image {
                                            anchors.centerIn: parent
                                            width: 18
                                            height: 18
                                            source: appTheme.isDark
                                                ? "../../../assets/icons/folder-placeholder-dark.svg"
                                                : "../../../assets/icons/folder-placeholder-light.svg"
                                            fillMode: Image.PreserveAspectFit
                                            smooth: true
                                        }
                                    }

                                    ColumnLayout {
                                        Layout.fillWidth: true
                                        spacing: 2

                                        Text {
                                            text: modelData.fileName + " (" + modelData.sizeText + ")"
                                            color: window.textPrimary
                                            font.pixelSize: 15
                                            font.bold: true
                                            elide: Text.ElideMiddle
                                            maximumLineCount: 1
                                            Layout.fillWidth: true
                                        }

                                        Text {
                                            text: modelData.parentPath ? modelData.parentPath + " • " + modelData.sizeText : modelData.sizeText
                                            color: appTheme.textSecondary
                                            font.pixelSize: 13
                                            elide: Text.ElideRight
                                            maximumLineCount: 1
                                            Layout.fillWidth: true
                                        }
                                    }

                                    Rectangle {
                                        radius: 12
                                        color: appTheme.badgeBg
                                        border.width: 1
                                        border.color: appTheme.itemBorder
                                        implicitWidth: typeBadge.implicitWidth + 22
                                        implicitHeight: 30

                                        Text {
                                            id: typeBadge
                                            anchors.centerIn: parent
                                            text: modelData.isDirectory ? "Folder" : "File"
                                            color: modelData.isDirectory ? appTheme.badgeText : appTheme.accentBlue
                                            font.pixelSize: 11
                                            font.bold: true
                                        }
                                    }

                                    Rectangle {
                                        width: 30
                                        height: 30
                                        radius: 9
                                        color: modelData.selected ? appTheme.itemSelectedBg : appTheme.insetSurface
                                        border.width: 1
                                        border.color: modelData.selected ? appTheme.itemSelectedBorder : appTheme.itemBorder
                                        visible: modelData.selectable && !backend.incomingTransferSenderClosed

                                        Image {
                                            anchors.centerIn: parent
                                            width: 14
                                            height: 14
                                            visible: modelData.selected
                                            source: appTheme.isDark
                                                ? "../../../assets/icons/checkmark-dark.svg"
                                                : "../../../assets/icons/checkmark-light.svg"
                                            fillMode: Image.PreserveAspectFit
                                            smooth: true
                                        }

                                        MouseArea {
                                            anchors.fill: parent
                                            cursorShape: Qt.PointingHandCursor
                                            onClicked: function() { backend.setIncomingTransferItemSelected(modelData.relativePath, !modelData.selected) }
                                        }
                                    }
                                }

                                Item {
                                    Layout.fillWidth: true
                                    Layout.preferredHeight: modelData.editable && modelData.selected && !backend.incomingTransferSenderClosed ? renameRow.implicitHeight : 0
                                    visible: modelData.editable && modelData.selected && !backend.incomingTransferSenderClosed

                                    RowLayout {
                                        id: renameRow
                                        anchors.fill: parent
                                        spacing: 10

                                        Text {
                                            text: "Receive as"
                                            color: appTheme.textSecondary
                                            font.pixelSize: 11
                                            font.bold: true
                                        }

                                        Rectangle {
                                            Layout.fillWidth: true
                                            Layout.preferredHeight: 42
                                            radius: 14
                                            color: appTheme.fieldBg
                                            border.width: 1
                                            border.color: renameField.activeFocus ? appTheme.fieldFocus : appTheme.fieldBorder

                                            TextField {
                                                id: renameField
                                                anchors.fill: parent
                                                anchors.margins: 3
                                                anchors.leftMargin: 12
                                                text: modelData.proposedName
                                                color: appTheme.textPrimary
                                                font.pixelSize: 13
                                                background: null
                                                selectByMouse: true
                                                onTextEdited: function() { backend.renameIncomingTransferItem(modelData.relativePath, text) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: 188
                Layout.minimumHeight: 188
                radius: 16
                color: appTheme.panelBg
                border.width: 1
                border.color: appTheme.panelBorder

                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 14
                    spacing: 8

                    Text {
                        text: backend.incomingTransferSenderClosed ? "Request closed" : "Review before receive"
                        color: window.textPrimary
                        font.pixelSize: 20
                        font.bold: true
                    }

                    Rectangle {
                        Layout.fillWidth: true
                        height: 10
                        radius: 5
                        color: appTheme.progressBg
                        clip: true

                        Rectangle {
                            width: parent.width * (backend.incomingTransferCanAccept ? 1.0 : 0.0)
                            height: parent.height
                            radius: 5
                            color: appTheme.accentBlue
                        }
                    }

                    Text {
                        text: backend.incomingTransferSummary
                        color: appTheme.textSecondary
                        font.pixelSize: 13
                    }
                    Text {
                        text: backend.incomingTransferSenderClosed
                              ? "Nothing will be received from this request."
                              : "The sender waits until you accept. Nothing is written until you confirm."
                        color: appTheme.textSecondary
                        font.pixelSize: 13
                        wrapMode: Text.WordWrap
                        Layout.fillWidth: true
                    }

                    Item { Layout.fillHeight: true }

                    RowLayout {
                        Layout.fillWidth: true
                        Item { Layout.fillWidth: true }

                        NeonButton {
                            visible: !backend.incomingTransferSenderClosed
                            text: "Decline"
                            compact: false
                            role: "danger"
                            onClicked: function() { backend.declineIncomingTransfer() }
                        }

                        NeonButton {
                            visible: !backend.incomingTransferSenderClosed
                            text: "Accept"
                            compact: false
                            role: "primary"
                            opacity: backend.incomingTransferCanAccept ? 1.0 : 0.45
                            onClicked: function() {
                                if (backend.incomingTransferCanAccept) {
                                    backend.acceptIncomingTransfer()
                                }
                            }
                        }

                        NeonButton {
                            visible: backend.incomingTransferSenderClosed
                            text: "Okay"
                            compact: false
                            role: "success"
                            onClicked: function() { backend.acknowledgeIncomingTransferClosed() }
                        }
                    }
                }
            }
        }
    }
    component ReceivePage: Item {
        id: receivePage
        property bool editingAlias: false
        property string aliasDraft: backend.alias

        IncomingTransferPage {
            anchors.fill: parent
            visible: backend.incomingTransferVisible
        }

        Connections {
            target: backend
            function onAliasChanged() {
                if (!receivePage.editingAlias) {
                    receivePage.aliasDraft = backend.alias
                }
            }
        }

        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 40
            spacing: 20
            visible: !backend.incomingTransferVisible


            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: 0
                visible: false
                radius: 24
                color: appTheme.cardSurface
                border.width: 1
                border.color: appTheme.cardBorder
                clip: true

                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 16
                    spacing: 10

                    Text {
                        text: backend.receiveSessionTitle
                        color: window.textPrimary
                        font.pixelSize: 24
                        font.bold: true
                    }
                    Text {
                        text: "Save to folder: " + backend.receiveSessionTargetDir
                        color: appTheme.textSecondary
                        font.pixelSize: 13
                        elide: Text.ElideMiddle
                        Layout.fillWidth: true
                    }
                    Text {
                        text: backend.receiveSessionSummary
                        color: appTheme.accentBlue
                        font.pixelSize: 13
                        font.bold: true
                    }

                    ListView {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        clip: true
                        spacing: 8
                        model: backend.receiveSessionFiles
                        delegate: Rectangle {
                            required property var modelData
                            width: ListView.view.width
                            height: 56
                            radius: 14
                            color: modelData.done ? appTheme.itemSelectedBg : appTheme.itemBg
                            border.width: 1
                            border.color: modelData.done ? appTheme.itemSelectedBorder : appTheme.itemBorder

                            RowLayout {
                                anchors.fill: parent
                                anchors.margins: 10
                                spacing: 10

                                ColumnLayout {
                                    Layout.fillWidth: true
                                    spacing: 2
                                    Text {
                                        text: modelData.fileName
                                        color: window.textPrimary
                                        font.pixelSize: 14
                                        font.bold: true
                                        elide: Text.ElideMiddle
                                        maximumLineCount: 1
                                        Layout.fillWidth: true
                                    }
                                    Text {
                                        text: modelData.statusText + " â€¢ " + modelData.sizeText
                                        color: modelData.done ? appTheme.accentBlue : appTheme.textSecondary
                                        font.pixelSize: 12
                                        elide: Text.ElideRight
                                        maximumLineCount: 1
                                        Layout.fillWidth: true
                                    }
                                }

                                NeonButton {
                                    visible: modelData.canOpen
                                    enabled: modelData.canOpen
                                    compact: true
                                    text: "Open"
                                    onClicked: function() { backend.openReceivedSessionFile(modelData.openPath) }
                                }
                            }
                        }
                    }

                    Rectangle {
                        Layout.fillWidth: true
                        height: 10
                        radius: 5
                        color: appTheme.progressBg
                        clip: true
                        Rectangle {
                            width: parent.width * backend.receiveSessionProgress
                            height: parent.height
                            radius: 5
                            gradient: Gradient {
                                GradientStop { position: 0.0; color: appTheme.primaryButtonHover }
                                GradientStop { position: 1.0; color: appTheme.accentBlue }
                            }
                        }
                    }
                }
            }

            Item { Layout.fillHeight: true }

            // Central Device Identity
            ColumnLayout {
                Layout.alignment: Qt.AlignCenter
                spacing: 24

                Rectangle {
                    Layout.alignment: Qt.AlignCenter
                    width: 140; height: 140
                    radius: 70
                    color: "transparent"
                    border.width: 4
                    border.color: appTheme.accentBlue
                    
                    Rectangle {
                        anchors.centerIn: parent
                        width: 80; height: 80; radius: 40
                        color: appTheme.accentBlue
                    }

                    SequentialAnimation on scale {
                        loops: Animation.Infinite
                        NumberAnimation { from: 1.0; to: 1.1; duration: 2000; easing.type: Easing.InOutQuad }
                        NumberAnimation { from: 1.1; to: 1.0; duration: 2000; easing.type: Easing.InOutQuad }
                    }
                }

                ColumnLayout {
                    Layout.alignment: Qt.AlignCenter
                    spacing: 8
                    Text {
                        Layout.alignment: Qt.AlignCenter
                        width: 380
                        visible: !receivePage.editingAlias
                        text: backend.alias
                        color: window.textPrimary
                        font.pixelSize: 28
                        font.bold: true
                        horizontalAlignment: Text.AlignHCenter
                        wrapMode: Text.NoWrap
                        elide: Text.ElideRight
                    }

                    Rectangle {
                        Layout.alignment: Qt.AlignCenter
                        visible: !receivePage.editingAlias
                        width: 40
                        height: 40
                        radius: 20
                        color: appTheme.buttonBase
                        border.width: 1
                        border.color: appTheme.itemSelectedBorder

                        Image {
                            anchors.centerIn: parent
                            width: 18
                            height: 18
                            source: appTheme.isDark
                                ? "../../../assets/icons/edit-pencil-dark.svg"
                                : "../../../assets/icons/edit-pencil-light.svg"
                            fillMode: Image.PreserveAspectFit
                            smooth: true
                        }

                        MouseArea {
                            anchors.fill: parent
                            cursorShape: Qt.PointingHandCursor
                            onClicked: {
                                receivePage.aliasDraft = backend.alias
                                receivePage.editingAlias = true
                            }
                        }
                    }

                    ColumnLayout {
                        Layout.alignment: Qt.AlignCenter
                        spacing: 10
                        visible: receivePage.editingAlias

                        Rectangle {
                            Layout.alignment: Qt.AlignCenter
                            width: 340
                            height: 52
                            radius: 18
                            color: appTheme.fieldBg
                            border.width: 1
                            border.color: appTheme.fieldBorder

                            TextField {
                                anchors.fill: parent
                                anchors.margins: 4
                                anchors.leftMargin: 12
                                anchors.rightMargin: 12
                                text: receivePage.aliasDraft
                                horizontalAlignment: TextInput.AlignHCenter
                                color: window.textPrimary
                                font.pixelSize: 24
                                font.bold: true
                                background: null
                                selectByMouse: true
                                onTextChanged: function() { receivePage.aliasDraft = text }
                            }
                        }

                        RowLayout {
                            Layout.alignment: Qt.AlignCenter
                            spacing: 10

                            NeonButton {
                                text: "Cancel"
                                compact: true
                                role: "ghost"
                                onClicked: {
                                    receivePage.aliasDraft = backend.alias
                                    receivePage.editingAlias = false
                                }
                            }

                            NeonButton {
                                text: "Apply"
                                compact: true
                                role: "primary"
                                enabled: receivePage.aliasDraft.trim().length > 0 && receivePage.aliasDraft.trim() !== backend.alias
                                opacity: enabled ? 1.0 : 0.45
                                onClicked: {
                                    backend.setAlias(receivePage.aliasDraft)
                                    receivePage.editingAlias = false
                                }
                            }
                        }
                    }
                }
            }

            Item { Layout.fillHeight: true }

            // Receiver Toggle
            ColumnLayout {
                Layout.alignment: Qt.AlignCenter
                spacing: 6

                Rectangle {
                    width: 210
                    height: 50
                    radius: 25
                    color: backend.serverRunning ? appTheme.itemSelectedBg : appTheme.ghostButton
                    border.width: 1
                    border.color: backend.serverRunning ? appTheme.itemSelectedBorder : appTheme.fieldBorder

                    RowLayout {
                        anchors.fill: parent
                        anchors.margins: 12
                        spacing: 8
                        Text {
                            text: "Online"
                            color: backend.serverRunning ? appTheme.accentBlue : window.textSecondary
                            font.pixelSize: 13
                            font.bold: true
                            Layout.fillWidth: true
                        }
                        Rectangle {
                            width: 26
                            height: 26
                            radius: 13
                            color: backend.serverRunning ? appTheme.accentBlue : appTheme.buttonPressed
                            Text {
                                anchors.centerIn: parent
                                text: backend.serverRunning ? "ON" : "OFF"
                                color: backend.serverRunning ? appTheme.primaryButtonText : appTheme.textPrimary
                                font.pixelSize: 10
                                font.bold: true
                            }
                        }
                    }

                    MouseArea {
                        anchors.fill: parent
                        cursorShape: Qt.PointingHandCursor
                        onClicked: function() { backend.serverRunning ? backend.stopServer() : backend.startServer() }
                    }
                }
            }
            
            Item { Layout.preferredHeight: 40 }
        }
    }

    component SendPage: Item {
        id: sendPage
        property bool selectionManagerOpen: false
        readonly property int discoveredDeviceCount: backend.devices.length
        readonly property bool hasAvailableDevices: discoveredDeviceCount > 0

        Timer {
            interval: 5000
            repeat: true
            running: window.currentTab === 1
            triggeredOnStart: true
            onTriggered: {
                networkPulse.restart()
                backend.refreshDiscoveryQuietly()
            }
        }

        SequentialAnimation {
            id: networkPulse
            running: false
            PropertyAnimation { target: networkIndicator; property: "scale"; to: 1.16; duration: 260; easing.type: Easing.OutCubic }
            PropertyAnimation { target: networkIndicator; property: "scale"; to: 1.0; duration: 540; easing.type: Easing.OutElastic }
        }

        ScrollView {
            id: sendScroll
            anchors.fill: parent
            ScrollBar.vertical: ScrollBar {
                active: false
                policy: ScrollBar.AlwaysOff
                visible: false
                anchors.right: parent.right
                anchors.rightMargin: 4
            }
            
            ColumnLayout {
                width: sendScroll.availableWidth
                anchors.margins: 40
                spacing: 32

                // Selection Collection
                SectionCard {
                    id: selectionCard
                    Layout.fillWidth: true
                    Layout.topMargin: 40
                    Layout.leftMargin: 40
                    Layout.rightMargin: 40
                    height: selectionCard.hasSelection ? 288 : 338
                    title: "Selection"
                    subtitle: selectionCard.hasSelection
                        ? "A compact overview of your current collection. Use Edit to review every item."
                        : "Add files or folders, then review details in Edit when your collection is ready."
                    readonly property bool hasSelection: backend.sendSelectionCount > 0
                    readonly property int previewLimit: width >= 1120 ? 7 : (width >= 900 ? 6 : 5)
                    readonly property int previewItemCount: Math.min(backend.sendSelectionCount, previewLimit)
                    readonly property int overflowCount: Math.max(0, backend.sendSelectionCount - previewItemCount)
                    readonly property bool dragActive: selectionDropArea.containsDrag

                    ColumnLayout {
                        anchors.fill: parent
                        anchors.margins: 18
                        anchors.topMargin: 84
                        spacing: 14

                        RowLayout {
                            Layout.fillWidth: true
                            spacing: 20

                            ColumnLayout {
                                spacing: 6
                                Text {
                                    text: "Files: " + backend.sendSelectionCount
                                    color: window.textPrimary
                                    font.pixelSize: 15
                                    font.bold: true
                                }
                                Text {
                                    text: "Size: " + backend.sendSelectionSizeText
                                    color: window.textSecondary
                                    font.pixelSize: 13
                                }
                            }

                            Item { Layout.fillWidth: true }

                            NeonButton {
                                text: "+ Add Files"
                                role: "primary"
                                compact: false
                                onClicked: function() { backend.addSendFiles() }
                            }

                            NeonButton {
                                text: "Add Folder"
                                compact: false
                                onClicked: function() { backend.addSendFolder() }
                            }

                            NeonButton {
                                text: "Edit"
                                role: "ghost"
                                compact: false
                                enabled: selectionCard.hasSelection
                                opacity: selectionCard.hasSelection ? 1.0 : 0.5
                                onClicked: function() { sendPage.selectionManagerOpen = true }
                            }
                        }

                        Rectangle {
                            id: selectionPreviewPanel
                            Layout.fillWidth: true
                            Layout.fillHeight: true
                            radius: 22
                            color: selectionCard.dragActive ? appTheme.itemSelectedBg : appTheme.panelBg
                            border.width: 1
                            border.color: selectionCard.dragActive ? appTheme.itemSelectedBorder : appTheme.panelBorder
                            clip: true
                            scale: selectionCard.dragActive ? 1.01 : 1.0

                            Behavior on color { ColorAnimation { duration: window.motionMed } }
                            Behavior on scale { NumberAnimation { duration: window.motionFast; easing.type: Easing.OutCubic } }

                            Rectangle {
                                anchors.fill: parent
                                radius: parent.radius
                                gradient: Gradient {
                                    GradientStop { position: 0.0; color: selectionCard.dragActive ? appTheme.itemSelectedBg : appTheme.cardSurface }
                                    GradientStop { position: 1.0; color: appTheme.insetSurface }
                                }
                                opacity: 0.82
                            }

                            Text {
                                anchors.top: parent.top
                                anchors.right: parent.right
                                anchors.topMargin: 14
                                anchors.rightMargin: 18
                                visible: selectionCard.hasSelection || selectionCard.dragActive
                                text: selectionCard.dragActive ? "Drop to add" : (selectionCard.hasSelection ? "Click Edit to manage all" : "Ready for your first files")
                                color: selectionCard.dragActive ? appTheme.textPrimary : window.textMuted
                                font.pixelSize: 12
                                font.bold: true
                            }

                            ColumnLayout {
                                visible: !selectionCard.hasSelection
                                anchors.fill: parent
                                anchors.margins: 20
                                width: undefined
                                spacing: 10

                                Item { Layout.fillHeight: true }

                                SelectionDropGlyph {
                                    Layout.alignment: Qt.AlignHCenter
                                    compact: true
                                    accent: selectionCard.dragActive ? appTheme.accentBlue : appTheme.textSecondary
                                }

                                Text {
                                    Layout.fillWidth: true
                                    text: "No items selected yet"
                                    color: window.textPrimary
                                    font.pixelSize: 18
                                    font.bold: true
                                    horizontalAlignment: Text.AlignHCenter
                                }

                                Text {
                                    Layout.fillWidth: true
                                    text: "Use Add Files or Add Folder to build your collection. You can also drag files onto this panel."
                                    color: window.textSecondary
                                    font.pixelSize: 12
                                    wrapMode: Text.WordWrap
                                    horizontalAlignment: Text.AlignHCenter
                                }

                                Item { Layout.fillHeight: true }
                            }

                            ColumnLayout {
                                visible: selectionCard.hasSelection
                                anchors.fill: parent
                                anchors.margins: 18
                                spacing: 12

                                Row {
                                    id: selectionPreviewRow
                                    spacing: 10
                                    Layout.alignment: Qt.AlignHCenter

                                    Repeater {
                                        model: selectionCard.previewItemCount
                                        delegate: SelectionPreviewTile {
                                            itemData: backend.sendSelection[index]
                                        }
                                    }

                                    SelectionPreviewTile {
                                        visible: selectionCard.overflowCount > 0
                                        overflow: true
                                        overflowCount: selectionCard.overflowCount
                                    }
                                }

                                Text {
                                    width: parent.width
                                    text: "Previewing the current collection. Open Edit to review names, remove items, or clear everything."
                                    color: window.textSecondary
                                    font.pixelSize: 12
                                    horizontalAlignment: Text.AlignHCenter
                                    wrapMode: Text.WordWrap
                                }
                            }

                            DropArea {
                                id: selectionDropArea
                                anchors.fill: parent
                                onDropped: function(drop) {
                                    if (drop.urls && drop.urls.length > 0) {
                                        backend.addSendDroppedPaths(drop.urls)
                                    }
                                }
                            }

                            MouseArea {
                                anchors.fill: parent
                                enabled: selectionCard.hasSelection && !selectionCard.dragActive
                                hoverEnabled: true
                                cursorShape: enabled ? Qt.PointingHandCursor : Qt.ArrowCursor
                                onClicked: sendPage.selectionManagerOpen = true
                            }
                        }
                    }
                }

                // Nearby Devices Section
                SectionCard {
                    Layout.fillWidth: true
                    Layout.leftMargin: 40
                    Layout.rightMargin: 40
                    height: 320
                    title: "Nearby Devices"
                    subtitle: hasAvailableDevices
                        ? "Found " + discoveredDeviceCount + " device" + (discoveredDeviceCount === 1 ? "" : "s") + ". Send your current selection to any online device."
                        : "Searching your network for available devices."

                    RowLayout {
                        anchors.right: parent.right
                        anchors.top: parent.top
                        anchors.margins: 20
                        anchors.topMargin: 22
                        spacing: 12

                        Text {
                            text: hasAvailableDevices
                                ? discoveredDeviceCount + " online"
                                : "Searching for Devices"
                            color: hasAvailableDevices ? appTheme.accentBlue : window.textSecondary
                            font.pixelSize: 14
                            font.bold: true
                        }

                        NetworkScanButton {
                            id: networkIndicator
                            active: hasAvailableDevices
                            onClicked: function() { backend.refreshDiscovery() }
                        }
                    }

                    ListView {
                        id: deviceList
                        anchors.fill: parent; anchors.margins: 20; anchors.topMargin: 84
                        clip: true; spacing: 12
                        model: backend.devices
                        delegate: Rectangle {
                            width: deviceList.width; height: 72; radius: 18; color: appTheme.itemBg; border.width: 1; border.color: appTheme.itemBorder
                            RowLayout {
                                anchors.fill: parent; anchors.margins: 14; spacing: 16
                                Rectangle {
                                    width: 44
                                    height: 44
                                    radius: 22
                                    color: appTheme.buttonBase
                                    border.width: 1
                                    border.color: appTheme.itemSelectedBorder

                                    Image {
                                        anchors.centerIn: parent
                                        width: 20
                                        height: 20
                                        source: appTheme.isDark
                                            ? "../../../assets/icons/device-placeholder-dark.svg"
                                            : "../../../assets/icons/device-placeholder-light.svg"
                                        fillMode: Image.PreserveAspectFit
                                        smooth: true
                                    }
                                }
                                ColumnLayout {
                                    Layout.alignment: Qt.AlignVCenter | Qt.AlignLeft
                                    spacing: 2
                                    Text { text: modelData.name; color: window.textPrimary; font.pixelSize: 14; font.bold: true }
                                    Text { text: "Ready to receive"; color: window.textSecondary; font.pixelSize: 11 }
                                }
                                Item { Layout.fillWidth: true }
                                NeonButton {
                                    text: "Send"
                                    compact: true
                                    role: "primary"
                                    Layout.alignment: Qt.AlignVCenter | Qt.AlignRight
                                    enabled: backend.sendSelectionCount > 0
                                    opacity: backend.sendSelectionCount > 0 ? 1.0 : 0.45
                                    onClicked: function() { backend.sendSelectionToDevice(modelData.deviceId) }
                                }
                            }
                        }
                    }
                }

                // Transfer Progress
                Rectangle {
                    Layout.fillWidth: true
                    Layout.leftMargin: 40
                    Layout.rightMargin: 40
                    Layout.bottomMargin: 40
                    height: 208
                    radius: window.radiusLg
                    color: appTheme.cardSurface
                    border.width: 1
                    border.color: appTheme.cardBorder
                    clip: true
                    visible: false

                    Rectangle {
                        anchors.fill: parent
                        gradient: Gradient {
                            GradientStop { position: 0.0; color: appTheme.elevatedSurface }
                            GradientStop { position: 0.58; color: appTheme.cardSurface }
                            GradientStop { position: 1.0; color: appTheme.insetSurface }
                        }
                    }

                    Rectangle {
                        width: 260
                        height: 260
                        radius: 130
                        x: -150
                        y: 56
                        color: appTheme.glowPrimary
                        opacity: 0.06
                    }

                    ColumnLayout {
                        anchors.fill: parent
                        anchors.margins: 22
                        spacing: 0

                        RowLayout {
                            Layout.fillWidth: true
                            Layout.fillHeight: true
                            spacing: 20

                            ColumnLayout {
                                Layout.fillWidth: true
                                Layout.fillHeight: true
                                spacing: 16

                                RowLayout {
                                    Layout.fillWidth: true
                                    spacing: 16

                                    Rectangle {
                                        width: 58
                                        height: 58
                                        radius: 29
                                        color: appTheme.itemSelectedBg
                                        border.width: 1
                                        border.color: appTheme.itemSelectedBorder

                                        Rectangle {
                                            anchors.centerIn: parent
                                            width: 28
                                            height: 28
                                            radius: 14
                                            color: appTheme.accentBlue
                                            opacity: 0.2
                                        }

                                        Rectangle {
                                            anchors.centerIn: parent
                                            width: 12
                                            height: 12
                                            radius: 6
                                            color: appTheme.accentBlue
                                        }
                                    }

                                    ColumnLayout {
                                        Layout.fillWidth: true
                                        spacing: 5

                                        Text {
                                            text: "Active Transfer"
                                            color: window.textPrimary
                                            font.pixelSize: 18
                                            font.bold: true
                                        }

                                        Text {
                                            text: backend.transferFileName || "Preparing transfer..."
                                            color: appTheme.textPrimary
                                            font.pixelSize: 16
                                            font.bold: true
                                            elide: Text.ElideMiddle
                                            maximumLineCount: 1
                                            Layout.fillWidth: true
                                        }

                                        Text {
                                            text: "Encrypted file stream over your local network"
                                            color: appTheme.textSecondary
                                            font.pixelSize: 12
                                        }
                                    }
                                }

                                Rectangle {
                                    Layout.fillWidth: true
                                    Layout.preferredHeight: 68
                                    radius: 22
                                    color: appTheme.insetSurface
                                    border.width: 1
                                    border.color: appTheme.panelBorder

                                    ColumnLayout {
                                        anchors.fill: parent
                                        anchors.margins: 16
                                        spacing: 10

                                        RowLayout {
                                            Layout.fillWidth: true
                                            Text {
                                                text: "Transfer Progress"
                                                color: appTheme.textSecondary
                                                font.pixelSize: 12
                                                font.bold: true
                                            }
                                            Item { Layout.fillWidth: true }
                                            Text {
                                                text: (backend.transferProgress * 100).toFixed(1) + "% complete"
                                                color: appTheme.accentBlue
                                                font.pixelSize: 12
                                                font.bold: true
                                            }
                                        }

                                        Rectangle {
                                            Layout.fillWidth: true
                                            height: 12
                                            radius: 6
                                            color: appTheme.progressBg
                                            clip: true

                                            Rectangle {
                                                width: parent.width * backend.transferProgress
                                                height: parent.height
                                                radius: 6
                                                gradient: Gradient {
                                                    GradientStop { position: 0.0; color: appTheme.primaryButtonHover }
                                                    GradientStop { position: 0.52; color: appTheme.primaryButton }
                                                    GradientStop { position: 1.0; color: appTheme.accentCyan }
                                                }
                                                Behavior on width { NumberAnimation { duration: 260; easing.type: Easing.OutCubic } }
                                            }
                                        }
                                    }
                                }

                                RowLayout {
                                    Layout.fillWidth: true
                                    spacing: 12

                                    Rectangle {
                                        radius: 14
                                        color: appTheme.itemBg
                                        border.width: 1
                                        border.color: appTheme.itemBorder
                                        implicitWidth: 128
                                        implicitHeight: 32

                                        Text {
                                            anchors.centerIn: parent
                                            text: "Secured Session"
                                            color: appTheme.textPrimary
                                            font.pixelSize: 12
                                            font.bold: true
                                        }
                                    }

                                    Rectangle {
                                        radius: 14
                                        color: appTheme.itemSelectedBg
                                        border.width: 1
                                        border.color: appTheme.itemSelectedBorder
                                        implicitWidth: 118
                                        implicitHeight: 32

                                        Text {
                                            anchors.centerIn: parent
                                            text: "Live Upload"
                                            color: appTheme.textPrimary
                                            font.pixelSize: 12
                                            font.bold: true
                                        }
                                    }

                                    Item { Layout.fillWidth: true }

                                    Text {
                                        text: "Queue clears if you cancel now."
                                        color: window.textMuted
                                        font.pixelSize: 11
                                    }
                                }
                            }

                            Rectangle {
                                Layout.preferredWidth: 228
                                Layout.fillHeight: true
                                radius: 24
                                color: appTheme.insetSurface
                                border.width: 1
                                border.color: appTheme.panelBorder

                                Rectangle {
                                    anchors.horizontalCenter: parent.horizontalCenter
                                    anchors.top: parent.top
                                    anchors.topMargin: 18
                                    width: 146
                                    height: 48
                                    radius: 18
                                    color: appTheme.itemSelectedBg
                                    border.width: 1
                                    border.color: appTheme.itemSelectedBorder

                                    Text {
                                        anchors.centerIn: parent
                                        text: (backend.transferProgress * 100).toFixed(1) + "%"
                                        color: appTheme.textPrimary
                                        font.pixelSize: 26
                                        font.bold: true
                                    }
                                }

                                Column {
                                    anchors.left: parent.left
                                    anchors.right: parent.right
                                    anchors.bottom: parent.bottom
                                    anchors.margins: 18
                                    spacing: 12

                                    Text {
                                        width: parent.width
                                        text: "Transferring"
                                        color: appTheme.textPrimary
                                        font.pixelSize: 14
                                        font.bold: true
                                        horizontalAlignment: Text.AlignHCenter
                                    }

                                    Text {
                                        width: parent.width
                                        text: "Stop the current file and clear the queue."
                                        color: appTheme.textSecondary
                                        font.pixelSize: 11
                                        horizontalAlignment: Text.AlignHCenter
                                        wrapMode: Text.WordWrap
                                    }

                                    NeonButton {
                                        width: parent.width
                                        text: "Cancel Transfer"
                                        role: "danger"
                                        compact: false
                                        onClicked: function() { backend.cancelTransfer() }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Rectangle {
            anchors.fill: parent
            z: 20
            visible: opacity > 0
            enabled: opacity > 0
            opacity: sendPage.selectionManagerOpen ? 1 : 0
            color: appTheme.overlayBg

            Behavior on opacity {
                NumberAnimation {
                    duration: window.motionMed
                    easing.type: Easing.InOutQuad
                }
            }

            MouseArea {
                anchors.fill: parent
            }

            Rectangle {
                anchors.centerIn: parent
                width: Math.min(parent.width - 96, 980)
                height: Math.min(parent.height - 88, 680)
                radius: 28
                color: appTheme.cardSurface
                border.width: 1
                border.color: appTheme.cardBorder
                clip: true
                scale: sendPage.selectionManagerOpen ? 1.0 : 0.98

                Behavior on scale {
                    NumberAnimation {
                        duration: window.motionMed
                        easing.type: Easing.OutCubic
                    }
                }

                Rectangle {
                    anchors.fill: parent
                    radius: parent.radius
                    gradient: Gradient {
                        GradientStop { position: 0.0; color: appTheme.elevatedSurface }
                        GradientStop { position: 1.0; color: appTheme.insetSurface }
                    }
                    opacity: 0.78
                }

                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 24
                    spacing: 18

                    RowLayout {
                        Layout.fillWidth: true
                        spacing: 12

                        NeonButton {
                            text: "Back"
                            role: "ghost"
                            compact: true
                            onClicked: function() { sendPage.selectionManagerOpen = false }
                        }

                        ColumnLayout {
                            spacing: 4
                            Layout.fillWidth: true

                            Text {
                                text: "Selection"
                                color: window.textPrimary
                                font.pixelSize: 28
                                font.bold: true
                            }

                            Text {
                                text: backend.sendSelectionCount
                                    + (backend.sendSelectionCount === 1 ? " File" : " Files")
                                    + " - " + backend.sendSelectionSizeText
                                color: window.textSecondary
                                font.pixelSize: 13
                            }
                        }

                        NeonButton {
                            text: "+ Add Files"
                            role: "primary"
                            compact: false
                            onClicked: function() { backend.addSendFiles() }
                        }

                        NeonButton {
                            text: "Add Folder"
                            compact: false
                            onClicked: function() { backend.addSendFolder() }
                        }

                        NeonButton {
                            text: "Clear All"
                            role: "danger"
                            compact: true
                            enabled: backend.sendSelectionCount > 0
                            opacity: backend.sendSelectionCount > 0 ? 1.0 : 0.5
                            onClicked: function() { backend.clearSendSelection() }
                        }
                    }

                    Rectangle {
                        Layout.fillWidth: true
                        height: 1
                        color: appTheme.divider
                        opacity: 0.9
                    }

                    Rectangle {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        radius: 22
                        color: appTheme.insetSurface
                        border.width: 1
                        border.color: appTheme.panelBorder
                        clip: true

                        Item {
                            anchors.fill: parent
                            visible: backend.sendSelectionCount === 0

                            ColumnLayout {
                                anchors.centerIn: parent
                                width: Math.min(parent.width - 48, 420)
                                spacing: 10

                                SelectionDropGlyph {
                                    Layout.alignment: Qt.AlignHCenter
                                    compact: false
                                }

                                Text {
                                    width: parent.width
                                    text: "Your selection is empty"
                                    color: window.textPrimary
                                    font.pixelSize: 22
                                    font.bold: true
                                    horizontalAlignment: Text.AlignHCenter
                                }

                                Text {
                                    width: parent.width
                                    text: "Add files or folders to rebuild the collection, then go back to the compact summary card."
                                    color: window.textSecondary
                                    font.pixelSize: 13
                                    wrapMode: Text.WordWrap
                                    horizontalAlignment: Text.AlignHCenter
                                }

                                Row {
                                    Layout.alignment: Qt.AlignHCenter
                                    spacing: 8
                                    Repeater {
                                        model: 4
                                        delegate: SelectionPreviewTile {
                                            placeholder: true
                                            scale: 0.88
                                            opacity: 0.92
                                        }
                                    }
                                }
                            }
                        }

                        ListView {
                            anchors.fill: parent
                            anchors.margins: 14
                            visible: backend.sendSelectionCount > 0
                            clip: true
                            spacing: 8
                            model: backend.sendSelection
                            ScrollBar.vertical: ThemedVScrollBar { }

                            delegate: Rectangle {
                                id: selectionManagerRow
                                required property var modelData
                                width: ListView.view.width
                                height: 66
                                radius: 18
                                color: selectionManagerHover.containsMouse ? appTheme.itemHoverBg : appTheme.itemBg
                                border.width: 1
                                border.color: selectionManagerHover.containsMouse ? appTheme.itemSelectedBorder : appTheme.itemBorder

                                Behavior on color { ColorAnimation { duration: window.motionFast } }

                                RowLayout {
                                    anchors.fill: parent
                                    anchors.margins: 14
                                    spacing: 14

                                    SelectionItemGlyph {
                                        directory: modelData.kind === "Folder"
                                    }

                                    ColumnLayout {
                                        Layout.fillWidth: true
                                        spacing: 3

                                        Text {
                                            text: modelData.fileName
                                            color: window.textPrimary
                                            font.pixelSize: 14
                                            font.bold: true
                                            elide: Text.ElideMiddle
                                            maximumLineCount: 1
                                            Layout.fillWidth: true
                                        }

                                        Text {
                                            text: modelData.kind
                                            color: window.textMuted
                                            font.pixelSize: 11
                                            Layout.fillWidth: true
                                        }
                                    }

                                    Text {
                                        text: modelData.sizeText
                                        color: appTheme.textPrimary
                                        font.pixelSize: 12
                                        font.bold: true
                                        verticalAlignment: Text.AlignVCenter
                                    }

                                    NeonButton {
                                        text: "Remove"
                                        role: "ghost"
                                        compact: true
                                        onClicked: function() { backend.removeSendSelectionItem(modelData.path) }
                                    }
                                }

                                MouseArea {
                                    id: selectionManagerHover
                                    anchors.fill: parent
                                    hoverEnabled: true
                                    acceptedButtons: Qt.NoButton
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    component ReceiveStatusPage: Item {
        id: receiveStatusPage
        readonly property bool hasFiles: backend.receiveSessionFiles.length > 0

        Rectangle {
            anchors.fill: parent
            color: appTheme.isDark ? appTheme.windowBg : "transparent"
        }

        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 18
            spacing: 12

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 4

                Text {
                    text: backend.receiveSessionTitle
                    color: window.textPrimary
                    font.pixelSize: 30
                    font.bold: true
                }
                Text {
                    text: "Save to folder: " + backend.receiveSessionTargetDir
                    color: appTheme.textSecondary
                    font.pixelSize: 16
                    elide: Text.ElideMiddle
                    maximumLineCount: 1
                    Layout.fillWidth: true
                }
            }

            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                radius: 16
                color: appTheme.cardSurface
                border.width: 1
                border.color: appTheme.cardBorder
                clip: true

                Item {
                    anchors.fill: parent
                    anchors.margins: 12

                    ListView {
                        id: receiveFilesList
                        anchors.fill: parent
                        clip: true
                        spacing: 8
                        model: backend.receiveSessionFiles
                        visible: receiveStatusPage.hasFiles
                        ScrollBar.vertical: ScrollBar {
                            policy: ScrollBar.AlwaysOff
                            visible: false
                        }

                        delegate: Rectangle {
                            required property var modelData
                            width: ListView.view.width
                            height: 74
                            radius: 14
                            color: modelData.done ? appTheme.itemSelectedBg : appTheme.itemBg
                            border.width: 1
                            border.color: modelData.done ? appTheme.itemSelectedBorder : appTheme.itemBorder

                            RowLayout {
                                anchors.fill: parent
                                anchors.margins: 10
                                spacing: 10

                                Rectangle {
                                    width: 42
                                    height: 42
                                    radius: 11
                                    color: appTheme.buttonBase

                                    Text {
                                        anchors.centerIn: parent
                                        text: "[]"
                                        color: appTheme.textPrimary
                                        font.pixelSize: 14
                                        font.bold: true
                                    }
                                }

                                ColumnLayout {
                                    Layout.fillWidth: true
                                    spacing: 2

                                    Text {
                                        text: modelData.fileName + " (" + modelData.sizeText + ")"
                                        color: window.textPrimary
                                        font.pixelSize: 15
                                        font.bold: true
                                        elide: Text.ElideMiddle
                                        maximumLineCount: 1
                                        Layout.fillWidth: true
                                    }

                                    Text {
                                        text: modelData.statusText
                                        color: modelData.done ? appTheme.accentBlue : appTheme.textMuted
                                        font.pixelSize: 13
                                        elide: Text.ElideRight
                                        maximumLineCount: 1
                                        Layout.fillWidth: true
                                    }
                                }

                                NeonButton {
                                    visible: modelData.canOpen
                                    enabled: modelData.canOpen
                                    compact: true
                                    text: "Open"
                                    role: "primary"
                                    onClicked: function() { backend.openReceivedSessionFile(modelData.openPath) }
                                }
                            }
                        }
                    }

                    Column {
                        anchors.centerIn: parent
                        width: parent.width - 40
                        spacing: 6
                        visible: !receiveStatusPage.hasFiles

                        Text {
                            width: parent.width
                            text: "No files in this session yet."
                            color: appTheme.textPrimary
                            font.pixelSize: 16
                            horizontalAlignment: Text.AlignHCenter
                        }

                        Text {
                            width: parent.width
                            text: "Files will appear here when transfer starts."
                            color: appTheme.textSecondary
                            font.pixelSize: 13
                            horizontalAlignment: Text.AlignHCenter
                        }
                    }
                }
            }

            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: 228
                Layout.minimumHeight: 228
                radius: 16
                color: appTheme.panelBg
                border.width: 1
                border.color: appTheme.panelBorder

                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 14
                    spacing: 6

                    Text {
                        text: backend.receiveSessionTitle
                        color: window.textPrimary
                        font.pixelSize: 20
                        font.bold: true
                    }

                    Rectangle {
                        Layout.fillWidth: true
                        height: 10
                        radius: 5
                        color: appTheme.progressBg
                        clip: true

                        Rectangle {
                            width: parent.width * backend.receiveSessionProgress
                            height: parent.height
                            radius: 5
                            color: appTheme.accentBlue
                        }
                    }

                    Text {
                        text: backend.receiveSessionSummary
                        color: appTheme.textSecondary
                        font.pixelSize: 13
                    }
                    Text {
                        text: backend.receiveSessionSizeLine
                        color: appTheme.textSecondary
                        font.pixelSize: 13
                    }
                    Text {
                        text: backend.receiveSessionSpeedLine
                        color: appTheme.textSecondary
                        font.pixelSize: 13
                    }

                    RowLayout {
                        Layout.fillWidth: true
                        Item { Layout.fillWidth: true }

                        NeonButton {
                            text: "Done"
                            compact: false
                            role: "success"
                            opacity: backend.receiveSessionCanExit ? 1.0 : 0.45
                            onClicked: function() {
                                if (backend.receiveSessionCanExit) {
                                    backend.completeReceiveSession()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    component SettingsPage: Item {
        ScrollView {
            id: settingsScroll
            anchors.fill: parent
            ScrollBar.vertical: ScrollBar {
                active: false
                policy: ScrollBar.AlwaysOff
                visible: false
                anchors.right: parent.right
                anchors.rightMargin: 4
            }
            
            ColumnLayout {
                width: settingsScroll.availableWidth
                anchors.margins: 40
                spacing: 32

                // Header
                ColumnLayout {
                    Layout.fillWidth: true
                    Layout.topMargin: 40
                    Layout.leftMargin: 40
                    Layout.rightMargin: 40
                    spacing: 0

                    RowLayout {
                        Layout.fillWidth: true

                        ColumnLayout {
                            spacing: 4
                            Text { text: "Setting"; color: window.textPrimary; font.pixelSize: 32; font.bold: true }
                            Text { text: "Organize HyperDrop preferences by setting type."; color: window.textSecondary; font.pixelSize: 14 }
                        }

                        Item { Layout.fillWidth: true }

                        NeonButton {
                            text: "GitHub"
                            compact: false
                            onClicked: function() {
                                Qt.openUrlExternally("https://github.com/ankix86/HyperDrop")
                            }
                        }
                    }
                }

                AdaptiveSectionCard {
                    Layout.fillWidth: true
                    Layout.leftMargin: 40
                    Layout.rightMargin: 40
                    title: "Network"
                    subtitle: "Control the server state and the communication port used for incoming transfers."

                        Text {
                            Layout.fillWidth: true
                            visible: backend.portStatusTone === "warn"
                            text: backend.portStatusText
                            color: appTheme.statusWarn
                            font.pixelSize: 13
                            font.bold: true
                            wrapMode: Text.WordWrap
                        }

                        Rectangle {
                            Layout.fillWidth: true
                            implicitHeight: serverPanelLayout.implicitHeight + 28
                            Layout.preferredHeight: implicitHeight
                            radius: 16
                            color: appTheme.panelBg
                            border.width: 1
                            border.color: appTheme.panelBorder

                            ColumnLayout {
                                id: serverPanelLayout
                                anchors.fill: parent
                                anchors.margins: 14
                                spacing: window.settingsNarrow ? 10 : 0

                                ColumnLayout {
                                    Layout.fillWidth: true
                                    spacing: 2

                                    Text {
                                        text: "Server"
                                        color: window.textPrimary
                                        font.pixelSize: 15
                                        font.bold: true
                                    }

                                    Text {
                                        text: backend.serverRunning ? "Online and ready for incoming transfers." : "Offline until you start the server."
                                        color: backend.serverRunning ? appTheme.statusSuccess : window.textSecondary
                                        font.pixelSize: 12
                                    }
                                }

                                RowLayout {
                                    Layout.fillWidth: true
                                    spacing: 10
                                    Layout.alignment: Qt.AlignRight

                                    Item {
                                        Layout.fillWidth: true
                                        visible: !window.settingsNarrow
                                    }

                                    NeonButton {
                                        visible: backend.serverRunning
                                        text: "Restart"
                                        role: "ghost"
                                        compact: true
                                        onClicked: function() { backend.restartServer() }
                                    }

                                    NeonButton {
                                        text: backend.serverRunning ? "Stop" : "Start"
                                        role: backend.serverRunning ? "danger" : "success"
                                        compact: true
                                        onClicked: function() {
                                            if (backend.serverRunning) {
                                                backend.stopServer()
                                            } else {
                                                backend.startServer()
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        LabelField {
                            Layout.fillWidth: true
                            label: "Communication Port"
                            value: backend.portText
                            onEdited: function(text) { backend.setPortText(text) }
                        }

                        Text {
                            Layout.fillWidth: true
                            text: backend.portStatusText
                            color: backend.portStatusTone === "error"
                                ? appTheme.statusError
                                : backend.portStatusTone === "warn"
                                    ? appTheme.statusWarn
                                : backend.portStatusTone === "success"
                                    ? appTheme.statusSuccess
                                    : appTheme.textMuted
                            font.pixelSize: 12
                            wrapMode: Text.WordWrap
                            visible: backend.portStatusTone !== "warn"
                        }

                        RowLayout {
                            Layout.fillWidth: true

                            NeonButton {
                                text: "Save Port"
                                role: "primary"
                                compact: false
                                onClicked: function() { backend.saveConnectionSettings() }
                            }

                            Item { Layout.fillWidth: true }
                        }
                }

                AdaptiveSectionCard {
                    Layout.fillWidth: true
                    Layout.leftMargin: 40
                    Layout.rightMargin: 40
                    title: "Receive"
                    subtitle: "Choose where files accepted on this PC are stored by default."

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 16
                        ColumnLayout {
                            Layout.fillWidth: true; spacing: 8
                            Text { text: "File Location"; color: window.textSecondary; font.pixelSize: 12; font.bold: true }
                            ColumnLayout {
                                 Layout.fillWidth: true
                                 spacing: 10
                                 Rectangle {
                                     Layout.fillWidth: true; Layout.preferredHeight: 46; radius: 14; color: appTheme.panelBg; border.width: 1; border.color: appTheme.panelBorder
                                     Text {
                                         anchors.fill: parent; anchors.leftMargin: 16; verticalAlignment: Text.AlignVCenter
                                         text: backend.receiveDir; color: window.textPrimary; font.pixelSize: 13; elide: Text.ElideMiddle
                                     }
                                 }
                                 RowLayout {
                                     Layout.fillWidth: true
                                     spacing: 12

                                     Item { Layout.fillWidth: true }

                                     NeonButton { text: "Change"; compact: true; onClicked: function() { backend.chooseReceiveFolder() } }
                                     NeonButton { text: "Open"; compact: true; onClicked: function() { backend.openReceiveFolder() } }
                                 }
                            }
                        }
                    }
                }

                AdaptiveSectionCard {
                    Layout.fillWidth: true
                    Layout.leftMargin: 40
                    Layout.rightMargin: 40
                    title: "Appearance"
                    subtitle: "Choose between light and dark visual styles or follow the system setting."

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 16

                        RowLayout {
                            Layout.fillWidth: true
                            spacing: 20

                            Repeater {
                                model: [
                                    { label: "Light", value: "light" },
                                    { label: "Dark", value: "dark" },
                                    { label: "System", value: "system" }
                                ]
                                delegate: Rectangle {
                                    Layout.preferredWidth: 140
                                    Layout.preferredHeight: 80
                                    radius: 12
                                    color: backend.themePreference === modelData.value ? appTheme.buttonHover : appTheme.cardSurface
                                    border.width: backend.themePreference === modelData.value ? 2 : 1
                                    border.color: backend.themePreference === modelData.value ? appTheme.accentBlue : appTheme.cardBorder

                                    ColumnLayout {
                                        anchors.centerIn: parent
                                        spacing: 8
                                        Text {
                                            text: modelData.label
                                            color: appTheme.textPrimary
                                            font.bold: backend.themePreference === modelData.value
                                            Layout.alignment: Qt.AlignHCenter
                                        }
                                        Rectangle {
                                            width: 16
                                            height: 16
                                            radius: 8
                                            color: backend.themePreference === modelData.value ? appTheme.accentBlue : "transparent"
                                            border.width: 1
                                            border.color: appTheme.textMuted
                                            Layout.alignment: Qt.AlignHCenter
                                        }
                                    }

                                    MouseArea {
                                        anchors.fill: parent
                                        cursorShape: Qt.PointingHandCursor
                                        onClicked: function() { backend.setThemePreference(modelData.value) }
                                    }
                                }
                            }
                            Item { Layout.fillWidth: true }
                        }
                    }
                }

                Item {
                    Layout.fillWidth: true
                    Layout.leftMargin: 40
                    Layout.rightMargin: 40
                    height: 320

                    ColumnLayout {
                        anchors.fill: parent
                        anchors.margins: 28
                        spacing: 8

                        Item { Layout.fillHeight: true }

                        Image {
                            Layout.alignment: Qt.AlignHCenter
                            Layout.preferredWidth: 260
                            Layout.preferredHeight: 96
                            source: "../../../assets/icons/hyperdrop-wordmark.png"
                            fillMode: Image.PreserveAspectFit
                            smooth: true
                        }

                        Text {
                            Layout.alignment: Qt.AlignHCenter
                            text: "Version: 1.0.0"
                            color: window.textPrimary
                            font.pixelSize: 18
                        }

                        Text {
                            Layout.alignment: Qt.AlignHCenter
                            text: "\u00A9 2026 RayZDev"
                            color: window.textSecondary
                            font.pixelSize: 16
                        }

                        Item { Layout.fillHeight: true }
                    }
                }
                
                Item { Layout.preferredHeight: 40 }
            }
        }
    }

    component SectionCard: Rectangle {
        id: sectionCard
        property string title: ""
        property string subtitle: ""
        radius: window.radiusLg; color: window.cardSurface; border.width: 1; border.color: window.cardBorder; clip: true
        
        Column {
            anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 18; spacing: 4
            Text { text: sectionCard.title; color: window.textPrimary; font.pixelSize: 18; font.bold: true }
            Text { width: parent.width; text: sectionCard.subtitle; color: window.textSecondary; font.pixelSize: 13; wrapMode: Text.WordWrap }
        }
    }

    component AdaptiveSectionCard: Rectangle {
        id: adaptiveSectionCard
        property string title: ""
        property string subtitle: ""
        property int contentSpacing: 14
        default property alias contentData: adaptiveSectionContent.data
        radius: window.radiusLg
        color: window.cardSurface
        border.width: 1
        border.color: window.cardBorder
        clip: true
        implicitHeight: adaptiveSectionLayout.implicitHeight + 36

        ColumnLayout {
            id: adaptiveSectionLayout
            anchors.fill: parent
            anchors.margins: 18
            spacing: 18

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 4

                Text {
                    Layout.fillWidth: true
                    text: adaptiveSectionCard.title
                    color: window.textPrimary
                    font.pixelSize: 18
                    font.bold: true
                }

                Text {
                    Layout.fillWidth: true
                    text: adaptiveSectionCard.subtitle
                    color: window.textSecondary
                    font.pixelSize: 13
                    wrapMode: Text.WordWrap
                }
            }

            ColumnLayout {
                id: adaptiveSectionContent
                Layout.fillWidth: true
                spacing: adaptiveSectionCard.contentSpacing
            }
        }
    }

    component SelectionDropGlyph: Item {
        property bool compact: false
        property color accent: appTheme.accentBlue
        readonly property int glyphSize: compact ? 54 : 72
        width: glyphSize
        height: glyphSize

        Rectangle {
            anchors.fill: parent
            radius: width / 2
            color: appTheme.cardSurface
            border.width: 1
            border.color: Qt.lighter(accent, 1.08)
            opacity: 0.95
        }

        Rectangle {
            width: compact ? 22 : 28
            height: compact ? 28 : 34
            radius: 8
            anchors.centerIn: parent
            anchors.verticalCenterOffset: compact ? 1 : 2
            color: appTheme.insetSurface
            border.width: 1
            border.color: Qt.lighter(accent, 1.04)
        }

        Rectangle {
            width: compact ? 8 : 10
            height: compact ? 8 : 10
            radius: 3
            x: compact ? 23 : 31
            y: compact ? 15 : 19
            rotation: 45
            color: appTheme.itemHoverBg
            border.width: 1
            border.color: Qt.lighter(accent, 1.02)
        }

        Text {
            anchors.centerIn: parent
            anchors.verticalCenterOffset: compact ? 1 : 2
            text: "^"
            color: accent
            font.pixelSize: compact ? 19 : 24
            font.bold: true
        }
    }

    component SelectionItemGlyph: Item {
        property bool directory: false
        width: 34
        height: 34

        Rectangle {
            visible: directory
            width: 24
            height: 15
            radius: 5
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.bottom: parent.bottom
            color: appTheme.itemSelectedBg
            border.width: 1
            border.color: appTheme.itemSelectedBorder
        }

        Rectangle {
            visible: directory
            width: 12
            height: 7
            radius: 3
            x: 6
            y: 8
            color: appTheme.avatarBg
            border.width: 1
            border.color: appTheme.itemSelectedBorder
        }

        Rectangle {
            visible: !directory
            width: 22
            height: 27
            radius: 6
            anchors.centerIn: parent
            color: appTheme.itemBg
            border.width: 1
            border.color: appTheme.itemBorder
        }

        Rectangle {
            visible: !directory
            width: 7
            height: 7
            x: 19
            y: 6
            rotation: 45
            color: appTheme.elevatedSurface
            border.width: 1
            border.color: appTheme.itemBorder
        }
    }

    component SelectionPreviewTile: Rectangle {
        property var itemData: null
        property bool placeholder: false
        property bool overflow: false
        property int overflowCount: 0

        width: 68
        height: 68
        radius: 18
        color: placeholder ? appTheme.insetSurface : (overflow ? appTheme.itemSelectedBg : appTheme.cardSurface)
        border.width: 1
        border.color: placeholder ? appTheme.itemBorder : (overflow ? appTheme.itemSelectedBorder : appTheme.cardBorder)

        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 8
            spacing: 4
            visible: !overflow

            Item { Layout.fillHeight: true }

            SelectionItemGlyph {
                Layout.alignment: Qt.AlignHCenter
                directory: itemData && itemData.kind === "Folder"
                visible: !placeholder
                opacity: 0.98
            }

            Rectangle {
                Layout.alignment: Qt.AlignHCenter
                visible: placeholder
                width: 24
                height: 24
                radius: 8
                color: appTheme.itemBg
                border.width: 1
                border.color: appTheme.itemBorder
            }

            Text {
                Layout.alignment: Qt.AlignHCenter
                text: placeholder ? "" : (itemData && itemData.kind === "Folder" ? "Folder" : "File")
                color: appTheme.textSecondary
                font.pixelSize: 10
                font.bold: true
                visible: !placeholder
            }

            Item { Layout.fillHeight: true }
        }

        ColumnLayout {
            anchors.centerIn: parent
            spacing: 2
            visible: overflow

            Text {
                text: "+" + overflowCount
                color: window.textPrimary
                font.pixelSize: 20
                font.bold: true
                horizontalAlignment: Text.AlignHCenter
            }

            Text {
                text: "More"
                color: appTheme.textSecondary
                font.pixelSize: 10
                font.bold: true
                horizontalAlignment: Text.AlignHCenter
            }
        }
    }

    component NeonButton: Rectangle {
        id: neonButton
        property alias text: buttonLabel.text
        property string role: "secondary"
        property color accent: appTheme.accentBlue
        property bool compact: false
        signal clicked
        implicitWidth: compact ? 84 : 154
        implicitHeight: compact ? 38 : 44
        radius: compact ? 14 : 16
        color: role === "primary" ? (mouseArea.pressed ? appTheme.primaryButtonPressed : (mouseArea.containsMouse ? appTheme.primaryButtonHover : appTheme.primaryButton))
               : (role === "danger" ? (mouseArea.pressed ? appTheme.dangerButtonPressed : (mouseArea.containsMouse ? appTheme.dangerButtonHover : appTheme.dangerButton))
                  : (role === "success" ? (mouseArea.pressed ? appTheme.successButtonPressed : (mouseArea.containsMouse ? appTheme.successButtonHover : appTheme.successButton))
                     : (role === "ghost" ? (mouseArea.pressed ? appTheme.ghostButtonPressed : (mouseArea.containsMouse ? appTheme.ghostButtonHover : appTheme.ghostButton))
                        : (mouseArea.pressed ? window.buttonPressed : (mouseArea.containsMouse ? window.buttonHover : window.buttonBase)))))
        border.width: 1
        border.color: role === "primary" ? appTheme.primaryButton
            : (role === "danger" ? appTheme.dangerButton
               : (role === "success" ? appTheme.successButton
                  : (role === "ghost" ? appTheme.fieldBorder : Qt.lighter(accent, 1.04))))
        scale: mouseArea.pressed ? 0.985 : 1.0
        Behavior on color { ColorAnimation { duration: 110 } }
        MouseArea { id: mouseArea; anchors.fill: parent; hoverEnabled: true; cursorShape: Qt.PointingHandCursor; onClicked: neonButton.clicked() }
        Text { id: buttonLabel; anchors.centerIn: parent; color: neonButton.role === "primary" ? appTheme.primaryButtonText : (neonButton.role === "success" ? appTheme.successButtonText : (neonButton.role === "ghost" ? appTheme.ghostButtonText : window.buttonText)); font.pixelSize: compact ? 13 : 14; font.bold: true }
    }

    component LabelField: ColumnLayout {
        id: labelField
        property string label: ""
        property string value: ""
        signal edited(string text)
        spacing: 6
        Text { text: labelField.label; color: window.textSecondary; font.pixelSize: 12; font.bold: true }
        Rectangle {
            Layout.fillWidth: true; Layout.preferredHeight: 46; radius: 18; color: appTheme.fieldBg; border.width: 1; border.color: input.activeFocus ? appTheme.fieldFocus : appTheme.fieldBorder
            TextField { id: input; anchors.fill: parent; anchors.margins: 4; anchors.leftMargin: 12; text: labelField.value; color: window.textPrimary; font.pixelSize: 14; background: null; selectByMouse: true; onTextEdited: labelField.edited(text) }
        }
    }

    component ThemedVScrollBar: ScrollBar {
        id: themedScrollBar
        width: 0
        policy: ScrollBar.AlwaysOff
        visible: false
        contentItem: Rectangle { implicitWidth: 8; radius: 4; color: themedScrollBar.pressed ? appTheme.buttonHover : (themedScrollBar.hovered ? appTheme.scrollbarHandle : appTheme.textMuted) }
        background: Rectangle { radius: 6; color: appTheme.insetSurface; opacity: 0.5 }
    }

    component NetworkScanButton: Rectangle {
        id: scanButton
        property bool active: false
        signal clicked
        width: 50
        height: 50
        radius: 25
        color: active ? appTheme.primaryButton : appTheme.ghostButton
        border.width: 1
        border.color: active ? appTheme.primaryButton : appTheme.fieldBorder
        antialiasing: true

        Behavior on color { ColorAnimation { duration: 180 } }

        Item {
            anchors.centerIn: parent
            width: 24
            height: 24

            Image {
                anchors.centerIn: parent
                width: 24
                height: 24
                source: "../../../assets/icons/sarching_icon.png"
                fillMode: Image.PreserveAspectFit
                smooth: true
                mipmap: true
            }

            RotationAnimator on rotation {
                from: 0
                to: 360
                duration: 2200
                loops: Animation.Infinite
                running: true
            }
        }

        MouseArea {
            anchors.fill: parent
            hoverEnabled: true
            cursorShape: Qt.PointingHandCursor
            onClicked: scanButton.clicked()
        }
    }
}
