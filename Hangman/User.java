/*
 * A user object that stores information about the user of the hangman game
 * CPSC441 Fall 2019
 * Instructor: Prof. Mea Wang
 *
 * This program was written and edited by:
 * Austin Graham
 * UCID : 30035861
 * Cody Clark
 * UCID : 30010560
 */

import java.net.*;
import java.nio.channels.*;

public class User {

    // Data fields
    private String username;
    private String ip;
    private SocketChannel channel;

    // Constructor
    public User (String username, String ip, SocketChannel channel) {
        this.username = username;
        this.ip = ip;
        this.channel = channel;
    }

    // Getter methods
    public String getUsername() { return this.username; }
    public String getIp() { return this.ip; }
    public SocketChannel getChannel() { return this.channel; }
}
