package ch.mdedic.m321.ui

import ch.mdedic.m321.handlers.MessageHandler
import ch.mdedic.m321.websocket.ChatWebSocketClient
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.text.StyleConstants

class ChatWindow : JFrame("Chat Client"), ChatWebSocketClient.ConnectionListener {

    // UI Components
    private val messagesArea = JTextPane()
    private val messageInput = JTextField()
    private val sendButton = JButton("Send")
    private val connectButton = JButton("Connect")
    private val disconnectButton = JButton("Disconnect")
    private val usernameField = JTextField(15)
    private val serverUrlField = JTextField("ws://localhost:8080/ws", 20)
    private val statusLabel = JLabel("Disconnected")

    // Application state
    private var client: ChatWebSocketClient? = null
    private var messageHandler: MessageHandler? = null
    private var username: String = ""
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    init {
        setupWindow()
        setupUI()
        setupListeners()
    }

    private fun setupWindow() {
        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(700, 600)
        setLocationRelativeTo(null)
        isVisible = true

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                disconnect()
            }
        })
    }

    private fun setupUI() {
        layout = BorderLayout(10, 10)
        add(createTopPanel(), BorderLayout.NORTH)
        add(createMessagesPanel(), BorderLayout.CENTER)
        add(createInputPanel(), BorderLayout.SOUTH)
        updateConnectionState(false)
    }

    private fun createTopPanel(): JPanel {
        val topPanel = JPanel()
        topPanel.layout = BoxLayout(topPanel, BoxLayout.Y_AXIS)
        topPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val connectionPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 5))
        connectionPanel.add(JLabel("Server:"))
        connectionPanel.add(serverUrlField)
        connectionPanel.add(JLabel("Username:"))
        connectionPanel.add(usernameField)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 5))
        buttonPanel.add(connectButton)
        buttonPanel.add(disconnectButton)

        topPanel.add(connectionPanel)
        topPanel.add(buttonPanel)

        return topPanel
    }

    private fun createMessagesPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)

        messagesArea.isEditable = false
        messagesArea.font = Font("Monospaced", Font.PLAIN, 12)
        messagesArea.background = Color(245, 245, 245)

        val scrollPane = JScrollPane(messagesArea)
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS

        panel.add(JLabel("Messages:"), BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    private fun createInputPanel(): JPanel {
        val panel = JPanel(BorderLayout(10, 10))
        panel.border = BorderFactory.createEmptyBorder(5, 10, 10, 10)

        val inputPanel = JPanel(BorderLayout(5, 0))
        inputPanel.add(messageInput, BorderLayout.CENTER)
        inputPanel.add(sendButton, BorderLayout.EAST)

        panel.add(inputPanel, BorderLayout.CENTER)
        panel.add(statusLabel, BorderLayout.SOUTH)

        return panel
    }

    private fun setupListeners() {
        connectButton.addActionListener { connect() }
        disconnectButton.addActionListener { disconnect() }
        sendButton.addActionListener { sendMessage() }
        messageInput.addActionListener { sendMessage() }
    }

    private fun connect() {
        username = usernameField.text.trim()
        val serverUrl = serverUrlField.text.trim()

        if (!validateInput(username, serverUrl)) return

        try {
            client = ChatWebSocketClient(URI(serverUrl), this)
            messageHandler = MessageHandler(username) { message ->
                SwingUtilities.invokeLater { displayMessage(message) }
            }

            client?.connect()
            displayMessage("[SYSTEM] Connecting to $serverUrl...")
        } catch (e: Exception) {
            showError("Failed to connect: ${e.message}")
        }
    }

    private fun disconnect() {
        client?.let {
            if (it.isOpen) {
                it.sendLeave(username)
            }
            it.close()
        }
        client = null
        messageHandler = null
    }

    private fun sendMessage() {
        val message = messageInput.text.trim()
        if (message.isEmpty()) return

        client?.let {
            if (it.isOpen) {
                it.sendChatMessage(username, message)

// START
                // Don't display @serveradmin or @server messages locally
                // They will be handled by the server response
                val isAdminDM = message.trim().lowercase().startsWith("@serveradmin")
                val isBotCommand = message.trim().startsWith("@server")

                if (!isAdminDM && !isBotCommand) {
                    // Only display regular chat messages locally
                    val time = getCurrentTime()
                    displayMessage("[$time] $username: $message")
                }
// END
                messageInput.text = ""
            }
        }
    }

    private fun validateInput(username: String, serverUrl: String): Boolean {
        if (username.isEmpty()) {
            showError("Please enter a username")
            return false
        }
        if (serverUrl.isEmpty()) {
            showError("Please enter a server URL")
            return false
        }
        return true
    }

    private fun showError(message: String) {
        JOptionPane.showMessageDialog(
            this,
            message,
            "Error",
            JOptionPane.ERROR_MESSAGE
        )
    }

    private fun displayMessage(message: String) {
        val doc = messagesArea.styledDocument
        val style = messagesArea.addStyle("style", null)

        when {
            message.startsWith("[SYSTEM]") -> {
                StyleConstants.setForeground(style, Color(100,100,100)) // dark gray
            }
            message.startsWith("[ERROR]") -> {
                StyleConstants.setForeground(style, Color(178, 34, 34)) // dark red
            }
            else -> {
                StyleConstants.setForeground(style, Color.BLACK)
            }
        }

        doc.insertString(doc.length, "$message\n", style)
        messagesArea.caretPosition = doc.length
    }


    private fun updateConnectionState(connected: Boolean) {
        connectButton.isEnabled = !connected
        disconnectButton.isEnabled = connected
        messageInput.isEnabled = connected
        sendButton.isEnabled = connected
        usernameField.isEnabled = !connected
        serverUrlField.isEnabled = !connected

        if (connected) {
            statusLabel.text = "Status: Connected as $username"
            statusLabel.foreground = Color(0, 128, 0)
        } else {
            statusLabel.text = "Status: Disconnected"
            statusLabel.foreground = Color(200, 0, 0)
        }
    }

    private fun getCurrentTime(): String {
        return Instant.now()
            .atZone(ZoneId.systemDefault())
            .format(timeFormatter)
    }

    // ConnectionListener implementation
    override fun onConnected() {
        SwingUtilities.invokeLater {
            displayMessage("[SYSTEM] Connected to server")
            updateConnectionState(true)
        }
        client?.sendJoin(username)
    }

    override fun onDisconnected(reason: String?) {
        SwingUtilities.invokeLater {
            val message = if (reason != null && reason.isNotEmpty()) {
                "[SYSTEM] Disconnected: $reason"
            } else {
                "[SYSTEM] Disconnected from server"
            }
            displayMessage(message)
            updateConnectionState(false)
        }
    }

    override fun onMessageReceived(message: String) {
        messageHandler?.handleMessage(message)
    }

    override fun onError(error: String) {
        SwingUtilities.invokeLater {
            displayMessage("[ERROR] $error")
        }
    }
}