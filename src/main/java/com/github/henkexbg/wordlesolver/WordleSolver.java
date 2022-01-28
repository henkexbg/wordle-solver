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
 * @author Henrik Bjerne
 *
 */
public class WordleSolver {

	public static final int WORD_LENGTH = 5;

	public static final String DICTIONARY_RESOURCE_LOCATION = "/dictionary.txt";

	public static final int MAX_NR_TURNS = 20;

	/**
	 * This word is always used as a start guess. Gave the lowest average number of
	 * tries (4.0) out of some random testing when benchmarking.
	 */
	public static final List<Character> START_WORD = Arrays.asList(new Character[] { 'r', 'a', 'n', 'c', 'e' });

	private static boolean detailedLog = false;

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
	Map<Character, LostLetter> lostLetterMap;

	/**
	 * Stores occurrences of each letter
	 */
	Map<Character, LetterOccurrence> occurrencesMap;

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
	public RunStat play() throws Exception {
		long startTime = System.currentTimeMillis();
		guessedWord = new ArrayList<>(Arrays.asList(new Character[WORD_LENGTH]));
		nonPresentChars = new HashSet<>();
		lostLetterMap = new HashMap<>();
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

		for (List<Character> oneWord : dictionary) {
			wordleAuthoritySim.setActualWord(oneWord);
			runStats.add(play());
		}
		int successCount = (int) runStats.stream().map(rs -> rs.success).filter(success -> success).count();
		long failureCount = runStats.size() - successCount;
		double avgNrTries = runStats.stream().map(rs -> rs.nrTries).collect(Collectors.averagingDouble(x -> x));
		long totalDurationMillis = runStats.stream().map(rs -> rs.durationMillis)
				.collect(Collectors.summingLong(x -> x));
		System.out.println(String.format(
				"Run completed. Successes: %s, failures: %s, average number of tries: %s. Total duration milliseconds: %s",
				successCount, failureCount, avgNrTries, totalDurationMillis));
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

		for (List<Character> oneWord : dictionary) {
			if (!validateConfirmedMatches(oneWord, guessedWord)) {
				continue;
			}

			// Check whether the word contains confirmed non-matching characters
			if (oneWord.stream().anyMatch(c -> nonPresentChars.contains(c))) {
				continue;
			}

			// Check whether there are lost letters with a discovered quantity
			// and match that towards the word
			if (!validateConfirmedLostLetterOccurrences(oneWord)) {
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
	boolean validateConfirmedLostLetterOccurrences(List<Character> potentialWord) {
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
		Map<Character, LostLetter> lostLetterMapCopy = new HashMap<>(lostLetterMap);
		for (int i = 0; i < potentialWord.size(); i++) {
			if (guessedWord.get(i) != null) {
				continue;
			}
			Character c = potentialWord.get(i);
			WordleSolver.LostLetter letterWithoutPosition = lostLetterMapCopy.get(c);
			if (letterWithoutPosition != null) {
				if (letterWithoutPosition.positionOk(i)) {
					lostLetterMapCopy.remove(c);
				} else {
					return false;
				}
			}
		}
		return lostLetterMapCopy.isEmpty();
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
				occurrencesPerChar.merge(c, 1, Integer::sum);
			} else if (positionGuess.positionResultState.equals(PositionResultState.OTHER_POSITION)) {
				if (lostLetterMap.containsKey(c)) {
					lostLetterMap.get(c).addBlackListedPosition(i);
				} else {
					LostLetter lwp = new LostLetter(c, i);
					lostLetterMap.put(c, lwp);
				}
				occurrencesPerChar.merge(c, 1, Integer::sum);
				guessedWord.set(i, null);
			} else {
				guessedWord.set(i, null);
			}
		}
		occurrencesPerChar.forEach((k, v) -> {
			LetterOccurrence letterOccurrence = occurrencesMap.get(k);
			if (letterOccurrence == null) {
				letterOccurrence = new LetterOccurrence();
				occurrencesMap.put(k, letterOccurrence);
			}
			letterOccurrence.setMinOccurrencesIfLarger(v);
		});

		// Determines whether we can lock in any occurrences of any letter
		for (int i = 0; i < result.size(); i++) {
			PositionResult positionGuess = result.get(i);
			Character c = positionGuess.c;
			if (positionGuess.positionResultState.equals(PositionResultState.NO_MATCH)) {
				Integer occurrencesOfChar = occurrencesPerChar.get(c);
				if (occurrencesOfChar != null) {
					occurrencesMap.get(c).setOccurrences(occurrencesOfChar);
				} else {
					nonPresentChars.add(c);
				}
			}
		}
		updateLostLetters(guessedWord);
		if (detailedLog) {
			System.out.println(String.format("Lost letters %s", lostLetterMap));
			System.out.println(String.format("Occurrences: %s", occurrencesMap));
			System.out.println(String.format("Not present letters: %s", nonPresentChars));
		}
		return false;
	}

	/**
	 * Updates the {@link #lostLetterMap}, which should be done any time a new
	 * correct letter has been added
	 * 
	 * @param guessedWord Current guessed word with any matching letters populated
	 */
	void updateLostLetters(List<Character> guessedWord) {
		Iterator<Entry<Character, WordleSolver.LostLetter>> it = lostLetterMap.entrySet().iterator();
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
			System.out.println("[s]imulator, [i]interactive or [b]enchmark?");
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
			} else {
				System.out.println("Not a valid choice. Exiting.");
				return;
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
