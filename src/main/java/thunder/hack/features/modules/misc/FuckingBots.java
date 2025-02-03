package thunder.hack.features.modules.misc;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.events.impl.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import java.util.*;
import java.io.*;
import java.nio.file.*;

public class FuckingBots extends Module {
    private final Setting<Integer> maxBots = new Setting<>("MaxBots", 5, 1, 20);
    private final Setting<Integer> spamDelay = new Setting<>("SpamDelay", 2000, 0, 10000);
    private final Setting<Boolean> autoSpam = new Setting<>("AutoSpam", false);
    private final Setting<Boolean> copyChat = new Setting<>("CopyChat", false);
    private final Setting<Boolean> randomMovement = new Setting<>("RandomMovement", true);
    private final Setting<Boolean> followMaster = new Setting<>("FollowMaster", false);
    private final Setting<Float> followRange = new Setting<>("FollowRange", 3f, 1f, 10f);
    private final Setting<String> serverIp = new Setting<>("ServerIP", "localhost");
    private final Setting<Integer> serverPort = new Setting<>("ServerPort", 25565, 1, 65535);
    
    private final Map<String, BotInfo> bots = new HashMap<>();
    private final Timer spamTimer = new Timer();
    private final List<String> spamMessages = new ArrayList<>();
    private Process nodeProcess;
    private BufferedWriter nodeInput;
    private BufferedReader nodeOutput;

    public FuckingBots() {
        super("FuckingBots", Category.MISC);
        setupNodeJS();
    }

    private void setupNodeJS() {
        try {
            // Проверяем наличие Node.js
            Process nodeCheck = Runtime.getRuntime().exec("node --version");
            int exitCode = nodeCheck.waitFor();
            if (exitCode != 0) {
                sendMessage("§cError: Node.js not found! Please install Node.js to use bots.");
                disable();
                return;
            }

            Path botsDir = Paths.get(mc.runDirectory.getPath(), "thunderhack", "bots");
            Files.createDirectories(botsDir);

            Path botFile = botsDir.resolve("bot.js");
            if (!Files.exists(botFile)) {
                Files.write(botFile, getBotScript().getBytes());
            }

            Path packageFile = botsDir.resolve("package.json");
            if (!Files.exists(packageFile)) {
                Files.write(packageFile, getPackageJson().getBytes());
            }

            // Проверяем/устанавливаем зависимости
            ProcessBuilder npmCheck = new ProcessBuilder("npm", "list");
            npmCheck.directory(botsDir.toFile());
            Process npmList = npmCheck.start();
            if (npmList.waitFor() != 0) {
                sendMessage("§eInstalling dependencies...");
                ProcessBuilder npmInstall = new ProcessBuilder("npm", "install");
                npmInstall.directory(botsDir.toFile());
                Process install = npmInstall.start();
                if (install.waitFor() != 0) {
                    sendMessage("§cError installing dependencies!");
                    disable();
                    return;
                }
                sendMessage("§aDependencies installed successfully!");
            }

        } catch (Exception e) {
            sendMessage("§cError setting up Node.js environment: " + e.getMessage());
            e.printStackTrace();
            disable();
        }
    }

