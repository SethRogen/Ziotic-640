package com.ziotic.adapter.file;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import com.ziotic.adapter.DatabaseLoader;
import com.ziotic.adapter.file.FileAuthenticator.AuthResult;
import com.ziotic.content.cc.Clan;
import com.ziotic.engine.login.LoginResponse;
import com.ziotic.logic.player.PlayerSave;
import com.ziotic.utility.FilePlayerManager;

/**
 * File-based player storage system.
 * Implements DatabaseLoader interface using binary file storage instead of SQL.
 *
 * @author Ziotic Development Team
 */
public class FilePlayerStorage implements DatabaseLoader {

    private static final Logger logger = Logger.getLogger(FilePlayerStorage.class);

    // Ban storage
    private final ConcurrentHashMap<String, Boolean> bannedUsers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> bannedIPs = new ConcurrentHashMap<>();

    // Ban file paths
    private static final String BAN_DIR = "data/playerdata/bans/";
    private static final String USER_BANS_FILE = BAN_DIR + "banned_users.txt";
    private static final String IP_BANS_FILE = BAN_DIR + "banned_ips.txt";

    // Clan storage directory
    private static final String CLAN_DIR = "data/playerdata/clans/";

    // User ID counter (for new registrations)
    private static final String USER_ID_FILE = "data/playerdata/next_userid.txt";
    private int nextUserId = 1000;

    /**
     * Initialize the file player storage system.
     */
    public FilePlayerStorage() {
        try {
            // Initialize file structure
            FilePlayerManager.initialize();

            // Create ban directories
            createDirectory(BAN_DIR);

            // Create clan directory
            createDirectory(CLAN_DIR);

            // Load ban lists
            loadBanLists();

            // Load next user ID
            loadNextUserId();

            logger.info("FilePlayerStorage initialized successfully");

        } catch (Exception e) {
            logger.error("Failed to initialize FilePlayerStorage", e);
            throw new RuntimeException("Could not initialize file player storage", e);
        }
    }

