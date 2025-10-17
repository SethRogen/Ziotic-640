package com.ziotic.utility;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

/**
 * Utility class for managing file-based player data storage.
 * Provides thread-safe file operations, atomic writes, and backup management.
 *
 * @author Ziotic Development Team
 */
public class FilePlayerManager {

    private static final Logger logger = Logger.getLogger(FilePlayerManager.class);

    // Base directory for all player data
    private static final String PLAYERDATA_DIR = "data/playerdata/";
    private static final String CHARACTERS_DIR = PLAYERDATA_DIR + "characters/";
    private static final String AUTH_DIR = PLAYERDATA_DIR + "auth/";

    // File extensions
    private static final String PLAYER_EXTENSION = ".dat";
    private static final String AUTH_EXTENSION = ".auth";
    private static final String TEMP_EXTENSION = ".tmp";
    private static final String BACKUP_EXTENSION = ".bak";

    // Lock management for concurrent file access
    private static final ConcurrentHashMap<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

    /**
     * Initialize the player data directory structure.
     * Creates all necessary directories if they don't exist.
     */
    public static void initialize() {
        try {
            // Create main playerdata directory
            createDirectory(PLAYERDATA_DIR);

            // Create auth directory
            createDirectory(AUTH_DIR);

            // Create characters directory
            createDirectory(CHARACTERS_DIR);

            // Pre-create hashed subdirectories (a-z, 0-9) inside characters/
            String[] hashChars = {"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
                                  "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
                                  "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};

            for (String first : hashChars) {
                for (String second : hashChars) {
                    createDirectory(CHARACTERS_DIR + first + "/" + second + "/");
                }
            }

            logger.info("FilePlayerManager initialized successfully");
            logger.info("Player data directory: " + new File(PLAYERDATA_DIR).getAbsolutePath());

        } catch (Exception e) {
            logger.error("Failed to initialize FilePlayerManager", e);
            throw new RuntimeException("Could not initialize player data storage", e);
        }
    }

    /**
     * Creates a directory if it doesn't exist.
     */
    private static void createDirectory(String path) throws IOException {
        File dir = new File(path);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Failed to create directory: " + path);
            }
        }
    }

    /**
     * Get the hashed directory path for a username.
     * Uses first two characters of lowercase username.
     *
     * @param username The player's username
     * @return The directory path (e.g., "data/playerdata/characters/a/b/")
     */
    public static String getHashedDirectory(String username) {
        String normalized = username.toLowerCase().replaceAll("[^a-z0-9]", "");

        if (normalized.length() == 0) {
            normalized = "00"; // Default for invalid usernames
        } else if (normalized.length() == 1) {
            normalized = normalized + "0";
        }

        char first = normalized.charAt(0);
        char second = normalized.charAt(1);

        return CHARACTERS_DIR + first + "/" + second + "/";
    }

    /**
     * Get the full file path for a player's save file.
     *
     * @param username The player's username
     * @return The complete file path
     */
    public static String getPlayerFilePath(String username) {
        return getHashedDirectory(username) + "player_" + username.toLowerCase() + PLAYER_EXTENSION;
    }

    /**
     * Get the full file path for a player's authentication file.
     *
     * @param username The player's username
     * @return The complete file path
     */
    public static String getAuthFilePath(String username) {
        return AUTH_DIR + username.toLowerCase() + AUTH_EXTENSION;
    }

    /**
     * Acquire a lock for a specific file to ensure thread-safe operations.
     *
     * @param filePath The file path to lock
     * @return The lock object
     */
    private static ReentrantLock acquireLock(String filePath) {
        return fileLocks.computeIfAbsent(filePath, k -> new ReentrantLock());
    }

    /**
     * Write binary data to a file atomically with backup.
     * Uses a temporary file and atomic rename to prevent corruption.
     *
     * @param filePath The target file path
     * @param data The binary data to write
     * @throws IOException If write operation fails
     */
    public static void writePlayerFile(String filePath, byte[] data) throws IOException {
        ReentrantLock lock = acquireLock(filePath);
        lock.lock();

        try {
            File targetFile = new File(filePath);
            File tempFile = new File(filePath + TEMP_EXTENSION);
            File backupFile = new File(filePath + BACKUP_EXTENSION);

            // Ensure directory exists
            File dir = targetFile.getParentFile();
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Failed to create directory: " + dir.getAbsolutePath());
            }

            // Write to temporary file
            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 FileChannel channel = fos.getChannel()) {

                // Acquire exclusive lock
                FileLock fileLock = channel.tryLock();
                if (fileLock == null) {
                    throw new IOException("Could not acquire file lock: " + tempFile);
                }

                try {
                    fos.write(data);
                    fos.flush();
                    channel.force(true); // Force write to disk
                } finally {
                    fileLock.release();
                }
            }

            // Create backup of existing file
            if (targetFile.exists()) {
                try {
                    Files.copy(targetFile.toPath(), backupFile.toPath(),
                              StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    logger.warn("Failed to create backup for: " + filePath, e);
                }
            }

            // Atomic rename
            Files.move(tempFile.toPath(), targetFile.toPath(),
                      StandardCopyOption.REPLACE_EXISTING,
                      StandardCopyOption.ATOMIC_MOVE);

            logger.debug("Successfully wrote player file: " + filePath);

        } finally {
            lock.unlock();
        }
    }

    /**
     * Read binary data from a player file.
     * Attempts to recover from backup if main file is corrupt.
     *
     * @param filePath The file path to read
     * @return The binary data, or null if file doesn't exist
     * @throws IOException If read operation fails and backup recovery fails
     */
    public static byte[] readPlayerFile(String filePath) throws IOException {
        ReentrantLock lock = acquireLock(filePath);
        lock.lock();

        try {
            File targetFile = new File(filePath);
            File backupFile = new File(filePath + BACKUP_EXTENSION);

            if (!targetFile.exists()) {
                return null; // Player doesn't exist
            }

            try {
                return readFileBytes(targetFile);
            } catch (IOException e) {
                logger.warn("Failed to read player file, attempting backup recovery: " + filePath, e);

                // Try to recover from backup
                if (backupFile.exists()) {
                    try {
                        byte[] backupData = readFileBytes(backupFile);

                        // Restore backup to main file
                        writePlayerFile(filePath, backupData);

                        logger.info("Successfully recovered player file from backup: " + filePath);
                        return backupData;

                    } catch (IOException backupEx) {
                        logger.error("Backup recovery failed for: " + filePath, backupEx);
                        throw new IOException("Both main file and backup are corrupt", backupEx);
                    }
                } else {
                    throw e; // No backup available
                }
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * Read all bytes from a file.
     */
    private static byte[] readFileBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }

            return bos.toByteArray();
        }
    }

    /**
     * Check if a player file exists.
     *
     * @param username The player's username
     * @return True if the player file exists
     */
    public static boolean playerExists(String username) {
        String filePath = getPlayerFilePath(username);
        return new File(filePath).exists();
    }

    /**
     * Check if an authentication file exists.
     *
     * @param username The player's username
     * @return True if the auth file exists
     */
    public static boolean authExists(String username) {
        String filePath = getAuthFilePath(username);
        return new File(filePath).exists();
    }

    /**
     * Delete a player's save file and backup.
     *
     * @param username The player's username
     * @return True if deletion was successful
     */
    public static boolean deletePlayer(String username) {
        String filePath = getPlayerFilePath(username);
        ReentrantLock lock = acquireLock(filePath);
        lock.lock();

        try {
            boolean deleted = true;

            File mainFile = new File(filePath);
            if (mainFile.exists()) {
                deleted &= mainFile.delete();
            }

            File backupFile = new File(filePath + BACKUP_EXTENSION);
            if (backupFile.exists()) {
                deleted &= backupFile.delete();
            }

            if (deleted) {
                logger.info("Deleted player files for: " + username);
            }

            return deleted;

        } finally {
            lock.unlock();
        }
    }

    /**
     * Generate SHA-256 hash of a password with salt.
     *
     * @param password The plaintext password
     * @param salt The salt bytes
     * @return The hashed password
     */
    public static byte[] hashPassword(String password, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(password.getBytes("UTF-8"));
            return digest.digest();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            logger.error("Failed to hash password", e);
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    /**
     * Generate a random salt for password hashing.
     *
     * @return 16 bytes of random salt
     */
    public static byte[] generateSalt() {
        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        return salt;
    }

    /**
     * Validate binary data has the expected magic number.
     *
     * @param data The binary data
     * @param expectedMagic The expected magic number
     * @return True if magic number matches
     */
    public static boolean validateMagicNumber(byte[] data, int expectedMagic) {
        if (data == null || data.length < 4) {
            return false;
        }

        int magic = ((data[0] & 0xFF) << 24) |
                    ((data[1] & 0xFF) << 16) |
                    ((data[2] & 0xFF) << 8) |
                    (data[3] & 0xFF);

        return magic == expectedMagic;
    }
}
