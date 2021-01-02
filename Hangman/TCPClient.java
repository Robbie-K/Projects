/*
 * A simple TCP client that sends messages to a server and display the message
   from the server.
 * CPSC 441, Fall 2019
 * Instructor: Prof. Mea Wang
 *
 * This program was written and edited by:
 * Austin Graham
 * UCID : 30035861
 * Cody Clark
 * UCID : 30010560
 */


import java.io.*;
import java.net.*;

import javax.lang.model.util.ElementScanner6;

class TCPClient2 {

    public static void main(String args[]) throws Exception
    {
        Boolean debug = false; // Disable to disable debug printouts
        if (args.length != 2)
        {
            System.out.println("Usage: TCPClient <Server IP> <Server Port>");
            System.exit(1);
        }

        // Initialize a client socket connection to the server
        Socket clientSocket = new Socket(args[0], Integer.parseInt(args[1]));

        if (debug) { System.out.println("before Threads running"); }
        // Initialize user input stream
        Runnable parameter0 = new inThread(clientSocket);
        new Thread(parameter0).start();
        Runnable parameter1 = new outThread(clientSocket);
        new Thread(parameter1).start();
    }
}

// This is the thread that gets information from the user through standard input and sends it over TCP to the server
class outThread extends Thread{
    Boolean debug = false; // Disable to disable debug printouts
    private Socket clientSocket;
    public outThread(Socket clientSocket){
        this.clientSocket = clientSocket;
    }
    
    public void run(){
        if (debug) { System.out.println("outThread running"); }
        PrintWriter outBuffer = null;
        BufferedReader inFromUser = null;
        try {
            outBuffer = new PrintWriter(clientSocket.getOutputStream(), true);

            String outLine = "";
            
            inFromUser = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("\nWelcome!");
            System.out.println("Please enter a message to be sent to the server ('/quit' to terminate)");
            System.out.println("Enter /login <username> <password> to login.\n" +
                                   "Or enter /signup <username> <password> to signup for an account.\n");
            System.out.print("Command: ");
            
            while (!outLine.equals("/quit")) {
                // Ask the user for input
                if ((outLine = inFromUser.readLine()) != "") {
                    outLine.replace("\n", "");
                    outBuffer.println(outLine);
                    outBuffer.flush();
                }
            }
        }
        catch(IOException e){ System.out.print(e); System.out.println("outThread exception"); }
    };
}

// This is the thread that reads from the TCP socket and prints the string data to the console
class inThread extends Thread{
    Boolean debug = false; // Disable to disable debug printouts
    private Socket clientSocket;
    public inThread(Socket clientSocket){
        this.clientSocket = clientSocket;
    }

    public void run(){
        if (debug) { System.out.println("inThread running"); }
        String inLine = "";
        BufferedReader inBuffer = null;
        try{
            // Initialize input and an output stream for the connection(s)
            inBuffer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // Loops and asks the user for commands as long as they haven't decided to quit
            while (!inLine.equals("/quit")){
                // Check to see if there's a response from the server
                if ((inLine = inBuffer.readLine()) != ""){
                    if (!inLine.equals("/quit")) { 
                        inLine = inLine.replace("^","\n");
                        System.out.println(inLine + "\n"); // Print out server response
                    }
                    else {
                        System.out.println("Quiting... Thank you for playing!");
                    }
                }
            
            }
        }
        catch( IOException e){ System.out.println(e); System.out.println("inThread exception");}
    };
}
