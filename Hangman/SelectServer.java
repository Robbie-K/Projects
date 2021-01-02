/*
 * A TCP game of hangman, where multiple users can play everyone's favorite deadly word guessing game!
 * CPSC 441 Fall 2019
 * Instructor: Prof. Mea Wang
 *
 * This program was further manipulated, edited, and added to by:
 * Austin Graham
 * UCID : 30035861
 * Cody Clark
 * UCID : 30010560
 * With help from http://www.java2s.com/Code/Java/Network-Protocol/HandlesTCPandUDPconnectionsandprovidesexceptionhandlinganderrorlogging.htm
 * And other java tutorials and manual pages
 */

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.lang.StringBuilder;

public class SelectServer {
    public static int BUFFERSIZE = 32;

    public static void main(String args[]) throws Exception
    {
        Boolean debug = false;

        Game newGame = new Game();

        // Check input fields
        if (args.length != 1)
        {
            System.out.println("Usage: TCPServer <Listening Port>");
            System.exit(1);
        }

        List<User> users = new ArrayList<User>();
        List<User> userOrder = new ArrayList<User>();
        List<SocketChannel> channels = new ArrayList<SocketChannel>();
        // Queue<User> players = new LinkedList<>();
        List<String> banned = new ArrayList<String>();

        // Initialize buffers and coders for channel receive and send
        String check = "";
        String host = "";
        String name = "";
        String inLine = "";
        String outLine = "";
        SocketChannel curUser;
        Charset charset = Charset.forName( "us-ascii" );
        CharsetDecoder decoder = charset.newDecoder();
        CharsetEncoder encoder = charset.newEncoder();

        ByteBuffer inBuffer = null;
        ByteBuffer outBuffer = null;
        CharBuffer cBuffer = null;
        CharBuffer cBuffer2 = null;
        int bytesSent, bytesRecv;     // number of bytes sent or received

        // The port we'll listen on
        SocketAddress localport = new InetSocketAddress(Integer.parseInt(args[0]));

        // Initialize the selector
        Selector selector = Selector.open();

        // Create a server channel and make it non-blocking
        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.configureBlocking(false);

        // Get the port number and bind the socket
        InetSocketAddress isa = new InetSocketAddress(Integer.parseInt(args[0]));
        channel.socket().bind(isa);

        // Register that the server selector is interested in connection requests
        channel.register(selector, SelectionKey.OP_ACCEPT);

        //Byte buffer to receive emtpy datagrams with.
        ByteBuffer receiveBuffer = ByteBuffer.allocate(1000);
        cBuffer = CharBuffer.allocate(1000);
        cBuffer2 = CharBuffer.allocate(1000);

        // Wait for something to happen among all registered sockets
        try {
            String text = "Packet Received\n";
            String outbound = "";
            ByteBuffer response = encoder.encode(CharBuffer.wrap(text));
            boolean terminated = false;
            while (!terminated)
            {
              // System.out.print("while looping \n");
                if (selector.select(500) < 0)
                {
                    System.out.println("select() failed");
                    System.exit(1);
                }

                // Get set of ready sockets
                Set readyKeys = selector.selectedKeys();
                Iterator readyItor = readyKeys.iterator();

                // Walk through the ready set
                while (readyItor.hasNext())
                {
                    if (debug) { System.out.print("while readyItor-ing \n"); }
                    // Get key from set
                    SelectionKey key = (SelectionKey)readyItor.next();

                    // Remove current entry
                    readyItor.remove();

                    // Get the channel associated with the key
                    Channel c = (Channel) key.channel();

                    // Accept new TCP connections, if any
                    if (key.isAcceptable() && c == channel)
                    {
                        SocketChannel cchannel = ((ServerSocketChannel)key.channel()).accept();
                        cchannel.configureBlocking(false);
                        System.out.println("Accepted connection from " + cchannel.socket().toString());

                        // Register the new connection for read operation
                        cchannel.register(selector, SelectionKey.OP_READ);
                    }

                    // Read the channel
                    else {
                        SocketChannel cchannel = (SocketChannel)key.channel();
                        
                        if (key.isReadable())
                        {
                            if (debug) { System.out.print("Reading channel\n"); }
                            Socket socket = cchannel.socket();

                            // Open input and output streams
                            inBuffer = ByteBuffer.allocateDirect(BUFFERSIZE);
                            cBuffer = CharBuffer.allocate(BUFFERSIZE);

                            // Read from socket
                            bytesRecv = cchannel.read(inBuffer);

                            // If there was a read error due to a disconnection or anything like that...
                            if (bytesRecv <= 0) {
                                System.out.println("read() error or connection was closed");
                                
                                // Remove the user from the game
                                for (User u : users) {
                                    if (u.getChannel() == cchannel) {
                                        users.remove(u);
                                        if (userOrder.getIndex(u) == 0) {
                                            outBuffer = encoder.encode(CharBuffer.wrap("It is now your turn to guess a letter!\n"));
                                            userOrder.get(1).getChannel().write(outBuffer);
                                            outBuffer.clear();
                                        }
                                        userOrder.remove(u);
                                        channels.remove(cchannel);

                                        // If they were the host, transfer host
                                        if (host.equals(u.getUsername())) { 
                                            if (users.size() >= 1) {
                                                host = users.get(0).getUsername();
                                                outLine = "The current host has left the game. You are now the host.\n";
                                                outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                                                users.get(0).getChannel().write(outBuffer);
                                                outBuffer.clear();
                                            } 
                                        }
                                        break;
                                    }
                                }

                                key.cancel();  // deregister the socket and move onto the next waiting connection
                                continue;
                            }

                            // Find the socket user's name so that it can be used later
                            name = "";
                            for (User user : users ) {
                                curUser = user.getChannel();
                                if (curUser == cchannel) {
                                    name = user.getUsername();
                                    break;
                                }
                            }
                        }

                        inBuffer.flip();      // make buffer available
                        decoder.decode(inBuffer, cBuffer, false);
                        cBuffer.flip();
                        inLine = cBuffer.toString();
                        if (name == "") {
                            name = cchannel.socket().toString();
                        }

                        // What to do when the user tries to sign up with an account
                        if (inLine.startsWith("/signup")) {
                            inLine = inLine.replace("/signup ","");

                            String user = inLine.split(" ")[0];

                            // Check if an account with that name has already been created
                            if (checkUsername(user)) {
                                if (storeInfo(inLine)) {
                                    outLine = "Account creation successful. You may now login. \n";
                                } 
                                else {
                                    outLine = "An error occured when storing user info. Please try again.\n";
                                }
                            }
                            else {
                                outLine = "Username already taken, please chose another.\n";
                            }
                             
                            // Send result to client
                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                            cchannel.write(outBuffer);
                            outBuffer.clear();
                        }

                        // A user attempts to log in
                        else if (inLine.startsWith("/login") && !channels.contains(cchannel) ) {
                            
                            // Check to make sure we're not over capacity
                            if (users.size() == 4 && !channels.contains(cchannel)) {
                                outLine = "Sorry, maximum capacity of 4 users has been reached, please try again later!^" +
                                          "Enter /quit to terminate or try logging in again.\n";
                                // Send the result of this operation to the client program
                                outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                                cchannel.write(outBuffer);
                                outBuffer.clear();
                                key.cancel();
                                continue;
                            }

                            inLine = inLine.replace("/login ","");
                            inLine = inLine.replace("\r","");
                            inLine = inLine.replace("\n","");

                            // Check to see if their details are correct or they're banned
                            boolean correct = verifyDetails(inLine);
                            if (banned.contains(inLine.split(" ")[0])) {
                                outLine = "You have been temporarily banned. Please try again later.\n";
                            }

                            // If they are, add them to the list of users and add their channel to the list of channels
                            else if (correct) {
                                String ip = cchannel.getRemoteAddress().toString();
                                String username = inLine.split(" ")[0];
                                Boolean alreadyLogged = false;
                                
                                // Check if they're already logged in
                                for (User user : users ) {
                                    check = user.getUsername();
                                    System.out.println(check + " " + username + " ==?" + (check == username));
                                    if (check.equals(username)) {
                                        alreadyLogged = true;
                                        outLine = "You are already logged in. Please try a different co mmand or /logout first.\n";
                                        break;
                                    }
                                }

                                // If they're not logged in, create a user object and add their information to the list of current users
                                if (!alreadyLogged) {
                                    User userObj  = new User(inLine.split(" ")[0], ip, cchannel);
                                    users.add(userObj);
                                    userOrder.add(userObj);
                                    if (users.size() == 1) {
                                        host = username;
                                    }
                                    channels.add(cchannel);
                                    System.out.println("User " + username + " has logged in.");
                                    System.out.println("Current list of connections: " + channels.toString());
                                    System.out.println("Current list of users: " + users.toString());
                                    outLine = ("^ -------------------------------------------------------------------------------------------------" +
                                    "^Game Commands:" +
                                    "^1. /setWord <word> to set the game word (The word is case sensitive)" +
                                    "^2. /gl <letter> to guess a letter" +
                                    "^3. /guesses to get the letters guessed so far" +
                                    "^4. /gameState prints out the word with correct letters guessed" +
                                    "^5. /board will display the game board (The hangman)" +
                                    "^6. /users prints out all of the online users" +
                                    "^7. /kick <username> (HOST ONLY) kicks the specified player" +
                                    "^8. /chat <message> allows you to communicate with online users" +
                                    "^9. /pm <username> <message> lets you send a message to only the specified user" +
                                    "^10. /help will list all of these commands once more" +
                                    "^11. /getHost will inform you of the current host" + 
                                    "^12. /logout will log you out of the game" +
                                    "^13. /quit will stop the client" +
                                    "^-------------------------------------------------------------------------------------------------^" +
                                    "Note: if you don't recevie anything back when you enter a command,^" +
                                    "you entered the command wrong, double check spelling.\n");
                                }
                                else {
                                    outLine = "Unsuccessful login.\n";
                                }
                            }
                            else {
                                outLine = "Unsuccessful login.\n";
                            }

                            // Send the result of this operation to the client program
                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                            cchannel.write(outBuffer);
                            outBuffer.clear();

                            // If the user is the first (or only one) to join the game, then this lets them know they're the host
                            for (User u : users) {
                                if (u.getChannel() == cchannel && u.getUsername().equals(host)) {
                                    outLine = "You are the new host! Please feel free to use the /setWord command to set the word to guess.\n";
                                    outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                                    cchannel.write(outBuffer);
                                    outBuffer.clear();
                                    break;
                                }
                            }
                            continue;
                        }

                        // Remove the player from all active lists and close their socket
                        else if (inLine.startsWith("/quit")) {
                            for (User u : users) {
                                if (u.getChannel() == cchannel) {
                                    System.out.println("The following user has quit: " + u.getUsername());
                                    users.remove(u);
                                    userOrder.remove(u);
                                    channels.remove(cchannel);

                                    // If they were the host, transfer host
                                    if (host.equals(u.getUsername())) { 
                                        if (users.size() >= 1) {
                                            host = users.get(0).getUsername();
                                            outLine = "The current host has left the game. You are now the host.\n";
                                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                                            users.get(0).getChannel().write(outBuffer);
                                            outBuffer.clear();
                                        } 
                                    }
                                break;
                                }
                            }

                            outLine = "/quit";
                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                            cchannel.write(outBuffer);
                            outBuffer.clear();

                            key.cancel();  // deregister the socket and move onto the next waiting connection
                            try { 
                                cchannel.close();
                            }
                            catch (IOException e) {
                                System.out.println(e);
                            }
                            continue;
                        }

                        // If the user has not yet logged in but is still trying to use commands, request that they log in or make an account
                        else if(!channels.contains(cchannel)) {
                            name = cchannel.socket().toString();
                            outLine = "^You are not yet logged in. Please login or register an account.^" + 
                                      "Use the command /login <username> <password> to login.^" +
                                      "Or use the command /signup <username> <password> to create an account.\n";
                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                            cchannel.write(outBuffer);
                            outBuffer.clear();
                            continue;
                        }

                        // Sets the word for the players to guess (Default is 'Default')
                        // Of the format: /setWord <word>
                        if (inLine.startsWith("/setWord")){
                            // Allow the user to set the new word if they're the host
                            for(User u : users) {
                                if (u.getChannel() == cchannel && u.getUsername().equals(host)) {
                                    try {
                                        String[] splitCommand = inLine.split("\\s+");
                                        if (splitCommand.length != 2) { // Make sure they're only setting one word
                                            throw new IllegalArgumentException();
                                        }
                                        newGame.setWord(splitCommand[1]);
                                        outLine = "New word successfully set.\n";
                                        System.out.println("New word set: " + splitCommand[1]);
                                        break;
                                    }
                                    catch (IllegalArgumentException e) {
                                        outLine = "Improper usage of /setWord. Please try again. Usage: /setWord <word>";
                                        break;
                                    }
                                }
                                else if (u.getChannel() == cchannel && !u.getUsername().equals(host)) {
                                    outLine = "Unfortunately you are not the host and cannot set a new word.^" +
                                              "The current host is: " + host + "\n";
                                }
                            }                          
                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                            cchannel.write(outBuffer);
                            outBuffer.clear();
                        }

                        // Used by a player in order to guess a letter
                        // Of the format: /gl <letter>
                        else if (inLine.startsWith("/gl")) {

                            // Check to see if it's the users turn to guess
                            if (userOrder.get(0).getChannel() != cchannel) {
                                outLine = "Unfortunately it's not your turn to guess a letter. Please wait.\n";
                                outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                                cchannel.write(outBuffer);
                                outBuffer.clear();
                                continue;
                            }

                            char l;
                            String myguess = "";
                            
                            // Make sure that a relatively proper guess is made
                            try {
                                String[] splitCommand = inLine.split("\\s+");
                                // Make sure there's only one argument and it's only 1 letter
                                
                                if (splitCommand.length != 2) {
                                    throw new IllegalArgumentException(); // Checks for improper arguments
                                }
                                
                                myguess = splitCommand[1];

                                if (myguess.length() != 1) {
                                    throw new IllegalArgumentException();
                                }
                                l = myguess.charAt(0);
                            }
                            catch (IllegalArgumentException e) {
                                outLine = "Improper usage for /gl, please try again. Usage: /gl <letter>";
                                outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                                cchannel.write(outBuffer);
                                outBuffer.clear();
                                continue;
                            }
                            
                            // Check if that letter has already been guessed
                            if (newGame.check(newGame.getGuesses(), l)) {
                                outLine = "New letter already guessed. Please try again.\n";
                                outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                                cchannel.write(outBuffer);
                                outBuffer.clear();
                            }
                            else {
                                // If the letter guessed is a correct guess...
                                if(newGame.guessLetter(l)) {
                                    outLine = "New letter successfully guessed!\n";
                                    outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                                    cchannel.write(outBuffer);
                                    outBuffer.clear();

                                    // Check if the game has been completed
                                    // Can be checked by seeing if the template has any * left
                                    if (!(new String(newGame.getTemplate()).contains("*"))) {
                                        newGame.resetGame(); // If true, reset the game and send a message to the user
                                        System.out.println("GAME WON");
                                        outLine = "*****GAME WON*****\n";

                                        // Choose the next person to be the host
                                        for (User u : users) {
                                            if (u.getUsername() == host) {
                                                userOrder.add(userOrder.remove(userOrder.indexOf(u))); // Put the host to the back of the user order list
                                                break;
                                            }
                                        }

                                        host = userOrder.get(0).getUsername(); // With the previous host at the back of the list, this now selects whoever is at the front

                                        // Send the game won message to every player
                                        for (User u : users) {
                                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                                            u.getChannel().write(outBuffer);
                                            outBuffer.clear();

                                            // Let the new host know that they're the host
                                            if (u.getUsername().equals(host)) {
                                                outBuffer = encoder.encode(CharBuffer.wrap("You are the new host! Please feel free to use the /setWord command to set the word to guess.\n"));
                                                u.getChannel().write(outBuffer);
                                                outBuffer.clear();
                                            }
                                        }
                                    }
                                }
                                // If the letter guessed is an incorrect guess then the turn passes
                                else {
                                    outLine = "New letter incorrectly guessed.\n";
                                    outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                                    cchannel.write(outBuffer);
                                    outBuffer.clear();

                                    if(newGame.getIncorrectGuesses() >= 6) {
                                        newGame.resetGame(); // If true, reset the game and send a message to the user
                                        System.out.println("GAME LOST");
                                        outLine = "*****GAME LOST*****\n";

                                        // Choose the next person to be the host
                                        for (User u : users) {
                                            if (u.getUsername() == host) {
                                                userOrder.add(userOrder.remove(userOrder.indexOf(u))); // Put the host to the back of the user order list
                                                break;
                                            }
                                        }

                                        host = userOrder.get(0).getUsername(); // With the previous host at the back of the list, this now selects whoever is at the front

                                        // Send the game won message to every player
                                        for (User u : users) {
                                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                                            u.getChannel().write(outBuffer);
                                            outBuffer.clear();

                                            // Let the new host know that they're the host
                                            if (u.getUsername().equals(host)) {
                                                outBuffer = encoder.encode(CharBuffer.wrap("You are the new host! Please feel free to use the /setWord command to set the word to guess.\n"));
                                                u.getChannel().write(outBuffer);
                                                outBuffer.clear();
                                            }
                                        }
                                    }

                                    // Takes the user at the head of the queue and moves them to the back
                                    // Then inform the next person who needs to guess
                                    userOrder.add(userOrder.remove(0));
                                    outBuffer = encoder.encode(CharBuffer.wrap("It is now your turn to guess a letter!\n"));
                                    userOrder.get(0).getChannel().write(outBuffer);
                                    outBuffer.clear();
                                }
                            }
                        }

                        // List to the user the current guesses
                        else if (inLine.startsWith("/guesses")) {
                            String guesses = new String(newGame.getGuesses());
                            outLine = "Current guesses: " + guesses + "\n";
                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                            cchannel.write(outBuffer);
                            outBuffer.clear();
                        }

                        // Prints the current row of correctly guessed letters in their places
                        else if (inLine.startsWith("/gameState")) {
                            String currProgress = new String(newGame.getTemplate());
                            outLine = "Current word to guess: " + currProgress + "\n";
                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                            cchannel.write(outBuffer);
                            outBuffer.clear();
                        }

                        // Prints out the current board state
                        else if (inLine.startsWith("/board")) {
                            String gameBoard = new String(newGame.gameBoard());
                            outLine = gameBoard + "\n";
                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                            cchannel.write(outBuffer);
                            outBuffer.clear();
                        }

                        // Prints out the current list of active users
                        else if (inLine.startsWith("/users")) {
                            outLine = "";
                            for (User a : users) {
                                outLine = outLine + a.getUsername() + "^";
                            }
                            outLine = outLine + "\n";
                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                            cchannel.write(outBuffer);
                            outBuffer.clear();
                        }

                        // Bans a player, only usable by the host
                        else if (inLine.startsWith("/kick") && name.equals(host) ) {
                            
                            try{
                                String kick = inLine.split(" ")[1];
                                kick = kick.replace("\n","");
                                kick = kick.replace("\r","");

                                // Make sure the host isn't kicking themselves
                                if (kick.equals(host)) {
                                    outLine = "You cannot kick yourself out of a game!\n";
                                    outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                                    cchannel.write(outBuffer);
                                    outBuffer.clear();
                                    break;
                                }

                                String playerBan = "";

                                for (int i=0; i < users.size(); i++) {
                                    playerBan = users.get(i).getUsername();
                                    curUser = users.get(i).getChannel();
                                    if (playerBan.equals(kick)) {
                                        outLine = "Sorry you have been kicked. Come back later\n";
                                        outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                                        curUser.write(outBuffer);
                                        outBuffer.clear();
                                        channels.remove(i);
                                        users.remove(i);
                                        outLine = "Kick was successful\n";
                                        break;
                                    }
                                    else if (i == users.size()) {
                                        outLine = "Kick was unsuccessful\n";
                                    }
                                }
                            }
                            catch (ArrayIndexOutOfBoundsException e) {
                                outLine = "You must specify a player to kick. Usage: /kick <name>";
                            }
                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                            cchannel.write(outBuffer);
                            outBuffer.clear();
                        }

                        // Sends a message to all active users
                        else if (inLine.startsWith("/chat")) {
                          
                            for (User b: users ) {
                                curUser = b.getChannel();
                                inLine = inLine.replace("/chat ","");
                                outLine = name + ": " + inLine;

                                outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                                curUser.write(outBuffer);
                                outBuffer.clear();
                            }
                        }

                        // Sends a private message to a user
                        else if (inLine.startsWith("/pm")) {
                            String pm = inLine.split(" ")[1];
                            inLine = inLine.split(" ")[2];
                            for (User b: users ) {
                                if (b.getUsername().equals(pm)) {
                                    curUser = b.getChannel();
                                    inLine = inLine.replace("/chat ","");
                                    outLine = "pm:" + name + ": " + inLine;
                                    outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                                    curUser.write(outBuffer);
                                    outBuffer.clear();
                                }
                            }
                        }

                        // Log the current player out
                        else if (inLine.startsWith("/logout")) {
                            for (User u : users) {
                                if (u.getChannel() == cchannel) {
                                    users.remove(u);
                                    if (userOrder.getIndex(u) == 0) {
                                        outBuffer = encoder.encode(CharBuffer.wrap("It is now your turn to guess a letter!\n"));
                                        userOrder.get(1).getChannel().write(outBuffer);
                                        outBuffer.clear();
                                    }
                                    userOrder.remove(u);
                                    channels.remove(cchannel);

                                    // If they were the host, transfer host
                                    if (host.equals(u.getUsername())) { 
                                        if (users.size() >= 1) {
                                            host = users.get(0).getUsername();
                                            outLine = "The current host has left the game. You are now the host.\n";
                                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                                            users.get(0).getChannel().write(outBuffer);
                                            outBuffer.clear();
                                        } 
                                    }
                                break;
                                }
                            }
                            outLine = "You have successfully logged out.\n";
                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                            cchannel.write(outBuffer);
                            outBuffer.clear();
                        }

                        else if (inLine.startsWith("/getHost")) {
                            outLine = "The current host is: " + host + "\n";
                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                            users.get(0).getChannel().write(outBuffer);
                            outBuffer.clear();
                        }
                        
                        // If the user enters /help, list all of the commands available to them
                        else if (inLine.startsWith("/help")) {
                            outLine = ("^ -------------------------------------------------------------------------------------------------" +
                            "^Game Commands:" +
                            "^1. /setWord <word> to set the game word  (The word is case sensitive)" +
                            "^2. /gl <letter> to guess a letter" +
                            "^3. /guesses to get the letters guessed so far" +
                            "^4. /gameState prints out the word with correct letters guessed" +
                            "^5. /board will display the game board (The hangman)" +
                            "^6. /users prints out all of the online users" +
                            "^7. /kick <username> (HOST ONLY) kicks the specified player" +
                            "^8. /chat <message> allows you to communicate with online users" +
                            "^9. /pm <username> <message> lets you send a message to only the specified user" +
                            "^10. /help will list all of these commands once more" +
                            "^11. /getHost will inform you of the current host" + 
                            "^12. /logout will log you out of the game" +
                            "^13. /quit will stop the client" +
                            "^-------------------------------------------------------------------------------------------------^" +
                            "Note: if you don't recevie anything back when you enter a command,^" +
                            "you entered the command wrong, double check spelling.\n");
                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                            cchannel.write(outBuffer);
                            outBuffer.clear();
                        }

                        // Send a message to the user in case a command isn't recognized
                        else {
                            outLine = "Unknown command, be careful with your spelling.\n";
                            outBuffer = encoder.encode(CharBuffer.wrap(outLine));
                            cchannel.write(outBuffer);
                            outBuffer.clear();
                        }
                    }
                }
            } // end of while (readyItor.hasNext())
        } // end of while (!terminated)

        catch (IOException e) {
            System.out.println(e);
        }

        // close all connections
        Set keys = selector.keys();
        Iterator itr = keys.iterator();
        while (itr.hasNext())
        {
            SelectionKey key = (SelectionKey)itr.next();
            //itr.remove();
            if (key.isAcceptable()) {
                ((ServerSocketChannel)key.channel()).socket().close();
            }
            else if (key.isValid()) {
                Channel c = (Channel) key.channel();
                if(c==channel) {
                    ((SocketChannel)key.channel()).socket().close();
                }
            }
        }
    }

