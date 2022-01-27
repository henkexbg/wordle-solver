package com.github.henkexbg.wordlesolver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class contains the starting point as well as the main client logic. A
 * {@link WordleAuthority} is then configured that will validate and provide
 * feedback on the guesses.
 * 
 * @author Henrik
 *
 */
public class WordleSolver {

	public static final int WORD_LENGTH = 5;

	public static final String DICTIONARY_RESOURCE_LOCATION = "/dictionary.txt";

	public static final int MAX_NR_TURNS = 20;

	/**
	 * Authority that will give us results
	 */
	WordleAuthority wordleAuthority;

	/**
	 * Stores that guessed word. Will always contain the correct matches, other
	 * positions may be null or store the letters of the current attempt
	 */
	List<Character> guessedWord;

	/**
	 * Stores letters we know don't exist in the word
	 */
	Set<Character> nonPresentChars;

	/**
	 * Stores a map of lost letters, with the character as the key for quick lookup
	 */
	Map<Character, LostLetter> charLwMap;

	/**
	 * Dictionary containing all possible words
	 */
	List<List<Character>> dictionary;

	public void setWordleAuthority(WordleAuthority wordleAuthority) {
		this.wordleAuthority = wordleAuthority;
	}

	/**
	 * Sets and loads the dictionary that will be used. File format is assumed to be
	 * one word per line. Words of the wrong length or that contain non-alphabetic
	 * characters will be thrown away.
	 * 
	 * @param dictionaryFile Dictionary file.
	 * @throws IOException
	 */
	public void setDictionarySource(InputStream dictionaryStream) throws Exception {
		dictionary = new ArrayList<>();
		BufferedReader br = new BufferedReader(new InputStreamReader(dictionaryStream));
		String oneLine = null;
		while (true) {
			oneLine = br.readLine();
			if (oneLine == null) {
				break;
			}
			if (oneLine.length() != WORD_LENGTH) {
				continue;
			}
			if (!isStringAlphabetic(oneLine)) {
				continue;
			}

			List<Character> oneWord = new ArrayList<>();
			for (int i = 0; i < oneLine.length(); i++) {
				oneWord.add(oneLine.charAt(i));
			}
			dictionary.add(Collections.unmodifiableList(oneWord));
		}
		System.out.println(String.format("Added %s words to dictionary", dictionary.size()));
		br.close();
	}

	/**
	 * Kicks off a game. Will find the next possible word (given all the information
	 * we have gathered), and test that word towards the {@link WordleAuthority}
	 * 
	 * @throws Exception
	 */
	public void play() throws Exception {
		long startTime = System.currentTimeMillis();
		guessedWord = new ArrayList<>(Arrays.asList(new Character[WORD_LENGTH]));
		nonPresentChars = new HashSet<>();
		charLwMap = new HashMap<>();

		for (int i = 0; i < MAX_NR_TURNS; i++) {
			boolean foundWord = findNextGuess(i);
			if (!foundWord) {
				System.out.println(String.format("Could not find a word. Exiting after %s turns", i + 1));
				return;
			}
			boolean result = makeGuess();
			System.out.println(String.format("Word after guess: %s", guessedWord));
			if (result) {
				System.out.println(String.format("Solved problem in %s turns. Duration: %s milliseconds", ((int) i + 1),
						System.currentTimeMillis() - startTime));
				break;
			}
		}
	}

