package com.github.henkexbg.wordlesolver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Interactive authority that allows the end user to give the result for each
 * guess that the program makes. A special syntax has been derived. The user
 * must on one line give the result per character. For each character the choice
 * is:
 * <li>x - just the letter by itself. This means the letter is correct - x in
 * this case. Green in Wordle.</li>
 * <li>- - a hyphen means wrong guess. Grey in Wordle.</li>
 * <li>x- - a letter followed by a hyphen means letter exists but is in wrong
 * position. Yellow in Wordle.</li>
 * 
 * @author Henrik Bjerne
 *
 */
public class WordleAuthorityInteractive implements WordleAuthority {

	@Override
	public List<PositionResult> giveResult(List<Character> guessedWord) {
		System.out.println("GUESS: " + guessedWord);
		System.out.println("Enter result (see source for syntax):");
		List<String> tokens = readLineFromStdIn();
		List<PositionResult> result = new ArrayList<>(Arrays.asList(new PositionResult[guessedWord.size()]));

		// First loop - correct and non-present guesses populated
		for (int i = 0; i < guessedWord.size(); i++) {
			Character c = guessedWord.get(i);
			String token = tokens.get(i);
			if ("-".equals(token)) {
				result.set(i, new PositionResult(PositionResultState.NO_MATCH, c));
			} else if (token.length() > 1 && token.charAt(1) == '-') {
				result.set(i, new PositionResult(PositionResultState.OTHER_POSITION, c));
			} else if (token.charAt(0) == c.charValue()) {
				result.set(i, new PositionResult(PositionResultState.MATCH, c));
			} else {
				throw new IllegalArgumentException();
			}
		}
		System.out.println("Result: " + result);
		return result;
	}

	/**
	 * Helper method for reading a line
	 * 
	 * @return One line from STDIN, tokenized by space.
	 */
	private List<String> readLineFromStdIn() {
		List<String> tokens = new ArrayList<>();
		BufferedReader br = null;

		try {
			br = new BufferedReader(new InputStreamReader(System.in));
			StringTokenizer st = new StringTokenizer(br.readLine());

			while (st != null && st.hasMoreElements()) {
				tokens.add(st.nextToken());
			}

		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
		return tokens;
	}

}