    private String getBotScript() {
        return """
                const mineflayer = require('mineflayer');
                const pathfinder = require('mineflayer-pathfinder').pathfinder;
                const Movements = require('mineflayer-pathfinder').Movements;
                const { GoalNear } = require('mineflayer-pathfinder').goals;
                
                const bots = new Map();
                const reconnectAttempts = new Map();
                const MAX_RECONNECT_ATTEMPTS = 3;
                
                process.stdin.on('data', data => {
                    try {
                        const cmd = data.toString().trim().split(' ');
                        handleCommand(cmd);
                    } catch (err) {
                        console.error('Error handling command:', err);
                    }
                });
                
                function handleCommand(cmd) {
                    switch(cmd[0]) {
                        case 'create':
                            createBot(cmd[1], cmd[2], parseInt(cmd[3]));
                            break;
                        case 'remove':
                            removeBot(cmd[1]);
                            break;
                        case 'chat':
                            botChat(cmd[1], cmd.slice(2).join(' '));
                            break;
                        case 'follow':
                            followPlayer(cmd[1], cmd[2]);
                            break;
                        case 'stop':
                            stopBot(cmd[1]);
                            break;
                        default:
                            console.log('Unknown command:', cmd[0]);
                    }
                }
                
                function createBot(name, host, port) {
                    if (bots.has(name)) {
                        console.log(`Bot ${name} already exists`);
                        return;
                    }
                
                    const options = {
                        host: host,
                        port: port,
                        username: name,
                        auth: 'offline',
                        version: '1.20.1',
                        keepAlive: true,
                        checkTimeoutInterval: 30000,
                        reconnect: false
                    };
                
                    const bot = mineflayer.createBot(options);
                    setupBot(bot, name, options);
                }
                
                function setupBot(bot, name, options) {
                    bot.loadPlugin(pathfinder);
                
                    bot.once('spawn', () => {
                        try {
                            const mcData = require('minecraft-data')(bot.version);
                            const movements = new Movements(bot, mcData);
                            bot.pathfinder.setMovements(movements);
                            console.log(`Bot ${name} spawned successfully`);
                            reconnectAttempts.set(name, 0);
                        } catch (err) {
                            console.error(`Error setting up bot ${name}:`, err);
                        }
                    });
                
                    bot.on('end', () => {
                        console.log(`Bot ${name} disconnected`);
                        const attempts = reconnectAttempts.get(name) || 0;
                        if (attempts < MAX_RECONNECT_ATTEMPTS) {
                            console.log(`Attempting to reconnect ${name} (Attempt ${attempts + 1}/${MAX_RECONNECT_ATTEMPTS})`);
                            reconnectAttempts.set(name, attempts + 1);
                            setTimeout(() => {
                                const newBot = mineflayer.createBot(options);
                                setupBot(newBot, name, options);
                            }, 5000);
                        } else {
                            console.log(`Failed to reconnect ${name} after ${MAX_RECONNECT_ATTEMPTS} attempts`);
                            bots.delete(name);
                            reconnectAttempts.delete(name);
                        }
                    });
                
                    bot.on('error', (err) => {
                        console.error(`Bot ${name} error:`, err);
                    });
                
                    bot.on('chat', (username, message) => {
                        if (username === bot.username) return;
                        console.log(`[${name}] ${username}: ${message}`);
                    });
                
                    bots.set(name, bot);
                }
                
                function removeBot(name) {
                    const bot = bots.get(name);
                    if (bot) {
                        reconnectAttempts.delete(name);
                        bot.end('Removed');
                        bots.delete(name);
                        console.log(`Bot ${name} removed`);
                    }
                }
                
                function botChat(name, message) {
                    const bot = bots.get(name);
                    if (bot && bot.entity) {
                        try {
                            bot.chat(message);
                            console.log(`[${name}] sent: ${message}`);
                        } catch (err) {
                            console.error(`Error sending message for ${name}:`, err);
                        }
                    }
                }
                
                function followPlayer(name, target) {
                    const bot = bots.get(name);
                    if (!bot || !bot.entity) return;
                    
                    try {
                        const player = bot.players[target];
                        if (!player || !player.entity) {
                            console.log(`Can't find player ${target}`);
                            return;
                        }
                        
                        const goal = new GoalNear(player.entity.position.x, player.entity.position.y, player.entity.position.z, 2);
                        bot.pathfinder.setGoal(goal, true);
                        console.log(`${name} following ${target}`);
                    } catch (err) {
                        console.error(`Error following player for ${name}:`, err);
                    }
                }
                
                function stopBot(name) {
                    const bot = bots.get(name);
                    if (bot && bot.entity) {
                        try {
                            bot.pathfinder.setGoal(null);
                            console.log(`${name} stopped following`);
                        } catch (err) {
                            console.error(`Error stopping ${name}:`, err);
                        }
                    }
                }
                
                // Обработка ошибок процесса
                process.on('uncaughtException', (err) => {
                    console.error('Uncaught exception:', err);
                });
                
                process.on('unhandledRejection', (err) => {
                    console.error('Unhandled rejection:', err);
                });
                """;
    }

    private String getPackageJson() {
        return """
                {
                    "name": "thunderhack-bots",
                    "version": "1.0.0",
                    "dependencies": {
                        "mineflayer": "^4.14.0",
                        "mineflayer-pathfinder": "^2.4.5",
                        "minecraft-data": "^3.54.0"
                    }
                }
                """;
    }