	/**
	 * Simple helper methd that checks that all letters in a string are alphabetical
	 * characters.
	 * 
	 * @param s String
	 * @return true if only alphabetic characters.
	 */
	boolean isStringAlphabetic(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (!Character.isLetter(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Finds the next word that fulfills all restrictions that have been found from
	 * previous guesses. The guess will be populated in {@link #guessedWord}
	 * 
	 * @param turn Which turn it is
	 * @return true if a word could be found
	 */
	boolean findNextGuess(int turn) {
		for (List<Character> oneWord : dictionary) {

			// For first turn ensure all letters are unique
			if (turn == 0) {
				if (!hasUniqueLetters(oneWord)) {
					continue;
				}
			}

			if (!validateConfirmedMatches(oneWord, guessedWord)) {
				continue;
			}

			// Check whether the word contains confirmed non-matching characters
			if (oneWord.stream().anyMatch(c -> nonPresentChars.contains(c))) {
				continue;
			}

			// Check whether there are lost letters with a discovered quantity
			// and match that towards the word
			if (!validateConfirmedLostLetterOccurrences(oneWord, guessedWord)) {
				continue;
			}

			// Check that all letters without position are in word
			if (!validateConfirmedLostLetters(oneWord, guessedWord)) {
				continue;
			}

			guessedWord = new ArrayList<>(oneWord);
			return true;
		}
		return false;
	}

	/**
	 * Check whether potential word is matching confirmed matches.
	 * 
	 * @param potentialWord Word from dictionary
	 * @param guessedWord   Current guessed word with any matching letters populated
	 * @return True if matching letters are valid
	 */
	boolean validateConfirmedMatches(List<Character> potentialWord, List<Character> guessedWord) {
		for (int i = 0; i < guessedWord.size(); i++) {
			Character c = guessedWord.get(i);
			if (c != null && !c.equals(potentialWord.get(i))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check whether potential word complies with any known letter occurrences, for
	 * example if we know 'e' exists twice the potential word needs to contains two
	 * 'e's.
	 * 
	 * @param potentialWord Word from dictionary
	 * @param guessedWord   Current guessed word with any matching letters populated
	 * @return True if any known occurrences are validated
	 */
	boolean validateConfirmedLostLetterOccurrences(List<Character> potentialWord, List<Character> guessedWord) {
		// Check whether there are letters without position with a discovered quantity
		// and match that towards the word
		Map<Character, Integer> occurrencesPerChar = new HashMap<>();
		for (int i = 0; i < potentialWord.size(); i++) {
			Character c = potentialWord.get(i);
			if (c != null) {
				occurrencesPerChar.merge(c, 1, Integer::sum);
			}
		}
		for (Entry<Character, Integer> oneEntry : occurrencesPerChar.entrySet()) {
			WordleSolver.LostLetter lostLetter = charLwMap.get(oneEntry.getKey());
			if (lostLetter != null && lostLetter.occurrences != null && lostLetter.occurrences != oneEntry.getValue()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check whether all lost letters are represented in the potential word not
	 * counting confirmed matches. Checks only one occurrence - does not validate
	 * quantity.
	 * 
	 * @param potentialWord Word from dictionary
	 * @param guessedWord   Current guessed word with any matching letters populated
	 * @return True if all lost letters are present in potential word
	 */
	boolean validateConfirmedLostLetters(List<Character> potentialWord, List<Character> guessedWord) {
		// Make a copy of lwp map - we will remove elements once used and make sure we
		// end up with empty map
		Map<Character, LostLetter> charLwMapCopy = new HashMap<>(charLwMap);
		for (int i = 0; i < potentialWord.size(); i++) {
			if (guessedWord.get(i) != null) {
				continue;
			}
			Character c = potentialWord.get(i);
			WordleSolver.LostLetter letterWithoutPosition = charLwMapCopy.get(c);
			if (letterWithoutPosition != null) {
				if (letterWithoutPosition.positionOk(i)) {
					charLwMapCopy.remove(c);
				} else {
					return false;
				}
			}
		}
		return charLwMapCopy.isEmpty();
	}

	/**
	 * Simple helper method that counts the occurrence of one letter in a word.
	 * 
	 * @param c    Letter
	 * @param word Word
	 * @return Number of occurrences.
	 */
	int countOccurrences(Character c, List<Character> word) {
		return (int) word.stream().filter(v -> v != null && v.equals(c)).count();
	}

	/**
	 * Checks whether all letters in the word are unique.
	 * 
	 * @param word Word
	 * @return True if all letters are unique.
	 */
	boolean hasUniqueLetters(List<Character> word) {
		Set<Character> wordSet = word.stream().collect(Collectors.toSet());
		return wordSet.size() == word.size();
	}

	/**
	 * Makes one guess towards the {@link WordleAuthority} with the current guessed
	 * word. If the guess is correct, this method returns true. Otherwise, internal
	 * structures will be updated with the information the answer provides, such as
	 * lost letters. These structures will be used the next time a potential word is
	 * chosen.
	 * 
	 * @return True if guess was correct.
	 */
	boolean makeGuess() {

		// This map counts the number of occurrences of each letter. It's required to
		// determine multi-occurring letters, and see if we can manage to determine the
		// occurrence of that letter
		Map<Character, Integer> occurrencesPerChar = new HashMap<>();

		System.out.println(String.format("Next word to guess: %s", guessedWord));
		List<PositionResult> result = wordleAuthority.giveResult(guessedWord);

		if ((int) result.stream().filter(e -> PositionResultState.MATCH.equals(e.positionResultState)).count() == result
				.size()) {
			// We guessed right!
			return true;
		}

		// Go through each character in the result and update accordingly
		for (int i = 0; i < result.size(); i++) {
			PositionResult positionGuess = result.get(i);
			Character c = positionGuess.c;
			if (positionGuess.positionResultState.equals(PositionResultState.MATCH)) {
				// Correct guess, set character and update lost letters as the letter may not be
				// lost anymore :)
				guessedWord.set(i, c);
				updateLostLetters(guessedWord);
				occurrencesPerChar.merge(c, 1, Integer::sum);
			} else if (positionGuess.positionResultState.equals(PositionResultState.OTHER_POSITION)) {
				if (charLwMap.containsKey(c)) {
					charLwMap.get(c).addBlackListedPosition(i);
				} else {
					LostLetter lwp = new LostLetter(c, i);
					charLwMap.put(c, lwp);
				}
				occurrencesPerChar.merge(c, 1, Integer::sum);
				guessedWord.set(i, null);
			} else {
				guessedWord.set(i, null);
			}
		}

		// Determines whether we can lock in any occurrences of any letter
		for (int i = 0; i < result.size(); i++) {
			PositionResult positionGuess = result.get(i);
			Character c = positionGuess.c;
			if (positionGuess.positionResultState.equals(PositionResultState.NO_MATCH)) {
				Integer occurrencesOfChar = occurrencesPerChar.get(c);
				if (occurrencesOfChar != null) {
					if (charLwMap.containsKey(c)) {
						charLwMap.get(c).occurrences = occurrencesOfChar;
					}
				} else {
					nonPresentChars.add(c);
				}
			}
		}
		return false;
	}

	/**
	 * Updates the {@link #charLwMap}, which should be done any time a new correct
	 * letter has been added
	 * 
	 * @param guessedWord Current guessed word with any matching letters populated
	 */
	void updateLostLetters(List<Character> guessedWord) {
		Iterator<Entry<Character, WordleSolver.LostLetter>> it = charLwMap.entrySet().iterator();
		while (it.hasNext()) {
			Entry<Character, WordleSolver.LostLetter> entry = it.next();
			int actualOccurrences = countOccurrences(entry.getKey(), guessedWord);
			if (actualOccurrences > 0) {
				if (entry.getValue().occurrences == null) {
					it.remove();
				} else if (entry.getValue().occurrences == actualOccurrences) {
					it.remove();
				}
			}
		}
	}

	/**
	 * This class will hold a "lost letter", i.e. a letter that we know exists in
	 * the word, but we don't know the position. We store additional data for this
	 * letter, such as black-listed positions where we know the letter will not be,
	 * as well as occurrences on the occasion we manage to determine that.
	 * 
	 * @author Henrik
	 *
	 */
	class LostLetter {

		Set<Integer> blacklistedPositions = new HashSet<>();

		Character c;

		Integer occurrences;

		LostLetter(Character c, int blackListedPosition) {
			this.c = c;
			blacklistedPositions.add(Integer.valueOf(blackListedPosition));
		}

		void addBlackListedPosition(int blackListedPosition) {
			blacklistedPositions.add(Integer.valueOf(blackListedPosition));
		}

		boolean positionOk(int pos) {
			return !blacklistedPositions.contains(Integer.valueOf(pos));
		}

		@Override
		public String toString() {
			return "LetterWithoutPosition [blacklistedPositions=" + blacklistedPositions + ", c=" + c + ", occurrences="
					+ occurrences + "]";
		}

	}

	public static void main(String[] args) throws Exception {
		WordleSolver ws = new WordleSolver();
		ws.setDictionarySource(WordleSolver.class.getResourceAsStream(DICTIONARY_RESOURCE_LOCATION));

		System.out.println(
				"Choose between simulator or interactive mode. Simulator simulates a Wordle backend, and you need\n"
						+ "to provide the correct word that the program should then try to find. For interactive, you will\n"
						+ "be providing the feedback from Wordle according to a special syntax. The syntax is the following.\n"
						+ "The whole response is written on one line separated by a space. For a correct guess, write the\n"
						+ "letter. For a misplaced letter (yellow), type the letter followed immediately by a -. For\n"
						+ "non-existing letters, just type a -.");

		while (true) {
			System.out.println("[s]imulator or [i]interactive?");
			String simOrInteractiveChoice = readLineFromStdIn();
			if ("s".equals(simOrInteractiveChoice)) {
				WordleAuthoritySim was = new WordleAuthoritySim();
				ws.setWordleAuthority(was);
				System.out.println("State word:");
				String word = readLineFromStdIn();
				List<Character> wordAsChars = new ArrayList<>();
				for (int i = 0; i < word.length(); i++) {
					wordAsChars.add(word.charAt(i));
				}
				was.setActualWord(wordAsChars);
			} else if ("i".equals(simOrInteractiveChoice)) {
				System.out.println("");
				WordleAuthorityInteractive wi = new WordleAuthorityInteractive();
				ws.setWordleAuthority(wi);
			} else {
				System.out.println("Not a valid choice. Exiting.");
				return;
			}
			ws.play();
		}

	}

	/**
	 * Helper method for reading a line.
	 * 
	 * @return One line from STDIN.
	 */
	static String readLineFromStdIn() {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(System.in));
			return br.readLine();
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}

}
