/* 
 * A game object for playing Hangman.
 * Records the current state of the game, as well as setting up a game.
 *
 * This program has been written and edited by:
 * Austin Graham
 * UCID : 30035861
 * Cody Clark
 * UCID : 30010560
 */

import java.util.Arrays;

public class Game{

    private String phrase;
    private char[] phraseArray;
    private char[] templateArray;
    private char[] guesses = new char[26];
    // private char[] incorrect;
    // private char[] correct;
    private int countIncorrect = 0;
    private int countCorrect = 0;
    private int count = 0;
    private Boolean debug = false;
    public Game(){
        resetGame();
    }

    public void setWord(String newWord){
        phrase = newWord;
        int length = (phrase.length());
        phraseArray = phrase.toCharArray();
        templateArray = phrase.toCharArray();

        // Set the * array to fill in by guesses
        for(int i=0; i < length; i++) {
            templateArray[i] = ("*").charAt(0);
        }
    }

    // Getter methods
    public char[] getGuesses(){ return(guesses); }
    public String getWord(){ return phrase; }
    public char[] getTemplate(){ return templateArray; }
    public int getnumGuesses(){ return(count); }
    public int getIncorrectGuesses() { return countIncorrect; }

    // The sets of strings that print out the current game state depending on the number of wrong guesses
    public String gameBoard(){
        int board = countIncorrect;
        String gameBoard ="-----------^           ^           ^           ^-----------";
        switch(board){
            case 0: gameBoard = "-----------^           ^           ^           ^-----------";
                break;
            case 1: gameBoard = "-----------^     O     ^           ^           ^-----------";
                break;
            case 2: gameBoard = "-----------^     O     ^     |     ^           ^-----------";
                break;
            case 3: gameBoard = "-----------^     O     ^    /|     ^           ^-----------";
                break;
            case 4: gameBoard = "-----------^     O     ^    /|\\    ^           ^-----------";
                break;
            case 5: gameBoard = "-----------^     O     ^    /|\\    ^    /      ^-----------";
                break;
            case 6: gameBoard = "-----------^     O     ^    /|\\    ^    / \\    ^-----------";
                break;
        }
        return gameBoard;
    }



    //Check if a letter has been guessed already - found on https://www.geeksforgeeks.org/check-if-a-value-is-present-in-an-array-in-java/
    public boolean check(char[] arr, char toCheckValue) {
        boolean test = false;
        for (char element : arr) {
            if (Character.compare(element, toCheckValue) == 0) {
                test = true;
                break;
            }
        }
        return test;
    }

    // Register the letter guessed as having been guessed
    // If the program arrives at this point the character hasn't been guessed yet
    public boolean guessLetter(char a){
        Boolean inWord = false;
        guesses[count] = a;
    
        if(phrase.indexOf(a) >= 0) {
            replace(a);
            inWord = true;
            ++countCorrect;
        }
        else { ++countIncorrect; }
        count++;
        return inWord;
    }

    // Finds all of the instances of the guessed letter in the phrase
    // Then it updates the * template
    private void replace(char a){
        int[] x = findIndex(phraseArray, a);
    
        for (int i= 0; i < x.length; i++) {
            if (debug) { System.out.println(x[i]); }
            templateArray[x[i]] = a;
        }
    }

    // Finds all of the indexes of the guessed letter in the array
    public int[] findIndex(char[] arr, char t){
        // find length of array
        int len = arr.length;
        int i = 0;
        int j = 0;
        int[] indices = new int[len];

        // traverse in the array
        while (i < len) {
            // if the i-th element is t
            // then return the index
            if (Character.compare(arr[i], t) == 0) {
                indices[j]=i;
                ++j;
                ++i;
            }
            else { ++i; }
        }
        indices = Arrays.copyOf(indices, j);
        return indices;
    }

    // Resets the game state
    public void resetGame(){
        setWord("Default");
        this.countIncorrect = 0;
        this.countCorrect = 0;
        this.count = 0;
        guesses = [];
    }
}