    @Override
    public void onEnable() {
        try {
            Path botsDir = Paths.get(mc.runDirectory.getPath(), "thunderhack", "bots");
            ProcessBuilder pb = new ProcessBuilder("node", "bot.js");
            pb.directory(botsDir.toFile());
            
            // Перенаправляем stderr в stdout для лучшего логирования
            pb.redirectErrorStream(true);
            
            nodeProcess = pb.start();
            nodeInput = new BufferedWriter(new OutputStreamWriter(nodeProcess.getOutputStream()));
            nodeOutput = new BufferedReader(new InputStreamReader(nodeProcess.getInputStream()));

            // Поток для чтения вывода Node.js
            new Thread(() -> {
                try {
                    String line;
                    while ((line = nodeOutput.readLine()) != null) {
                        final String message = line;
                        if (message.startsWith("Error") || message.startsWith("Uncaught")) {
                            sendMessage("§c[Bot Error] " + message);
                        } else {
                            sendMessage("§7[Bot] " + message);
                        }
                    }
                } catch (IOException e) {
                    if (!isDisabled()) {
                        sendMessage("§cBot process disconnected! Disabling module...");
                        disable();
                    }
                }
            }, "Bot-Output-Reader").start();

        } catch (Exception e) {
            sendMessage("§cError starting bot process: " + e.getMessage());
            e.printStackTrace();
            disable();
        }
    }

    @Override
    public void onDisable() {
        try {
            if (nodeInput != null) {
                bots.keySet().forEach(this::removeBot);
                nodeInput.close();
            }
            if (nodeOutput != null) nodeOutput.close();
            if (nodeProcess != null) nodeProcess.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        bots.clear();
        spamMessages.clear();
    }

    private void sendNodeCommand(String... args) {
        try {
            nodeInput.write(String.join(" ", args) + "\n");
            nodeInput.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createBot(String name) {
        if (bots.size() >= maxBots.getValue()) {
            sendMessage("Max bots limit reached!");
            return;
        }
        if (bots.containsKey(name)) {
            sendMessage("Bot with name " + name + " already exists!");
            return;
        }
        bots.put(name, new BotInfo(name));
        sendNodeCommand("create", name, serverIp.getValue(), serverPort.getValue().toString());
        sendMessage("Bot " + name + " created!");
    }

    private void removeBot(String name) {
        if (bots.remove(name) != null) {
            sendNodeCommand("remove", name);
            sendMessage("Bot " + name + " removed!");
        } else {
            sendMessage("Bot " + name + " not found!");
        }
    }

    private void botChat(String name, String message) {
        if (!bots.containsKey(name)) {
            sendMessage("Bot " + name + " not found!");
            return;
        }
        sendNodeCommand("chat", name, message);
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ChatMessageC2SPacket packet) {
            String message = packet.chatMessage();
            if (message.startsWith("@bot")) {
                event.cancel();
                handleBotCommand(message);
            }
        }
    }

    private void handleBotCommand(String message) {
        String[] args = message.split(" ");
        if (args.length < 2) return;

        switch (args[1].toLowerCase()) {
            case "create" -> {
                if (args.length < 3) {
                    sendMessage("Usage: @bot create <name>");
                    return;
                }
                createBot(args[2]);
            }
            case "remove" -> {
                if (args.length < 3) {
                    sendMessage("Usage: @bot remove <name>");
                    return;
                }
                removeBot(args[2]);
            }
            case "chat" -> {
                if (args.length < 4) {
                    sendMessage("Usage: @bot chat <name> <message>");
                    return;
                }
                String botName = args[2];
                String chatMessage = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                botChat(botName, chatMessage);
            }
            case "follow" -> {
                if (args.length < 3) {
                    sendMessage("Usage: @bot follow <name>");
                    return;
                }
                sendNodeCommand("follow", args[2], mc.player.getName().getString());
            }
            case "stop" -> {
                if (args.length < 3) {
                    sendMessage("Usage: @bot stop <name>");
                    return;
                }
                sendNodeCommand("stop", args[2]);
            }
            case "list" -> listBots();
            case "clear" -> clearBots();
            default -> sendMessage("Unknown command. Available: create, remove, chat, follow, stop, list, clear");
        }
    }

    private void listBots() {
        if (bots.isEmpty()) {
            sendMessage("No bots available!");
            return;
        }
        StringBuilder sb = new StringBuilder("Bots (" + bots.size() + "): ");
        bots.keySet().forEach(name -> sb.append(name).append(", "));
        sendMessage(sb.substring(0, sb.length() - 2));
    }

    private void clearBots() {
        int count = bots.size();
        bots.forEach((name, bot) -> removeBot(name));
        sendMessage("Removed " + count + " bots!");
    }

    private static class BotInfo {
        private final String name;
        private double x, y, z;
        private float yaw, pitch;
        private boolean isMoving;

        public BotInfo(String name) {
            this.name = name;
            if (mc.player != null) {
                this.x = mc.player.getX();
                this.y = mc.player.getY();
                this.z = mc.player.getZ();
                this.yaw = mc.player.getYaw();
                this.pitch = mc.player.getPitch();
            }
        }
    }
} 