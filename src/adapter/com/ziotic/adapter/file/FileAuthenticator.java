package com.ziotic.adapter.file;

import java.io.*;
import java.util.Arrays;

import org.apache.log4j.Logger;

import com.ziotic.utility.FilePlayerManager;

/**
 * File-based authentication system for player accounts.
 * Replaces the MySQL forummembers table with binary file storage.
 *
 * Auth file format:
 * [Magic: 0xABC12345] (4 bytes)
 * [UserId: int] (4 bytes)
 * [Rights: byte] (1 byte)
 * [Salt: 16 bytes]
 * [PasswordHash: SHA-256 32 bytes]
 *
 * @author Ziotic Development Team
 */
public class FileAuthenticator {

    private static final Logger logger = Logger.getLogger(FileAuthenticator.class);

    // Magic number for auth file validation
    private static final int AUTH_MAGIC = 0xABC12345;

    // Player rights constants (from original database schema)
    public static final byte RIGHTS_PLAYER = 0;
    public static final byte RIGHTS_MODERATOR = 1;
    public static final byte RIGHTS_ADMIN = 2;
    public static final byte RIGHTS_OWNER = 3;

    /**
     * Authentication result container.
     */
    public static class AuthResult {
        public final boolean success;
        public final int userId;
        public final byte rights;
        public final String message;

        public AuthResult(boolean success, int userId, byte rights, String message) {
            this.success = success;
            this.userId = userId;
            this.rights = rights;
            this.message = message;
        }

        public static AuthResult success(int userId, byte rights) {
            return new AuthResult(true, userId, rights, "Authentication successful");
        }

        public static AuthResult failure(String message) {
            return new AuthResult(false, -1, RIGHTS_PLAYER, message);
        }
    }

    /**
     * Authenticate a player with username and password.
     *
     * @param username The player's username
     * @param password The plaintext password
     * @return AuthResult containing authentication status and user info
     */
    public static AuthResult authenticate(String username, String password) {
        try {
            // Check if auth file exists
            if (!FilePlayerManager.authExists(username)) {
                logger.debug("Authentication failed: User does not exist: " + username);
                return AuthResult.failure("Invalid username or password");
            }

            // Read auth file
            String authPath = FilePlayerManager.getAuthFilePath(username);
            byte[] authData = FilePlayerManager.readPlayerFile(authPath);

            if (authData == null || authData.length < 57) {
                logger.error("Corrupt auth file for user: " + username);
                return AuthResult.failure("Authentication error");
            }

            // Parse auth file
            ByteArrayInputStream bis = new ByteArrayInputStream(authData);
            DataInputStream dis = new DataInputStream(bis);

            // Validate magic number
            int magic = dis.readInt();
            if (magic != AUTH_MAGIC) {
                logger.error("Invalid magic number in auth file for user: " + username);
                return AuthResult.failure("Authentication error");
            }

            // Read user data
            int userId = dis.readInt();
            byte rights = dis.readByte();

            // Read salt and stored hash
            byte[] salt = new byte[16];
            dis.readFully(salt);

            byte[] storedHash = new byte[32];
            dis.readFully(storedHash);

            // Hash the provided password with the stored salt
            byte[] providedHash = FilePlayerManager.hashPassword(password, salt);

            // Compare hashes
            if (Arrays.equals(storedHash, providedHash)) {
                logger.info("Authentication successful for user: " + username + " (ID: " + userId + ")");
                return AuthResult.success(userId, rights);
            } else {
                logger.debug("Authentication failed: Invalid password for user: " + username);
                return AuthResult.failure("Invalid username or password");
            }

        } catch (Exception e) {
            logger.error("Authentication error for user: " + username, e);
            return AuthResult.failure("Authentication error");
        }
    }

