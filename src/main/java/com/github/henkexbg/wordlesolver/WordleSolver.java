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
import java.util.LinkedList;
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
 * @author Henrik Bjerne
 *
 */
public class WordleSolver {

	public static final int WORD_LENGTH = 5;

	public static final String DICTIONARY_RESOURCE_LOCATION = "/dictionary.txt";

	public static final int MAX_NR_TURNS = 6;

	/**
	 * This word is always used as a start guess. Gave the lowest average number of
	 * tries (3.80) out of some random testing when benchmarking.
	 */
	public static final List<Character> START_WORD = Arrays.asList(new Character[] { 's', 'a', 'l', 'e', 't' });

	static boolean detailedLog = false;

	/**
	 * Authority that will take guesses and give us results
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
	Set<Character> nonPresentLetters;

	/**
	 * Stores a map of lost letters, with the character as the key for quick lookup
	 */
	Map<Character, LostLetter> lostLettersMap;

	/**
	 * Stores occurrences of each letter
	 */
	Map<Character, LetterOccurrence> occurrencesMap;

	/**
	 * Dictionary containing all possible words. Considered read-only
	 */
	List<List<Character>> originalDictionary;

	/**
	 * Dictionary containing all possible words for one run. This list will be
	 * loaded with all words for each new run, and non-valid words will be removed
	 * during the run
	 */
	List<List<Character>> dictionary;

	public void setWordleAuthority(WordleAuthority wordleAuthority) {
		this.wordleAuthority = wordleAuthority;
	}

