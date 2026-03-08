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
    color: "#070b1d"

    property int currentTab: 0
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
    readonly property color textPrimary: "#edf2ff"
    readonly property color textSecondary: "#b6c5f8"
    readonly property color textMuted: "#93a6e5"
    readonly property color buttonBase: "#18255c"
    readonly property color buttonHover: "#23357c"
    readonly property color buttonPressed: "#141f4f"
    readonly property color buttonText: "#ecf2ff"
    readonly property color cardSurface: "#0f1438"
    readonly property color cardBorder: "#27418f"
    readonly property color wavePurple: "#7752ff"
    readonly property color waveBlue: "#4f74ff"
    readonly property color waveCyan: "#5eb4ff"
    readonly property bool compactLayout: width < 1320
    readonly property int contentTopInset: 86
    readonly property var tabs: [
        { label: "Control", accent: "#58e7ff", icon: "../../../assets/icons/tab-control.png" },
        { label: "Send", accent: "#b45cff", icon: "../../../assets/icons/tab-send.png" },
        { label: "Activity", accent: "#6a84ff", icon: "../../../assets/icons/tab-activity.png" }
    ]

    function statusColor(level) {
        if (level === "error") return "#ff6b9d"
        if (level === "success") return "#58e7ff"
        if (level === "warn") return "#ffb255"
        return "#b9c6ff"
    }

    Rectangle {
        anchors.fill: parent
        gradient: Gradient {
            GradientStop { position: 0.0; color: "#070b1d" }
            GradientStop { position: 0.55; color: "#0a1140" }
            GradientStop { position: 1.0; color: "#09112b" }
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
            color: "#7a54ff"
            opacity: 0.09

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
            color: "#5f9dff"
            opacity: 0.08

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
                        ctx.strokeStyle = "rgba(121, 170, 255, 0.64)"
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
                    border.color: "#7baeffa3"
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
                        color: "#8d9dffa3"
                    }
                    Rectangle {
                        anchors.centerIn: parent
                        width: parent.width * 0.74
                        height: 2.6
                        color: "#8d9dffa3"
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
                    border.color: "#76a8ff9a"
                }
            }
        }

        // Soft vignette to keep center readable and push glow toward edges.
        Rectangle {
            anchors.fill: parent
            gradient: Gradient {
                orientation: Gradient.Vertical
                GradientStop { position: 0.0; color: "#18050b24" }
                GradientStop { position: 0.35; color: "#050a2400" }
                GradientStop { position: 0.72; color: "#050a2400" }
                GradientStop { position: 1.0; color: "#1d050b24" }
            }
        }

    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 16
        spacing: 10

        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 146
            radius: window.radiusLg
            color: "transparent"
            border.width: 0
            border.color: "transparent"
            clip: true

            RowLayout {
                anchors.fill: parent
                anchors.margins: 20
                spacing: 18

                ColumnLayout {
                    Layout.fillWidth: true
                    Layout.minimumWidth: 380
                    spacing: 4

                    Text {
                        text: "HyperDrop"
                        color: window.textPrimary
                        font.pixelSize: 30
                        font.bold: true
                    }

                    Text {
                        text: "Lightning-fast encrypted local transfer for desktop"
                        color: window.textSecondary
                        font.pixelSize: 33/2
                    }

                    Text {
                        text: "Device: " + backend.deviceName + "  |  ID: " + backend.deviceId
                        color: window.textSecondary
                        font.pixelSize: 13
                        elide: Text.ElideRight
                    }

                    Text {
                        text: backend.statusText
                        id: headerStatusText
                        color: "#5ff2ff"
                        font.pixelSize: 14
                        font.bold: true
                        elide: Text.ElideRight

                        onTextChanged: statusFade.restart()
                        SequentialAnimation {
                            id: statusFade
                            NumberAnimation { target: headerStatusText; property: "opacity"; from: 0.3; to: 1.0; duration: window.motionMed; easing.type: Easing.OutCubic }
                        }
                    }
                }

                Rectangle {
                    id: wordmarkCapsule
                    Layout.preferredWidth: window.width >= 1280 ? 390 : 280
                    Layout.preferredHeight: 92
                    Layout.alignment: Qt.AlignRight | Qt.AlignVCenter
                    visible: window.width >= 980
                    radius: height / 2
                    color: "transparent"
                    clip: true
                    antialiasing: true

                    // Subtle edge hint only; interior stays transparent to reveal original background.
                    Rectangle {
                        anchors.fill: parent
                        radius: parent.radius
                        color: "transparent"
                        border.width: 0
                        border.color: "transparent"
                    }

                    Image {
                        property real logoScale: 3.0
                        anchors.fill: parent
                        // Keep x3 while preventing crop: shrink base painted area responsively.
                        anchors.leftMargin: Math.ceil(wordmarkCapsule.width * (1 - 1 / logoScale) * 0.5)
                        anchors.rightMargin: Math.ceil(wordmarkCapsule.width * (1 - 1 / logoScale) * 0.5)
                        anchors.topMargin: Math.ceil(wordmarkCapsule.height * (1 - 1 / logoScale) * 0.5)
                        anchors.bottomMargin: Math.ceil(wordmarkCapsule.height * (1 - 1 / logoScale) * 0.5)
                        source: "../../../assets/icons/hyperdrop-wordmark.svg"
                        fillMode: Image.PreserveAspectFit
                        smooth: true
                        transformOrigin: Item.Center
                        scale: logoScale
                    }
                }
            }
        }

        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 62
            radius: window.radiusLg
            color: "#0f1540"
            border.width: 1
            border.color: "#2a458e"

            RowLayout {
                id: tabBarRow
                anchors.fill: parent
                anchors.margins: 8
                spacing: 10

                Rectangle {
                    z: 0
                    y: 2
                    width: (tabBarRow.width - (tabBarRow.spacing * 2)) / 3
                    height: tabBarRow.height - 4
                    x: currentTab * (width + tabBarRow.spacing)
                    radius: 18
                    color: "#304497"
                    border.width: 1
                    border.color: currentTab === 0 ? "#58e7ff" : (currentTab === 1 ? "#b45cff" : "#6a84ff")

                    Behavior on x { NumberAnimation { duration: window.motionMed; easing.type: Easing.OutCubic } }
                    Behavior on border.color { ColorAnimation { duration: window.motionMed } }
                    Behavior on color { ColorAnimation { duration: window.motionMed } }
                }

                Repeater {
                    model: window.tabs

                    delegate: Rectangle {
                        required property int index
                        required property var modelData

                        z: 1
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        radius: 18
                        color: "transparent"
                        border.width: 0
                        border.color: "transparent"

                        MouseArea {
                            anchors.fill: parent
                            cursorShape: Qt.PointingHandCursor
                            onClicked: window.currentTab = index
                        }

                        Item {
                            id: tabContentGroup
                            anchors.centerIn: parent
                            width: tabRow.implicitWidth
                            height: tabRow.implicitHeight
                            transformOrigin: Item.Center
                            scale: window.currentTab === index ? 2.0 : 1.0

                            Behavior on scale { NumberAnimation { duration: window.motionMed; easing.type: Easing.OutCubic } }

                            Row {
                                id: tabRow
                                anchors.centerIn: parent
                                spacing: 8

                                Image {
                                    width: 18
                                    height: 18
                                    source: modelData.icon
                                    fillMode: Image.PreserveAspectFit
                                    smooth: true
                                    opacity: window.currentTab === index ? 1.0 : 0.72
                                }

                                Text {
                                    text: modelData.label
                                    color: window.currentTab === index ? window.textPrimary : window.textSecondary
                                    font.pixelSize: 14
                                    font.bold: true

                                    Behavior on color { ColorAnimation { duration: window.motionFast } }
                                }
                            }
                        }
                    }
                }
            }
        }

        StackLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            currentIndex: window.currentTab

            ControlPage { Layout.fillWidth: true; Layout.fillHeight: true }
            SendPage { Layout.fillWidth: true; Layout.fillHeight: true }
            ActivityPage { Layout.fillWidth: true; Layout.fillHeight: true }
        }
    }

    component ControlPage: Item {
        ScrollView {
            id: controlScroll
            anchors.fill: parent
            clip: true
            ScrollBar.vertical: ThemedVScrollBar {
                anchors.right: parent.right
                anchors.rightMargin: 6
                anchors.top: parent.top
                anchors.bottom: parent.bottom
            }
            ScrollBar.horizontal: ScrollBar { policy: ScrollBar.AlwaysOff }

            Item {
                width: controlScroll.availableWidth
                implicitHeight: controlFlow.implicitHeight + 8

                Flow {
                    id: controlFlow
                    width: parent.width
                    spacing: 16

                    property real cardWidth: window.compactLayout ? width : (width - spacing) / 2

                    SectionCard {
                        width: controlFlow.cardWidth
                        height: Math.max(300, connectionContent.implicitHeight + 112)
                        title: "Connection Control"
                        subtitle: "Choose your pairing code, save bind settings, and scan the local network."

                        ColumnLayout {
                            id: connectionContent
                            anchors.fill: parent
                            anchors.margins: 18
                            anchors.topMargin: window.contentTopInset
                            spacing: 12

                            GridLayout {
                                Layout.fillWidth: true
                                columns: window.compactLayout ? 1 : 2
                                columnSpacing: 12
                                rowSpacing: 12

                                LabelField {
                                    label: "Port"
                                    value: backend.portText
                                    onEdited: backend.setPortText(text)
                                    Layout.fillWidth: true
                                }

                                LabelField {
                                    label: "Pairing Code"
                                    value: backend.pairingCode
                                    onEdited: backend.setPairingCode(text)
                                    Layout.fillWidth: true
                                }
                            }

                            Flow {
                                Layout.fillWidth: true
                                spacing: 10

                                NeonButton { text: "Save"; role: "secondary"; accent: "#58e7ff"; onClicked: backend.saveConnectionSettings() }
                                NeonButton { text: "Scan LAN"; role: "secondary"; accent: "#b45cff"; onClicked: backend.refreshDiscovery() }
                                NeonButton { text: "New Code"; role: "secondary"; accent: "#6a84ff"; onClicked: backend.generatePairingCode() }
                            }
                        }
                    }

                    SectionCard {
                        width: controlFlow.cardWidth
                        height: Math.max(300, serviceContent.implicitHeight + 112)
                        title: "Service Control"
                        subtitle: backend.serverRunning ? "Receiver service is active and ready." : "Receiver service is currently offline."

                        ColumnLayout {
                            id: serviceContent
                            anchors.fill: parent
                            anchors.margins: 18
                            anchors.topMargin: window.contentTopInset
                            spacing: 12

                            Flow {
                                Layout.fillWidth: true
                                spacing: 10

                                NeonButton { text: "Start Server"; role: "primary"; accent: "#58e7ff"; onClicked: backend.startServer() }
                                NeonButton { text: "Stop Server"; role: "danger"; accent: "#ff6b9d"; onClicked: backend.stopServer() }
                                NeonButton { text: "Open Receive"; role: "secondary"; accent: "#6a84ff"; onClicked: backend.openReceiveFolder() }
                            }

                            StatusPill {
                                Layout.fillWidth: true
                                text: backend.serverRunning ? "Ready for incoming transfers" : "Start the service to receive files"
                                accent: backend.serverRunning ? "#58e7ff" : "#ffb255"
                            }

                            StatusPill {
                                Layout.fillWidth: true
                                text: "Receive Folder: " + backend.receiveDir
                                accent: "#b45cff"
                            }
                        }
                    }

                }
            }
        }
    }

    component SendPage: Item {
        ScrollView {
            id: sendScroll
            anchors.fill: parent
            clip: true
            ScrollBar.vertical: ThemedVScrollBar {
                anchors.right: parent.right
                anchors.rightMargin: 6
                anchors.top: parent.top
                anchors.bottom: parent.bottom
            }
            ScrollBar.horizontal: ScrollBar { policy: ScrollBar.AlwaysOff }

            Item {
                width: sendScroll.availableWidth
                implicitHeight: sendFlow.implicitHeight + 8

                Flow {
                    id: sendFlow
                    width: parent.width
                    spacing: 16

                    property real cardWidth: window.compactLayout ? width : (width - spacing) / 2

                    SectionCard {
                        width: sendFlow.cardWidth
                        height: Math.max(308, sendDeckContent.implicitHeight + 112)
                        title: "Send Deck"
                        subtitle: "Queue files or folders for the currently selected device."

                        ColumnLayout {
                            id: sendDeckContent
                            anchors.fill: parent
                            anchors.margins: 18
                            anchors.topMargin: window.contentTopInset
                            spacing: 12

                            StatusPill { Layout.fillWidth: true; text: "Target: " + backend.activeTargetName + "  |  " + backend.activeTargetAddress; accent: "#58e7ff" }
                            StatusPill { Layout.fillWidth: true; text: "Receive Folder: " + backend.receiveDir; accent: "#b45cff" }

                            Flow {
                                Layout.fillWidth: true
                                spacing: 10

                                NeonButton { text: "Send Files"; role: "primary"; accent: "#58e7ff"; onClicked: backend.sendFiles() }
                                NeonButton { text: "Send Folder"; role: "secondary"; accent: "#b45cff"; onClicked: backend.sendFolder() }
                                NeonButton { text: "Choose Receive Folder"; role: "secondary"; accent: "#6a84ff"; onClicked: backend.chooseReceiveFolder() }
                            }
                        }
                    }

                    SectionCard {
                        width: sendFlow.cardWidth
                        height: 356
                        title: "Target Device"
                        subtitle: backend.activeTargetName + "  |  " + backend.activeTargetAddress

                        ListView {
                            anchors.fill: parent
                            anchors.margins: 18
                            anchors.topMargin: 86
                            clip: true
                            spacing: 8
                            model: backend.devices

                            delegate: Rectangle {
                                required property var modelData
                                width: ListView.view.width
                                height: 74
                                radius: 16
                                color: "#121a48"
                                border.width: 1
                                border.color: "#2b429d"

                                RowLayout {
                                    anchors.fill: parent
                                    anchors.margins: 12
                                    spacing: 12

                                    ColumnLayout {
                                        Layout.fillWidth: true
                                        spacing: 2

                                        Text {
                                            text: modelData.name
                                            color: window.textPrimary
                                            font.pixelSize: 14
                                            font.bold: true
                                        }

                                        Text {
                                            text: modelData.platform + "  |  " + modelData.address
                                            color: window.textSecondary
                                            font.pixelSize: 12
                                        }
                                    }

                                    NeonButton {
                                        text: "Use"
                                        compact: true
                                        role: "primary"
                                        accent: "#58e7ff"
                                        onClicked: backend.selectDevice(modelData.deviceId)
                                    }
                                }
                            }

                            footer: Item {
                                width: parent.width
                                height: backend.devices.length === 0 ? 170 : 0
                                visible: backend.devices.length === 0

                                Text {
                                    anchors.centerIn: parent
                                    text: "No devices discovered yet"
                                    color: window.textMuted
                                    font.pixelSize: 15
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    component ActivityPage: Item {
        ScrollView {
            id: activityScroll
            anchors.fill: parent
            clip: true
            ScrollBar.vertical: ThemedVScrollBar {
                anchors.right: parent.right
                anchors.rightMargin: 6
                anchors.top: parent.top
                anchors.bottom: parent.bottom
            }
            ScrollBar.horizontal: ScrollBar { policy: ScrollBar.AlwaysOff }

            Item {
                width: activityScroll.availableWidth
                implicitHeight: activityFlow.implicitHeight + 8

                Flow {
                    id: activityFlow
                    width: parent.width
                    spacing: 16

                    property real cardWidth: window.compactLayout ? width : (width - spacing) / 2

                    SectionCard {
                        width: activityFlow.cardWidth
                        height: Math.max(372, activityStatusContent.implicitHeight + 112)
                        title: "Live Status"
                        subtitle: "Keep key service and pairing state visible while monitoring activity."

                        ColumnLayout {
                            id: activityStatusContent
                            anchors.fill: parent
                            anchors.margins: 18
                            anchors.topMargin: window.contentTopInset
                            spacing: 10

                            StatusPill { Layout.fillWidth: true; text: "Status: " + backend.statusText; accent: "#58e7ff" }
                            StatusPill { Layout.fillWidth: true; text: "Pairing: " + backend.pairingCode; accent: "#6a84ff" }
                            StatusPill { Layout.fillWidth: true; text: "Receive Folder: " + backend.receiveDir; accent: "#b45cff" }
                            StatusPill { Layout.fillWidth: true; text: "Target: " + backend.activeTargetName; accent: "#58e7ff" }
                        }
                    }

                    SectionCard {
                        width: activityFlow.cardWidth
                        height: 420
                        title: "Transfer Activity"
                        subtitle: "Live server events, transfer retries, and completions from the shared backend."

                        ListView {
                            anchors.fill: parent
                            anchors.margins: 18
                            anchors.topMargin: 86
                            clip: true
                            spacing: 8
                            model: backend.events

                            delegate: Rectangle {
                                required property var modelData
                                width: ListView.view.width
                                height: Math.max(58, eventText.implicitHeight + 24)
                                radius: 16
                                color: "#121a48"
                                border.width: 1
                                border.color: "#2b429d"

                                RowLayout {
                                    anchors.fill: parent
                                    anchors.margins: 12
                                    spacing: 12

                                    Text {
                                        id: eventText
                                        Layout.fillWidth: true
                                        text: modelData.details
                                        color: window.statusColor(modelData.status)
                                        wrapMode: Text.WordWrap
                                        font.pixelSize: 13
                                    }
                                }
                            }

                            footer: Item {
                                width: parent.width
                                height: backend.events.length === 0 ? 180 : 0
                                visible: backend.events.length === 0

                                Text {
                                    anchors.centerIn: parent
                                    text: "No events yet"
                                    color: window.textMuted
                                    font.pixelSize: 15
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    component SectionCard: Rectangle {
        id: sectionCard
        property string title: ""
        property string subtitle: ""

        radius: window.radiusLg
        color: window.cardSurface
        border.width: 1
        border.color: window.cardBorder
        clip: true
        opacity: 0.0
        y: 8

        Component.onCompleted: cardReveal.start()
        ParallelAnimation {
            id: cardReveal
            NumberAnimation { target: sectionCard; property: "opacity"; from: 0.0; to: 1.0; duration: window.motionSlow; easing.type: Easing.OutCubic }
            NumberAnimation { target: sectionCard; property: "y"; from: 8; to: 0; duration: window.motionSlow; easing.type: Easing.OutCubic }
        }

        Column {
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: 18
            spacing: 4

            Text {
                text: sectionCard.title
                color: window.textPrimary
                font.pixelSize: 18
                font.bold: true
            }

            Text {
                width: parent.width
                text: sectionCard.subtitle
                color: window.textSecondary
                font.pixelSize: 13
                wrapMode: Text.WordWrap
            }
        }
    }

    component NeonButton: Rectangle {
        id: neonButton
        property alias text: buttonLabel.text
        property string role: "secondary"
        property color accent: "#58e7ff"
        property bool compact: false
        signal clicked

        implicitWidth: compact ? 84 : 154
        implicitHeight: compact ? 38 : 44
        radius: compact ? 14 : 16
        color: role === "primary"
               ? (mouseArea.pressed ? "#1e3f9e" : (mouseArea.containsMouse ? "#2853c5" : "#2349b0"))
               : (role === "danger"
                  ? (mouseArea.pressed ? "#5a2345" : (mouseArea.containsMouse ? "#74305a" : "#672a50"))
                  : (mouseArea.pressed ? window.buttonPressed : (mouseArea.containsMouse ? window.buttonHover : window.buttonBase)))
        border.width: 1
        border.color: role === "primary" ? "#65d9ff" : (role === "danger" ? "#ff83b1" : Qt.lighter(accent, 1.12))
        scale: mouseArea.pressed ? 0.985 : 1.0

        Behavior on color { ColorAnimation { duration: window.motionFast } }
        Behavior on border.color { ColorAnimation { duration: window.motionFast } }
        Behavior on scale { NumberAnimation { duration: 110; easing.type: Easing.OutCubic } }
        Behavior on opacity { NumberAnimation { duration: window.motionFast } }

        Rectangle {
            anchors.fill: parent
            radius: parent.radius
            color: "transparent"
            border.width: mouseArea.containsMouse ? 1 : 0
            border.color: Qt.lighter(neonButton.border.color, 1.15)
            opacity: mouseArea.containsMouse ? 0.42 : 0.0
            Behavior on opacity { NumberAnimation { duration: window.motionFast } }
            Behavior on border.width { NumberAnimation { duration: window.motionFast } }
        }

        MouseArea {
            id: mouseArea
            anchors.fill: parent
            hoverEnabled: true
            cursorShape: Qt.PointingHandCursor
            enabled: neonButton.enabled
            onClicked: neonButton.clicked()
        }

        Text {
            id: buttonLabel
            anchors.centerIn: parent
            color: window.buttonText
            font.pixelSize: compact ? 13 : 14
            font.bold: true
        }
    }

    component LabelField: ColumnLayout {
        id: labelField
        property string label: ""
        property string value: ""
        signal edited(string text)

        spacing: 6

        Text {
            text: labelField.label
            color: window.textSecondary
            font.pixelSize: 12
            font.bold: true
        }

        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 46
            radius: 18
            color: "#09102a"
            border.width: 1
            border.color: input.activeFocus ? "#58e7ff" : "#31458f"

            Behavior on border.color { ColorAnimation { duration: window.motionFast } }

            Rectangle {
                anchors.fill: parent
                anchors.margins: 1
                radius: parent.radius - 1
                color: "#5adfff"
                opacity: input.activeFocus ? 0.08 : 0.0
                Behavior on opacity { NumberAnimation { duration: window.motionFast } }
            }

            TextField {
                id: input
                anchors.fill: parent
                anchors.leftMargin: 12
                anchors.rightMargin: 12
                anchors.topMargin: 4
                anchors.bottomMargin: 4
                text: labelField.value
                color: window.textPrimary
                font.pixelSize: 14
                background: null
                selectByMouse: true
                onTextEdited: labelField.edited(text)
            }
        }
    }

    component StatusPill: Rectangle {
        id: statusPill
        property string text: ""
        property color accent: "#58e7ff"

        radius: 20
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0.0; color: "#10183f" }
            GradientStop { position: 1.0; color: "#0b1230" }
        }
        border.width: 1
        border.color: "#243b7f"
        implicitHeight: 54

        RowLayout {
            anchors.fill: parent
            anchors.margins: 12
            spacing: 0

            Text {
                Layout.fillWidth: true
                text: statusPill.text
                color: window.textPrimary
                font.pixelSize: 13
                wrapMode: Text.WordWrap
            }
        }
    }

    component ThemedVScrollBar: ScrollBar {
        id: themedScrollBar
        width: 12
        minimumSize: 0.08
        policy: ScrollBar.AsNeeded
        visible: themedScrollBar.size < 0.999

        contentItem: Rectangle {
            implicitWidth: 8
            radius: 4
            color: themedScrollBar.pressed ? "#7df1ff" : (themedScrollBar.hovered ? "#63d8ff" : "#50beff")
            border.width: 1
            border.color: "#8cefff"
        }

        background: Rectangle {
            radius: 6
            color: "#0b1235"
            border.width: 1
            border.color: "#223b8a"
            opacity: themedScrollBar.active || themedScrollBar.hovered ? 1.0 : 0.5
        }
    }
}