package ch.mdedic.m321

import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

fun main() {
    SwingUtilities.invokeLater {
        ChatClientWindow()
    }
}

class ChatClientWindow : JFrame("WebSocket Chat Client") {
    private val messagesArea = JTextArea()
    private val messageInput = JTextField()
    private val sendButton = JButton("Send")
    private val connectButton = JButton("Connect")
    private val disconnectButton = JButton("Disconnect")
    private val usernameField = JTextField(15)
    private val serverUrlField = JTextField("ws://localhost:8080/ws", 20)
    private val statusLabel = JLabel("Disconnected")

    private var webSocketClient: WebSocketClient? = null
    private var username: String = ""
    private val objectMapper = jacksonObjectMapper()
    private val dateFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    init {
        setupUI()
        setupListeners()

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

        // Top panel - Connection controls
        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 10))
        topPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        topPanel.add(JLabel("Server:"))
        topPanel.add(serverUrlField)
        topPanel.add(JLabel("Username:"))
        topPanel.add(usernameField)
        topPanel.add(connectButton)
        topPanel.add(disconnectButton)

        disconnectButton.isEnabled = false

        add(topPanel, BorderLayout.NORTH)

        // Center panel - Messages
        val centerPanel = JPanel(BorderLayout())
        centerPanel.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)

        messagesArea.isEditable = false
        messagesArea.lineWrap = true
        messagesArea.wrapStyleWord = true
        messagesArea.font = Font("Monospaced", Font.PLAIN, 12)
        messagesArea.background = Color(245, 245, 245)

        val scrollPane = JScrollPane(messagesArea)
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS

        centerPanel.add(JLabel("Messages:"), BorderLayout.NORTH)
        centerPanel.add(scrollPane, BorderLayout.CENTER)

        add(centerPanel, BorderLayout.CENTER)

        // Bottom panel - Message input
        val bottomPanel = JPanel(BorderLayout(10, 10))
        bottomPanel.border = BorderFactory.createEmptyBorder(5, 10, 10, 10)

        messageInput.isEnabled = false
        sendButton.isEnabled = false

        val inputPanel = JPanel(BorderLayout(5, 0))
        inputPanel.add(messageInput, BorderLayout.CENTER)
        inputPanel.add(sendButton, BorderLayout.EAST)

        bottomPanel.add(inputPanel, BorderLayout.CENTER)
        bottomPanel.add(statusLabel, BorderLayout.SOUTH)

        add(bottomPanel, BorderLayout.SOUTH)

        updateStatusLabel(false)
    }

    private fun setupListeners() {
        connectButton.addActionListener {
            connect()
        }

        disconnectButton.addActionListener {
            disconnect()
        }

        sendButton.addActionListener {
            sendMessage()
        }

        messageInput.addActionListener {
            sendMessage()
        }
    }

    private fun connect() {
        username = usernameField.text.trim()
        val serverUrl = serverUrlField.text.trim()

        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Please enter a username",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        if (serverUrl.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Please enter a server URL",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        try {
            webSocketClient = object : WebSocketClient(URI(serverUrl)) {
                override fun onOpen(handshakedata: ServerHandshake?) {
                    SwingUtilities.invokeLater {
                        appendMessage("[SYSTEM] Connected to server")
                        updateStatusLabel(true)
                        connectButton.isEnabled = false
                        disconnectButton.isEnabled = true
                        messageInput.isEnabled = true
                        sendButton.isEnabled = true
                        usernameField.isEnabled = false
                        serverUrlField.isEnabled = false
                    }

                    // Send JOIN message
                    val joinMessage = mapOf(
                        "type" to "JOIN",
                        "userId" to username
                    )
                    send(objectMapper.writeValueAsString(joinMessage))
                }

                override fun onMessage(message: String?) {
                    message?.let {
                        SwingUtilities.invokeLater {
                            handleIncomingMessage(it)
                        }
                    }
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    SwingUtilities.invokeLater {
                        appendMessage("[SYSTEM] Disconnected from server${if (reason != null) ": $reason" else ""}")
                        updateStatusLabel(false)
                        connectButton.isEnabled = true
                        disconnectButton.isEnabled = false
                        messageInput.isEnabled = false
                        sendButton.isEnabled = false
                        usernameField.isEnabled = true
                        serverUrlField.isEnabled = true
                    }
                }

                override fun onError(ex: Exception?) {
                    SwingUtilities.invokeLater {
                        appendMessage("[ERROR] ${ex?.message ?: "Unknown error"}")
                        ex?.printStackTrace()
                    }
                }
            }

            webSocketClient?.connect()
            appendMessage("[SYSTEM] Connecting to $serverUrl...")

        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                "Failed to connect: ${e.message}",
                "Connection Error",
                JOptionPane.ERROR_MESSAGE
            )
            e.printStackTrace()
        }
    }

    private fun disconnect() {
        webSocketClient?.let { client ->
            if (client.isOpen) {
                // Send LEAVE message
                val leaveMessage = mapOf(
                    "type" to "LEAVE",
                    "userId" to username
                )
                client.send(objectMapper.writeValueAsString(leaveMessage))
            }
            client.close()
        }
        webSocketClient = null
    }

    private fun sendMessage() {
        val message = messageInput.text.trim()

        if (message.isEmpty()) {
            return
        }

        webSocketClient?.let { client ->
            if (client.isOpen) {
                val chatMessage = mapOf(
                    "type" to "CHAT",
                    "userId" to username,
                    "content" to message,
                    "timestamp" to System.currentTimeMillis()
                )

                client.send(objectMapper.writeValueAsString(chatMessage))

                // Display own message
                val timestamp = getCurrentTime()
                appendMessage("[$timestamp] $username: $message")

                messageInput.text = ""
            }
        }
    }

    private fun handleIncomingMessage(jsonMessage: String) {
        try {
            val messageData: Map<String, Any> = objectMapper.readValue(jsonMessage)
            val messageType = messageData["type"] as? String ?: "UNKNOWN"

            when (messageType) {
                "SYSTEM" -> {
                    val content = messageData["message"] as? String ?: jsonMessage
                    appendMessage("[SYSTEM] $content")
                }
                "JOIN" -> {
                    val userId = messageData["userId"] as? String ?: "Unknown"
                    val content = messageData["message"] as? String ?: "$userId joined"
                    appendMessage("[SYSTEM] $content")
                }
                "JOIN_ACK" -> {
                    val content = messageData["message"] as? String ?: "Successfully joined"
                    val activeUsers = messageData["activeUsers"] as? List<*>
                    appendMessage("[SYSTEM] $content")
                    if (activeUsers != null) {
                        appendMessage("[SYSTEM] Active users: ${activeUsers.joinToString(", ")}")
                    }
                }
                "LEAVE" -> {
                    val userId = messageData["userId"] as? String ?: "Unknown"
                    val content = messageData["message"] as? String ?: "$userId left"
                    appendMessage("[SYSTEM] $content")
                }
                "CHAT" -> {
                    val senderId = messageData["senderId"] as? String ?: "Unknown"
                    val content = messageData["content"] as? String ?: ""
                    val timestamp = (messageData["timestamp"] as? Number)?.toLong()

                    val timeStr = if (timestamp != null) {
                        formatTimestamp(timestamp)
                    } else {
                        getCurrentTime()
                    }

                    // Don't display if it's our own message (we already displayed it)
                    if (senderId != username) {
                        appendMessage("[$timeStr] $senderId: $content")
                    }
                }
                "TYPING" -> {
                    val userId = messageData["userId"] as? String ?: "Unknown"
                    appendMessage("[SYSTEM] $userId is typing...")
                }
                "ERROR" -> {
                    val content = messageData["message"] as? String ?: "Unknown error"
                    appendMessage("[ERROR] $content")
                }
                else -> {
                    appendMessage("[DEBUG] $jsonMessage")
                }
            }
        } catch (e: Exception) {
            appendMessage("[ERROR] Failed to parse message: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun appendMessage(message: String) {
        messagesArea.append("$message\n")
        messagesArea.caretPosition = messagesArea.document.length
    }

    private fun updateStatusLabel(connected: Boolean) {
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
            .format(dateFormatter)
    }

    private fun formatTimestamp(timestamp: Long): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(dateFormatter)
    }
}