    /**
     * Create a directory if it doesn't exist.
     */
    private void createDirectory(String path) throws IOException {
        File dir = new File(path);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Failed to create directory: " + path);
            }
        }
    }

    /**
     * Load ban lists from files.
     */
    private void loadBanLists() {
        try {
            // Load banned users
            File userBansFile = new File(USER_BANS_FILE);
            if (userBansFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(userBansFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            bannedUsers.put(line.toLowerCase(), true);
                        }
                    }
                }
                logger.info("Loaded " + bannedUsers.size() + " banned users");
            } else {
                // Create empty file
                userBansFile.createNewFile();
            }

            // Load banned IPs
            File ipBansFile = new File(IP_BANS_FILE);
            if (ipBansFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(ipBansFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            bannedIPs.put(line, true);
                        }
                    }
                }
                logger.info("Loaded " + bannedIPs.size() + " banned IPs");
            } else {
                // Create empty file
                ipBansFile.createNewFile();
            }

        } catch (IOException e) {
            logger.error("Failed to load ban lists", e);
        }
    }

    /**
     * Load the next user ID from file.
     */
    private void loadNextUserId() {
        try {
            File userIdFile = new File(USER_ID_FILE);
            if (userIdFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(userIdFile))) {
                    String line = reader.readLine();
                    if (line != null) {
                        nextUserId = Integer.parseInt(line.trim());
                    }
                }
            } else {
                // Create file with initial value
                saveNextUserId();
            }
        } catch (Exception e) {
            logger.error("Failed to load next user ID, using default: " + nextUserId, e);
        }
    }

    /**
     * Save the next user ID to file.
     */
    private void saveNextUserId() {
        try {
            File userIdFile = new File(USER_ID_FILE);
            try (PrintWriter writer = new PrintWriter(new FileWriter(userIdFile))) {
                writer.println(nextUserId);
            }
        } catch (IOException e) {
            logger.error("Failed to save next user ID", e);
        }
    }

    /**
     * Get and increment the next user ID.
     */
    private synchronized int getNextUserId() {
        int id = nextUserId++;
        saveNextUserId();
        return id;
    }

    @Override
    public void banIP(String ip) {
        bannedIPs.put(ip, true);

        // Append to file
        try (PrintWriter writer = new PrintWriter(new FileWriter(IP_BANS_FILE, true))) {
            writer.println(ip);
            logger.info("Banned IP: " + ip);
        } catch (IOException e) {
            logger.error("Failed to save IP ban: " + ip, e);
        }
    }

    @Override
    public boolean isBanned(String userName) {
        return bannedUsers.containsKey(userName.toLowerCase());
    }

    @Override
    public boolean isIPBanned(String ip) {
        return bannedIPs.containsKey(ip);
    }

    @Override
    public LoginResponse loadPlayer(String userName, String password, PlayerSave player) {
        try {
            logger.debug("Loading player: " + userName);

            // Check if user is banned
            if (isBanned(userName)) {
                logger.info("Login denied: User is banned: " + userName);
                return LoginResponse.BANNED;
            }

            // Check if user exists
            if (!FilePlayerManager.authExists(userName)) {
                // New player - create account
                logger.info("Creating new player account: " + userName);

                int userId = getNextUserId();
                boolean registered = FileAuthenticator.register(userName, password, userId, FileAuthenticator.RIGHTS_PLAYER);

                if (!registered) {
                    logger.error("Failed to register new player: " + userName);
                    return LoginResponse.ERROR;
                }

                // Initialize new player save with defaults
                player.userId = userId;
                player.rights = FileAuthenticator.RIGHTS_PLAYER;
                player.x = 3222; // Default spawn location (Lumbridge)
                player.y = 3218;
                player.z = 0;
                player.runEnergy = 100;
                player.specialEnergy = 100;
                player.spellBook = 193;

                // Initialize lastIPs array
                for (int i = 0; i < player.lastIPs.length; i++) {
                    player.lastIPs[i] = "";
                }

                // Initialize inventory/equipment as empty
                for (int i = 0; i < 28; i++) {
                    player.inv[i] = -1;
                    player.invN[i] = 0;
                }
                for (int i = 0; i < 14; i++) {
                    player.equip[i] = -1;
                    player.equipN[i] = 0;
                }

                // Initialize levels (1 for most skills, 10 for HP, 0 exp)
                for (int i = 0; i < player.level.length; i++) {
                    player.level[i] = (byte) (i == 3 ? 10 : 1); // HP starts at 10
                    player.exp[i] = 0.0;
                }

                // Initialize appearance defaults
                player.looks[0] = 0; // Hair
                player.looks[1] = 10; // Beard
                player.looks[2] = 18; // Torso
                player.looks[3] = 26; // Arms
                player.looks[4] = 33; // Hands
                player.looks[5] = 36; // Legs
                player.looks[6] = 42; // Feet

                player.colours[0] = 0; // Hair color
                player.colours[1] = 0; // Torso color
                player.colours[2] = 0; // Leg color
                player.colours[3] = 0; // Feet color
                player.colours[4] = 0; // Skin color

                // Save new player file
                savePlayer(userName, false, player);

                logger.info("Created new player: " + userName + " with ID: " + userId);
                return LoginResponse.LOGIN;
            }

            // Authenticate existing player
            AuthResult auth = FileAuthenticator.authenticate(userName, password);

            if (!auth.success) {
                logger.info("Login denied: Invalid credentials for user: " + userName);
                return LoginResponse.INVALID_DETAILS;
            }

            // Load player save file
            String playerFilePath = FilePlayerManager.getPlayerFilePath(userName);
            byte[] playerData = FilePlayerManager.readPlayerFile(playerFilePath);

            if (playerData == null) {
                // Auth file exists but no player save - corrupted state
                logger.error("Auth file exists but no player save for: " + userName);
                return LoginResponse.ERROR;
            }

            // Deserialize player data
            try {
                player.load(playerData);
                player.userId = auth.userId;
                player.rights = auth.rights;

                logger.info("Loaded player: " + userName + " (ID: " + auth.userId + ")");
                return LoginResponse.LOGIN;

            } catch (IOException e) {
                logger.error("Failed to deserialize player data for: " + userName, e);
                return LoginResponse.ERROR;
            }

        } catch (Exception e) {
            logger.error("Error loading player: " + userName, e);
            return LoginResponse.ERROR;
        }
    }

    @Override
    public void savePlayer(String userName, boolean lobby, PlayerSave player) {
        try {
            logger.debug("Saving player: " + userName + " (lobby: " + lobby + ")");

            // Serialize player data
            byte[] playerData = player.toByteArray();

            if (playerData == null) {
                logger.error("Failed to serialize player data for: " + userName);
                return;
            }

            // Write to file
            String playerFilePath = FilePlayerManager.getPlayerFilePath(userName);
            FilePlayerManager.writePlayerFile(playerFilePath, playerData);

            logger.debug("Saved player: " + userName + " (" + playerData.length + " bytes)");

        } catch (Exception e) {
            logger.error("Error saving player: " + userName, e);
        }
    }

    @Override
    public void registerClan(String owner, String name) {
        // Clans are saved as part of player save (ownClan field)
        // This method is for registering clan names in a global registry
        try {
            File clanRegistry = new File(CLAN_DIR + "clan_registry.txt");

            // Check if clan already registered
            if (clanRegistry.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(clanRegistry))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith(owner + ":")) {
                            logger.debug("Clan already registered for owner: " + owner);
                            return;
                        }
                    }
                }
            }

            // Append to registry
            try (PrintWriter writer = new PrintWriter(new FileWriter(clanRegistry, true))) {
                writer.println(owner + ":" + name);
                logger.info("Registered clan: " + name + " (owner: " + owner + ")");
            }

        } catch (IOException e) {
            logger.error("Failed to register clan for owner: " + owner, e);
        }
    }

    @Override
    public boolean loadClan(String owner, Clan clan) {
        try {
            // Load clan data from owner's player save
            String playerFilePath = FilePlayerManager.getPlayerFilePath(owner);
            byte[] playerData = FilePlayerManager.readPlayerFile(playerFilePath);

            if (playerData == null) {
                logger.debug("No player save found for clan owner: " + owner);
                return false;
            }

            // Deserialize player save to extract clan data
            PlayerSave playerSave = new PlayerSave();
            playerSave.load(playerData);

            if (playerSave.ownClan == null) {
                logger.debug("No clan data found for owner: " + owner);
                return false;
            }

            // Copy clan data
            // Note: Clan.load() is already called during PlayerSave.load()
            // We need to copy the clan object
            // Since we can't directly copy, we'll serialize and deserialize
            byte[] clanData = playerSave.ownClan.toByteArray();
            clan.load(clanData);

            logger.info("Loaded clan for owner: " + owner);
            return true;

        } catch (Exception e) {
            logger.error("Failed to load clan for owner: " + owner, e);
            return false;
        }
    }

    @Override
    public void saveClan(Clan clan) {
        try {
            String owner = clan.getOwner();

            // Load player save
            String playerFilePath = FilePlayerManager.getPlayerFilePath(owner);
            byte[] playerData = FilePlayerManager.readPlayerFile(playerFilePath);

            if (playerData == null) {
                logger.error("Cannot save clan: No player save for owner: " + owner);
                return;
            }

            // Deserialize, update clan, re-serialize
            PlayerSave playerSave = new PlayerSave();
            playerSave.load(playerData);
            playerSave.ownClan = clan;

            byte[] updatedData = playerSave.toByteArray();
            FilePlayerManager.writePlayerFile(playerFilePath, updatedData);

            logger.info("Saved clan for owner: " + owner);

        } catch (Exception e) {
            logger.error("Failed to save clan: " + clan.getOwner(), e);
        }
    }

    @Override
    public void reload() {
        logger.info("Reloading FilePlayerStorage...");

        // Clear and reload ban lists
        bannedUsers.clear();
        bannedIPs.clear();
        loadBanLists();

        logger.info("FilePlayerStorage reloaded successfully");
    }

    /**
     * Ban a player by username.
     *
     * @param userName The username to ban
     */
    public void banUser(String userName) {
        bannedUsers.put(userName.toLowerCase(), true);

        // Append to file
        try (PrintWriter writer = new PrintWriter(new FileWriter(USER_BANS_FILE, true))) {
            writer.println(userName.toLowerCase());
            logger.info("Banned user: " + userName);
        } catch (IOException e) {
            logger.error("Failed to save user ban: " + userName, e);
        }
    }

    /**
     * Unban a player by username.
     *
     * @param userName The username to unban
     */
    public void unbanUser(String userName) {
        bannedUsers.remove(userName.toLowerCase());

        // Rewrite file without this user
        try {
            File tempFile = new File(USER_BANS_FILE + ".tmp");
            try (BufferedReader reader = new BufferedReader(new FileReader(USER_BANS_FILE));
                 PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().equalsIgnoreCase(userName)) {
                        writer.println(line);
                    }
                }
            }

            // Replace original file
            File originalFile = new File(USER_BANS_FILE);
            originalFile.delete();
            tempFile.renameTo(originalFile);

            logger.info("Unbanned user: " + userName);

        } catch (IOException e) {
            logger.error("Failed to unban user: " + userName, e);
        }
    }

    /**
     * Unban an IP address.
     *
     * @param ip The IP to unban
     */
    public void unbanIP(String ip) {
        bannedIPs.remove(ip);

        // Rewrite file without this IP
        try {
            File tempFile = new File(IP_BANS_FILE + ".tmp");
            try (BufferedReader reader = new BufferedReader(new FileReader(IP_BANS_FILE));
                 PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().equals(ip)) {
                        writer.println(line);
                    }
                }
            }

            // Replace original file
            File originalFile = new File(IP_BANS_FILE);
            originalFile.delete();
            tempFile.renameTo(originalFile);

            logger.info("Unbanned IP: " + ip);

        } catch (IOException e) {
            logger.error("Failed to unban IP: " + ip, e);
        }
    }
}
