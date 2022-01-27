package com.github.henkexbg.wordlesolver;

import java.util.List;

/**
 * Describes the authority that knows the actual word, and can give a result
 * based on a guessed word. The result states which character was correct, not
 * correct and in the wrong place.
 * 
 * @author Henrik
 *
 */
public interface WordleAuthority {

	List<PositionResult> giveResult(List<Character> guessedWord);

}