    // Checks whether or not a username is in our files (A user has created an account)
    private static boolean checkUsername(String user) {
        Boolean debug = false;
        String line = "";
        String curDir = System.getProperty("user.dir");
        Boolean result = true;
        if (debug) { System.out.println(curDir); }  //for testing
        File file =  new File(curDir + File.separator + "Accounts.txt");
        try {
            Scanner reader = new Scanner(file);

            while (reader.hasNextLine()) {
                line = reader.nextLine();

                if (line.split(" ")[0].equals(user)) {
                    result = false;
                    break;
                }
            }

            reader.close();
        } 
        catch (FileNotFoundException e) {
            System.out.println(e);
            result = false;
        }
        return result;
    }

    // Used to enter a new account into our accounts file
    private static boolean storeInfo(String info) {
        // String curDir = System.getProperty("user.dir");
        Boolean result = false;

        try {
            File file = new File("Accounts.txt");
            FileWriter writer = new FileWriter(file, true);
            writer.write(info);
            writer.close();
            result = true;
        }
        catch (IOException e) {
            System.out.println(e);
        }
        return result;
    }

    // Used to verify login details
    private static boolean verifyDetails(String info) {
        Boolean result = false;  
      
        if (info == "") { return result;}

        String line = "";
        String curDir = System.getProperty("user.dir");

        try {
            File file =  new File(curDir +File.separator + "Accounts.txt");
            FileReader fr = new FileReader(file);
            BufferedReader br = new BufferedReader(fr);
            while ( (line = br.readLine()) != null) {
                line.replace("\r","");
                // line.replace("\n","");
                // line.replace(" ", "");
                // System.out.println();
                // System.out.println(line.contains(info) + ", " + info.contains(line));
                if (info.equals(line)) {
                    result = true;
                    break;
                }
            }
            br.close();
        }
        catch (IOException e) {
            // fr.close();
            System.out.println(e);
            result = false;
        }
        return result;
    }
}
