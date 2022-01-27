package com.github.henkexbg.wordlesolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Fully functioning authority that is configured with one word, and will give
 * results based on that word.
 * 
 * @author Henrik
 *
 */
public class WordleAuthoritySim implements WordleAuthority {

	private List<Character> actualWord;

	public void setActualWord(List<Character> actualWord) {
		this.actualWord = actualWord;
	}

	@Override
	public List<PositionResult> giveResult(List<Character> guessedWord) {
		List<PositionResult> result = new ArrayList<>(Arrays.asList(new PositionResult[actualWord.size()]));

		// Keep track of all characters so that it can be determined at the end whether
		// a non-exact match is in another position or no match at all. This collection
		// will have characters removed as they are "used"
		Collection<Character> unsortedActualChars = new LinkedList<>(actualWord);

		// First loop - correct and non-present guesses populated
		for (int i = 0; i < guessedWord.size(); i++) {
			Character c = guessedWord.get(i);
			if (c != actualWord.get(i)) {
				if (!actualWord.contains(c)) {
					unsortedActualChars.remove(c);
					result.set(i, new PositionResult(PositionResultState.NO_MATCH, c));
				}
			} else {
				result.set(i, new PositionResult(PositionResultState.MATCH, c));
				unsortedActualChars.remove(c);
			}
		}

		// Second loop - letters in wrong position
		for (int i = 0; i < guessedWord.size(); i++) {
			Character c = guessedWord.get(i);
			if (result.get(i) == null) {
				if (unsortedActualChars.contains(c)) {
					result.set(i, new PositionResult(PositionResultState.OTHER_POSITION, c));
					unsortedActualChars.remove(c);
				} else {
					result.set(i, new PositionResult(PositionResultState.NO_MATCH, c));
				}
			}
		}
		System.out.println("Result: " + result);
		return result;
	}

}