	/**
	 * Sets and loads the dictionary that will be used. File format is assumed to be
	 * one word per line. Words of the wrong length or words that contain
	 * non-alphabetic characters will be discarded.
	 * 
	 * @param dictionaryFile Dictionary file.
	 * @throws IOException
	 */
	public void setDictionarySource(InputStream dictionaryStream) throws Exception {
		List<List<Character>> originalDictionary = new ArrayList<>();
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
			originalDictionary.add(Collections.unmodifiableList(oneWord));
		}
		this.originalDictionary = Collections.unmodifiableList(originalDictionary);
		System.out.println(String.format("Added %s words to dictionary", originalDictionary.size()));
		br.close();
	}

	/**
	 * Kicks off a game. Will find the next possible word (given all the information
	 * we have gathered), and test that word towards the {@link WordleAuthority}
	 * 
	 * @throws Exception
	 */
	public RunStat play() throws Exception {
		long startTime = System.currentTimeMillis();
		dictionary = new LinkedList<>(originalDictionary);
		guessedWord = new ArrayList<>(Arrays.asList(new Character[WORD_LENGTH]));
		nonPresentLetters = new HashSet<>();
		lostLettersMap = new HashMap<>();
		occurrencesMap = new HashMap<>();
		int turn = 1;
		boolean success = false;

		for (; turn <= MAX_NR_TURNS; turn++) {
			boolean foundWord = findNextGuess(turn);
			if (!foundWord) {
				System.out.println(String.format("Could not find a word. Exiting after %s turns", turn));
				break;
			}
			boolean result = makeGuess();
			System.out.println(String.format("Word after guess: %s", guessedWord));
			if (result) {
				success = true;
				break;
			}
		}
		RunStat result = new RunStat(success, turn, System.currentTimeMillis() - startTime);
		System.out.println(String.format("DONE. Result:\n%s", result));
		return result;
	}

	/**
	 * Plays a round with each word in the dictionary, then summarizes the data from
	 * all runs and prints it. Requires the {@link WordleAuthoritySim} to be
	 * configured.
	 * 
	 * @throws Exception
	 */
	public void benchmark() throws Exception {
		List<RunStat> runStats = new ArrayList<>();

		if (!(wordleAuthority instanceof WordleAuthoritySim)) {
			throw new IllegalArgumentException("wordleAuthority must be of type WordleAuthoritySim for benchmark!");
		}
		WordleAuthoritySim wordleAuthoritySim = (WordleAuthoritySim) wordleAuthority;

		for (List<Character> oneWord : originalDictionary) {
			wordleAuthoritySim.setActualWord(oneWord);
			runStats.add(play());
		}
		int successCount = (int) runStats.stream().map(rs -> rs.success).filter(success -> success).count();
		long failureCount = runStats.size() - successCount;
		double avgNrTries = runStats.stream().map(rs -> rs.nrTries).collect(Collectors.averagingDouble(x -> x));
		int maxNrTries = runStats.stream().map(rs -> rs.nrTries).max(Integer::compare).get();
		long totalDurationMillis = runStats.stream().map(rs -> rs.durationMillis)
				.collect(Collectors.summingLong(x -> x));
		System.out.println(String.format(
				"Run completed. Successes: %s, failures: %s, average number of tries: %s, max number of tries: %s. Total duration milliseconds: %s",
				successCount, failureCount, avgNrTries, maxNrTries, totalDurationMillis));
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
		if (turn == 1) {
			guessedWord = new ArrayList<>(START_WORD);
			return true;
		}
		Iterator<List<Character>> it = dictionary.iterator();
		while (it.hasNext()) {
			List<Character> oneWord = it.next();
			if (!validateConfirmedMatches(oneWord, guessedWord)) {
				it.remove();
				continue;
			}

			// Check whether the word contains confirmed non-matching characters
			if (oneWord.stream().anyMatch(c -> nonPresentLetters.contains(c))) {
				it.remove();
				continue;
			}

			// Check whether there are lost letters with a discovered quantity
			// and match that towards the word
			if (!validateLetterOccurrences(oneWord)) {
				it.remove();
				continue;
			}

			// Check that all letters without position are in word
			if (!validateLostLetters(oneWord, guessedWord)) {
				it.remove();
				continue;
			}
		}
		if (!dictionary.isEmpty()) {
			guessedWord = new ArrayList<>(selectBestWord(dictionary));
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
	 * @return True if any known occurrences are validated
	 */
	boolean validateLetterOccurrences(List<Character> potentialWord) {
		// Count occurrences in potential word
		Map<Character, Integer> potentialWordOccurrencesPerChar = new HashMap<>();
		for (int i = 0; i < potentialWord.size(); i++) {
			Character c = potentialWord.get(i);
			if (c != null) {
				potentialWordOccurrencesPerChar.merge(c, 1, Integer::sum);
			}
		}
		// Validate potential word occurrences towards our currently known restrictions
		// in occurrencesMap
		for (Entry<Character, LetterOccurrence> oneEntry : occurrencesMap.entrySet()) {
			Integer potentialWordOccurrence = potentialWordOccurrencesPerChar.get(oneEntry.getKey());
			if (potentialWordOccurrence == null || !oneEntry.getValue().occurrenceOk(potentialWordOccurrence)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check whether all lost letters are represented in the potential word and
	 * 
	 * @param potentialWord Word from dictionary
	 * @param guessedWord   Current guessed word with any matching letters populated
	 * @return True if all lost letters are present in potential word
	 */
	boolean validateLostLetters(List<Character> potentialWord, List<Character> guessedWord) {
		for (int i = 0; i < potentialWord.size(); i++) {
			if (guessedWord.get(i) != null) {
				continue;
			}
			Character c = potentialWord.get(i);
			WordleSolver.LostLetter lostLetter = lostLettersMap.get(c);
			if (lostLetter != null) {
				if (!lostLetter.positionOk(i)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Picks the best word out of a list of valid words. "Best" in this case is
	 * simply the word with most unique characters.
	 * 
	 * @param validWords List of valid words
	 * @return Word with most unique characters
	 */
	List<Character> selectBestWord(List<List<Character>> validWords) {
		List<Character> bestWord = null;
		int bestUniqueCount = 0;
		for (List<Character> oneValidWord : validWords) {
			int oneUniqueCount = (int) oneValidWord.stream().distinct().count();
			if (oneUniqueCount > bestUniqueCount) {
				bestUniqueCount = oneUniqueCount;
				bestWord = oneValidWord;
			}
		}
		return bestWord;
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

		// Make guess towards authority and receive answer
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
				// Correct guess on letter and position
				guessedWord.set(i, c);
				occurrencesPerChar.merge(c, 1, Integer::sum);
			} else if (positionGuess.positionResultState.equals(PositionResultState.OTHER_POSITION)) {
				if (lostLettersMap.containsKey(c)) {
					lostLettersMap.get(c).addBlackListedPosition(i);
				} else {
					LostLetter lwp = new LostLetter(c, i);
					lostLettersMap.put(c, lwp);
				}
				occurrencesPerChar.merge(c, 1, Integer::sum);
				guessedWord.set(i, null);
			} else {
				guessedWord.set(i, null);
			}
		}

		// Updates minimum occurrences of letters
		occurrencesPerChar.forEach((k, v) -> {
			LetterOccurrence letterOccurrence = occurrencesMap.get(k);
			if (letterOccurrence == null) {
				letterOccurrence = new LetterOccurrence();
				occurrencesMap.put(k, letterOccurrence);
			}
			letterOccurrence.setMinOccurrencesIfLarger(v);
		});

		// Determines whether we can lock in any exact occurrences of any letter, or
		// determine that a letter is not present
		for (int i = 0; i < result.size(); i++) {
			PositionResult positionGuess = result.get(i);
			Character c = positionGuess.c;
			if (positionGuess.positionResultState.equals(PositionResultState.NO_MATCH)) {
				Integer occurrencesOfChar = occurrencesPerChar.get(c);
				if (occurrencesOfChar != null) {
					occurrencesMap.get(c).setOccurrences(occurrencesOfChar);
				} else {
					nonPresentLetters.add(c);
				}
			}
		}

		// Update lost letters structure as some letters may not be lost anymore.
		updateLostLetters(guessedWord);
		if (detailedLog) {
			System.out.println(String.format("Lost letters %s", lostLettersMap));
			System.out.println(String.format("Occurrences: %s", occurrencesMap));
			System.out.println(String.format("Not present letters: %s", nonPresentLetters));
		}
		return false;
	}

	/**
	 * Updates the {@link #lostLettersMap}, which should be done any time a new
	 * correct letter has been added
	 * 
	 * @param guessedWord Current guessed word with any matching letters populated
	 */
	void updateLostLetters(List<Character> guessedWord) {
		Iterator<Entry<Character, WordleSolver.LostLetter>> it = lostLettersMap.entrySet().iterator();
		while (it.hasNext()) {
			Entry<Character, WordleSolver.LostLetter> entry = it.next();
			Character c = entry.getKey();
			Integer guessedWordOccurrences = countOccurrences(c, guessedWord);
			if (guessedWordOccurrences > 0) {
				LetterOccurrence letterOccurrence = occurrencesMap.get(c);
				if (letterOccurrence == null) {
					it.remove();
				} else if (guessedWordOccurrences.equals(letterOccurrence.occurrences)) {
					it.remove();
				} else if (letterOccurrence.minOccurrences != null
						&& letterOccurrence.minOccurrences.equals(guessedWordOccurrences)) {
					it.remove();
				}
			}
		}
	}

	/**
	 * This class will hold a "lost letter", i.e. a letter that we know exists in
	 * the word, along with black-listed positions where we know the letter will not
	 * be.
	 * 
	 * @author Henrik
	 *
	 */
	class LostLetter {

		Set<Integer> blacklistedPositions = new HashSet<>();

		Character c;

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
			return "LostLetter [blacklistedPositions=" + blacklistedPositions + ", c=" + c + "]";
		}
	}

	/**
	 * Keeps track of the occurrences of a letter. minOccurrences can be gradually
	 * increased as more information is gained. If occurrences is populated it means
	 * we know the exact number of occurrences for a particular character.
	 *
	 */
	class LetterOccurrence {

		Integer minOccurrences;

		Integer occurrences;

		public void setMinOccurrencesIfLarger(Integer minOccurrences) {
			if (this.minOccurrences == null || minOccurrences > this.minOccurrences) {
				this.minOccurrences = minOccurrences;
			}
		}

		public void setOccurrences(Integer occurrences) {
			this.minOccurrences = occurrences;
			this.occurrences = occurrences;
		}

		public boolean occurrenceOk(Integer actualOccurrences) {
			if (occurrences != null) {
				return occurrences.equals(actualOccurrences);
			} else if (minOccurrences != null) {
				return actualOccurrences >= minOccurrences;
			}
			return true;
		}

		@Override
		public String toString() {
			return "LetterOccurrence [minOccurrences=" + minOccurrences + ", occurrences=" + occurrences + "]";
		}
	}

	/**
	 * Holds the stats of one run.
	 * 
	 * @author Henrik
	 *
	 */
	class RunStat {

		boolean success;

		int nrTries;

		long durationMillis;

		public RunStat(boolean success, int nrTries, long durationMillis) {
			super();
			this.success = success;
			this.nrTries = nrTries;
			this.durationMillis = durationMillis;
		}

		@Override
		public String toString() {
			return "RunStat [success=" + success + ", nrTries=" + nrTries + ", durationMillis=" + durationMillis + "]";
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
						+ "non-existing letters, just type a -. Finally, there is also a benchmark mode, that runs through\n"
						+ "all words in the dictionary, and gives statistics on an aggregated level.");

		while (true) {
			System.out.println("[s]imulator, [i]interactive, [b]enchmark or [q]uit?");
			String choice = readLineFromStdIn();
			if ("s".equals(choice)) {
				WordleAuthoritySim was = new WordleAuthoritySim();
				ws.setWordleAuthority(was);
				System.out.println("State word:");
				String word = readLineFromStdIn().toLowerCase();
				List<Character> wordAsChars = new ArrayList<>();
				for (int i = 0; i < word.length(); i++) {
					wordAsChars.add(word.charAt(i));
				}
				was.setActualWord(wordAsChars);
				ws.play();
			} else if ("i".equals(choice)) {
				WordleAuthorityInteractive wi = new WordleAuthorityInteractive();
				ws.setWordleAuthority(wi);
				ws.play();
			} else if ("b".equals(choice)) {
				WordleAuthoritySim was = new WordleAuthoritySim();
				ws.setWordleAuthority(was);
				ws.benchmark();
			} else if ("q".equals(choice)) {
				System.out.println("Exiting");
				return;
			} else {
				System.out.println("Not a valid choice.");
			}
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