    /**
     * Register a new player account.
     *
     * @param username The player's username
     * @param password The plaintext password
     * @param userId The user ID (must be unique)
     * @param rights The player's rights level
     * @return True if registration was successful
     */
    public static boolean register(String username, String password, int userId, byte rights) {
        try {
            // Check if user already exists
            if (FilePlayerManager.authExists(username)) {
                logger.warn("Registration failed: User already exists: " + username);
                return false;
            }

            // Generate salt
            byte[] salt = FilePlayerManager.generateSalt();

            // Hash password
            byte[] passwordHash = FilePlayerManager.hashPassword(password, salt);

            // Build auth file
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);

            dos.writeInt(AUTH_MAGIC);
            dos.writeInt(userId);
            dos.writeByte(rights);
            dos.write(salt);
            dos.write(passwordHash);

            dos.flush();
            byte[] authData = bos.toByteArray();

            // Write auth file
            String authPath = FilePlayerManager.getAuthFilePath(username);
            FilePlayerManager.writePlayerFile(authPath, authData);

            logger.info("Registered new user: " + username + " (ID: " + userId + ", Rights: " + rights + ")");
            return true;

        } catch (Exception e) {
            logger.error("Registration failed for user: " + username, e);
            return false;
        }
    }

    /**
     * Change a player's password.
     *
     * @param username The player's username
     * @param oldPassword The current password
     * @param newPassword The new password
     * @return True if password change was successful
     */
    public static boolean changePassword(String username, String oldPassword, String newPassword) {
        try {
            // Authenticate with old password first
            AuthResult auth = authenticate(username, oldPassword);
            if (!auth.success) {
                logger.warn("Password change failed: Authentication failed for user: " + username);
                return false;
            }

            // Generate new salt and hash
            byte[] salt = FilePlayerManager.generateSalt();
            byte[] passwordHash = FilePlayerManager.hashPassword(newPassword, salt);

            // Build updated auth file
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);

            dos.writeInt(AUTH_MAGIC);
            dos.writeInt(auth.userId);
            dos.writeByte(auth.rights);
            dos.write(salt);
            dos.write(passwordHash);

            dos.flush();
            byte[] authData = bos.toByteArray();

            // Write updated auth file
            String authPath = FilePlayerManager.getAuthFilePath(username);
            FilePlayerManager.writePlayerFile(authPath, authData);

            logger.info("Password changed for user: " + username);
            return true;

        } catch (Exception e) {
            logger.error("Password change failed for user: " + username, e);
            return false;
        }
    }

    /**
     * Update a player's rights level.
     *
     * @param username The player's username
     * @param newRights The new rights level
     * @return True if update was successful
     */
    public static boolean updateRights(String username, byte newRights) {
        try {
            // Read existing auth file
            String authPath = FilePlayerManager.getAuthFilePath(username);
            byte[] authData = FilePlayerManager.readPlayerFile(authPath);

            if (authData == null || authData.length < 57) {
                logger.error("Cannot update rights: Corrupt or missing auth file for user: " + username);
                return false;
            }

            // Parse existing auth file
            ByteArrayInputStream bis = new ByteArrayInputStream(authData);
            DataInputStream dis = new DataInputStream(bis);

            int magic = dis.readInt();
            if (magic != AUTH_MAGIC) {
                logger.error("Cannot update rights: Invalid magic number for user: " + username);
                return false;
            }

            int userId = dis.readInt();
            dis.readByte(); // Skip old rights

            byte[] salt = new byte[16];
            dis.readFully(salt);

            byte[] passwordHash = new byte[32];
            dis.readFully(passwordHash);

            // Build updated auth file with new rights
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);

            dos.writeInt(AUTH_MAGIC);
            dos.writeInt(userId);
            dos.writeByte(newRights);
            dos.write(salt);
            dos.write(passwordHash);

            dos.flush();
            byte[] updatedAuthData = bos.toByteArray();

            // Write updated auth file
            FilePlayerManager.writePlayerFile(authPath, updatedAuthData);

            logger.info("Updated rights for user: " + username + " to " + newRights);
            return true;

        } catch (Exception e) {
            logger.error("Rights update failed for user: " + username, e);
            return false;
        }
    }

    /**
     * Get a player's user ID.
     *
     * @param username The player's username
     * @return The user ID, or -1 if not found
     */
    public static int getUserId(String username) {
        try {
            if (!FilePlayerManager.authExists(username)) {
                return -1;
            }

            String authPath = FilePlayerManager.getAuthFilePath(username);
            byte[] authData = FilePlayerManager.readPlayerFile(authPath);

            if (authData == null || authData.length < 9) {
                return -1;
            }

            ByteArrayInputStream bis = new ByteArrayInputStream(authData);
            DataInputStream dis = new DataInputStream(bis);

            int magic = dis.readInt();
            if (magic != AUTH_MAGIC) {
                return -1;
            }

            return dis.readInt();

        } catch (Exception e) {
            logger.error("Failed to get user ID for: " + username, e);
            return -1;
        }
    }

    /**
     * Get a player's rights level.
     *
     * @param username The player's username
     * @return The rights level, or RIGHTS_PLAYER if not found
     */
    public static byte getRights(String username) {
        try {
            if (!FilePlayerManager.authExists(username)) {
                return RIGHTS_PLAYER;
            }

            String authPath = FilePlayerManager.getAuthFilePath(username);
            byte[] authData = FilePlayerManager.readPlayerFile(authPath);

            if (authData == null || authData.length < 10) {
                return RIGHTS_PLAYER;
            }

            ByteArrayInputStream bis = new ByteArrayInputStream(authData);
            DataInputStream dis = new DataInputStream(bis);

            int magic = dis.readInt();
            if (magic != AUTH_MAGIC) {
                return RIGHTS_PLAYER;
            }

            dis.readInt(); // Skip user ID

            return dis.readByte();

        } catch (Exception e) {
            logger.error("Failed to get rights for: " + username, e);
            return RIGHTS_PLAYER;
        }
    }

    /**
     * Delete a player's authentication file.
     *
     * @param username The player's username
     * @return True if deletion was successful
     */
    public static boolean deleteAuth(String username) {
        try {
            String authPath = FilePlayerManager.getAuthFilePath(username);
            File authFile = new File(authPath);

            if (authFile.exists()) {
                boolean deleted = authFile.delete();
                if (deleted) {
                    logger.info("Deleted auth file for user: " + username);
                }
                return deleted;
            }

            return true; // Already doesn't exist

        } catch (Exception e) {
            logger.error("Failed to delete auth for user: " + username, e);
            return false;
        }
    }
}
